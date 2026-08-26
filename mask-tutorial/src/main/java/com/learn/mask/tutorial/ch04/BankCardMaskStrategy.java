package com.learn.mask.tutorial.ch04;

/** 银行卡策略。卡号 16~19 位不定长，星号数量由 {@link MaskUtils#keepMask} 按实际长度算。 */
public class BankCardMaskStrategy extends AbstractKeepMaskStrategy {

    @Override
    public SensitiveType type() {
        return SensitiveType.BANK_CARD;
    }
}
