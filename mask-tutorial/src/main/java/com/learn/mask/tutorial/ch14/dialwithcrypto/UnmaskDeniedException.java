package com.learn.mask.tutorial.ch14.dialwithcrypto;

/**
 * 外呼签发阶段的业务拒绝：无权 {@code canUnmask()}、字段不在白名单、用户或字段不存在。
 */
public class UnmaskDeniedException extends RuntimeException {

    /**
     * @param message 拒绝原因
     */
    public UnmaskDeniedException(String message) {
        super(message);
    }
}
