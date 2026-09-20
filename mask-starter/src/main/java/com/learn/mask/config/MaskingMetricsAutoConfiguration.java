package com.learn.mask.config;

import com.learn.mask.metrics.MaskRecorder;
import com.learn.mask.metrics.MaskingMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * Micrometer 在 classpath 上时才提供 {@link MaskingMetrics}。
 */
@AutoConfiguration(before = MaskingAutoConfiguration.class)
@ConditionalOnClass(MeterRegistry.class)
public class MaskingMetricsAutoConfiguration {

    /** 注册 masking.invoke / duration / fail 等指标。 */
    @Bean
    @ConditionalOnMissingBean(MaskRecorder.class)
    public MaskingMetrics maskingMetrics(ObjectProvider<MeterRegistry> meterRegistries) {
        MeterRegistry registry = meterRegistries.getIfAvailable(SimpleMeterRegistry::new);
        return new MaskingMetrics(registry);
    }
}
