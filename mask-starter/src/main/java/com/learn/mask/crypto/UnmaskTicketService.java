package com.learn.mask.crypto;

import com.learn.mask.config.MaskingProperties;
import com.learn.mask.context.MaskContext;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 签发并核销限时票据。查看走 {@link #revealForView} / {@link #refreshView}；
 * 点拨走 {@link #issueDialToken} / {@link #redeemForDial}。
 */
public class UnmaskTicketService {

    private final ReversibleMasker masker;
    private final SensitiveFieldLookup store;
    private final MaskingProperties properties;
    private final MaskContext maskContext;
    private final Duration ttl;
    private final Clock clock;
    private final List<AuditEvent> audit = new ArrayList<>();

    public UnmaskTicketService(ReversibleMasker masker,
                               SensitiveFieldLookup store,
                               MaskingProperties properties,
                               MaskContext maskContext) {
        this(masker, store, properties, maskContext, Duration.ofSeconds(60), Clock.systemUTC());
    }

    public UnmaskTicketService(ReversibleMasker masker,
                               SensitiveFieldLookup store,
                               MaskingProperties properties,
                               MaskContext maskContext,
                               Duration ttl,
                               Clock clock) {
        this.masker = masker;
        this.store = store;
        this.properties = properties;
        this.maskContext = maskContext;
        this.ttl = ttl;
        this.clock = clock;
    }

    public RevealResult revealForView(String subjectId, String field) {
        authorize(field);
        String value = store.read(subjectId, field);
        Instant expiresAt = clock.instant().plus(ttl);
        String token = seal(new UnmaskTicket(subjectId, field, expiresAt, TicketPurpose.VIEW));
        audit.add(new AuditEvent("REVEAL", subjectId, field, TicketPurpose.VIEW));
        return new RevealResult(field, value, token, expiresAt);
    }

    public RevealResult refreshView(String token) {
        UnmaskTicket ticket = open(token, TicketPurpose.VIEW);
        if (maskContext == null || !maskContext.canUnmask()) {
            throw new UnmaskDeniedException("Current role cannot unmask");
        }
        String value = store.read(ticket.subjectId(), ticket.field());
        audit.add(new AuditEvent("REFRESH", ticket.subjectId(), ticket.field(), TicketPurpose.VIEW));
        return new RevealResult(ticket.field(), value, token, ticket.expiresAt());
    }

    public String issueDialToken(String subjectId, String field) {
        authorize(field);
        store.read(subjectId, field);
        Instant expiresAt = clock.instant().plus(ttl);
        String token = seal(new UnmaskTicket(subjectId, field, expiresAt, TicketPurpose.DIAL));
        audit.add(new AuditEvent("ISSUE_DIAL", subjectId, field, TicketPurpose.DIAL));
        return token;
    }

    public String redeemForDial(String token) {
        UnmaskTicket ticket = open(token, TicketPurpose.DIAL);
        String value = store.read(ticket.subjectId(), ticket.field());
        audit.add(new AuditEvent("REDEEM_DIAL", ticket.subjectId(), ticket.field(), TicketPurpose.DIAL));
        return value;
    }

    public List<AuditEvent> auditTrail() {
        return List.copyOf(audit);
    }

    private void authorize(String field) {
        if (!properties.getReversible().isEnabled()) {
            throw new UnmaskDeniedException("Reversible unmask is disabled");
        }
        if (maskContext == null || !maskContext.canUnmask()) {
            throw new UnmaskDeniedException("Current role cannot unmask");
        }
        if (!properties.getReversible().allows(field)) {
            throw new UnmaskDeniedException("Field is not reversible: " + field);
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

    public static class UnmaskDeniedException extends RuntimeException {
        public UnmaskDeniedException(String message) {
            super(message);
        }
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
