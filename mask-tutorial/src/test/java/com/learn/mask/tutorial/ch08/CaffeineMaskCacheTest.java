package com.learn.mask.tutorial.ch08;

import com.github.benmanes.caffeine.cache.Ticker;
import com.learn.mask.tutorial.ch04.SensitiveType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

class CaffeineMaskCacheTest {

    /** 手动推进的时钟，用来测过期而不用真的等。 */
    static class ManualTicker implements Ticker {
        private long nanos;

        @Override
        public long read() {
            return nanos;
        }

        void advance(Duration duration) {
            nanos += duration.toNanos();
        }
    }

    private final AtomicLong ruleVersion = new AtomicLong(1);

    private CaffeineMaskCache cache(CacheOptions options) {
        return new CaffeineMaskCache(options, ruleVersion::get);
    }

    @Nested
    @DisplayName("基本读写")
    class Basics {

        @Test
        void putThenGet() {
            CaffeineMaskCache cache = cache(CacheOptions.defaults());

            assertThat(cache.get("PHONE", "13812345678")).isNull();
            cache.put("PHONE", "13812345678", "138****5678");
            assertThat(cache.get("PHONE", "13812345678")).isEqualTo("138****5678");
        }

        @Test
        @DisplayName("关闭后读写都是 no-op")
        void disabledIsNoOp() {
            CaffeineMaskCache cache = cache(CacheOptions.disabled());

            cache.put("PHONE", "13812345678", "138****5678");
            assertThat(cache.get("PHONE", "13812345678")).isNull();
        }

        @Test
        @DisplayName("null 明文和 null 结果都不写不读")
        void nullsAreIgnored() {
            CaffeineMaskCache cache = cache(CacheOptions.defaults());

            cache.put("PHONE", null, "138****5678");
            cache.put("PHONE", "13812345678", null);

            assertThat(cache.get("PHONE", null)).isNull();
            assertThat(cache.get("PHONE", "13812345678")).isNull();
            assertThat(cache.estimatedSize()).isZero();
        }

        @Test
        void invalidateAllClears() {
            CaffeineMaskCache cache = cache(CacheOptions.defaults());
            cache.put("PHONE", "13812345678", "138****5678");

            cache.invalidateAll();

            assertThat(cache.get("PHONE", "13812345678")).isNull();
            assertThat(cache.estimatedSize()).isZero();
        }
    }

    @Nested
    @DisplayName("缓存 key 的三个组成部分")
    class KeyComposition {

        @Test
        @DisplayName("类型不同则互不干扰")
        void typeIsPartOfKey() {
            CaffeineMaskCache cache = cache(CacheOptions.defaults());
            cache.put(SensitiveType.PHONE.name(), "13812345678", "138****5678");
            cache.put(SensitiveType.CUSTOM.name(), "13812345678", "1*********8");

            assertThat(cache.get("PHONE", "13812345678")).isEqualTo("138****5678");
            assertThat(cache.get("CUSTOM", "13812345678")).isEqualTo("1*********8");
        }

        @Test
        @DisplayName("类型编码大小写不敏感")
        void typeCodeIsNormalized() {
            CaffeineMaskCache cache = cache(CacheOptions.defaults());
            cache.put("phone", "13812345678", "138****5678");

            assertThat(cache.get("PHONE", "13812345678")).isEqualTo("138****5678");
            assertThat(cache.get(" Phone ", "13812345678")).isEqualTo("138****5678");
        }

        @Test
        @DisplayName("规则版本变化后旧条目立刻不可达 —— 不依赖任何清理动作")
        void ruleVersionInvalidatesWithoutCleanup() {
            CaffeineMaskCache cache = cache(CacheOptions.defaults());
            cache.put("PHONE", "13812345678", "138****5678");
            assertThat(cache.get("PHONE", "13812345678")).isEqualTo("138****5678");

            ruleVersion.incrementAndGet();

            assertThat(cache.get("PHONE", "13812345678"))
                    .as("版本号变了，旧 key 查不到")
                    .isNull();
            assertThat(cache.estimatedSize())
                    .as("但旧条目还占着内存，所以 invalidateAll 仍然有价值")
                    .isEqualTo(1);
        }

        @Test
        @DisplayName("版本号回退后旧条目又能命中 —— 说明失效是靠 key 而不是删除")
        void revertingVersionRestoresVisibility() {
            CaffeineMaskCache cache = cache(CacheOptions.defaults());
            cache.put("PHONE", "13812345678", "138****5678");
            ruleVersion.incrementAndGet();
            assertThat(cache.get("PHONE", "13812345678")).isNull();

            ruleVersion.decrementAndGet();

            assertThat(cache.get("PHONE", "13812345678")).isEqualTo("138****5678");
        }
    }

    @Nested
    @DisplayName("容量与过期")
    class EvictionAndExpiry {

        @Test
        @DisplayName("超过 maxSize 后会淘汰")
        void evictsBeyondMaxSize() {
            CaffeineMaskCache cache = cache(new CacheOptions(true, 10, Duration.ofMinutes(10)));

            for (int i = 0; i < 100; i++) {
                cache.put("PHONE", "1381234" + i, "138****" + i);
            }

            assertThat(cache.estimatedSize())
                    .as("Caffeine 的淘汰是近似的，允许短暂超出，但不该接近 100")
                    .isLessThanOrEqualTo(10);
            assertThat(cache.stats().evictionCount()).isPositive();
        }

        @Test
        @DisplayName("过期时间到了就淘汰 —— 用可注入时钟测，不用真的等")
        void expiresAfterAccess() {
            ManualTicker ticker = new ManualTicker();
            CaffeineMaskCache cache = new CaffeineMaskCache(
                    new CacheOptions(true, 100, Duration.ofMinutes(10)), ruleVersion::get, ticker);

            cache.put("PHONE", "13812345678", "138****5678");
            assertThat(cache.get("PHONE", "13812345678")).isEqualTo("138****5678");

            ticker.advance(Duration.ofMinutes(9));
            assertThat(cache.get("PHONE", "13812345678"))
                    .as("9 分钟没到期")
                    .isEqualTo("138****5678");

            // 上一次 get 刷新了访问时间，所以从这里再算 10 分钟
            ticker.advance(Duration.ofMinutes(11));
            assertThat(cache.get("PHONE", "13812345678"))
                    .as("距上次访问超过 10 分钟，已过期")
                    .isNull();
        }

        @Test
        @DisplayName("expireAfterAccess 会被访问刷新 —— 热点数据不会被时间赶走")
        void accessRefreshesExpiry() {
            ManualTicker ticker = new ManualTicker();
            CaffeineMaskCache cache = new CaffeineMaskCache(
                    new CacheOptions(true, 100, Duration.ofMinutes(10)), ruleVersion::get, ticker);
            cache.put("PHONE", "13812345678", "138****5678");

            // 每 9 分钟访问一次，连续 5 次共 45 分钟，远超 10 分钟的过期时间
            for (int i = 0; i < 5; i++) {
                ticker.advance(Duration.ofMinutes(9));
                assertThat(cache.get("PHONE", "13812345678")).isEqualTo("138****5678");
            }
        }

        @Test
        @DisplayName("非法配置被钳制，不会让 Caffeine 抛异常")
        void invalidOptionsAreSanitized() {
            CaffeineMaskCache cache = cache(new CacheOptions(true, 0, Duration.ZERO));

            cache.put("PHONE", "13812345678", "138****5678");
            assertThat(cache.get("PHONE", "13812345678")).isEqualTo("138****5678");
        }
    }

    @Nested
    @DisplayName("统计")
    class Stats {

        @Test
        @DisplayName("命中率能反映实际情况")
        void hitRateIsMeaningful() {
            CaffeineMaskCache cache = cache(CacheOptions.defaults());
            cache.put("PHONE", "13812345678", "138****5678");

            cache.get("PHONE", "13812345678");     // 命中
            cache.get("PHONE", "13812345678");     // 命中
            cache.get("PHONE", "13900000000");     // 未命中

            assertThat(cache.stats().hitCount()).isEqualTo(2);
            assertThat(cache.stats().missCount()).isEqualTo(1);
            assertThat(cache.stats().hitRate()).isCloseTo(2.0 / 3, org.assertj.core.data.Offset.offset(0.001));
        }
    }
}
