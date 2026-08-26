package com.learn.mask.tutorial.ch09;

import com.learn.mask.tutorial.ch05.MaskContext;
import com.learn.mask.tutorial.ch06.MaskEngine;
import com.learn.mask.tutorial.ch07.MaskingProperties;

/**
 * 把 Spring 容器里的引擎、配置、角色上下文挂到静态位。
 * <p>
 * Jackson 通过 {@code @JsonSerialize(using = X.class)} 用<strong>无参构造</strong>反射创建序列化器，
 * 这个过程绕过了 Spring，所以序列化器拿不到 {@code @Autowired} 的引擎。
 * Logback Converter 和 MyBatis TypeHandler 也有同样的问题。
 * <p>
 * 静态桥是对这个现实的折中，不是优雅设计。第 9.7 节讨论它的代价和替代方案。
 */
public final class MaskingSpringBridge {

    private static volatile MaskEngine engine;
    private static volatile MaskingProperties properties;
    private static volatile MaskContext context;
    private static volatile ChannelProperties channels;

    private MaskingSpringBridge() {
    }

    public static void bind(MaskEngine maskEngine,
                            MaskingProperties maskingProperties,
                            MaskContext maskContext,
                            ChannelProperties channelProperties) {
        engine = maskEngine;
        properties = maskingProperties;
        context = maskContext;
        channels = channelProperties;
    }

    /**
     * 测试用。生产代码在进程生命周期内不会 unbind——自动配置绑一次就一直用。
     * <p>
     * 没有这一步，测试之间会串状态：上一个测试绑的引擎被下一个测试读到。
     * 真实项目缺了它，所以 starter 的序列化器单测几乎没法脱离 Spring 来写。
     */
    public static void unbind() {
        engine = null;
        properties = null;
        context = null;
        channels = null;
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

    public static ChannelProperties channels() {
        return channels;
    }
}
