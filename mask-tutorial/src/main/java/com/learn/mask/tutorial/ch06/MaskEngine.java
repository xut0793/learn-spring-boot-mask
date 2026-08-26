package com.learn.mask.tutorial.ch06;

import com.learn.mask.tutorial.ch04.MaskRule;
import com.learn.mask.tutorial.ch04.MaskStrategy;
import com.learn.mask.tutorial.ch04.MaskStrategyRegistry;
import com.learn.mask.tutorial.ch04.MaskUtils;
import com.learn.mask.tutorial.ch04.SensitiveType;
import com.learn.mask.tutorial.ch05.MaskContext;
import com.learn.mask.tutorial.ch05.MaskRole;

import java.time.Duration;

/**
 * 脱敏引擎。**全项目唯一的脱敏入口。**
 * <p>
 * 第 4 章的策略、第 5 章的角色、第 7 章的配置、第 8 章的缓存和指标，全都在
 * {@link #apply} 这一个方法里汇合。四个通道（Jackson / Logback / MyBatis / AOP）
 * 都只调它，不自己做任何判断。
 * <p>
 * 为什么必须只有一个入口：如果四个通道各自实现「查规则 → 判角色 → 调策略」，
 * 那么「ADMIN 旁路」这条语义就有四份实现。改一次要改四处，漏一处就是一个
 * 「某个通道下 ADMIN 看不到明文」的诡异 bug。
 */
public class MaskEngine {

    private final MaskSettings settings;
    private final MaskStrategyRegistry registry;
    private final MaskResultCache cache;
    private final AlreadyMaskedDetector alreadyMaskedDetector;
    private final MaskRecorder recorder;

    public MaskEngine(MaskSettings settings,
                      MaskStrategyRegistry registry,
                      MaskResultCache cache,
                      AlreadyMaskedDetector alreadyMaskedDetector,
                      MaskRecorder recorder) {
        this.settings = settings;
        this.registry = registry;
        this.cache = cache == null ? MaskResultCache.NO_OP : cache;
        this.alreadyMaskedDetector = alreadyMaskedDetector;
        this.recorder = recorder == null ? MaskRecorder.NO_OP : recorder;
    }

    /** 按内置类型脱敏。 */
    public String apply(String raw, SensitiveType type, MaskContext context) {
        return apply(raw, type, type == null ? null : type.name(), context);
    }

    /**
     * 按类型编码脱敏。八步判定链，任何一步都可能提前返回。
     *
     * @param code 业务自定义编码，非空时优先于 {@code type}
     */
    public String apply(String raw, SensitiveType type, String code, MaskContext context) {
        // 计时从最开头开始：包含查规则、查策略、判角色的全部开销。
        // 只统计 strategy.mask() 会让指标显得很好看，但对定位「脱敏拖慢了接口」毫无帮助。
        long start = System.nanoTime();

        // 角色先解析出来。即使后面走了旁路分支，指标也需要知道是谁触发的。
        MaskRole role = context == null ? MaskRole.USER : context.current();
        String resolvedCode = resolveCode(code, type);

        try {
            // 第 1 步：空值与总开关。放在最前面因为最便宜，而且能挡掉大部分调用。
            if (MaskUtils.isBlank(raw) || !settings.isEnabled()) {
                return record(raw, resolvedCode, role, MaskAction.BYPASS, start);
            }

            // 第 2 步：角色旁路。必须在查规则之前 —— ADMIN 不该受任何规则配置影响。
            if (context != null && context.shouldBypass()) {
                return record(raw, resolvedCode, role, MaskAction.BYPASS, start);
            }

            // 第 3 步：查策略与规则。
            MaskStrategy strategy = registry.get(resolvedCode);
            MaskRule rule = settings.ruleOf(resolvedCode);

            // 第 4 步：规则缺失或被禁用 → 旁路。
            // 注意 strategy 理论上不会是 null（注册表会回落 CUSTOM），
            // 但仍然判一次：注册表可能被业务替换成一个会返回 null 的实现。
            if (strategy == null || rule == null || !rule.enabled()) {
                return record(raw, resolvedCode, role, MaskAction.BYPASS, start);
            }

            // 第 5 步：幂等跳过。必须在缓存之前 ——
            // 已打码的值不该进缓存，否则会占位并污染命中率统计。
            if (alreadyMaskedDetector.isAlreadyMasked(raw, strategy, rule)) {
                return record(raw, resolvedCode, role, MaskAction.SKIP_ALREADY_MASKED, start);
            }

            // 第 6 步：查缓存。
            String cached = cache.get(resolvedCode, raw);
            if (cached != null) {
                // 记成 MASK 而不是单独的 CACHE_HIT：对调用方而言语义相同，
                // 而缓存命中率应该由缓存组件自己的指标来度量（第 8 章）。
                return record(cached, resolvedCode, role, MaskAction.MASK, start);
            }

            // 第 7 步：真正脱敏。
            String masked = strategy.mask(raw, rule);

            // 第 8 步：回填缓存并返回。
            cache.put(resolvedCode, raw, masked);
            return record(masked, resolvedCode, role, MaskAction.MASK, start);

        } catch (RuntimeException ex) {
            // 记 FAIL 之后**重新抛出**，不吞异常。
            // 吞掉会返回 null 或明文：null 让业务拿到脏数据，明文直接是泄露。
            // 让异常冒出去，接口 500，问题立刻可见。见 6.8 节。
            record(raw, resolvedCode, role, MaskAction.FAIL, start);
            throw ex;
        }
    }

    /** 编码优先于枚举，两者都空时归一化成 CUSTOM。 */
    private static String resolveCode(String code, SensitiveType type) {
        if (code != null && !code.isBlank()) {
            return MaskStrategyRegistry.normalize(code);
        }
        return MaskStrategyRegistry.normalize(type == null ? null : type.name());
    }

    private String record(String result, String typeCode, MaskRole role, MaskAction action, long startNanos) {
        recorder.record(typeCode, role, action, Duration.ofNanos(System.nanoTime() - startNanos));
        return result;
    }
}
