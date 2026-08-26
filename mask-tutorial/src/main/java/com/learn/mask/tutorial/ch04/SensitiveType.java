package com.learn.mask.tutorial.ch04;

/**
 * 内置脱敏类型。
 * <p>
 * 注意这个枚举**只放 starter 自己提供默认策略的类型**。业务要加新类型时不改这里，
 * 而是实现 {@link MaskStrategy#code()} 返回一个枚举里没有的编码，见
 * {@link ExpressNoMaskStrategy}。
 */
public enum SensitiveType {
    PHONE,
    ID_CARD,
    BANK_CARD,
    EMAIL,
    /** 通用兜底类型：按保留前后缀处理，也是策略表未命中时的回落目标。 */
    CUSTOM
}
