package com.learn.mask.strategy;

import com.learn.mask.config.MaskRule;
import com.learn.mask.support.MaskUtils;

/**
 * 保留前缀/后缀、中间打星的通用策略基类，手机号、证件号、卡号、自定义规则均复用此实现。
 */
public abstract class AbstractKeepMaskStrategy implements MaskStrategy {

    /** 按规则的 keepPrefix/keepSuffix 中间打星。 */
    @Override
    public String mask(String raw, MaskRule rule) {
        if (MaskUtils.isBlank(raw) || rule == null || !rule.enabled()) {
            return raw;
        }
        return MaskUtils.keepMask(raw, rule);
    }

    /** 中间段是否已全为掩码字符。 */
    @Override
    public boolean alreadyMasked(String raw, MaskRule rule) {
        if (rule == null) {
            return false;
        }
        return MaskUtils.alreadyKeepMasked(raw, rule);
    }
}
