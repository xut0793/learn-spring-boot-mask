package com.learn.mask.config;

/**
 * 单个类型的脱敏规则，不可变。
 * <p>
 * 规则会被四个通道并发读取。可变字段在热更新时会出现「前一半新值、后一半旧值」的中间态。
 * 写路径走 {@link RuleConfig}，读路径只拿本快照。
 */
public record MaskRule(boolean enabled, int keepPrefix, int keepSuffix, char maskChar) {

    public static MaskRule of(int keepPrefix, int keepSuffix) {
        return new MaskRule(true, keepPrefix, keepSuffix, '*');
    }

    public static MaskRule of(int keepPrefix, int keepSuffix, char maskChar) {
        return new MaskRule(true, keepPrefix, keepSuffix, maskChar);
    }

    public static MaskRule disabled() {
        return new MaskRule(false, 0, 0, '*');
    }
}
