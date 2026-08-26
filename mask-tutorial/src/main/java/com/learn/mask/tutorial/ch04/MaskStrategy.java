package com.learn.mask.tutorial.ch04;

/**
 * 脱敏策略。整个方案的扩展点，只有四个方法。
 * <p>
 * 新增内置类型：实现 {@link #type()}。
 * 新增业务自定义类型：{@link #type()} 返回 {@link SensitiveType#CUSTOM}，
 * 并覆写 {@link #code()} 返回一个枚举里没有的编码。
 */
public interface MaskStrategy {

    /** 对应的内置类型；业务自定义策略返回 {@link SensitiveType#CUSTOM}。 */
    SensitiveType type();

    /**
     * 策略编码，用作策略表的主键。默认等于枚举名。
     * <p>
     * 这个方法是「不改枚举也能扩展」的关键：编码是字符串，不受枚举取值限制。
     */
    default String code() {
        return type() == null ? SensitiveType.CUSTOM.name() : type().name();
    }

    /** 按规则把明文打码。入参为空或规则关闭时应原样返回。 */
    String mask(String raw, MaskRule rule);

    /**
     * 判断入参是否已经是本策略打码后的形态。
     * <p>
     * 现在看这个方法会觉得多余——脱敏就脱敏，干嘛还要反向判断？
     * 因为第 2 章讲的四个通道可能叠加：AOP 已经把对象改成打码值了，Jackson 还会再走一遍。
     * 引擎靠这个方法做幂等跳过，第 6 章 6.5 节揭晓。
     */
    boolean alreadyMasked(String raw, MaskRule rule);
}
