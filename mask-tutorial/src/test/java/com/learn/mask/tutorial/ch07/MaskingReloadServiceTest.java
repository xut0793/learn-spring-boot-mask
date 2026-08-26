package com.learn.mask.tutorial.ch07;

import com.learn.mask.tutorial.ch04.MaskRule;
import com.learn.mask.tutorial.ch04.MaskStrategyRegistry;
import com.learn.mask.tutorial.ch04.SensitiveType;
import com.learn.mask.tutorial.ch06.AlreadyMaskedDetector;
import com.learn.mask.tutorial.ch06.MaskEngine;
import com.learn.mask.tutorial.ch06.MaskRecorder;
import com.learn.mask.tutorial.ch06.MaskResultCache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class MaskingReloadServiceTest {

    /** 记录 invalidateAll 被调了几次的极简缓存。 */
    static class TrackingCache implements MaskResultCache {
        private final Map<String, String> store = new ConcurrentHashMap<>();
        int invalidateCount;

        @Override
        public String get(String typeCode, String raw) {
            return store.get(typeCode + ":" + raw);
        }

        @Override
        public void put(String typeCode, String raw, String masked) {
            store.put(typeCode + ":" + raw, masked);
        }

        @Override
        public void invalidateAll() {
            invalidateCount++;
            store.clear();
        }

        int size() {
            return store.size();
        }
    }

    private MaskingProperties properties;
    private TrackingCache cache;
    private MaskingReloadService service;

    @BeforeEach
    void setUp() {
        properties = new MaskingProperties();
        cache = new TrackingCache();
        service = new MaskingReloadService(properties, cache);
    }

    private static ReloadRequest phoneRule(int keepPrefix, int keepSuffix) {
        Map<String, RuleConfig> rules = new HashMap<>();
        rules.put("PHONE", new RuleConfig(keepPrefix, keepSuffix));
        return ReloadRequest.ofRules(rules);
    }

    @Nested
    @DisplayName("基本行为")
    class Basics {

        @Test
        @DisplayName("改规则后引擎立刻看到新值")
        void ruleChangeTakesEffect() {
            assertThat(properties.ruleOf(SensitiveType.PHONE).keepSuffix()).isEqualTo(4);

            service.reload(phoneRule(3, 0));

            assertThat(properties.ruleOf(SensitiveType.PHONE))
                    .isEqualTo(new MaskRule(true, 3, 0, '*'));
        }

        @Test
        @DisplayName("每次 reload 都提升版本号")
        void versionBumps() {
            assertThat(properties.getRuleVersion()).isEqualTo(1);

            assertThat(service.reload(phoneRule(3, 0))).isEqualTo(2);
            assertThat(service.reload(null)).isEqualTo(3);
        }

        @Test
        @DisplayName("缓存被清空")
        void cacheIsInvalidated() {
            cache.put("PHONE", "13812345678", "138****5678");
            assertThat(cache.size()).isEqualTo(1);

            service.reload(phoneRule(3, 0));

            assertThat(cache.invalidateCount).isEqualTo(1);
            assertThat(cache.size()).isZero();
        }

        @Test
        @DisplayName("请求体为 null 时只提版本 + 清缓存，规则不变")
        void nullRequestOnlyBumpsAndClears() {
            service.reload(null);

            assertThat(properties.ruleOf(SensitiveType.PHONE).keepSuffix()).isEqualTo(4);
            assertThat(properties.getRuleVersion()).isEqualTo(2);
            assertThat(cache.invalidateCount).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("部分更新")
    class PartialUpdate {

        @Test
        @DisplayName("只传 PHONE 时其他类型不受影响")
        void onlyListedTypesChange() {
            service.reload(phoneRule(3, 0));

            assertThat(properties.ruleOf(SensitiveType.EMAIL))
                    .isEqualTo(new MaskRule(true, 1, 0, '*'));
            assertThat(properties.ruleOf(SensitiveType.ID_CARD))
                    .isEqualTo(new MaskRule(true, 6, 4, '*'));
        }

        @Test
        @DisplayName("enabled 传 null 表示不改总开关")
        void nullEnabledDoesNotTouchGlobalSwitch() {
            service.reload(phoneRule(3, 0));

            assertThat(properties.isEnabled())
                    .as("请求里没传 enabled，不该把组件关掉")
                    .isTrue();
        }

        @Test
        @DisplayName("enabled 传 false 才关闭总开关")
        void explicitFalseDisables() {
            service.reload(new ReloadRequest(false, null));

            assertThat(properties.isEnabled()).isFalse();
        }

        @Test
        @DisplayName("keepPrefix 传负数表示这一项不改")
        void negativeMeansUnchanged() {
            Map<String, RuleConfig> rules = new HashMap<>();
            RuleConfig incoming = new RuleConfig(-1, 0);
            rules.put("PHONE", incoming);
            service.reload(ReloadRequest.ofRules(rules));

            assertThat(properties.ruleOf(SensitiveType.PHONE).keepPrefix())
                    .as("传了 -1，应保留原来的 3")
                    .isEqualTo(3);
            assertThat(properties.ruleOf(SensitiveType.PHONE).keepSuffix()).isZero();
        }

        @Test
        @DisplayName("未知编码会在 extras 里创建新规则")
        void unknownCodeCreatesExtra() {
            Map<String, RuleConfig> rules = new HashMap<>();
            rules.put("express", new RuleConfig(2, 4));
            service.reload(ReloadRequest.ofRules(rules));

            assertThat(properties.ruleOf("EXPRESS")).isEqualTo(new MaskRule(true, 2, 4, '*'));
            assertThat(properties.getRules().getExtras())
                    .containsKey(MaskStrategyRegistry.normalize("express"));
        }
    }

    @Nested
    @DisplayName("端到端：热更新对引擎输出的影响")
    class EndToEnd {

        @Test
        @DisplayName("reload 之后同一个明文的打码结果变了")
        void engineOutputChangesAfterReload() {
            MaskEngine engine = new MaskEngine(properties, MaskStrategyRegistry.withBuiltins(),
                    cache, new AlreadyMaskedDetector(), MaskRecorder.NO_OP);

            assertThat(engine.apply("13812345678", SensitiveType.PHONE, null))
                    .isEqualTo("138****5678");

            service.reload(phoneRule(3, 0));

            assertThat(engine.apply("13812345678", SensitiveType.PHONE, null))
                    .as("缓存已失效，应按新规则重算")
                    .isEqualTo("138********");
        }

        @Test
        @DisplayName("如果不清缓存，旧结果会被继续命中 —— 所以 invalidateAll 是必需的")
        void staleResultWithoutInvalidation() {
            MaskEngine engine = new MaskEngine(properties, MaskStrategyRegistry.withBuiltins(),
                    cache, new AlreadyMaskedDetector(), MaskRecorder.NO_OP);
            engine.apply("13812345678", SensitiveType.PHONE, null);

            // 手动模拟「只改规则、不清缓存」
            properties.getRules().getPhone().setKeepSuffix(0);
            properties.rebuildSnapshot();

            assertThat(engine.apply("13812345678", SensitiveType.PHONE, null))
                    .as("本教程的 TrackingCache 的 key 里没有版本号，所以会命中旧结果")
                    .isEqualTo("138****5678");
        }
    }

    @Nested
    @DisplayName("并发下的原子性")
    class Concurrency {

        /**
         * 一边持续 reload，一边持续读规则，断言**读到的永远是一个完整的规则组合**，
         * 而不是「新的 keepPrefix + 旧的 keepSuffix」这种从未配置过的中间态。
         * <p>
         * 这个性质来自快照的整体替换。如果像 mask-starter 那样逐个 setter 修改
         * 共享的可变规则对象，这个测试就会失败（见 7.7 节）。
         */
        @Test
        @DisplayName("读到的规则永远是完整组合，不会出现中间态")
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
                        service.reload(flip ? phoneRule(3, 4) : phoneRule(6, 2));
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

            assertThat(observed.keySet())
                    .as("只应出现两种配置过的组合，不该有 3/2 或 6/4 这类中间态")
                    .containsExactlyInAnyOrder("3/4", "6/2");
        }
    }
}
