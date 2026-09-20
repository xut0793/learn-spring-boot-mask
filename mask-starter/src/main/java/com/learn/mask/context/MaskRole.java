package com.learn.mask.context;

import java.util.Locale;

/**
 * 脱敏角色：ADMIN 旁路明文，USER 始终打星，CS 出口打星但可调用还原接口。
 */
public enum MaskRole {
    /** 所有通道旁路，出口明文。 */
    ADMIN,
    /** 默认角色，正常脱敏。 */
    USER,
    /** 出口脱敏，但可调用还原接口。 */
    CS;

    /** 解析角色名或 {@code ROLE_*} 前缀；无法识别时返回 {@code null}。 */
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
