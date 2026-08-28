package com.learn.mask.web;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.learn.mask.annotation.SensitiveType;
import com.learn.mask.cache.MaskCache;
import com.learn.mask.config.MaskRule;
import com.learn.mask.config.MaskingProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

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
    private MaskCache cache;
    private MaskingReloadService service;

    @BeforeEach
    void setUp() {
        properties = new MaskingProperties();
        properties.getCache().setEnabled(true);
        cache = new MaskCache(properties);
        service = new MaskingReloadService(properties, cache);
    }

    private static MaskingReloadService.ReloadRequest phonePatch(int keepPrefix, int keepSuffix) {
        return new MaskingReloadService.ReloadRequest(
                null,
                null,
                Map.of("PHONE", new MaskingReloadService.RulePatch(null, keepPrefix, keepSuffix, null))
        );
    }

    @Nested
    @DisplayName("写入 RuleSet → 重建快照 → 提版本 → 清缓存")
    class ApplyOrder {

        @Test
        @DisplayName("规则变更在 rebuildSnapshot 之后立刻对 ruleOf 可见")
        void ruleChangeTakesEffectAfterSnapshot() {
            assertThat(properties.ruleOf(SensitiveType.PHONE).keepSuffix()).isEqualTo(4);

            service.reload(phonePatch(3, 0));

            assertThat(properties.ruleOf(SensitiveType.PHONE)).isEqualTo(MaskRule.of(3, 0));
        }

        @Test
        @DisplayName("每次 reload 都 bump 版本，request 为 null 也一样")
        void versionBumpsOnEachReload() {
            assertThat(properties.getRuleVersion()).isEqualTo(1);

            service.reload(phonePatch(3, 0));
            assertThat(properties.getRuleVersion()).isEqualTo(2);
            service.reload(null);
            assertThat(properties.getRuleVersion()).isEqualTo(3);
        }

        @Test
        @DisplayName("RulePatch 字段为 null 表示这一项不改")
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
        @DisplayName("热更新可以关掉总开关，并打 ERROR 提醒会出明文")
        void turningEnabledOffTakesEffectAndLogs() {
            ListAppender<ILoggingEvent> appender = new ListAppender<>();
            appender.start();
            Logger logger = (Logger) LoggerFactory.getLogger(MaskingReloadService.class);
            logger.addAppender(appender);
            try {
                service.reload(new MaskingReloadService.ReloadRequest(false, null, null));

                assertThat(properties.isEnabled()).isFalse();
                assertThat(appender.list)
                        .anyMatch(event -> event.getLevel() == Level.ERROR
                                && event.getFormattedMessage().contains("turned off"));
            } finally {
                logger.detachAppender(appender);
            }
        }

        @Test
        @DisplayName("未知编码会写进 extras，键已被归一化")
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
        @DisplayName("reload 后旧缓存不可达：版本变了，并且 invalidateAll 清掉了条目")
        void reloadInvalidatesCache() {
            cache.put("PHONE", "13812345678", "138****5678");
            assertThat(cache.get("PHONE", "13812345678")).isEqualTo("138****5678");

            service.reload(phonePatch(3, 0));

            assertThat(cache.get("PHONE", "13812345678")).isNull();
            assertThat(cache.estimatedSize()).isZero();
        }
    }

    @Nested
    @DisplayName("并发读快照看不到半新半旧规则")
    class ConcurrentSnapshot {

        @Test
        @DisplayName("读线程只能看到完整的 3/4 或 6/2，不会看到 3/2")
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
    }
}
