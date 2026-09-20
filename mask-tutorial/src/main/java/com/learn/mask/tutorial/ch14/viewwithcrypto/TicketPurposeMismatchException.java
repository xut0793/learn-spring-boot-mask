package com.learn.mask.tutorial.ch14.viewwithcrypto;

/**
 * 票面用途与当前操作不符（例如拿 DIAL 票来刷新弹层）。
 */
public class TicketPurposeMismatchException extends RuntimeException {

    /**
     * @param expected 本接口要求的用途，查看场景为 {@link InMemoryTokenStore#PURPOSE_VIEW}
     * @param actual   票面或 Redis 记录中的实际用途
     */
    public TicketPurposeMismatchException(String expected, String actual) {
        super("Ticket purpose " + actual + " cannot be used as " + expected);
    }
}
