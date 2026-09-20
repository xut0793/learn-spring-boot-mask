package com.learn.mask.tutorial.ch09;

import com.learn.mask.tutorial.ch04.MaskStrategyRegistry;
import com.learn.mask.tutorial.ch04.SensitiveType;
import com.learn.mask.tutorial.ch05.MaskContext;
import com.learn.mask.tutorial.ch06.MaskEngine;
import com.learn.mask.tutorial.ch07.MaskingProperties;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.BeanProperty;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;

/**
 * Jackson 3 字段序列化器：读到 String 时先问引擎，再把结果写进 JSON。
 * <p>
 * 无参构造给 Jackson 反射用。真正的引擎从 {@link MaskingSpringBridge} 取——
 * 因为 Jackson 创建这个对象时绕过了 Spring。
 * <p>
 * <b>本类最重要的方法是 {@link #createContextual}：必须返回新实例。</b>
 * 序列化器对象是按类型共享的，字段规则是各自的。如果改 {@code this} 再返回自己，
 * 一个 Bean 里的 phone 和 email 会互相覆盖。第 9.4 节有反例。
 */
public class SensitiveValueSerializer extends ValueSerializer<String> {

    private final MaskEngine engine;
    private final MaskingProperties properties;
    private final MaskContext maskContext;
    private final ChannelProperties channels;
    private final SensitiveType type;
    private final String code;

    public SensitiveValueSerializer() {
        this(MaskingSpringBridge.engine(),
                MaskingSpringBridge.properties(),
                MaskingSpringBridge.context(),
                MaskingSpringBridge.channels(),
                SensitiveType.CUSTOM,
                SensitiveType.CUSTOM.name());
    }

    public SensitiveValueSerializer(MaskEngine engine,
                                    MaskingProperties properties,
                                    MaskContext maskContext,
                                    ChannelProperties channels,
                                    SensitiveType type,
                                    String code) {
        this.engine = engine;
        this.properties = properties;
        this.maskContext = maskContext;
        this.channels = channels;
        this.type = type;
        this.code = code;
    }

    /**
     * Jackson 在「确定这个属性用哪个序列化器」时调用。每个属性调用一次。
     * <p>
     * 返回值会被 Jackson <em>按属性缓存</em>。所以这里必须 {@code new} 一个带有
     * 该属性规则的实例，而不是改 {@code this} 再返回自己。
     */
    @Override
    public ValueSerializer<?> createContextual(SerializationContext ctxt, BeanProperty property) {
        if (property == null) {
            return this;
        }
        Sensitive annotation = findSensitive(property);
        if (annotation != null) {
            return new SensitiveValueSerializer(
                    resolveEngine(), resolveProperties(), resolveContext(), resolveChannels(),
                    annotation.type(),
                    MaskStrategyRegistry.normalize(
                            annotation.code() == null || annotation.code().isBlank()
                                    ? annotation.type().name()
                                    : annotation.code()));
        }
        // 注解未命中：用属性名查 masking.map-keys。认得出就 new 一份，
        // CUSTOM 只占枚举位，真正进引擎的是 resolvedCode。
        // 普通 DTO 几乎走不到——没 @Sensitive 时 Jackson 不会挂本序列化器；
        // 留给全局注册 String 序列化器时用。
        MaskingProperties props = resolveProperties();
        if (props != null && property.getName() != null) {
            String resolvedCode = props.typeCodeOf(property.getName());
            if (resolvedCode != null) {
                return new SensitiveValueSerializer(
                        resolveEngine(), props, resolveContext(), resolveChannels(),
                        SensitiveType.CUSTOM, resolvedCode);
            }
        }
        return this;
    }

    @Override
    public void serialize(String value, JsonGenerator gen, SerializationContext ctxt) {
        MaskingProperties props = resolveProperties();
        MaskEngine maskEngine = resolveEngine();
        ChannelProperties channelProps = resolveChannels();
        // 引擎还没绑上、总开关关掉、Jackson 通道关掉：原样写出。
        // 这是 fail-open：启动早期或配置错误时泄漏明文。第 9.7 节会质疑这个决定。
        if (value == null
                || maskEngine == null
                || props == null
                || !props.isEnabled()
                || (channelProps != null && !channelProps.isJackson())) {
            gen.writeString(value);
            return;
        }
        gen.writeString(maskEngine.apply(value, type, code, resolveContext()));
    }

    SensitiveType type() {
        return type;
    }

    String code() {
        return code;
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

    private MaskEngine resolveEngine() {
        return engine != null ? engine : MaskingSpringBridge.engine();
    }

    private MaskingProperties resolveProperties() {
        return properties != null ? properties : MaskingSpringBridge.properties();
    }

    private MaskContext resolveContext() {
        return maskContext != null ? maskContext : MaskingSpringBridge.context();
    }

    private ChannelProperties resolveChannels() {
        return channels != null ? channels : MaskingSpringBridge.channels();
    }
}
