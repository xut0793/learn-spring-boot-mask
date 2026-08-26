package com.learn.mask.tutorial.ch04;

/**
 * 单个类型的脱敏规则，不可变。
 * <p>
 * 不可变是有意的，而且会一直保持到最后：规则会被四个通道并发读取，一旦可变，
 * 热更新改字段的瞬间就可能有请求读到「前一半旧值、后一半新值」的规则。
 * 第 7 章的做法是让**配置容器**可变、让它每次返回一个不可变的规则快照。
 * <p>
 * 注意 {@code mask-starter} 的 {@code MaskRule} 是可变 JavaBean，
 * 和这里的取舍不同。第 7 章 7.7 节会对比两者的差异和各自的代价。
 *
 * @param enabled    规则是否启用；关闭时策略应原样返回明文
 * @param keepPrefix 保留的前缀字符数
 * @param keepSuffix 保留的后缀字符数
 * @param maskChar   掩码字符
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
