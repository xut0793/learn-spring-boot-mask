package com.learn.mask.tutorial.ch09;

import com.learn.mask.tutorial.ch04.SensitiveType;
import com.learn.mask.tutorial.ch06.MaskEngine;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.BeanProperty;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;

/**
 * 反例：按属性名决定类型，但 {@code createContextual} 改 {@code this} 再返回自己。
 * <p>
 * 这个 bug 只在「整个应用共用一个序列化器实例」时出现——也就是用
 * {@code SimpleModule.addSerializer(String.class, shared)} 全局注册的时候。
 * 按字段注解 {@code @JsonSerialize(using = ...)} 时，Jackson 会为每个属性
 * new 一个新实例，返回 this 反而看不出问题。第 9.4 节会把这个差别讲清楚。
 */
public class MutatingSensitiveValueSerializer extends ValueSerializer<String> {

    private SensitiveType type;

    @Override
    public ValueSerializer<?> createContextual(SerializationContext ctxt, BeanProperty property) {
        this.type = resolveType(property);
        return this;
    }

    @Override
    public void serialize(String value, JsonGenerator gen, SerializationContext ctxt) {
        if (value == null || type == null) {
            gen.writeString(value);
            return;
        }
        MaskEngine engine = MaskingSpringBridge.engine();
        if (engine == null) {
            gen.writeString(value);
            return;
        }
        gen.writeString(engine.apply(value, type, MaskingSpringBridge.context()));
    }

    private static SensitiveType resolveType(BeanProperty property) {
        if (property == null || property.getName() == null) {
            return null;
        }
        return switch (property.getName()) {
            case "phone" -> SensitiveType.PHONE;
            case "email" -> SensitiveType.EMAIL;
            default -> null;
        };
    }
}
