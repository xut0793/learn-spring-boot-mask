package com.learn.mask.tutorial.ch05;

import java.util.Locale;

/**
 * 脱敏角色。
 * <p>
 * 只有三个值，但它们代表的是三种**不同的权限语义**，不要理解成「权限从低到高」：
 * <ul>
 *   <li>{@link #USER}：始终脱敏。也是所有无法识别情况的安全默认值。</li>
 *   <li>{@link #ADMIN}：旁路脱敏，接口直接返回明文。</li>
 *   <li>{@link #CS}：出口仍然打星，但**允许调用还原接口**按需取明文。</li>
 * </ul>
 * CS 不是「介于 USER 和 ADMIN 之间」，而是另一个维度：它的默认视图和 USER 一样干净，
 * 区别只在于多了一条「按次、可审计」的明文获取通道。见第 14 章。
 */
public enum MaskRole {
    ADMIN,
    USER,
    CS;

    /**
     * 从任意字符串解析角色，无法识别时返回 {@code null}。
     * <p>
     * 之所以要这么宽容，是因为入参来源五花八门：Spring Security 的权限字符串
     * （{@code ROLE_ADMIN}）、调试请求头（{@code admin}）、配置文件里的列表项（{@code ADMIN}）。
     * 与其要求每个调用点自己规整，不如在这里一次处理干净。
     */
    public static MaskRole parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String normalized = raw.trim().toUpperCase(Locale.ROOT);
        // Spring Security 的 GrantedAuthority 默认带 ROLE_ 前缀
        if (normalized.startsWith("ROLE_")) {
            normalized = normalized.substring(5);
        }
        return switch (normalized) {
            case "ADMIN" -> ADMIN;
            // 同一个角色的两种常见写法都接受
            case "CS", "CUSTOMER_SERVICE" -> CS;
            case "USER" -> USER;
            // 注意：返回 null 而不是 USER。让调用方决定怎么兜底，
            // 因为「解析失败」和「确实是 USER」是两件事，混在一起会掩盖配置错误。
            default -> null;
        };
    }
}
