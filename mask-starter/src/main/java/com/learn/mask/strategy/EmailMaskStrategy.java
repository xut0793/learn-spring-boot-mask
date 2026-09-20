package com.learn.mask.strategy;

import com.learn.mask.annotation.SensitiveType;
import com.learn.mask.config.MaskRule;
import com.learn.mask.support.MaskUtils;

/**
 * 邮箱脱敏：保留本地部分前若干位，域名不打星。
 */
public class EmailMaskStrategy implements MaskStrategy {

    @Override
    public SensitiveType type() {
        return SensitiveType.EMAIL;
    }

    /** 仅打星 {@code @} 前本地部分，域名保留。 */
    @Override
    public String mask(String raw, MaskRule rule) {
        if (MaskUtils.isBlank(raw) || rule == null || !rule.enabled()) {
            return raw;
        }
        int at = raw.indexOf('@');
        if (at <= 0) {
            return MaskUtils.keepMask(raw, rule);
        }
        String local = raw.substring(0, at);
        String domain = raw.substring(at);
        int keep = Math.max(rule.keepPrefix(), 1);
        if (local.length() <= keep) {
            return String.valueOf(rule.maskChar()).repeat(local.length()) + domain;
        }
        return local.substring(0, keep)
                + String.valueOf(rule.maskChar()).repeat(local.length() - keep)
                + domain;
    }

    /** 无 {@code @} 时退化为通用 keep 形态检测。 */
    @Override
    public boolean alreadyMasked(String raw, MaskRule rule) {
        if (MaskUtils.isBlank(raw) || rule == null) {
            return false;
        }
        int at = raw.indexOf('@');
        if (at <= 0) {
            return MaskUtils.alreadyKeepMasked(raw, rule);
        }
        String local = raw.substring(0, at);
        int keep = Math.max(rule.keepPrefix(), 1);
        char maskChar = rule.maskChar();
        if (local.length() <= keep) {
            return local.chars().allMatch(c -> c == maskChar);
        }
        for (int i = keep; i < local.length(); i++) {
            if (local.charAt(i) != maskChar) {
                return false;
            }
        }
        return true;
    }
}
