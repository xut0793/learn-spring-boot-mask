package com.learn.mask.tutorial.ch14.immediatewithoutcrypto;

/**
 * 还原请求被拒绝：角色无权 {@code canUnmask()}、字段不在白名单、或用户/字段在模拟库中不存在。
 */
public class UnmaskDeniedException extends RuntimeException {

    /**
     * @param message 拒绝原因，会原样返回给调用方（教程里直接抛出，真实项目可映射 403）
     */
    public UnmaskDeniedException(String message) {
        super(message);
    }
}
