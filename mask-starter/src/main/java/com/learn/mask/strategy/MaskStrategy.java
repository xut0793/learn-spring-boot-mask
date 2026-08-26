package com.learn.mask.strategy;

import com.learn.mask.annotation.SensitiveType;
import com.learn.mask.config.MaskRule;

/**
 * 脱敏策略。新增内置类型时实现 {@link #type()}；业务自定义类型实现 {@link #code()} 并注册为 Spring Bean。
 */
public interface MaskStrategy {

    /** 对应的内置类型。业务自定义策略可返回 {@link SensitiveType#CUSTOM}。 */
    SensitiveType type();

    /**
     * 策略编码，用作策略表主键。默认等于 {@link #type()} 的枚举名。
     * 业务自定义类型返回 starter 枚举中不存在的编码，例如 {@code YOUR_TYPE}。
     */
    default String code() {
        return type() == null ? SensitiveType.CUSTOM.name() : type().name();
    }

    /**
     * 将明文按规则脱敏。入参为空或规则关闭时应原样返回。
     */
    String mask(String raw, MaskRule rule);

    /**
     * 判断字符串是否已经是脱敏形态，供引擎做幂等跳过，避免 Jackson 与 AOP/MyBatis 叠加时二次打星。
     */
    boolean alreadyMasked(String raw, MaskRule rule);
}
