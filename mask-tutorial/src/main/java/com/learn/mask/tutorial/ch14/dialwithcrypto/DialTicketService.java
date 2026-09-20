package com.learn.mask.tutorial.ch14.dialwithcrypto;

import com.learn.mask.tutorial.ch05.MaskContext;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 场景：点拨外呼，浏览器永远不拿号码（线程 B · DIAL）。
 * <p>
 * <b>issueDialToken</b>：鉴权 → 确认库中有该字段 → AES 封 DIAL 票 → Redis 登记 → 只返回 token。<br>
 * <b>redeemForDial</b>：外呼进程持 token 核销，不再要求 CS 的 {@link MaskContext}，明文从
 * {@link InMemoryUserStore} 读取。
 */
public class DialTicketService {

    private static final String SEP = "\u001f";

    private static final String PURPOSE_DIAL = InMemoryTokenStore.PURPOSE_DIAL;

    /** 允许签发外呼票的字段白名单。 */
    private final Set<String> reversibleFields;

    /** 库中明文来源。 */
    private final InMemoryUserStore userStore;

    /** 本场景专属 token Redis 模拟。 */
    private final InMemoryTokenStore tokenStore;

    private final AesGcmReversibleMasker masker;

    /** DIAL 票 TTL。 */
    private final Duration ttl;

    private final Clock clock;

    /** ISSUE_DIAL / REDEEM_DIAL 审计。 */
    private final List<AuditEvent> audit = new ArrayList<>();

    public DialTicketService(Set<String> reversibleFields,
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
     * 教程默认 wiring：60 秒 TTL、demo 用户与密钥。
     *
     * @param clock 单测可注入可变时间
     */
    public static DialTicketService demo(Clock clock) {
        Set<String> fields = new LinkedHashSet<>();
        fields.add("phone");
        fields.add("idCard");
        return new DialTicketService(
                fields,
                InMemoryUserStore.demo(),
                new InMemoryTokenStore(clock),
                new AesGcmReversibleMasker(ReversibleOptions.demo()),
                Duration.ofSeconds(60),
                clock);
    }

    /**
     * 客服点「呼叫」：响应体不含 {@code value}，只有 token。
     *
     * @param context 浏览器侧 CS 会话，须 {@code canUnmask()}
     * @param request 目标用户与字段
     * @return AES 加密且已写入 Redis 的 token
     */
    public String issueDialToken(MaskContext context, UnmaskRequest request) {
        authorize(context, request.field());
        userStore.readField(request.userId(), request.field());
        Instant expiresAt = clock.instant().plus(ttl);
        String payload = serializePayload(request.userId(), request.field(), expiresAt, PURPOSE_DIAL);
        String token = masker.encrypt(payload);
        tokenStore.saveDialTicket(token, request.userId(), request.field(), expiresAt);
        audit.add(new AuditEvent("ISSUE_DIAL", request.userId(), request.field()));
        return token;
    }

    /**
     * 外呼内网进程核销：查 Redis → 验 AES 票面 → 读库得号。
     *
     * @param token {@link #issueDialToken} 的返回值
     * @return 待拨打的明文号码（用完应丢弃，勿写日志）
     */
    public String redeemForDial(String token) {
        TokenSnapshot snapshot = openDialToken(token);
        String value = userStore.readField(snapshot.userId(), snapshot.field());
        audit.add(new AuditEvent("REDEEM_DIAL", snapshot.userId(), snapshot.field()));
        return value;
    }

    public List<AuditEvent> auditTrail() {
        return List.copyOf(audit);
    }

    private void authorize(MaskContext context, String field) {
        if (context == null || !context.canUnmask()) {
            throw new UnmaskDeniedException("Current role cannot unmask");
        }
        if (field == null || !reversibleFields.contains(field)) {
            throw new UnmaskDeniedException("Field is not reversible: " + field);
        }
    }

    private TokenSnapshot openDialToken(String token) {
        InMemoryTokenStore.TokenRecord redisRecord = tokenStore.requireDialTicket(token);
        TokenSnapshot fromCipher = parsePayload(masker.decrypt(token));
        if (fromCipher.userId() != redisRecord.userId()
                || !fromCipher.field().equals(redisRecord.field())
                || !fromCipher.expiresAt().equals(redisRecord.expiresAt())) {
            throw new IllegalStateException("Ticket payload does not match store record");
        }
        if (!PURPOSE_DIAL.equals(fromCipher.purpose())) {
            throw new TicketPurposeMismatchException(PURPOSE_DIAL, fromCipher.purpose());
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

    private record TokenSnapshot(long userId, String field, Instant expiresAt, String purpose) {
    }

    /**
     * @param action   {@code ISSUE_DIAL} 或 {@code REDEEM_DIAL}
     * @param userId   用户 id
     * @param field    字段名
     */
    public record AuditEvent(String action, long userId, String field) {
    }
}
