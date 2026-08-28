package com.learn.mask.web;

import com.learn.mask.annotation.SensitiveType;
import com.learn.mask.cache.MaskCache;
import com.learn.mask.config.MaskRule;
import com.learn.mask.config.MaskingProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class MaskingReloadServiceTest {

    private MaskingProperties properties;
    private MaskingReloadService service;

    @BeforeEach
    void setUp() {
        properties = new MaskingProperties();
        service = new MaskingReloadService(properties, new MaskCache(properties));
    }

    @Test
    void ruleChangeTakesEffectAfterSnapshot() {
        assertThat(properties.ruleOf(SensitiveType.PHONE).keepSuffix()).isEqualTo(4);

        service.reload(phonePatch(3, 0));

        assertThat(properties.ruleOf(SensitiveType.PHONE)).isEqualTo(MaskRule.of(3, 0));
    }

    @Test
    void versionBumpsOnEachReload() {
        assertThat(properties.getRuleVersion()).isEqualTo(1);

        service.reload(phonePatch(3, 0));
        assertThat(properties.getRuleVersion()).isEqualTo(2);
        service.reload(null);
        assertThat(properties.getRuleVersion()).isEqualTo(3);
    }

    @Test
    void nullPatchFieldsDoNotOverwrite() {
        service.reload(new MaskingReloadService.ReloadRequest(
                null,
                null,
                Map.of("PHONE", new MaskingReloadService.RulePatch(null, null, 0, null))
        ));

        assertThat(properties.ruleOf(SensitiveType.PHONE).keepPrefix()).isEqualTo(3);
        assertThat(properties.ruleOf(SensitiveType.PHONE).keepSuffix()).isZero();
        assertThat(properties.ruleOf(SensitiveType.PHONE).enabled()).isTrue();
        assertThat(properties.isEnabled()).isTrue();
    }

    @Test
    void turningEnabledOffTakesEffect() {
        service.reload(new MaskingReloadService.ReloadRequest(false, null, null));
        assertThat(properties.isEnabled()).isFalse();
    }

    @Test
    void unknownCodeCreatesExtra() {
        service.reload(new MaskingReloadService.ReloadRequest(
                null,
                null,
                Map.of("express", new MaskingReloadService.RulePatch(null, 2, 4, null))
        ));

        assertThat(properties.ruleOf("EXPRESS")).isEqualTo(MaskRule.of(2, 4));
        assertThat(properties.getRules().getExtras()).containsKey("EXPRESS");
    }

    @Test
    void readerNeverSeesPartialRule() throws Exception {
        AtomicBoolean running = new AtomicBoolean(true);
        Map<String, Integer> observed = new ConcurrentHashMap<>();
        CountDownLatch started = new CountDownLatch(2);
        ExecutorService pool = Executors.newFixedThreadPool(2);

        try {
            pool.submit(() -> {
                started.countDown();
                boolean flip = false;
                while (running.get()) {
                    service.reload(flip ? phonePatch(3, 4) : phonePatch(6, 2));
                    flip = !flip;
                }
            });
            pool.submit(() -> {
                started.countDown();
                while (running.get()) {
                    MaskRule rule = properties.ruleOf(SensitiveType.PHONE);
                    observed.merge(rule.keepPrefix() + "/" + rule.keepSuffix(), 1, Integer::sum);
                }
            });

            assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
            Thread.sleep(300);
        } finally {
            running.set(false);
            pool.shutdown();
            assertThat(pool.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }

        assertThat(observed.keySet()).containsExactlyInAnyOrder("3/4", "6/2");
    }

    private static MaskingReloadService.ReloadRequest phonePatch(int keepPrefix, int keepSuffix) {
        return new MaskingReloadService.ReloadRequest(
                null,
                null,
                Map.of("PHONE", new MaskingReloadService.RulePatch(null, keepPrefix, keepSuffix, null))
        );
    }
}
