package com.learn.mask.tutorial.ch11;

import com.learn.mask.tutorial.ch04.SensitiveType;

public class BankCardSensitiveTypeHandler extends SensitiveTypeHandler {
    public BankCardSensitiveTypeHandler() {
        super(SensitiveType.BANK_CARD);
    }
}
