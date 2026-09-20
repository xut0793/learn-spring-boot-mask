package com.learn.mask.support;

import com.learn.mask.config.MaskRule;

/**
 * 字符串脱敏工具：按保留前后缀打星，并判断是否已经是该形态。
 */
public final class MaskUtils {

    private MaskUtils() {
    }

    /**
     * 是否为空白。{@code value} 可以为 null，null 视为空白。
     */
    public static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /** 保留前 prefix、后 suffix 个字符，中间用掩码字符填充；过短则全部打星。 */
    public static String keepMask(String raw, MaskRule rule) {
        if (raw == null) {
            return null;
        }
        int prefix = Math.max(rule.keepPrefix(), 0);
        int suffix = Math.max(rule.keepSuffix(), 0);
        char maskChar = rule.maskChar();
        int len = raw.length();
        if (len == 0) {
            return raw;
        }
        if (len <= prefix + suffix) {
            return String.valueOf(maskChar).repeat(len);
        }
        return raw.substring(0, prefix)
                + String.valueOf(maskChar).repeat(len - prefix - suffix)
                + raw.substring(len - suffix);
    }

    /** 中间段是否已全为掩码字符，用于引擎幂等跳过。 */
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
