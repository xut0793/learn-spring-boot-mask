package com.learn.mask.tutorial.ch09;

import com.learn.mask.tutorial.ch04.SensitiveType;
import tools.jackson.databind.annotation.JsonSerialize;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * 故意漏掉 {@code @JacksonAnnotationsInside} 的对照注解。
 * Jackson 不会穿透它去看 {@code @JsonSerialize}，字段会按明文输出。
 */
@Retention(RetentionPolicy.RUNTIME)
@JsonSerialize(using = SensitiveValueSerializer.class)
public @interface BareSensitive {
    SensitiveType type() default SensitiveType.PHONE;
}
