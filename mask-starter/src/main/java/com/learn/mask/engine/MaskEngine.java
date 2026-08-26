package com.learn.mask.engine;

import com.learn.mask.annotation.SensitiveType;
import com.learn.mask.cache.MaskCache;
import com.learn.mask.config.MaskRule;
import com.learn.mask.config.MaskingProperties;
import com.learn.mask.context.MaskContext;
import com.learn.mask.context.MaskRole;
import com.learn.mask.metrics.MaskingMetrics;
import com.learn.mask.strategy.MaskStrategy;
import com.learn.mask.strategy.MaskStrategyRegistry;
import com.learn.mask.support.MaskUtils;

import java.time.Duration;

/**
 * 脱敏引擎：角色旁路、幂等跳过、缓存与指标都在这里汇合，四个通道共用同一入口。
 */
public class MaskEngine {

    private final MaskingProperties properties;
    private final MaskStrategyRegistry registry;
    private final MaskCache cache;
    private final AlreadyMaskedDetector alreadyMaskedDetector;
    private final MaskingMetrics metrics;

    public MaskEngine(MaskingProperties properties,
                      MaskStrategyRegistry registry,
                      MaskCache cache,
                      AlreadyMaskedDetector alreadyMaskedDetector,
                      MaskingMetrics metrics) {
        this.properties = properties;
        this.registry = registry;
        this.cache = cache;
        this.alreadyMaskedDetector = alreadyMaskedDetector;
        this.metrics = metrics;
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
        String resolvedCode = MaskingProperties.normalizeCode(code, type);
        try {
            if (raw == null || MaskUtils.isBlank(raw) || !properties.isEnabled()) {
                record(resolvedCode, role, MaskAction.BYPASS, start);
                return raw;
            }
            if (context != null && context.shouldBypass()) {
                record(resolvedCode, role, MaskAction.BYPASS, start);
                return raw;
            }
            MaskStrategy strategy = registry.get(resolvedCode);
            MaskRule rule = properties.ruleOf(resolvedCode);
            if (strategy == null || rule == null || !rule.isEnabled()) {
                record(resolvedCode, role, MaskAction.BYPASS, start);
                return raw;
            }
            if (alreadyMaskedDetector.isAlreadyMasked(raw, strategy, rule)) {
                record(resolvedCode, role, MaskAction.SKIP_ALREADY_MASKED, start);
                return raw;
            }
            String cached = cache.get(resolvedCode, raw);
            if (cached != null) {
                record(resolvedCode, role, MaskAction.MASK, start);
                return cached;
            }
            String masked = strategy.mask(raw, rule);
            cache.put(resolvedCode, raw, masked);
            record(resolvedCode, role, MaskAction.MASK, start);
            return masked;
        } catch (RuntimeException ex) {
            record(resolvedCode, role, MaskAction.FAIL, start);
            throw ex;
        }
    }

    private void record(String typeCode, MaskRole role, MaskAction action, long startNanos) {
        if (metrics == null) {
            return;
        }
        metrics.record(typeCode, role, action, Duration.ofNanos(System.nanoTime() - startNanos));
    }
}
