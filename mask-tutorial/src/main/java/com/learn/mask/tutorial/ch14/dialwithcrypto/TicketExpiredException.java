package com.learn.mask.tutorial.ch14.dialwithcrypto;

import java.time.Instant;

/**
 * DIAL 票已过期，外呼进程须让客服重新点「呼叫」签发新票。
 */
public class TicketExpiredException extends RuntimeException {

    /**
     * @param expiresAt 票面过期时刻
     */
    public TicketExpiredException(Instant expiresAt) {
        super("Ticket expired at " + expiresAt);
    }
}
