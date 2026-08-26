package com.learn.mask.annotation;

/**
 * 脱敏类型。内置手机号、证件号、卡号、邮箱；{@link #CUSTOM} 走通用保留前后缀规则。
 * <p>
 * 业务若要增加 starter 未预定义的类型，不必改本枚举：实现 {@code MaskStrategy#code()}，
 * 并在字段上使用 {@code @Sensitive(code = "YOUR_TYPE")}。
 */
public enum SensitiveType {
    /** 手机号，默认保留前 3 后 4。 */
    PHONE,
    /** 身份证号，默认保留前 6 后 4。 */
    ID_CARD,
    /** 银行卡号，默认保留前 4 后 4。 */
    BANK_CARD,
    /** 邮箱，默认保留本地部分前 1 位。 */
    EMAIL,
    /** 通用规则，对应 {@code masking.rules.custom} 的保留前后缀配置。 */
    CUSTOM,
    /**
     * 地址。starter 不提供默认策略，由接入方实现 {@code MaskStrategy} 并注册为 Spring Bean。
     */
    ADDRESS
}
