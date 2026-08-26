package com.learn.mask.tutorial.ch04;

/** 手机号策略。行为全部来自基类，保留位数来自规则配置。 */
public class PhoneMaskStrategy extends AbstractKeepMaskStrategy {

    @Override
    public SensitiveType type() {
        return SensitiveType.PHONE;
    }
}
