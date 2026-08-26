package com.learn.mask.tutorial.ch04;

/**
 * 「保留前后缀」类策略的基类。
 * <p>
 * 手机号、身份证、银行卡、通用规则的差别**只在配置的保留位数上**，行为完全一样。
 * 所以它们的子类都只需要覆写 {@link #type()}，一行代码。
 */
public abstract class AbstractKeepMaskStrategy implements MaskStrategy {

    @Override
    public String mask(String raw, MaskRule rule) {
        if (MaskUtils.isBlank(raw) || rule == null || !rule.enabled()) {
            return raw;
        }
        return MaskUtils.keepMask(raw, rule);
    }

    @Override
    public boolean alreadyMasked(String raw, MaskRule rule) {
        if (rule == null) {
            return false;
        }
        return MaskUtils.alreadyKeepMasked(raw, rule);
    }
}
