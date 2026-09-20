package com.learn.mask.tutorial.ch14.immediatewithoutcrypto;

/**
 * 对应 {@code POST /api/unmask} 的 JSON 请求体。
 *
 * @param userId 要还原的用户主键，例如 {@code 1}
 * @param field  字段名，例如 {@code "phone"}，须在服务白名单内
 */
public record UnmaskRequest(long userId, String field) {
}
