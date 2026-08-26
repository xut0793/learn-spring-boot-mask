package com.learn.mask.tutorial.ch09;

import com.fasterxml.jackson.annotation.JacksonAnnotationsInside;
import com.learn.mask.tutorial.ch04.SensitiveType;
import tools.jackson.databind.annotation.JsonSerialize;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记需要脱敏的字段。Jackson 看到它，就会选用 {@link SensitiveValueSerializer}。
 * <p>
 * {@link JacksonAnnotationsInside} 是关键：没有它，Jackson 不会穿透元注解去看
 * {@code @JsonSerialize}，这个注解就会变成一个「只有 AOP 认识、Jackson 当它不存在」的标记。
 * 第 9.3 节用一个反例证明这一点。
 */
@Documented
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
@JacksonAnnotationsInside
@JsonSerialize(using = SensitiveValueSerializer.class)
public @interface Sensitive {

    /** 内置类型。自定义类型保持默认 {@link SensitiveType#CUSTOM}，改用 {@link #code()}。 */
    SensitiveType type() default SensitiveType.CUSTOM;

    /** 业务自定义编码。非空时优先于 {@link #type()}。 */
    String code() default "";
}
