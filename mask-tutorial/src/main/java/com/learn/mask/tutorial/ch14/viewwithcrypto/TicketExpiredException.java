package com.learn.mask.tutorial.ch14.viewwithcrypto;

import java.time.Instant;

/**
 * VIEW 票已超过 {@link ViewTicketService} 配置的 TTL，须重新 {@code revealForView}。
 */
public class TicketExpiredException extends RuntimeException {

    /**
     * @param expiresAt 票面记录的过期时刻（UTC）
     */
    public TicketExpiredException(Instant expiresAt) {
        super("Ticket expired at " + expiresAt);
    }
}
