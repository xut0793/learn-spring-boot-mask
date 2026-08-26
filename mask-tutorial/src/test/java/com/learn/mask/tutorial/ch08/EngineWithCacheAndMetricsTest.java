package com.learn.mask.tutorial.ch08;

import com.learn.mask.tutorial.ch04.MaskStrategyRegistry;
import com.learn.mask.tutorial.ch04.SensitiveType;
import com.learn.mask.tutorial.ch06.AlreadyMaskedDetector;
import com.learn.mask.tutorial.ch06.MaskEngine;
import com.learn.mask.tutorial.ch07.MaskingProperties;
import com.learn.mask.tutorial.ch07.MaskingReloadService;
import com.learn.mask.tutorial.ch07.ReloadRequest;
import com.learn.mask.tutorial.ch07.RuleConfig;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 第 4~8 章的所有零件装在一起：真实配置 + 真实缓存 + 真实指标 + 热更新。
 * <p>
 * 这是第二部分的收尾测试。第 6 章的引擎里那两个 {@code NO_OP} 位置，
 * 到这里终于换上了真东西，而**引擎本身一行都没改**。
 */
class EngineWithCacheAndMetricsTest {

    private MaskingProperties properties;
    private CaffeineMaskCache cache;
    private MeterRegistry registry;
    private MaskEngine engine;
    private MaskingReloadService reloadService;

    @BeforeEach
    void setUp() {
        properties = new MaskingProperties();
        cache = new CaffeineMaskCache(CacheOptions.defaults(), properties::getRuleVersion);
        registry = new SimpleMeterRegistry();
        MaskCacheMetrics.bind(registry, cache);
        engine = new MaskEngine(properties, MaskStrategyRegistry.withBuiltins(), cache,
                new AlreadyMaskedDetector(), new MicrometerMaskRecorder(registry));
        reloadService = new MaskingReloadService(properties, cache);
    }

    @Test
    @DisplayName("完整链路：脱敏 → 缓存命中 → 指标记录")
    void fullPath() {
        assertThat(engine.apply("13812345678", SensitiveType.PHONE, null)).isEqualTo("138****5678");
        assertThat(engine.apply("13812345678", SensitiveType.PHONE, null)).isEqualTo("138****5678");

        assertThat(registry.get("masking.invoke")
                .tag("type", "PHONE").tag("role", "USER").tag("result", "mask")
                .counter().count())
                .as("两次调用都记成 mask，缓存命中不单独区分")
                .isEqualTo(2);
        assertThat(cache.stats().hitCount()).isEqualTo(1);
        assertThat(cache.stats().missCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("热更新后缓存自动失效，引擎按新规则重算")
    void reloadInvalidatesCache() {
        assertThat(engine.apply("13812345678", SensitiveType.PHONE, null)).isEqualTo("138****5678");
        assertThat(cache.estimatedSize()).isEqualTo(1);

        reloadService.reload(ReloadRequest.ofRules(Map.of("PHONE", new RuleConfig(3, 0))));

        assertThat(engine.apply("13812345678", SensitiveType.PHONE, null)).isEqualTo("138********");
        assertThat(properties.getRuleVersion()).isEqualTo(2);
    }

    @Test
    @DisplayName("幂等跳过既不查缓存也不占缓存，但会被单独计数")
    void idempotentSkipIsVisibleInMetrics() {
        engine.apply("138****5678", SensitiveType.PHONE, null);

        assertThat(cache.estimatedSize()).isZero();
        assertThat(cache.stats().requestCount()).as("连缓存都没查").isZero();
        assertThat(registry.get("masking.skipped").tag("type", "PHONE").counter().count()).isEqualTo(1);
    }

    @Test
    @DisplayName("缓存指标已绑定，可以直接读出来")
    void cacheGaugesAreBound() {
        engine.apply("13812345678", SensitiveType.PHONE, null);
        engine.apply("13812345678", SensitiveType.PHONE, null);

        assertThat(registry.get("masking.cache.size").gauge().value()).isEqualTo(1.0);
        assertThat(registry.get("masking.cache.hit.rate").gauge().value()).isEqualTo(0.5);
        assertThat(registry.get("masking.cache.eviction").gauge().value()).isZero();
    }

    @Test
    @DisplayName("总开关关闭时全部旁路，缓存不增长")
    void disabledEngineDoesNotPopulateCache() {
        reloadService.reload(new ReloadRequest(false, null));

        assertThat(engine.apply("13812345678", SensitiveType.PHONE, null)).isEqualTo("13812345678");
        assertThat(cache.estimatedSize()).isZero();
        assertThat(registry.get("masking.bypass").tag("role", "USER").counter().count()).isEqualTo(1);
    }
}
