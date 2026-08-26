package com.learn.mask.config;

import com.learn.mask.context.MaskContext;
import com.learn.mask.engine.MaskEngine;
import com.learn.mask.jackson.SensitiveMapSerializer;
import com.learn.mask.jackson.SensitiveValueSerializer;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import tools.jackson.databind.ValueSerializer;

/**
 * Jackson 3 序列化通道：为 {@code @Sensitive} 字段与 Map 视图注册脱敏序列化器。
 */
@AutoConfiguration(after = MaskingAutoConfiguration.class)
@ConditionalOnClass(ValueSerializer.class)
@ConditionalOnBean(MaskEngine.class)
public class JacksonMaskingAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public SensitiveValueSerializer sensitiveValueSerializer(MaskEngine engine,
                                                             MaskingProperties properties,
                                                             MaskContext maskContext) {
        return new SensitiveValueSerializer(engine, properties, maskContext);
    }

    @Bean
    @ConditionalOnMissingBean
    public SensitiveMapSerializer sensitiveMapSerializer(MaskEngine engine,
                                                         MaskingProperties properties,
                                                         MaskContext maskContext) {
        return new SensitiveMapSerializer(engine, properties, maskContext);
    }
}
