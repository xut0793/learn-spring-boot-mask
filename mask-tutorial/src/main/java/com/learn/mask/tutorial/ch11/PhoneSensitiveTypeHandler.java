package com.learn.mask.tutorial.ch11;

import com.learn.mask.tutorial.ch04.SensitiveType;

/**
 * 手机号 TypeHandler。MyBatis 的 {@code @Result(typeHandler = X.class)} 只能写类名、
 * 不能传构造参数，所以每种类型必须有一个无参子类。
 */
public class PhoneSensitiveTypeHandler extends SensitiveTypeHandler {
    public PhoneSensitiveTypeHandler() {
        super(SensitiveType.PHONE);
    }
}
