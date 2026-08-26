package com.learn.mask.strategy;

import com.learn.mask.config.MaskRule;
import com.learn.mask.support.MaskUtils;

/**
 * 保留前缀/后缀、中间打星的通用策略基类，手机号、证件号、卡号、自定义规则均复用此实现。
 */
public abstract class AbstractKeepMaskStrategy implements MaskStrategy {

    @Override
    public String mask(String raw, MaskRule rule) {
        if (MaskUtils.isBlank(raw) || rule == null || !rule.isEnabled()) {
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
