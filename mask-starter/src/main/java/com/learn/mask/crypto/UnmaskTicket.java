package com.learn.mask.crypto;

import java.time.Instant;

/**
 * AES 保护的是「能看 / 能拨哪一条」的声明，明文始终再从存储读取。
 */
public record UnmaskTicket(String subjectId, String field, Instant expiresAt, TicketPurpose purpose) {

    /** 字段分隔符，避免与常见 subjectId/field 字符冲突。 */
    private static final String SEP = "\u001f";

    /** 序列化为加密前的明文载荷。 */
    public String serialize() {
        return subjectId + SEP + field + SEP + expiresAt.toEpochMilli() + SEP + purpose.name();
    }

    /** 从 {@link #serialize()} 的结果解析；格式错误时抛出 {@link IllegalStateException}。 */
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
