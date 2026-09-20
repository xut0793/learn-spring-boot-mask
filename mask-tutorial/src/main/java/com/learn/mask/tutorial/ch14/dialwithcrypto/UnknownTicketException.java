package com.learn.mask.tutorial.ch14.dialwithcrypto;

/**
 * 外呼 Redis Map 中无此 token（未在本场景签发，或已过期删除）。
 */
public class UnknownTicketException extends RuntimeException {

    /**
     * @param token 客户端或外呼进程提交的 token
     */
    public UnknownTicketException(String token) {
        super("Unknown or expired ticket in store: " + token);
    }
}
