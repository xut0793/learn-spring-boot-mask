package com.learn.mask.config;

/**
 * 单个类型的脱敏规则，不可变。
 * <p>
 * 规则会被四个通道并发读取。可变字段在热更新时会出现「前一半新值、后一半旧值」的中间态。
 * 写路径走 {@link RuleConfig}，读路径只拿本快照。
 */
public record MaskRule(boolean enabled, int keepPrefix, int keepSuffix, char maskChar) {

    /** 启用规则，掩码字符为 {@code *}。 */
    public static MaskRule of(int keepPrefix, int keepSuffix) {
        return new MaskRule(true, keepPrefix, keepSuffix, '*');
    }

    /** 启用规则并指定掩码字符。 */
    public static MaskRule of(int keepPrefix, int keepSuffix, char maskChar) {
        return new MaskRule(true, keepPrefix, keepSuffix, maskChar);
    }

    /** 关闭该类型的脱敏。 */
    public static MaskRule disabled() {
        return new MaskRule(false, 0, 0, '*');
    }
}
