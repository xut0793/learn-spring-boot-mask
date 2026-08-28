package com.learn.mask.metrics;

import com.learn.mask.annotation.SensitiveType;
import com.learn.mask.context.MaskRole;
import com.learn.mask.engine.MaskAction;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 脱敏次数、耗时、失败、旁路、幂等跳过等 Micrometer 指标。
 * Meter 引用按标签缓存，避免每次 {@code builder().register()}。
 */
public class MaskingMetrics implements MaskRecorder {

    private final MeterRegistry registry;
    private final ConcurrentHashMap<String, Counter> invoke = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Timer> duration = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Counter> fail = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Counter> bypass = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Counter> skipped = new ConcurrentHashMap<>();

    public MaskingMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void record(SensitiveType type, MaskRole role, MaskAction action, Duration duration) {
        record(type == null ? "unknown" : type.name(), role, action, duration);
    }

    public void record(String typeCode, MaskRole role, MaskAction action, Duration elapsed) {
        String typeTag = (typeCode == null || typeCode.isBlank()) ? "unknown" : typeCode;
        String roleTag = role == null ? "unknown" : role.name();
        String result = action == null ? "unknown" : action.name().toLowerCase();
        invoke.computeIfAbsent(typeTag + "|" + roleTag + "|" + result, key -> Counter.builder("masking.invoke")
                        .tag("type", typeTag)
                        .tag("role", roleTag)
                        .tag("result", result)
                        .register(registry))
                .increment();
        duration.computeIfAbsent(typeTag + "|" + roleTag, key -> Timer.builder("masking.duration")
                        .tag("type", typeTag)
                        .tag("role", roleTag)
                        .register(registry))
                .record(elapsed);
        if (action == MaskAction.FAIL) {
            fail.computeIfAbsent(typeTag, key -> Counter.builder("masking.fail")
                            .tag("type", typeTag)
                            .register(registry))
                    .increment();
        }
        if (action == MaskAction.BYPASS) {
            bypass.computeIfAbsent(roleTag, key -> Counter.builder("masking.bypass")
                            .tag("role", roleTag)
                            .register(registry))
                    .increment();
        }
        if (action == MaskAction.SKIP_ALREADY_MASKED) {
            skipped.computeIfAbsent(typeTag, key -> Counter.builder("masking.skipped")
                            .tag("type", typeTag)
                            .register(registry))
                    .increment();
        }
    }
}
