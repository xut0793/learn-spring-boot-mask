package com.learn.mask.jackson;

import com.learn.mask.support.MaskingSpringBridge;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.annotation.JsonSerialize;

import java.util.Map;

/**
 * 控制器返回 Map 时的包装类型，强制走 {@link SensitiveMapSerializer}。
 */
@JsonSerialize(using = SensitiveMapView.Serializer.class)
public record SensitiveMapView(Map<String, Object> data) {

    /** 委托 {@link SensitiveMapSerializer}，依赖静态桥取引擎。 */
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
                    MaskingSpringBridge.context()
            ).serialize(value.data(), gen, ctxt);
        }
    }
}
