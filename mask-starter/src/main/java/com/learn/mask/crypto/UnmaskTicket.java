package com.learn.mask.crypto;

import java.time.Instant;

/**
 * AES 保护的是「能看 / 能拨哪一条」的声明，明文始终再从存储读取。
 */
public record UnmaskTicket(String subjectId, String field, Instant expiresAt, TicketPurpose purpose) {

    private static final String SEP = "\u001f";

    public String serialize() {
        return subjectId + SEP + field + SEP + expiresAt.toEpochMilli() + SEP + purpose.name();
    }

    public static UnmaskTicket parse(String raw) {
        if (raw == null) {
            throw new IllegalStateException("Ticket payload is blank");
        }
        String[] parts = raw.split(SEP, -1);
        if (parts.length != 4) {
            throw new IllegalStateException("Malformed ticket payload");
        }
        return new UnmaskTicket(
                parts[0],
                parts[1],
                Instant.ofEpochMilli(Long.parseLong(parts[2])),
                TicketPurpose.valueOf(parts[3]));
    }
}
