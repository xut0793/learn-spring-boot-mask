package com.learn.mask.strategy;

import com.learn.mask.annotation.SensitiveType;

/**
 * 身份证脱敏，默认保留前 6 后 4。
 */
public class IdCardMaskStrategy extends AbstractKeepMaskStrategy {

    @Override
    public SensitiveType type() {
        return SensitiveType.ID_CARD;
    }
}
