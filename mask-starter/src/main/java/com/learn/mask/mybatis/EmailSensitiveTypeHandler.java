package com.learn.mask.mybatis;

import com.learn.mask.annotation.SensitiveType;

/**
 * 邮箱结果集处理器，在 Mapper {@code @Result} 中按类名引用。
 */
public class EmailSensitiveTypeHandler extends SensitiveTypeHandler {

    public EmailSensitiveTypeHandler() {
        super(SensitiveType.EMAIL);
    }
}
