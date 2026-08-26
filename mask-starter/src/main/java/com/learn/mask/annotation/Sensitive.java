package com.learn.mask.annotation;

import com.fasterxml.jackson.annotation.JacksonAnnotationsInside;
import com.learn.mask.jackson.SensitiveValueSerializer;
import tools.jackson.databind.annotation.JsonSerialize;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记需要脱敏的字段或 getter。Jackson 通道会据此选择序列化器；AOP 通道会按同一注解改写内存对象。
 */
@Documented
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
@JacksonAnnotationsInside
@JsonSerialize(using = SensitiveValueSerializer.class)
public @interface Sensitive {

    /**
     * 内置脱敏类型。业务自定义类型可保持默认 {@link SensitiveType#CUSTOM}，改用 {@link #code()}。
     */
    SensitiveType type() default SensitiveType.CUSTOM;

    /**
     * 业务自定义类型编码。非空时按此编码查找策略与规则，无需在 starter 的 {@link SensitiveType} 中新增枚举值。
     */
    String code() default "";

    /**
     * 是否允许通过还原接口拿回明文。接口响应仍输出星号，明文由库中原文经 AES 令牌还原，不把密文写进 JSON。
     */
    boolean reversible() default false;
}
