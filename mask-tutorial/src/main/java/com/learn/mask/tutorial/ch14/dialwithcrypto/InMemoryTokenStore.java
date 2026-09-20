package com.learn.mask.tutorial.ch14.dialwithcrypto;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Map 模拟 Redis：仅保存本场景签发的 DIAL token，与 {@code viewwithcrypto} 包的 Redis 相互独立。
 */
final class InMemoryTokenStore {

    /** 外呼核销用途。 */
    static final String PURPOSE_DIAL = "DIAL";

    /** token → 签发时写入的元数据（不含明文号码）。 */
    private final Map<String, TokenRecord> tokens = new ConcurrentHashMap<>();

    /** 判断 TTL 是否已过。 */
    private final Clock clock;

    InMemoryTokenStore(Clock clock) {
        this.clock = clock;
    }

    /** {@link DialTicketService#issueDialToken} 成功后登记。 */
    void saveDialTicket(String token, long userId, String field, Instant expiresAt) {
        tokens.put(token, new TokenRecord(userId, field, expiresAt, PURPOSE_DIAL));
    }

    /**
     * 外呼核销前查 Redis。
     *
     * @throws UnknownTicketException  未在本场景签发
     * @throws TicketExpiredException  已超过 expiresAt
     */
    TokenRecord requireDialTicket(String token) {
        TokenRecord record = tokens.get(token);
        if (record == null) {
            throw new UnknownTicketException(token);
        }
        if (!clock.instant().isBefore(record.expiresAt())) {
            tokens.remove(token);
            throw new TicketExpiredException(record.expiresAt());
        }
        if (!PURPOSE_DIAL.equals(record.purpose())) {
            throw new TicketPurposeMismatchException(PURPOSE_DIAL, record.purpose());
        }
        return record;
    }

    /**
     * Redis 中一条 DIAL 票。
     *
     * @param userId    被拨号用户
     * @param field     字段
     * @param expiresAt 过期时间
     * @param purpose   固定 {@link #PURPOSE_DIAL}
     */
    record TokenRecord(long userId, String field, Instant expiresAt, String purpose) {
    }
}
