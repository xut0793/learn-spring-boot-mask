package com.learn.mask.metrics;

import com.learn.mask.context.MaskRole;
import com.learn.mask.engine.MaskAction;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class MaskingMetricsTest {

    private final MeterRegistry registry = new SimpleMeterRegistry();
    private final MaskingMetrics recorder = new MaskingMetrics(registry);

    private static Duration micros(long value) {
        return Duration.ofNanos(value * 1_000);
    }

    @Test
    @DisplayName("masking.invoke 带上 type / role / 小写 result")
    void recordsAllTags() {
        recorder.record("PHONE", MaskRole.USER, MaskAction.MASK, micros(20));

        assertThat(registry.get("masking.invoke")
                .tag("type", "PHONE")
                .tag("role", "USER")
                .tag("result", "mask")
                .counter().count()).isEqualTo(1);
    }

    @Test
    @DisplayName("result 标签必须是小写，Prometheus 按枚举名大写查会落空")
    void resultTagIsLowercased() {
        recorder.record("PHONE", MaskRole.USER, MaskAction.SKIP_ALREADY_MASKED, micros(5));

        assertThat(registry.get("masking.invoke")
                .tag("result", "skip_already_masked")
                .counter().count()).isEqualTo(1);
        assertThat(registry.find("masking.invoke").tag("result", "SKIP_ALREADY_MASKED").counter())
                .isNull();
    }

    @Test
    @DisplayName("耗时 Timer 不带 result，同 type+role 的各动作累加到同一条")
    void durationHasNoResultTag() {
        recorder.record("PHONE", MaskRole.USER, MaskAction.MASK, micros(40));
        recorder.record("PHONE", MaskRole.USER, MaskAction.BYPASS, micros(10));

        assertThat(registry.get("masking.duration")
                .tag("type", "PHONE")
                .tag("role", "USER")
                .timer().count()).isEqualTo(2);
        assertThat(registry.get("masking.duration").timer().totalTime(TimeUnit.MICROSECONDS)).isEqualTo(50);
    }

    @Test
    @DisplayName("FAIL 走独立计数器，不记 bypass / skipped")
    void failHasDedicatedCounter() {
        recorder.record("PHONE", MaskRole.USER, MaskAction.FAIL, micros(30));

        assertThat(registry.get("masking.fail").tag("type", "PHONE").counter().count()).isEqualTo(1);
        assertThat(registry.find("masking.bypass").counter()).isNull();
        assertThat(registry.find("masking.skipped").counter()).isNull();
    }

    @Test
    @DisplayName("BYPASS 计数器只按 role 打标，不同类型会累加到同一条")
    void bypassCounterIsTaggedByRoleOnly() {
        recorder.record("PHONE", MaskRole.ADMIN, MaskAction.BYPASS, micros(3));
        recorder.record("EMAIL", MaskRole.ADMIN, MaskAction.BYPASS, micros(3));

        assertThat(registry.get("masking.bypass").tag("role", "ADMIN").counter().count()).isEqualTo(2);
    }

    @Test
    @DisplayName("DISABLED 不增加 masking.bypass —— 总开关关闭和角色旁路必须拆开")
    void disabledDoesNotIncrementBypass() {
        recorder.record("PHONE", MaskRole.USER, MaskAction.DISABLED, micros(2));

        assertThat(registry.get("masking.invoke").tag("result", "disabled").counter().count()).isEqualTo(1);
        assertThat(registry.find("masking.bypass").counter()).isNull();
    }

    @Test
    @DisplayName("SKIP_ALREADY_MASKED 按 type 计数")
    void skippedCounterIsTaggedByType() {
        recorder.record("PHONE", MaskRole.USER, MaskAction.SKIP_ALREADY_MASKED, micros(4));

        assertThat(registry.get("masking.skipped").tag("type", "PHONE").counter().count()).isEqualTo(1);
    }

    @Test
    @DisplayName("null / 空白标签回落 unknown")
    void nullAndBlankTagsFallBackToUnknown() {
        recorder.record((String) null, null, null, micros(1));
        recorder.record("   ", MaskRole.USER, MaskAction.MASK, micros(1));

        assertThat(registry.get("masking.invoke")
                .tag("type", "unknown")
                .tag("role", "unknown")
                .tag("result", "unknown")
                .counter().count()).isEqualTo(1);
        assertThat(registry.get("masking.invoke")
                .tag("type", "unknown")
                .tag("role", "USER")
                .tag("result", "mask")
                .counter().count()).isEqualTo(1);
    }
}
