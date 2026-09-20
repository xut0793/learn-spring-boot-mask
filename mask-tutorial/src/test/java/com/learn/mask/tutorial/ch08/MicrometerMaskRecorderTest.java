package com.learn.mask.tutorial.ch08;

import com.learn.mask.tutorial.ch05.MaskRole;
import com.learn.mask.tutorial.ch06.MaskAction;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class MicrometerMaskRecorderTest {

    private final MeterRegistry registry = new SimpleMeterRegistry();
    private final MicrometerMaskRecorder recorder = new MicrometerMaskRecorder(registry);

    /** java.time.Duration 没有 ofMicros，用纳秒换算。 */
    private static Duration micros(long value) {
        return Duration.ofNanos(value * 1_000);
    }

    @Test
    @DisplayName("三个标签都会打上")
    void recordsAllTags() {
        recorder.record("PHONE", MaskRole.USER, MaskAction.MASK, micros(20));

        assertThat(registry.get("masking.invoke")
                .tag("type", "PHONE")
                .tag("role", "USER")
                .tag("result", "mask")
                .counter().count()).isEqualTo(1);
    }

    @Test
    @DisplayName("result 标签是小写，type 和 role 保持大写")
    void resultTagIsLowercased() {
        recorder.record("PHONE", MaskRole.USER, MaskAction.SKIP_ALREADY_MASKED, micros(5));

        assertThat(registry.get("masking.invoke")
                .tag("result", "skip_already_masked")
                .counter().count()).isEqualTo(1);
        assertThat(registry.find("masking.invoke").tag("result", "SKIP_ALREADY_MASKED").counter())
                .as("大写查不到 —— 这是第 3 章实验里踩过的坑")
                .isNull();
    }

    @Test
    @DisplayName("耗时记进 Timer，且不带 result 标签")
    void durationHasNoResultTag() {
        recorder.record("PHONE", MaskRole.USER, MaskAction.MASK, micros(40));
        recorder.record("PHONE", MaskRole.USER, MaskAction.BYPASS, micros(10));

        assertThat(registry.get("masking.duration")
                .tag("type", "PHONE")
                .tag("role", "USER")
                .timer().count())
                .as("不同动作的耗时累加到同一条上")
                .isEqualTo(2);
        assertThat(registry.get("masking.duration").timer()
                .totalTime(TimeUnit.MICROSECONDS)).isEqualTo(50);
    }

    @Test
    @DisplayName("FAIL 走独立计数器，不记旁路")
    void failHasDedicatedCounter() {
        recorder.record("PHONE", MaskRole.USER, MaskAction.FAIL, micros(30));

        assertThat(registry.get("masking.fail").tag("type", "PHONE").counter().count()).isEqualTo(1);
        assertThat(registry.find("masking.bypass").counter()).isNull();
        assertThat(registry.find("masking.skipped").counter()).isNull();
    }

    @Test
    @DisplayName("BYPASS 只按角色打标，不同类型累加到一条")
    void bypassCounterIsTaggedByRoleOnly() {
        recorder.record("PHONE", MaskRole.ADMIN, MaskAction.BYPASS, micros(3));
        recorder.record("EMAIL", MaskRole.ADMIN, MaskAction.BYPASS, micros(3));

        assertThat(registry.get("masking.bypass").tag("role", "ADMIN").counter().count())
                .as("不同类型的旁路按同一角色累加到一条")
                .isEqualTo(2);
    }

    @Test
    @DisplayName("SKIP_ALREADY_MASKED 走独立计数器，只按 type 打标")
    void skippedCounterIsTaggedByType() {
        recorder.record("PHONE", MaskRole.USER, MaskAction.SKIP_ALREADY_MASKED, micros(4));

        assertThat(registry.get("masking.skipped").tag("type", "PHONE").counter().count()).isEqualTo(1);
    }

    @Test
    @DisplayName("null 标签值不会让打点抛异常")
    void nullTagsFallBackToUnknown() {
        recorder.record(null, null, null, micros(1));

        assertThat(registry.get("masking.invoke")
                .tag("type", "unknown")
                .tag("role", "unknown")
                .tag("result", "unknown")
                .counter().count()).isEqualTo(1);
    }

    @Test
    @DisplayName("空白类型编码回落为 unknown")
    void blankTypeCodeFallsBack() {
        recorder.record("   ", MaskRole.USER, MaskAction.MASK, micros(1));

        assertThat(registry.get("masking.invoke").tag("type", "unknown").counter().count()).isEqualTo(1);
    }

    @Test
    @DisplayName("相同标签重复打点会累加，不会新建多条序列")
    void repeatedRecordsAccumulate() {
        for (int i = 0; i < 100; i++) {
            recorder.record("PHONE", MaskRole.USER, MaskAction.MASK, micros(10));
        }

        assertThat(registry.get("masking.invoke").tag("type", "PHONE").counter().count()).isEqualTo(100);
        assertThat(registry.find("masking.invoke").counters()).hasSize(1);
    }
}
