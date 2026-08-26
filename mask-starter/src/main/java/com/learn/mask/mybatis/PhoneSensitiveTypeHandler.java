package com.learn.mask.mybatis;

import com.learn.mask.annotation.SensitiveType;

/**
 * 手机号结果集处理器，在 Mapper {@code @Result} 中按类名引用。
 */
public class PhoneSensitiveTypeHandler extends SensitiveTypeHandler {

    public PhoneSensitiveTypeHandler() {
        super(SensitiveType.PHONE);
    }
}
