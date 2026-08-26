package com.learn.mask.tutorial.ch04;

/** 身份证策略。 */
public class IdCardMaskStrategy extends AbstractKeepMaskStrategy {

    @Override
    public SensitiveType type() {
        return SensitiveType.ID_CARD;
    }
}
