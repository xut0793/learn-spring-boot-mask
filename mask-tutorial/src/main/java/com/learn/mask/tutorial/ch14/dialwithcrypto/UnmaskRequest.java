package com.learn.mask.tutorial.ch14.dialwithcrypto;

/**
 * 点拨外呼时的签发请求（浏览器仍只见打码手机号，响应里只有 token）。
 *
 * @param userId 被呼叫用户 id
 * @param field  通常为 {@code phone}
 */
public record UnmaskRequest(long userId, String field) {
}
