package com.learn.mask.tutorial.ch14.viewwithcrypto;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Map 模拟 Redis：本场景签发的 VIEW token 写入此处，{@code refreshView} 时必须先命中。
 * <p>
 * 与 AES 票面双重校验：Redis 证明「服务端曾签发」，AES 证明「票面未被篡改」。
 */
final class InMemoryTokenStore {

    /** 查看弹层用途常量，写入票面与 Redis 记录。 */
    static final String PURPOSE_VIEW = "VIEW";

    /** token 字符串 → 签发时登记的元数据。 */
    private final Map<String, TokenRecord> tokens = new ConcurrentHashMap<>();

    /** 判断过期、与票面对齐过期时刻。 */
    private final Clock clock;

    /**
     * @param clock 通常可注入固定时钟以便单测 TTL
     */
    InMemoryTokenStore(Clock clock) {
        this.clock = clock;
    }

    /**
     * 签发查看票后调用，TTL 由 {@link ViewTicketService} 在 {@code expiresAt} 中体现。
     */
    void saveViewTicket(String token, long userId, String field, Instant expiresAt) {
        tokens.put(token, new TokenRecord(userId, field, expiresAt, PURPOSE_VIEW));
    }

    /**
     * 刷新前查 Redis：不存在、已过期或用途不对均 fail-loud。
     *
     * @return 与 token 关联的元数据，供与 AES 解密结果比对
     */
    TokenRecord requireViewTicket(String token) {
        TokenRecord record = tokens.get(token);
        if (record == null) {
            throw new UnknownTicketException(token);
        }
        if (!clock.instant().isBefore(record.expiresAt())) {
            tokens.remove(token);
            throw new TicketExpiredException(record.expiresAt());
        }
        if (!PURPOSE_VIEW.equals(record.purpose())) {
            throw new TicketPurposeMismatchException(PURPOSE_VIEW, record.purpose());
        }
        return record;
    }

    /**
     * 模拟 Redis 中一条 VIEW 票记录（不含明文，只有定位信息）。
     *
     * @param userId    用户 id
     * @param field     字段名
     * @param expiresAt 过期时刻
     * @param purpose   固定为 {@link #PURPOSE_VIEW}
     */
    record TokenRecord(long userId, String field, Instant expiresAt, String purpose) {
    }
}
