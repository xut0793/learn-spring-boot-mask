package com.learn.mask.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.learn.mask.cache.MaskCache;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;

/**
 * 缓存 Gauge：只有 Caffeine 和 Micrometer 同时存在时才注册，避免单独缺一个时本类加载失败。
 */
@AutoConfiguration(after = {MaskingCacheAutoConfiguration.class, MaskingMetricsAutoConfiguration.class})
@ConditionalOnClass({Caffeine.class, MeterRegistry.class})
@ConditionalOnBean(MaskCache.class)
public class MaskingCacheMetricsAutoConfiguration {

    /** 暴露命中率、估算大小与驱逐次数 Gauge。 */
    @Bean
    public MeterBinder maskingCacheGauges(MaskCache cache) {
        return registry -> {
            Gauge.builder("masking.cache.hit_rate", cache, MaskCache::hitRate).register(registry);
            Gauge.builder("masking.cache.size", cache, MaskCache::estimatedSize).register(registry);
            Gauge.builder("masking.cache.eviction_count", cache, value -> (double) value.evictionCount())
                    .register(registry);
        };
    }
}
