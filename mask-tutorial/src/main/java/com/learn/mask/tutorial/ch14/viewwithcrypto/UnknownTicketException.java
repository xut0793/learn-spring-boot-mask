package com.learn.mask.tutorial.ch14.viewwithcrypto;

/**
 * 模拟 Redis 中不存在该 token：未在本场景签发，或已被过期清理。
 */
public class UnknownTicketException extends RuntimeException {

    /**
     * @param token 客户端提交的 token 字符串（仅用于错误信息，日志中应脱敏）
     */
    public UnknownTicketException(String token) {
        super("Unknown or expired ticket in store: " + token);
    }
}
