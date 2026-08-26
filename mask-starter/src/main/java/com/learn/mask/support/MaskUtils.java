package com.learn.mask.support;

import com.learn.mask.config.MaskRule;

/**
 * 字符串脱敏工具：按保留前后缀打星，并判断是否已经是该形态。
 */
public final class MaskUtils {

    private MaskUtils() {
    }

    public static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /** 保留前 prefix、后 suffix 个字符，中间用掩码字符填充；过短则全部打星。 */
    public static String keepMask(String raw, MaskRule rule) {
        if (raw == null) {
            return null;
        }
        int prefix = Math.max(rule.getKeepPrefix(), 0);
        int suffix = Math.max(rule.getKeepSuffix(), 0);
        char maskChar = rule.getMaskChar();
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

    public static boolean alreadyKeepMasked(String raw, MaskRule rule) {
        if (isBlank(raw)) {
            return false;
        }
        int prefix = Math.max(rule.getKeepPrefix(), 0);
        int suffix = Math.max(rule.getKeepSuffix(), 0);
        char maskChar = rule.getMaskChar();
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
