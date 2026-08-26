package com.learn.mask.tutorial.ch12;

import com.learn.mask.tutorial.ch05.MaskContext;
import com.learn.mask.tutorial.ch06.MaskEngine;
import com.learn.mask.tutorial.ch07.MaskingProperties;
import com.learn.mask.tutorial.ch09.ChannelProperties;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;

/**
 * AOP 切面：{@code @SensitiveMethod} 方法返回后改写结果。
 * <p>
 * 返回 {@link String} 时按注解上的 type/code 调引擎；
 * 返回对象时交给 {@link SensitiveObjectWalker} 就地改写。
 */
@Aspect
public class SensitiveMethodAspect {

    private final MaskEngine engine;
    private final MaskingProperties properties;
    private final ChannelProperties channels;
    private final MaskContext maskContext;
    private final SensitiveObjectWalker walker;

    public SensitiveMethodAspect(MaskEngine engine,
                                 MaskingProperties properties,
                                 ChannelProperties channels,
                                 MaskContext maskContext,
                                 SensitiveObjectWalker walker) {
        this.engine = engine;
        this.properties = properties;
        this.channels = channels;
        this.maskContext = maskContext;
        this.walker = walker;
    }

    @Around("@annotation(sensitiveMethod)")
    public Object around(ProceedingJoinPoint joinPoint, SensitiveMethod sensitiveMethod) throws Throwable {
        Object result = joinPoint.proceed();
        if (result == null || !properties.isEnabled() || channels == null || !channels.isAop()) {
            return result;
        }
        if (result instanceof String text) {
            return engine.apply(text, sensitiveMethod.type(), sensitiveMethod.code(), maskContext);
        }
        return walker.mask(result);
    }
}
