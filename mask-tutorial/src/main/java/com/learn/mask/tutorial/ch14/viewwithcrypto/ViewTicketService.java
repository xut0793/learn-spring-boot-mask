package com.learn.mask.tutorial.ch14.viewwithcrypto;

import com.learn.mask.tutorial.ch05.MaskContext;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 场景：客服弹层 60 秒内刷新（线程 B · VIEW）。
 * <p>
 * <b>revealForView</b>：鉴权 → {@link InMemoryUserStore} 读明文 → AES 封票面 →
 * {@link InMemoryTokenStore} 登记 token → 返回 {@code value + token}。<br>
 * <b>refreshView</b>：Redis 查票 → AES 验票面 → 再 {@code canUnmask()} → 再读库。
 */
public class ViewTicketService {

    /** 票面字段分隔符（ASCII Unit Separator）。 */
    private static final String SEP = "\u001f";

    private static final String PURPOSE_VIEW = InMemoryTokenStore.PURPOSE_VIEW;

    /** 可还原字段白名单。 */
    private final Set<String> reversibleFields;

    /** 用户敏感列原文。 */
    private final InMemoryUserStore userStore;

    /** 模拟 Redis，仅存本场景签发的 VIEW token。 */
    private final InMemoryTokenStore tokenStore;

    /** 票面 AES-GCM 加解密。 */
    private final AesGcmReversibleMasker masker;

    /** 查看票有效时长，默认 demo 为 60 秒。 */
    private final Duration ttl;

    /** 当前时间，与 token 过期判断、Redis 清理一致。 */
    private final Clock clock;

    /** 教程用内存审计链（REVEAL / REFRESH）。 */
    private final List<AuditEvent> audit = new ArrayList<>();

    /**
     * 组装完整查看场景（生产由 Spring 注入各依赖）。
     */
    public ViewTicketService(Set<String> reversibleFields,
                             InMemoryUserStore userStore,
                             InMemoryTokenStore tokenStore,
                             AesGcmReversibleMasker masker,
                             Duration ttl,
                             Clock clock) {
        this.reversibleFields = Set.copyOf(reversibleFields);
        this.userStore = userStore;
        this.tokenStore = tokenStore;
        this.masker = masker;
        this.ttl = ttl;
        this.clock = clock;
    }

    /**
     * 教程默认：白名单 phone/idCard、demo 用户库、60s TTL、demo AES 密钥。
     *
     * @param clock 可传入可变时钟以便单测过期
     */
    public static ViewTicketService demo(Clock clock) {
        Set<String> fields = new LinkedHashSet<>();
        fields.add("phone");
        fields.add("idCard");
        return new ViewTicketService(
                fields,
                InMemoryUserStore.demo(),
                new InMemoryTokenStore(clock),
                new AesGcmReversibleMasker(ReversibleOptions.demo()),
                Duration.ofSeconds(60),
                clock);
    }

    /**
     * CS 点「查看完整号码」：返回弹层明文 + 60 秒 VIEW token。
     *
     * @param context 须 {@code canUnmask()}
     * @param request 用户与字段
     */
    public RevealResult revealForView(MaskContext context, UnmaskRequest request) {
        authorize(context, request.field());
        String value = userStore.readField(request.userId(), request.field());
        Instant expiresAt = clock.instant().plus(ttl);
        String payload = serializePayload(request.userId(), request.field(), expiresAt, PURPOSE_VIEW);
        String token = masker.encrypt(payload);
        tokenStore.saveViewTicket(token, request.userId(), request.field(), expiresAt);
        audit.add(new AuditEvent("REVEAL", request.userId(), request.field()));
        return new RevealResult(request.userId(), request.field(), value, token, expiresAt);
    }

    /**
     * 弹层关闭再打开：持原 token 刷新，仍须 {@code canUnmask()}，明文再次从库读取。
     *
     * @param token {@link #revealForView} 返回的 token
     */
    public RevealResult refreshView(MaskContext context, String token) {
        TokenSnapshot snapshot = openViewToken(token);
        if (context == null || !context.canUnmask()) {
            throw new UnmaskDeniedException("Current role cannot unmask");
        }
        String value = userStore.readField(snapshot.userId(), snapshot.field());
        audit.add(new AuditEvent("REFRESH", snapshot.userId(), snapshot.field()));
        return new RevealResult(snapshot.userId(), snapshot.field(), value, token, snapshot.expiresAt());
    }

    /** 返回本实例累积的审计事件副本。 */
    public List<AuditEvent> auditTrail() {
        return List.copyOf(audit);
    }

    /** 角色 + 字段白名单。 */
    private void authorize(MaskContext context, String field) {
        if (context == null || !context.canUnmask()) {
            throw new UnmaskDeniedException("Current role cannot unmask");
        }
        if (field == null || !reversibleFields.contains(field)) {
            throw new UnmaskDeniedException("Field is not reversible: " + field);
        }
    }

    /** Redis 查票 + AES 解密 + 用途/过期/与 Redis 记录一致性校验。 */
    private TokenSnapshot openViewToken(String token) {
        InMemoryTokenStore.TokenRecord redisRecord = tokenStore.requireViewTicket(token);
        TokenSnapshot fromCipher = parsePayload(masker.decrypt(token));
        if (fromCipher.userId() != redisRecord.userId()
                || !fromCipher.field().equals(redisRecord.field())
                || !fromCipher.expiresAt().equals(redisRecord.expiresAt())) {
            throw new IllegalStateException("Ticket payload does not match store record");
        }
        if (!PURPOSE_VIEW.equals(fromCipher.purpose())) {
            throw new TicketPurposeMismatchException(PURPOSE_VIEW, fromCipher.purpose());
        }
        if (!clock.instant().isBefore(fromCipher.expiresAt())) {
            throw new TicketExpiredException(fromCipher.expiresAt());
        }
        return fromCipher;
    }

    private static String serializePayload(long userId, String field, Instant expiresAt, String purpose) {
        return userId + SEP + field + SEP + expiresAt.toEpochMilli() + SEP + purpose;
    }

    private static TokenSnapshot parsePayload(String raw) {
        if (raw == null) {
            throw new IllegalStateException("Ticket payload is blank");
        }
        String[] parts = raw.split(SEP, -1);
        if (parts.length != 4) {
            throw new IllegalStateException("Malformed ticket payload");
        }
        return new TokenSnapshot(
                Long.parseLong(parts[0]),
                parts[1],
                Instant.ofEpochMilli(Long.parseLong(parts[2])),
                parts[3]);
    }

    /** AES 解密后的票面快照（不含手机号）。 */
    private record TokenSnapshot(long userId, String field, Instant expiresAt, String purpose) {
    }

    /**
     * 查看/刷新接口响应。
     *
     * @param userId    用户 id
     * @param field     字段
     * @param value     库中明文（仅 reveal/refresh 时返回给前端弹层）
     * @param token     AES+Redis 票
     * @param expiresAt 过期时刻
     */
    public record RevealResult(long userId, String field, String value, String token, Instant expiresAt) {
    }

    /**
     * 审计日志条目。
     *
     * @param action  {@code REVEAL} 或 {@code REFRESH}
     * @param userId  目标用户
     * @param field   字段名
     */
    public record AuditEvent(String action, long userId, String field) {
    }
}
