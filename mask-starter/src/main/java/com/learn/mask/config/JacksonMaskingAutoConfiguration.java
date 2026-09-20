package com.learn.mask.config;

import com.learn.mask.context.MaskContext;
import com.learn.mask.engine.MaskEngine;
import com.learn.mask.jackson.SensitiveJacksonModule;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import tools.jackson.databind.ValueSerializer;

/**
 * Jackson 3 序列化通道：注册 {@link SensitiveJacksonModule}，由内省器发现 {@code @Sensitive}。
 */
@AutoConfiguration(after = MaskingAutoConfiguration.class)
@ConditionalOnClass(ValueSerializer.class)
@ConditionalOnBean(MaskEngine.class)
public class JacksonMaskingAutoConfiguration {

    /** 向 Jackson 注册 {@link com.learn.mask.jackson.SensitiveAnnotationIntrospector}。 */
    @Bean
    @ConditionalOnMissingBean
    public SensitiveJacksonModule sensitiveJacksonModule(MaskEngine engine,
                                                         MaskingProperties properties,
                                                         MaskContext maskContext) {
        return new SensitiveJacksonModule(engine, properties, maskContext);
    }
}
