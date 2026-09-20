package com.learn.mask.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.learn.mask.cache.MaskCache;
import com.learn.mask.cache.MaskResultCache;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Caffeine 在 classpath 上时才提供 {@link MaskCache}。缺依赖时由 {@link MaskingAutoConfiguration} 落到 NO_OP。
 */
@AutoConfiguration(before = MaskingAutoConfiguration.class)
@ConditionalOnClass(Caffeine.class)
@EnableConfigurationProperties(MaskingProperties.class)
public class MaskingCacheAutoConfiguration {

    /** 明文→打码结果 Caffeine 缓存，键含规则版本。 */
    @Bean
    @ConditionalOnMissingBean(MaskResultCache.class)
    public MaskCache maskCache(MaskingProperties properties) {
        return new MaskCache(properties);
    }
}
