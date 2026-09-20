package com.learn.mask.engine;

import com.learn.mask.annotation.SensitiveType;
import com.learn.mask.cache.MaskResultCache;
import com.learn.mask.config.MaskRule;
import com.learn.mask.config.MaskSettings;
import com.learn.mask.context.MaskContext;
import com.learn.mask.context.MaskRole;
import com.learn.mask.metrics.MaskRecorder;
import com.learn.mask.strategy.MaskStrategy;
import com.learn.mask.strategy.MaskStrategyRegistry;
import com.learn.mask.support.MaskUtils;

import java.time.Duration;

/**
 * 脱敏引擎：角色旁路、幂等跳过、缓存与指标都在这里汇合，四个通道共用同一入口。
 * 依赖 {@link MaskSettings} / {@link MaskResultCache} / {@link MaskRecorder}，不绑具体配置或 Caffeine。
 */
public class MaskEngine {

    private final MaskSettings settings;
    private final MaskStrategyRegistry registry;
    /** 可选；未配置 Caffeine 时为 NO_OP。 */
    private final MaskResultCache cache;
    private final AlreadyMaskedDetector alreadyMaskedDetector;
    /** 可选；未配置 Micrometer 时为 NO_OP。 */
    private final MaskRecorder recorder;

    /** 组装四通道共用的脱敏流水线：旁路 → 策略 → 幂等 → 缓存 → 打码。 */
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

    /**
     * 按内置类型与当前角色对明文脱敏。管理员旁路、已脱敏字符串、开关关闭时均原样返回。
     */
    public String apply(String raw, SensitiveType type, MaskContext context) {
        return apply(raw, type, type == null ? null : type.name(), context);
    }

    /**
     * 按类型编码脱敏。{@code code} 非空时优先按编码查找策略，用于业务自定义类型。
     */
    public String apply(String raw, SensitiveType type, String code, MaskContext context) {
        long start = System.nanoTime();
        MaskRole role = context == null ? MaskRole.USER : context.current();
        String resolvedCode = MaskStrategyRegistry.resolve(code, type);
        try {
            if (MaskUtils.isBlank(raw)) {
                return record(raw, resolvedCode, role, MaskAction.BYPASS, start);
            }
            if (!settings.isEnabled()) {
                return record(raw, resolvedCode, role, MaskAction.DISABLED, start);
            }
            if (context != null && context.shouldBypass()) {
                return record(raw, resolvedCode, role, MaskAction.BYPASS, start);
            }
            MaskStrategy strategy = registry.get(resolvedCode);
            MaskRule rule = settings.ruleOf(resolvedCode);
            if (strategy == null || rule == null || !rule.enabled()) {
                return record(raw, resolvedCode, role, MaskAction.BYPASS, start);
            }
            if (alreadyMaskedDetector.isAlreadyMasked(raw, strategy, rule)) {
                return record(raw, resolvedCode, role, MaskAction.SKIP_ALREADY_MASKED, start);
            }
            String cached = cache.get(resolvedCode, raw);
            if (cached != null) {
                return record(cached, resolvedCode, role, MaskAction.MASK, start);
            }
            String masked = strategy.mask(raw, rule);
            cache.put(resolvedCode, raw, masked);
            return record(masked, resolvedCode, role, MaskAction.MASK, start);
        } catch (RuntimeException ex) {
            record(raw, resolvedCode, role, MaskAction.FAIL, start);
            throw ex;
        }
    }

    /** 统一写指标并返回业务结果，不改变 apply 的返回值语义。 */
    private String record(String result, String typeCode, MaskRole role, MaskAction action, long startNanos) {
        recorder.record(typeCode, role, action, Duration.ofNanos(System.nanoTime() - startNanos));
        return result;
    }
}
