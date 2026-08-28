package com.learn.mask.tutorial.ch14;

import com.learn.mask.tutorial.ch05.MaskContext;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 线程 B 的完整形态：签发可核销的限时票据。
 * <p>
 * 场景一 {@link #revealForView} / {@link #refreshView}：客服弹层 60 秒内刷新，不必再点一次字段。
 * 场景二 {@link #issueDialToken} / {@link #redeemForDial}：浏览器不拿明文，外呼系统持票取号。
 */
public class UnmaskTicketService {

    private final ReversibleMasker masker;
    private final SensitiveFieldStore store;
    private final Set<String> reversibleFields;
    private final Duration ttl;
    private final Clock clock;
    private final List<AuditEvent> audit = new ArrayList<>();

    public UnmaskTicketService(ReversibleMasker masker,
                               SensitiveFieldStore store,
                               Set<String> reversibleFields,
                               Duration ttl,
                               Clock clock) {
        this.masker = masker;
        this.store = store;
        this.reversibleFields = Set.copyOf(reversibleFields);
        this.ttl = ttl;
        this.clock = clock;
    }

    public static UnmaskTicketService demo(ReversibleMasker masker, SensitiveFieldStore store) {
        Set<String> fields = new LinkedHashSet<>();
        fields.add("phone");
        fields.add("idCard");
        return new UnmaskTicketService(masker, store, fields, Duration.ofSeconds(60), Clock.systemUTC());
    }

    /**
     * 场景一签发：CS 点「查看完整号码」。返回明文给弹层，并给一张 60 秒 VIEW 票。
     */
    public RevealResult revealForView(MaskContext context, String subjectId, String field) {
        authorize(context, field);
        String value = store.read(subjectId, field);
        Instant expiresAt = clock.instant().plus(ttl);
        String token = seal(new UnmaskTicket(subjectId, field, expiresAt, TicketPurpose.VIEW));
        audit.add(new AuditEvent("REVEAL", subjectId, field, TicketPurpose.VIEW));
        return new RevealResult(field, value, token, expiresAt);
    }

    /**
     * 场景一核销：弹层关掉再打开，持同一张票刷新。
     * 角色可能已被收回，所以还要再跑一遍 {@code canUnmask()}。号码再读库，不信票面。
     */
    public RevealResult refreshView(MaskContext context, String token) {
        UnmaskTicket ticket = open(token, TicketPurpose.VIEW);
        if (context == null || !context.canUnmask()) {
            throw new UnmaskService.UnmaskDeniedException("Current role cannot unmask");
        }
        String value = store.read(ticket.subjectId(), ticket.field());
        audit.add(new AuditEvent("REFRESH", ticket.subjectId(), ticket.field(), TicketPurpose.VIEW));
        return new RevealResult(ticket.field(), value, token, ticket.expiresAt());
    }

    /**
     * 场景二签发：CS 点「呼叫」。浏览器只拿到 token，看不到号码。
     */
    public String issueDialToken(MaskContext context, String subjectId, String field) {
        authorize(context, field);
        store.read(subjectId, field);
        Instant expiresAt = clock.instant().plus(ttl);
        String token = seal(new UnmaskTicket(subjectId, field, expiresAt, TicketPurpose.DIAL));
        audit.add(new AuditEvent("ISSUE_DIAL", subjectId, field, TicketPurpose.DIAL));
        return token;
    }

    /**
     * 场景二核销：外呼进程持票取号。票本身就是对「这一条、这一用途、这一分钟」的能力证明。
     * 外呼服务用自己的身份进内网（真实环境是 mTLS / 服务账号），这里不再要 CS 的 MaskContext。
     */
    public String redeemForDial(String token) {
        UnmaskTicket ticket = open(token, TicketPurpose.DIAL);
        String value = store.read(ticket.subjectId(), ticket.field());
        audit.add(new AuditEvent("REDEEM_DIAL", ticket.subjectId(), ticket.field(), TicketPurpose.DIAL));
        return value;
    }

    public List<AuditEvent> auditTrail() {
        return List.copyOf(audit);
    }

    private void authorize(MaskContext context, String field) {
        if (context == null || !context.canUnmask()) {
            throw new UnmaskService.UnmaskDeniedException("Current role cannot unmask");
        }
        if (field == null || !reversibleFields.contains(field)) {
            throw new UnmaskService.UnmaskDeniedException("Field is not reversible: " + field);
        }
    }

    private String seal(UnmaskTicket ticket) {
        return masker.encrypt(ticket.serialize());
    }

    private UnmaskTicket open(String token, TicketPurpose expected) {
        UnmaskTicket ticket = UnmaskTicket.parse(masker.decrypt(token));
        if (ticket.purpose() != expected) {
            throw new TicketPurposeMismatchException(expected, ticket.purpose());
        }
        if (!clock.instant().isBefore(ticket.expiresAt())) {
            throw new TicketExpiredException(ticket.expiresAt());
        }
        return ticket;
    }

    public record RevealResult(String field, String value, String token, Instant expiresAt) {
    }

    public record AuditEvent(String action, String subjectId, String field, TicketPurpose purpose) {
    }

    public static class TicketExpiredException extends RuntimeException {
        public TicketExpiredException(Instant expiresAt) {
            super("Ticket expired at " + expiresAt);
        }
    }

    public static class TicketPurposeMismatchException extends RuntimeException {
        public TicketPurposeMismatchException(TicketPurpose expected, TicketPurpose actual) {
            super("Ticket purpose " + actual + " cannot be used as " + expected);
        }
    }
}
