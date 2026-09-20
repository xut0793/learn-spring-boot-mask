package com.learn.mask.jackson;

import com.learn.mask.config.MaskingProperties;
import com.learn.mask.context.MaskContext;
import com.learn.mask.engine.MaskEngine;
import tools.jackson.core.Version;
import tools.jackson.databind.JacksonModule;

/**
 * 把 {@link SensitiveAnnotationIntrospector} 插进 Jackson 3，使纯 {@code @Sensitive} 生效。
 * Spring Boot 会收集容器里的 {@link JacksonModule} Bean 并注册到 {@code JsonMapper}。
 */
public final class SensitiveJacksonModule extends JacksonModule {

    private final MaskEngine engine;
    private final MaskingProperties properties;
    private final MaskContext maskContext;

    /** 构造时注入依赖，供 {@link #setupModule} 注册内省器。 */
    public SensitiveJacksonModule(MaskEngine engine,
                                  MaskingProperties properties,
                                  MaskContext maskContext) {
        this.engine = engine;
        this.properties = properties;
        this.maskContext = maskContext;
    }

    @Override
    public String getModuleName() {
        return "maskingSensitive";
    }

    @Override
    public Version version() {
        return Version.unknownVersion();
    }

    @Override
    public Object getRegistrationId() {
        return getModuleName();
    }

    /** 向 Jackson 插入 {@link SensitiveAnnotationIntrospector}。 */
    @Override
    public void setupModule(SetupContext context) {
        context.insertAnnotationIntrospector(
                new SensitiveAnnotationIntrospector(engine, properties, maskContext));
    }
}
