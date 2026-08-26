package com.learn.mask.context;

import java.util.Locale;

/**
 * 脱敏角色：ADMIN 旁路明文，USER 始终打星，CS 出口打星但可调用还原接口。
 */
public enum MaskRole {
    ADMIN,
    USER,
    CS;

    public static MaskRole parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String normalized = raw.trim().toUpperCase(Locale.ROOT);
        if (normalized.startsWith("ROLE_")) {
            normalized = normalized.substring(5);
        }
        return switch (normalized) {
            case "ADMIN" -> ADMIN;
            case "CS", "CUSTOMER_SERVICE" -> CS;
            case "USER" -> USER;
            default -> null;
        };
    }
}
