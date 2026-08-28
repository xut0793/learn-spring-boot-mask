package com.learn.mask.jackson;

import com.learn.mask.annotation.Sensitive;
import com.learn.mask.annotation.SensitiveType;
import com.learn.mask.config.MaskingProperties;
import com.learn.mask.context.MaskContext;
import com.learn.mask.engine.MaskEngine;
import com.learn.mask.support.MaskingSpringBridge;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.BeanProperty;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;

/**
 * Jackson 3 字段序列化器：按 {@link Sensitive} 或 {@code masking.map-keys}/{@code extra-map-keys} 调用引擎后再写出。
 * {@link com.learn.mask.jackson.SensitiveJacksonModule} 会直接传入引擎；
 * 无参构造留给 {@link SensitiveMapView} 等仍走静态桥的路径。
 */
public class SensitiveValueSerializer extends ValueSerializer<String> {

    private final MaskEngine engine;
    private final MaskingProperties properties;
    private final MaskContext maskContext;
    private final SensitiveType type;
    private final String code;
    private final boolean passthrough;

    public SensitiveValueSerializer() {
        this(MaskingSpringBridge.engine(), MaskingSpringBridge.properties(), MaskingSpringBridge.context(),
                SensitiveType.CUSTOM, SensitiveType.CUSTOM.name(), false);
    }

    public SensitiveValueSerializer(MaskEngine engine, MaskingProperties properties, MaskContext maskContext) {
        this(engine, properties, maskContext, SensitiveType.CUSTOM, SensitiveType.CUSTOM.name(), false);
    }

    public SensitiveValueSerializer(MaskEngine engine,
                                    MaskingProperties properties,
                                    MaskContext maskContext,
                                    SensitiveType type) {
        this(engine, properties, maskContext, type, type == null ? null : type.name(), false);
    }

    public SensitiveValueSerializer(MaskEngine engine,
                                    MaskingProperties properties,
                                    MaskContext maskContext,
                                    SensitiveType type,
                                    String code) {
        this(engine, properties, maskContext, type, code, false);
    }

    private SensitiveValueSerializer(MaskEngine engine,
                                     MaskingProperties properties,
                                     MaskContext maskContext,
                                     SensitiveType type,
                                     String code,
                                     boolean passthrough) {
        this.engine = engine;
        this.properties = properties;
        this.maskContext = maskContext;
        this.type = type;
        this.code = code;
        this.passthrough = passthrough;
    }

    @Override
    public ValueSerializer<?> createContextual(SerializationContext ctxt, BeanProperty property) {
        if (property == null) {
            return this;
        }
        Sensitive annotation = findSensitive(property);
        if (annotation != null) {
            return new SensitiveValueSerializer(
                    resolveEngine(), resolveProperties(), resolveContext(),
                    annotation.type(), MaskingProperties.normalizeCode(annotation.code(), annotation.type()));
        }
        MaskingProperties props = resolveProperties();
        if (props != null && property.getName() != null) {
            String resolvedCode = props.typeCodeOf(property.getName());
            if (resolvedCode != null) {
                return new SensitiveValueSerializer(
                        resolveEngine(), props, resolveContext(), SensitiveType.CUSTOM, resolvedCode);
            }
        }
        return new SensitiveValueSerializer(null, null, null, SensitiveType.CUSTOM, null, true);
    }

    private Sensitive findSensitive(BeanProperty property) {
        Sensitive annotation = property.getAnnotation(Sensitive.class);
        if (annotation != null) {
            return annotation;
        }
        annotation = property.getContextAnnotation(Sensitive.class);
        if (annotation != null) {
            return annotation;
        }
        if (property.getMember() != null) {
            return property.getMember().getAnnotation(Sensitive.class);
        }
        return null;
    }

    @Override
    public void serialize(String value, JsonGenerator gen, SerializationContext ctxt) {
        if (value == null || passthrough) {
            gen.writeString(value);
            return;
        }
        MaskingProperties props = resolveProperties();
        MaskEngine maskEngine = resolveEngine();
        if (maskEngine == null || props == null) {
            throw new IllegalStateException("Masking engine is not bound");
        }
        if (!props.isEnabled() || !props.getChannels().isJackson()) {
            gen.writeString(value);
            return;
        }
        gen.writeString(maskEngine.apply(value, type, code, resolveContext()));
    }

    private MaskEngine resolveEngine() {
        return engine != null ? engine : MaskingSpringBridge.engine();
    }

    private MaskingProperties resolveProperties() {
        return properties != null ? properties : MaskingSpringBridge.properties();
    }

    private MaskContext resolveContext() {
        return maskContext != null ? maskContext : MaskingSpringBridge.context();
    }
}
