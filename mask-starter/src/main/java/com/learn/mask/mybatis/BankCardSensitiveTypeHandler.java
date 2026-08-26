package com.learn.mask.mybatis;

import com.learn.mask.annotation.SensitiveType;

/**
 * 银行卡结果集处理器，在 Mapper {@code @Result} 中按类名引用。
 */
public class BankCardSensitiveTypeHandler extends SensitiveTypeHandler {

    public BankCardSensitiveTypeHandler() {
        super(SensitiveType.BANK_CARD);
    }
}
