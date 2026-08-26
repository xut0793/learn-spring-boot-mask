package com.learn.mask.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记需要 AOP 脱敏的方法。开启 {@code masking.channels.aop} 后，切面会在方法返回后改写结果。
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface SensitiveMethod {

    /**
     * 返回值为 {@link String} 时使用的内置脱敏类型。Bean、List、Map 则按字段上的 {@link Sensitive} 递归处理。
     */
    SensitiveType type() default SensitiveType.CUSTOM;

    /**
     * 返回值为 {@link String} 时的业务自定义类型编码，优先级高于 {@link #type()}。
     */
    String code() default "";
}
