package com.learn.mask.mybatis;

import com.learn.mask.annotation.SensitiveType;

/**
 * 身份证结果集处理器，在 Mapper {@code @Result} 中按类名引用。
 */
public class IdCardSensitiveTypeHandler extends SensitiveTypeHandler {

    public IdCardSensitiveTypeHandler() {
        super(SensitiveType.ID_CARD);
    }
}
