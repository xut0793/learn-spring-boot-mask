package com.learn.mask.metrics;

import com.learn.mask.annotation.SensitiveType;
import com.learn.mask.context.MaskRole;
import com.learn.mask.engine.MaskAction;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import java.time.Duration;

/**
 * 脱敏次数、耗时、失败、旁路、幂等跳过等 Micrometer 指标。
 */
public class MaskingMetrics {

    private final MeterRegistry registry;

    public MaskingMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void record(SensitiveType type, MaskRole role, MaskAction action, Duration duration) {
        record(type == null ? "unknown" : type.name(), role, action, duration);
    }

    public void record(String typeCode, MaskRole role, MaskAction action, Duration duration) {
        String typeTag = (typeCode == null || typeCode.isBlank()) ? "unknown" : typeCode;
        String roleTag = role == null ? "unknown" : role.name();
        String result = action == null ? "unknown" : action.name().toLowerCase();
        Counter.builder("masking.invoke")
                .tag("type", typeTag)
                .tag("role", roleTag)
                .tag("result", result)
                .register(registry)
                .increment();
        Timer.builder("masking.duration")
                .tag("type", typeTag)
                .tag("role", roleTag)
                .register(registry)
                .record(duration);
        if (action == MaskAction.FAIL) {
            Counter.builder("masking.fail")
                    .tag("type", typeTag)
                    .register(registry)
                    .increment();
        }
        if (action == MaskAction.BYPASS) {
            Counter.builder("masking.bypass")
                    .tag("role", roleTag)
                    .register(registry)
                    .increment();
        }
        if (action == MaskAction.SKIP_ALREADY_MASKED) {
            Counter.builder("masking.skipped")
                    .tag("type", typeTag)
                    .register(registry)
                    .increment();
        }
    }
}
