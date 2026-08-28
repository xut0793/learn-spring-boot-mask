package com.learn.mask.jackson;

import com.learn.mask.config.MaskingProperties;
import com.learn.mask.context.MaskContext;
import com.learn.mask.engine.MaskEngine;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;

import java.util.List;
import java.util.Map;

/**
 * 对 {@code Map<String, ?>} 按键名脱敏并递归处理嵌套 Map/List，用于无注解的动态结构出口。
 */
public class SensitiveMapSerializer extends ValueSerializer<Map<String, ?>> {

    private final MaskEngine engine;
    private final MaskingProperties properties;
    private final MaskContext maskContext;

    public SensitiveMapSerializer(MaskEngine engine, MaskingProperties properties, MaskContext maskContext) {
        this.engine = engine;
        this.properties = properties;
        this.maskContext = maskContext;
    }

    @Override
    public void serialize(Map<String, ?> value, JsonGenerator gen, SerializationContext ctxt) {
        gen.writeStartObject();
        if (value != null) {
            for (Map.Entry<String, ?> entry : value.entrySet()) {
                String key = entry.getKey();
                Object raw = entry.getValue();
                gen.writeName(key);
                writeValue(key, raw, gen, ctxt);
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
            String typeCode = properties.typeCodeOf(key);
            if (typeCode != null && properties.isEnabled() && properties.getChannels().isJackson()) {
                if (engine == null) {
                    throw new IllegalStateException("Masking engine is not bound");
                }
                gen.writeString(engine.apply(text, null, typeCode, maskContext));
            } else {
                gen.writeString(text);
            }
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
                    String typeCode = properties.typeCodeOf(key);
                    if (typeCode != null && properties.isEnabled() && properties.getChannels().isJackson()) {
                        if (engine == null) {
                            throw new IllegalStateException("Masking engine is not bound");
                        }
                        gen.writeString(engine.apply(text, null, typeCode, maskContext));
                    } else {
                        gen.writeString(text);
                    }
                } else {
                    ctxt.writeValue(gen, item);
                }
            }
            gen.writeEndArray();
            return;
        }
        ctxt.writeValue(gen, raw);
    }
}
