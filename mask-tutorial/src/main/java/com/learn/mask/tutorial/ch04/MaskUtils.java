package com.learn.mask.tutorial.ch04;

/**
 * 从 {@link NaiveMasker} 里抽出来的通用能力：保留前后缀打星，以及反向判断。
 * <p>
 * 把这段逻辑下沉到这里之后，四个内置策略几乎都不用写代码了。
 */
public final class MaskUtils {

    private MaskUtils() {
    }

    public static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /**
     * 保留前 keepPrefix、后 keepSuffix 个字符，中间用掩码字符填充。
     * <p>
     * 长度不足 keepPrefix + keepSuffix 时**全部打星**而不是抛异常。这个兜底很重要：
     * 面对脏数据，「遮得更多」永远比「报错」或「原样返回」安全。
     */
    public static String keepMask(String raw, MaskRule rule) {
        if (raw == null) {
            return null;
        }
        int prefix = Math.max(rule.keepPrefix(), 0);
        int suffix = Math.max(rule.keepSuffix(), 0);
        int len = raw.length();
        if (len == 0) {
            return raw;
        }
        if (len <= prefix + suffix) {
            return String.valueOf(rule.maskChar()).repeat(len);
        }
        return raw.substring(0, prefix)
                + String.valueOf(rule.maskChar()).repeat(len - prefix - suffix)
                + raw.substring(len - suffix);
    }

    /**
     * 判断字符串是否已经是 {@link #keepMask} 产出的形态：中间段全是掩码字符。
     * <p>
     * 第 6 章的幂等跳过依赖这个判断，用来防止多通道叠加时二次打星。
     */
    public static boolean alreadyKeepMasked(String raw, MaskRule rule) {
        if (isBlank(raw)) {
            return false;
        }
        int prefix = Math.max(rule.keepPrefix(), 0);
        int suffix = Math.max(rule.keepSuffix(), 0);
        char maskChar = rule.maskChar();
        int len = raw.length();
        if (len <= prefix + suffix) {
            return raw.chars().allMatch(c -> c == maskChar);
        }
        for (int i = prefix; i < len - suffix; i++) {
            if (raw.charAt(i) != maskChar) {
                return false;
            }
        }
        return true;
    }
}
