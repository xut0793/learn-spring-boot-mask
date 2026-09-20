package com.learn.mask.support;

import com.learn.mask.config.MaskingProperties;
import com.learn.mask.context.MaskContext;
import com.learn.mask.engine.MaskEngine;

/**
 * 把 Spring 容器里的引擎、配置、角色上下文挂到静态位。
 * Logback Converter、MyBatis TypeHandler、Jackson 无参序列化器无法注入依赖，只能从这里取。
 */
public final class MaskingSpringBridge {

    /** 由 {@link MaskingSpringBridgeLifecycle} 在容器启动时写入。 */
    private static volatile MaskEngine engine;
    private static volatile MaskingProperties properties;
    /** 请求级角色解析；无 Web 请求时仍可读默认角色。 */
    private static volatile MaskContext context;

    private MaskingSpringBridge() {
    }

    /** 容器就绪后挂静态引用，供 Logback / TypeHandler / 无参序列化器使用。 */
    public static void bind(MaskEngine maskEngine, MaskingProperties maskingProperties, MaskContext maskContext) {
        engine = maskEngine;
        properties = maskingProperties;
        context = maskContext;
    }

    /** 容器关闭时清空，避免已销毁的 Bean 仍被静态通道引用。 */
    public static void unbind() {
        engine = null;
        properties = null;
        context = null;
    }

    /** 未 bind 时返回 {@code null}，调用方需做启动窗口兜底。 */
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
