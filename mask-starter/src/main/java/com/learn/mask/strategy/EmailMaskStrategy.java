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

    @Override
    public String mask(String raw, MaskRule rule) {
        if (MaskUtils.isBlank(raw) || rule == null || !rule.isEnabled()) {
            return raw;
        }
        int at = raw.indexOf('@');
        if (at <= 0) {
            return MaskUtils.keepMask(raw, rule);
        }
        String local = raw.substring(0, at);
        String domain = raw.substring(at);
        int keep = Math.max(rule.getKeepPrefix(), 1);
        if (local.length() <= keep) {
            return String.valueOf(rule.getMaskChar()).repeat(local.length()) + domain;
        }
        return local.substring(0, keep)
                + String.valueOf(rule.getMaskChar()).repeat(local.length() - keep)
                + domain;
    }

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
        int keep = Math.max(rule.getKeepPrefix(), 1);
        char maskChar = rule.getMaskChar();
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
