package com.learn.mask.strategy;

import com.learn.mask.annotation.SensitiveType;

/**
 * 银行卡脱敏，默认保留前 4 后 4。
 */
public class BankCardMaskStrategy extends AbstractKeepMaskStrategy {

    @Override
    public SensitiveType type() {
        return SensitiveType.BANK_CARD;
    }
}
