package com.learn.mask.tutorial.ch12;

import com.learn.mask.tutorial.ch04.SensitiveType;
import com.learn.mask.tutorial.ch09.Sensitive;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记需要 AOP 脱敏的方法。切面在方法返回后改写结果。
 * <p>
 * {@code type} / {@code code} 只在返回值是 {@link String} 时使用。
 * 返回 Bean / List / Map 时，按字段上的 {@link Sensitive} 递归处理。
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface SensitiveMethod {

    SensitiveType type() default SensitiveType.CUSTOM;

    String code() default "";
}
