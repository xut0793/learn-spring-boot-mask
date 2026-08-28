package com.learn.mask.crypto;

import com.learn.mask.config.MaskingProperties;
import com.learn.mask.context.MaskContext;
import com.learn.mask.context.MaskRole;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UnmaskTicketServiceTest {

    private MaskingProperties properties;
    private MaskContext context;
    private UnmaskTicketService tickets;

    @BeforeEach
    void setUp() {
        properties = new MaskingProperties();
        properties.getDebug().setHeaderRoleEnabled(true);
        context = new MaskContext(properties);
        tickets = new UnmaskTicketService(
                new AesGcmReversibleMasker(properties),
                (id, field) -> Map.of("phone", "13812345678").get(field),
                properties,
                context,
                Duration.ofSeconds(60),
                Clock.fixed(Instant.parse("2026-08-26T12:00:00Z"), ZoneOffset.UTC));
    }

    @AfterEach
    void tearDown() {
        MaskContext.clearHeaderRole();
    }

    @Test
    void revealThenRefresh() {
        MaskContext.setHeaderRole(MaskRole.CS);
        UnmaskTicketService.RevealResult first = tickets.revealForView("1", "phone");
        UnmaskTicketService.RevealResult again = tickets.refreshView(first.token());
        assertThat(first.value()).isEqualTo("13812345678");
        assertThat(again.value()).isEqualTo("13812345678");
        assertThat(first.token()).doesNotContain("13812345678");
    }

    @Test
    void dialTokenHidesPlaintext() {
        MaskContext.setHeaderRole(MaskRole.CS);
        String token = tickets.issueDialToken("1", "phone");
        assertThat(token).doesNotContain("13812345678");
        assertThat(tickets.redeemForDial(token)).isEqualTo("13812345678");
    }

    @Test
    void emailIsNotAllowlisted() {
        MaskContext.setHeaderRole(MaskRole.CS);
        assertThatThrownBy(() -> tickets.revealForView("1", "email"))
                .isInstanceOf(UnmaskTicketService.UnmaskDeniedException.class);
    }
}
