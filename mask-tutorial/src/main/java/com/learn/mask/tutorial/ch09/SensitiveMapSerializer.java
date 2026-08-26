package com.learn.mask.tutorial.ch09;

import com.learn.mask.tutorial.ch05.MaskContext;
import com.learn.mask.tutorial.ch06.MaskEngine;
import com.learn.mask.tutorial.ch07.MaskingProperties;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;

import java.util.List;
import java.util.Map;

/**
 * 对 {@code Map<String, ?>} 按键名脱敏。Map 的值没有字段注解，所以 {@code @Sensitive}
 * 这条路走不通，只能靠 {@code masking.map-keys} 的字段名映射。
 * <p>
 * 嵌套 Map / List 会递归处理；其他类型交给 Jackson 默认序列化。
 */
public class SensitiveMapSerializer extends ValueSerializer<Map<String, ?>> {

    private final MaskEngine engine;
    private final MaskingProperties properties;
    private final MaskContext maskContext;
    private final ChannelProperties channels;

    public SensitiveMapSerializer(MaskEngine engine,
                                  MaskingProperties properties,
                                  MaskContext maskContext,
                                  ChannelProperties channels) {
        this.engine = engine;
        this.properties = properties;
        this.maskContext = maskContext;
        this.channels = channels;
    }

    @Override
    public void serialize(Map<String, ?> value, JsonGenerator gen, SerializationContext ctxt) {
        gen.writeStartObject();
        if (value != null) {
            for (Map.Entry<String, ?> entry : value.entrySet()) {
                String key = entry.getKey();
                gen.writeName(key);
                writeValue(key, entry.getValue(), gen, ctxt);
            }
        }
        gen.writeEndObject();
    }

    private void writeValue(String key, Object raw, JsonGenerator gen, SerializationContext ctxt) {
        if (raw == null) {
            gen.writeNull();
            return;
        }
        if (raw instanceof String text) {
            gen.writeString(maskIfMapped(key, text));
            return;
        }
        if (raw instanceof Map<?, ?> nested) {
            @SuppressWarnings("unchecked")
            Map<String, ?> cast = (Map<String, ?>) nested;
            serialize(cast, gen, ctxt);
            return;
        }
        if (raw instanceof List<?> list) {
            gen.writeStartArray();
            for (Object item : list) {
                if (item instanceof Map<?, ?> mapItem) {
                    @SuppressWarnings("unchecked")
                    Map<String, ?> cast = (Map<String, ?>) mapItem;
                    serialize(cast, gen, ctxt);
                } else if (item instanceof String text) {
                    gen.writeString(maskIfMapped(key, text));
                } else {
                    ctxt.writeValue(gen, item);
                }
            }
            gen.writeEndArray();
            return;
        }
        ctxt.writeValue(gen, raw);
    }

    private String maskIfMapped(String key, String text) {
        if (engine == null || properties == null || !properties.isEnabled()
                || (channels != null && !channels.isJackson())) {
            return text;
        }
        String typeCode = properties.typeCodeOf(key);
        if (typeCode == null) {
            return text;
        }
        return engine.apply(text, null, typeCode, maskContext);
    }
}
