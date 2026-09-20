package com.learn.mask.aop;

import com.learn.mask.annotation.SensitiveMethod;
import com.learn.mask.config.MaskingProperties;
import com.learn.mask.context.MaskContext;
import com.learn.mask.engine.MaskEngine;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;

/**
 * AOP 切面：方法返回 String 时按注解类型脱敏，返回对象时交给 {@link SensitiveObjectWalker} 就地改写。
 */
@Aspect
public class SensitiveMethodAspect {

    private final MaskEngine engine;
    private final MaskingProperties properties;
    private final MaskContext maskContext;
    private final SensitiveObjectWalker walker;

    /** 注入引擎、通道配置、角色上下文与对象遍历器。 */
    public SensitiveMethodAspect(MaskEngine engine,
                                 MaskingProperties properties,
                                 MaskContext maskContext,
                                 SensitiveObjectWalker walker) {
        this.engine = engine;
        this.properties = properties;
        this.maskContext = maskContext;
        this.walker = walker;
    }

    /** 通道开启时对 String 或复杂对象返回值脱敏；关闭或 null 时直接返回。 */
    @Around("@annotation(sensitiveMethod)")
    public Object around(ProceedingJoinPoint joinPoint, SensitiveMethod sensitiveMethod) throws Throwable {
        Object result = joinPoint.proceed();
        if (result == null || !properties.isEnabled() || !properties.getChannels().isAop()) {
            return result;
        }
        if (result instanceof String text) {
            return engine.apply(text, sensitiveMethod.type(), sensitiveMethod.code(), maskContext);
        }
        return walker.mask(result);
    }
}
