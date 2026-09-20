package com.learn.mask.config;

import com.learn.mask.aop.SensitiveMethodAspect;
import com.learn.mask.aop.SensitiveObjectWalker;
import com.learn.mask.context.MaskContext;
import com.learn.mask.engine.MaskEngine;
import org.aspectj.lang.ProceedingJoinPoint;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * AOP 通道：对 {@code @SensitiveMethod} 的返回值做内存脱敏。这是改写对象本身的通道。
 */
@AutoConfiguration(after = MaskingAutoConfiguration.class)
@ConditionalOnClass(ProceedingJoinPoint.class)
@ConditionalOnBean(MaskEngine.class)
public class AopMaskingAutoConfiguration {

    /** 递归扫描 {@code @Sensitive} 字段并就地改写。 */
    @Bean
    @ConditionalOnMissingBean
    public SensitiveObjectWalker sensitiveObjectWalker(MaskEngine engine,
                                                       MaskingProperties properties,
                                                       MaskContext maskContext) {
        return new SensitiveObjectWalker(engine, properties, maskContext);
    }

    /** 拦截 {@code @SensitiveMethod}，对返回值脱敏。 */
    @Bean
    @ConditionalOnMissingBean
    public SensitiveMethodAspect sensitiveMethodAspect(MaskEngine engine,
                                                       MaskingProperties properties,
                                                       MaskContext maskContext,
                                                       SensitiveObjectWalker walker) {
        return new SensitiveMethodAspect(engine, properties, maskContext, walker);
    }
}
