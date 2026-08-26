package com.learn.mask.tutorial.ch08;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * 把 Caffeine 的内部统计暴露成指标。
 * <p>
 * 第 6 章 6.6 节说过「缓存命中率应该由缓存组件自己度量」，这就是那句话的落地。
 * {@code masking.invoke{result=mask}} 回答「脱敏了多少次」，
 * 这里的指标回答「缓存配得对不对」——两个不同的问题，两组不同的指标。
 * <p>
 * 用 {@link Gauge} 而不是 Counter，因为这些值是「当前状态的快照」，
 * 由 Caffeine 内部维护，我们只是读出来。Counter 的语义是「我们自己累加」。
 */
public final class MaskCacheMetrics {

    private MaskCacheMetrics() {
    }

    public static void bind(MeterRegistry registry, CaffeineMaskCache cache) {
        Gauge.builder("masking.cache.size", cache, CaffeineMaskCache::estimatedSize)
                .description("当前缓存条目数。持续贴着 maxSize 说明容量可能不够")
                .register(registry);

        Gauge.builder("masking.cache.hit.rate", cache, c -> c.stats().hitRate())
                .description("命中率。低于 0.5 说明缓存收益有限，值得检查明文的重复度")
                .register(registry);

        Gauge.builder("masking.cache.eviction", cache, c -> c.stats().evictionCount())
                .description("累计淘汰数。快速增长 + 低命中率 = 容量配小了")
                .register(registry);
    }
}
