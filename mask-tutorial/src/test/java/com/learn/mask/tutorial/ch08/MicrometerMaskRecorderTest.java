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

    /** java.time.Duration ?? ofMicros??????? */
    private static Duration micros(long value) {
        return Duration.ofNanos(value * 1_000);
    }

    @Test
    @DisplayName("????????")
    void recordsAllTags() {
        recorder.record("PHONE", MaskRole.USER, MaskAction.MASK, micros(20));

        assertThat(registry.get("masking.invoke")
                .tag("type", "PHONE")
                .tag("role", "USER")
                .tag("result", "mask")
                .counter().count()).isEqualTo(1);
    }

    @Test
    @DisplayName("result ??????type ? role ????")
    void resultTagIsLowercased() {
        recorder.record("PHONE", MaskRole.USER, MaskAction.SKIP_ALREADY_MASKED, micros(5));

        assertThat(registry.get("masking.invoke")
                .tag("result", "skip_already_masked")
                .counter().count()).isEqualTo(1);
        assertThat(registry.find("masking.invoke").tag("result", "SKIP_ALREADY_MASKED").counter())
                .as("????? ?? ??? 3 ????????")
                .isNull();
    }

    @Test
    @DisplayName("???? Timer???? result ??")
    void durationHasNoResultTag() {
        recorder.record("PHONE", MaskRole.USER, MaskAction.MASK, micros(40));
        recorder.record("PHONE", MaskRole.USER, MaskAction.BYPASS, micros(10));

        assertThat(registry.get("masking.duration")
                .tag("type", "PHONE")
                .tag("role", "USER")
                .timer().count())
                .as("??????????????")
                .isEqualTo(2);
        assertThat(registry.get("masking.duration").timer()
                .totalTime(TimeUnit.MICROSECONDS)).isEqualTo(50);
    }

    @Test
    @DisplayName("FAIL ??????????")
    void failHasDedicatedCounter() {
        recorder.record("PHONE", MaskRole.USER, MaskAction.FAIL, micros(30));

        assertThat(registry.get("masking.fail").tag("type", "PHONE").counter().count()).isEqualTo(1);
        assertThat(registry.find("masking.bypass").counter()).isNull();
        assertThat(registry.find("masking.skipped").counter()).isNull();
    }

    @Test
    @DisplayName("BYPASS ????????????????")
    void bypassCounterIsTaggedByRoleOnly() {
        recorder.record("PHONE", MaskRole.ADMIN, MaskAction.BYPASS, micros(3));
        recorder.record("EMAIL", MaskRole.ADMIN, MaskAction.BYPASS, micros(3));

        assertThat(registry.get("masking.bypass").tag("role", "ADMIN").counter().count())
                .as("????????????????????????")
                .isEqualTo(2);
    }

    @Test
    @DisplayName("SKIP_ALREADY_MASKED ?????????? ?? ????????")
    void skippedCounterIsTaggedByType() {
        recorder.record("PHONE", MaskRole.USER, MaskAction.SKIP_ALREADY_MASKED, micros(4));

        assertThat(registry.get("masking.skipped").tag("type", "PHONE").counter().count()).isEqualTo(1);
    }

    @Test
    @DisplayName("null ???????????")
    void nullTagsFallBackToUnknown() {
        recorder.record(null, null, null, micros(1));

        assertThat(registry.get("masking.invoke")
                .tag("type", "unknown")
                .tag("role", "unknown")
                .tag("result", "unknown")
                .counter().count()).isEqualTo(1);
    }

    @Test
    @DisplayName("????????? unknown")
    void blankTypeCodeFallsBack() {
        recorder.record("   ", MaskRole.USER, MaskAction.MASK, micros(1));

        assertThat(registry.get("masking.invoke").tag("type", "unknown").counter().count()).isEqualTo(1);
    }

    @Test
    @DisplayName("??????????????????????????")
    void repeatedRecordsAccumulate() {
        for (int i = 0; i < 100; i++) {
            recorder.record("PHONE", MaskRole.USER, MaskAction.MASK, micros(10));
        }

        assertThat(registry.get("masking.invoke").tag("type", "PHONE").counter().count()).isEqualTo(100);
        assertThat(registry.find("masking.invoke").counters()).hasSize(1);
    }
}
