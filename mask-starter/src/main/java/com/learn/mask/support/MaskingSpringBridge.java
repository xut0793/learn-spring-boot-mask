package com.learn.mask.support;

import com.learn.mask.config.MaskingProperties;
import com.learn.mask.context.MaskContext;
import com.learn.mask.engine.MaskEngine;

/**
 * 把 Spring 容器里的引擎、配置、角色上下文挂到静态位。
 * Logback Converter、MyBatis TypeHandler、Jackson 无参序列化器无法注入依赖，只能从这里取。
 */
public final class MaskingSpringBridge {

    private static volatile MaskEngine engine;
    private static volatile MaskingProperties properties;
    private static volatile MaskContext context;

    private MaskingSpringBridge() {
    }

    public static void bind(MaskEngine maskEngine, MaskingProperties maskingProperties, MaskContext maskContext) {
        engine = maskEngine;
        properties = maskingProperties;
        context = maskContext;
    }

    public static void unbind() {
        engine = null;
        properties = null;
        context = null;
    }

    public static MaskEngine engine() {
        return engine;
    }

    public static MaskingProperties properties() {
        return properties;
    }

    public static MaskContext context() {
        return context;
    }
}
