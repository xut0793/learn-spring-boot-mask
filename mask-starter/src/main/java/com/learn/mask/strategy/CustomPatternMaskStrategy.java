package com.learn.mask.strategy;

import com.learn.mask.annotation.SensitiveType;

/**
 * 通用自定义规则，对应 {@link com.learn.mask.annotation.SensitiveType#CUSTOM}，行为由 {@code masking.rules.custom} 决定。
 */
public class CustomPatternMaskStrategy extends AbstractKeepMaskStrategy {

    @Override
    public SensitiveType type() {
        return SensitiveType.CUSTOM;
    }
}
