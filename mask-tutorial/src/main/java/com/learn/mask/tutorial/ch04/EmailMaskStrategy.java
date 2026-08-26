package com.learn.mask.tutorial.ch04;

/**
 * 邮箱策略。**唯一不继承 {@link AbstractKeepMaskStrategy} 的内置策略。**
 * <p>
 * 原因：通用的「保留前后缀」是按整串长度算的，套到邮箱上会把域名也打星，
 * 得到 {@code z*******@examp*.com} 这种既难看又没有实际收益的结果
 * （域名不是个人标识，遮它没有意义，反而破坏了「一眼看出是哪家邮箱」的业务可用性）。
 * <p>
 * 所以邮箱必须拆成 local 和 domain 两段，只处理 local 段。
 */
public class EmailMaskStrategy implements MaskStrategy {

    @Override
    public SensitiveType type() {
        return SensitiveType.EMAIL;
    }

    @Override
    public String mask(String raw, MaskRule rule) {
        if (MaskUtils.isBlank(raw) || rule == null || !rule.enabled()) {
            return raw;
        }
        int at = raw.indexOf('@');
        // at == -1：不是邮箱；at == 0：@ 在开头，没有 local 段。
        // 两种情况都放弃邮箱专用逻辑，回落到通用打星，而不是抛异常或原样返回。
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
        if (local.length() <= keep) {
            return local.chars().allMatch(c -> c == rule.maskChar());
        }
        for (int i = keep; i < local.length(); i++) {
            if (local.charAt(i) != rule.maskChar()) {
                return false;
            }
        }
        return true;
    }
}
