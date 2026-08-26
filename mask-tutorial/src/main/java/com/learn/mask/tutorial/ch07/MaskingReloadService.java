package com.learn.mask.tutorial.ch07;

import com.learn.mask.tutorial.ch04.MaskStrategyRegistry;
import com.learn.mask.tutorial.ch04.SensitiveType;
import com.learn.mask.tutorial.ch06.MaskResultCache;

import java.util.Map;

/**
 * 不重启改规则。
 * <p>
 * 逻辑放在 Service 而不是 Controller 里，因为它有真实的并发语义需要测试，
 * 而这些测试不该被 HTTP 层的模拟拖慢（第 7 章 7.8 节）。
 */
public class MaskingReloadService {

    private final MaskingProperties properties;
    private final MaskResultCache cache;

    /** 保证多个并发 reload 请求串行执行，避免两次更新交错。 */
    private final Object reloadLock = new Object();

    public MaskingReloadService(MaskingProperties properties, MaskResultCache cache) {
        this.properties = properties;
        this.cache = cache == null ? MaskResultCache.NO_OP : cache;
    }

    /**
     * 应用变更并发布。
     * <p>
     * <b>四步的顺序是本方法的全部难点</b>，任何两步互换都会引入 bug，见 7.6 节：
     * <ol>
     *   <li>写入可变的 {@code RuleSet}（此时引擎还看不到，因为它只读快照）</li>
     *   <li>重建并原子发布快照（引擎从这一刻起看到新规则）</li>
     *   <li>提升规则版本（旧缓存条目从这一刻起不可达）</li>
     *   <li>清空缓存（回收内存；正确性已由第 3 步保证）</li>
     * </ol>
     *
     * @return 变更后的规则版本号
     */
    public long reload(ReloadRequest request) {
        synchronized (reloadLock) {
            if (request != null) {
                if (request.enabled() != null) {
                    properties.setEnabled(request.enabled());
                }
                if (request.rules() != null) {
                    request.rules().forEach(this::applyRule);
                }
            }
            // 第 2 步在第 3 步之前：如果先提版本号，就会有请求用**旧规则**算出结果、
            // 却缓存在**新版本**的 key 下，那条陈旧结果会一直被命中
            properties.rebuildSnapshot();
            properties.bumpRuleVersion();
            cache.invalidateAll();
            return properties.getRuleVersion();
        }
    }

    /**
     * 把一条传入规则合并到对应的 {@link RuleConfig} 上。
     * <p>
     * 注意这里是**合并**而不是替换：负数表示「这一项不改」。
     * 和 {@link ReloadRequest} 用包装类型是同一个考虑——
     * 部分更新接口必须能表达「不改」。
     */
    private void applyRule(String code, RuleConfig incoming) {
        if (code == null || incoming == null) {
            return;
        }
        RuleConfig target = configOf(MaskStrategyRegistry.normalize(code));
        if (incoming.getKeepPrefix() >= 0) {
            target.setKeepPrefix(incoming.getKeepPrefix());
        }
        if (incoming.getKeepSuffix() >= 0) {
            target.setKeepSuffix(incoming.getKeepSuffix());
        }
        // 这两行是**无条件覆盖**，和上面两行的「负数表示不改」不一致。
        // 后果：一次只想改保留位数的调用，会把这条规则的 enabled 重置成请求体里的值——
        // 如果这条规则原本被主动关闭，它会被意外打开。
        // 这里刻意保留了和 mask-starter 相同的写法，练习 7.3 会分析它并给出修法。
        target.setMaskChar(incoming.getMaskChar());
        target.setEnabled(incoming.isEnabled());
    }

    /** 内置类型返回具名字段，未知编码在 extras 里按需创建。 */
    private RuleConfig configOf(String normalizedCode) {
        RuleSet rules = properties.getRules();
        SensitiveType type = parseType(normalizedCode);
        if (type != null) {
            return switch (type) {
                case PHONE -> rules.getPhone();
                case ID_CARD -> rules.getIdCard();
                case BANK_CARD -> rules.getBankCard();
                case EMAIL -> rules.getEmail();
                case CUSTOM -> rules.getCustom();
            };
        }
        Map<String, RuleConfig> extras = rules.getExtras();
        return extras.computeIfAbsent(normalizedCode, key -> new RuleConfig());
    }

    private static SensitiveType parseType(String normalizedCode) {
        try {
            return SensitiveType.valueOf(normalizedCode);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
