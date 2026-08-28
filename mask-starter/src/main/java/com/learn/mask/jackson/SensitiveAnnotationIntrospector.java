package com.learn.mask.jackson;

import com.learn.mask.annotation.Sensitive;
import com.learn.mask.config.MaskingProperties;
import com.learn.mask.context.MaskContext;
import com.learn.mask.engine.MaskEngine;
import tools.jackson.core.Version;
import tools.jackson.databind.cfg.MapperConfig;
import tools.jackson.databind.introspect.Annotated;
import tools.jackson.databind.introspect.NopAnnotationIntrospector;

/**
 * 发现 {@link Sensitive} 后返回已注入引擎的序列化器，字段上不必再写 {@code @JsonSerialize}。
 */
public final class SensitiveAnnotationIntrospector extends NopAnnotationIntrospector {

    private final MaskEngine engine;
    private final MaskingProperties properties;
    private final MaskContext maskContext;

    public SensitiveAnnotationIntrospector(MaskEngine engine,
                                           MaskingProperties properties,
                                           MaskContext maskContext) {
        this.engine = engine;
        this.properties = properties;
        this.maskContext = maskContext;
    }

    @Override
    public Version version() {
        return Version.unknownVersion();
    }

    @Override
    public Object findSerializer(MapperConfig<?> config, Annotated am) {
        Sensitive sensitive = am.getAnnotation(Sensitive.class);
        if (sensitive == null) {
            return null;
        }
        return new SensitiveValueSerializer(
                engine,
                properties,
                maskContext,
                sensitive.type(),
                MaskingProperties.normalizeCode(sensitive.code(), sensitive.type()));
    }
}
