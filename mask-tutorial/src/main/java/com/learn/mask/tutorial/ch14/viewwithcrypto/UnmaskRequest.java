package com.learn.mask.tutorial.ch14.viewwithcrypto;

/**
 * 首次查看完整字段时的请求体（与 {@code POST /api/unmask} 形态一致）。
 *
 * @param userId 用户 id
 * @param field  要查看的敏感字段，须在白名单内
 */
public record UnmaskRequest(long userId, String field) {
}
