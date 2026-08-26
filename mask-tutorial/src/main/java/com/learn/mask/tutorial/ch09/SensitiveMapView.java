package com.learn.mask.tutorial.ch09;

import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.annotation.JsonSerialize;

import java.util.Map;

/**
 * 控制器返回 Map 时的包装类型。Jackson 对裸 {@code Map} 不会走 {@link SensitiveMapSerializer}，
 * 必须用一个带 {@code @JsonSerialize} 的类型把它「骗」过来。
 */
@JsonSerialize(using = SensitiveMapView.Serializer.class)
public record SensitiveMapView(Map<String, Object> data) {

    public static final class Serializer extends ValueSerializer<SensitiveMapView> {
        @Override
        public void serialize(SensitiveMapView value, JsonGenerator gen, SerializationContext ctxt) {
            if (value == null || value.data() == null) {
                gen.writeNull();
                return;
            }
            new SensitiveMapSerializer(
                    MaskingSpringBridge.engine(),
                    MaskingSpringBridge.properties(),
                    MaskingSpringBridge.context(),
                    MaskingSpringBridge.channels()
            ).serialize(value.data(), gen, ctxt);
        }
    }
}
