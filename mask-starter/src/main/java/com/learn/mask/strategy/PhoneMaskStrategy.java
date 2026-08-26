package com.learn.mask.strategy;

import com.learn.mask.annotation.SensitiveType;

/**
 * 手机号脱敏，默认保留前 3 后 4。
 */
public class PhoneMaskStrategy extends AbstractKeepMaskStrategy {

    @Override
    public SensitiveType type() {
        return SensitiveType.PHONE;
    }
}
