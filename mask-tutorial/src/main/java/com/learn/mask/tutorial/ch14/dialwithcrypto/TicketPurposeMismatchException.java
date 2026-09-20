package com.learn.mask.tutorial.ch14.dialwithcrypto;

/**
 * 票面用途不是 {@link InMemoryTokenStore#PURPOSE_DIAL}，不能用于外呼核销。
 */
public class TicketPurposeMismatchException extends RuntimeException {

    /**
     * @param expected 外呼核销要求 {@code DIAL}
     * @param actual   票面实际用途
     */
    public TicketPurposeMismatchException(String expected, String actual) {
        super("Ticket purpose " + actual + " cannot be used as " + expected);
    }
}
