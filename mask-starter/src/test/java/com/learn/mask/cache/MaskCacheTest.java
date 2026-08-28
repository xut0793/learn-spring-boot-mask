package com.learn.mask.cache;

import com.learn.mask.annotation.SensitiveType;
import com.learn.mask.config.MaskingProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MaskCacheTest {

    private MaskingProperties properties;
    private MaskCache cache;

    @BeforeEach
    void setUp() {
        properties = new MaskingProperties();
        // starter 默认 cache.enabled=false，测真实缓存必须先打开
        properties.getCache().setEnabled(true);
        cache = new MaskCache(properties);
    }

    @Nested
    @DisplayName("开关与空值")
    class Gates {

        @Test
        @DisplayName("默认关闭时读写都是 no-op")
        void disabledByDefaultIsNoOp() {
            MaskingProperties defaults = new MaskingProperties();
            MaskCache closed = new MaskCache(defaults);

            closed.put("PHONE", "13812345678", "138****5678");
            assertThat(closed.get("PHONE", "13812345678")).isNull();
            assertThat(closed.estimatedSize()).isZero();
        }

        @Test
        @DisplayName("打开后可以 put / get")
        void putThenGet() {
            assertThat(cache.get("PHONE", "13812345678")).isNull();
            cache.put("PHONE", "13812345678", "138****5678");
            assertThat(cache.get("PHONE", "13812345678")).isEqualTo("138****5678");
        }

        @Test
        @DisplayName("null 明文和 null 结果都不写不读")
        void nullsAreIgnored() {
            cache.put("PHONE", null, "138****5678");
            cache.put("PHONE", "13812345678", null);

            assertThat(cache.get("PHONE", null)).isNull();
            assertThat(cache.get("PHONE", "13812345678")).isNull();
            assertThat(cache.estimatedSize()).isZero();
        }

        @Test
        @DisplayName("运行中关掉开关后读写立刻失效")
        void togglingEnabledOffStopsHits() {
            cache.put("PHONE", "13812345678", "138****5678");
            properties.getCache().setEnabled(false);

            assertThat(cache.get("PHONE", "13812345678")).isNull();
            cache.put("PHONE", "13900001111", "139****1111");
            properties.getCache().setEnabled(true);
            assertThat(cache.get("PHONE", "13900001111")).isNull();
        }
    }

    @Nested
    @DisplayName("缓存 key：版本 + 归一化编码 + 明文")
    class KeyComposition {

        @Test
        @DisplayName("类型不同则互不干扰")
        void typeIsPartOfKey() {
            cache.put(SensitiveType.PHONE, "13812345678", "138****5678");
            cache.put(SensitiveType.CUSTOM, "13812345678", "1*********8");

            assertThat(cache.get("PHONE", "13812345678")).isEqualTo("138****5678");
            assertThat(cache.get("CUSTOM", "13812345678")).isEqualTo("1*********8");
        }

        @Test
        @DisplayName("类型编码大小写不敏感")
        void typeCodeIsNormalized() {
            cache.put("phone", "13812345678", "138****5678");

            assertThat(cache.get("PHONE", "13812345678")).isEqualTo("138****5678");
            assertThat(cache.get(" Phone ", "13812345678")).isEqualTo("138****5678");
        }

        @Test
        @DisplayName("规则版本变化后旧条目立刻不可达 —— 不依赖任何清理动作")
        void ruleVersionInvalidatesWithoutCleanup() {
            cache.put("PHONE", "13812345678", "138****5678");
            assertThat(cache.get("PHONE", "13812345678")).isEqualTo("138****5678");

            properties.bumpRuleVersion();

            assertThat(cache.get("PHONE", "13812345678"))
                    .as("版本号变了，旧 key 查不到")
                    .isNull();
            assertThat(cache.estimatedSize())
                    .as("但旧条目还占着内存，所以 invalidateAll 仍然有价值")
                    .isEqualTo(1);
        }

        @Test
        @DisplayName("invalidateAll 真正清掉条目")
        void invalidateAllClears() {
            cache.put("PHONE", "13812345678", "138****5678");
            cache.invalidateAll();

            assertThat(cache.get("PHONE", "13812345678")).isNull();
            assertThat(cache.estimatedSize()).isZero();
        }
    }

    @Nested
    @DisplayName("容量与非法配置")
    class Capacity {

        @Test
        @DisplayName("写入超过 maxSize 不会抛异常，最近的条目仍能读到")
        void acceptsWritesBeyondMaxSize() {
            properties.getCache().setMaxSize(10);
            MaskCache small = new MaskCache(properties);

            for (int i = 0; i < 100; i++) {
                small.put("PHONE", "1381234" + i, "138****" + i);
            }

            // Caffeine 的窗口淘汰是异步的，单测里不一定立刻看到 evictionCount > 0
            assertThat(small.get("PHONE", "138123499")).isEqualTo("138****99");
        }

        @Test
        @DisplayName("maxSize=0 会被钳成至少 1，不会让 Caffeine 抛异常")
        void invalidOptionsAreSanitized() {
            properties.getCache().setMaxSize(0);
            properties.getCache().setExpireAfterAccessMinutes(0);
            MaskCache sanitized = new MaskCache(properties);

            sanitized.put("PHONE", "13812345678", "138****5678");
            assertThat(sanitized.get("PHONE", "13812345678")).isEqualTo("138****5678");
        }
    }
}
