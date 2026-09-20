package com.learn.mask.tutorial.ch14.dialwithcrypto;

import com.learn.mask.tutorial.ch05.MaskContext;
import com.learn.mask.tutorial.ch05.MaskRole;
import com.learn.mask.tutorial.ch05.RoleProperties;
import com.learn.mask.tutorial.ch14.viewwithcrypto.ViewTicketService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DialTicketServiceTest {

    private RoleProperties roles;
    private MaskContext context;
    private MutableClock clock;
    private DialTicketService dialTickets;

    @BeforeEach
    void setUp() {
        roles = new RoleProperties();
        roles.getDebug().setHeaderRoleEnabled(true);
        context = new MaskContext(roles);
        clock = new MutableClock(Instant.parse("2026-08-26T12:00:00Z"));
        dialTickets = DialTicketService.demo(clock);
    }

    @AfterEach
    void tearDown() {
        MaskContext.clearHeaderRole();
    }

    @Test
    @DisplayName("点拨只返回 token；外呼核销后从用户 Map 取号")
    void dialTokenNeverExposesPlainToIssuer() {
        MaskContext.setHeaderRole(MaskRole.CS);

        String token = dialTickets.issueDialToken(context, new UnmaskRequest(1L, "phone"));

        assertThat(token).isNotEqualTo("13812345678");
        assertThat(token).doesNotContain("13812345678");
        assertThat(dialTickets.redeemForDial(token)).isEqualTo("13812345678");
        assertThat(dialTickets.auditTrail())
                .extracting(DialTicketService.AuditEvent::action)
                .containsExactly("ISSUE_DIAL", "REDEEM_DIAL");
    }

    @Test
    @DisplayName("查看场景的票不在外呼 Redis Map 中，不能核销")
    void viewTokenCannotRedeemOnDialStore() {
        MaskContext.setHeaderRole(MaskRole.CS);
        ViewTicketService viewTickets = ViewTicketService.demo(Clock.systemUTC());
        String viewToken = viewTickets.revealForView(
                context, new com.learn.mask.tutorial.ch14.viewwithcrypto.UnmaskRequest(1L, "phone")).token();

        assertThatThrownBy(() -> dialTickets.redeemForDial(viewToken))
                .isInstanceOf(UnknownTicketException.class);
    }

    @Test
    @DisplayName("外呼票不能在查看场景 Redis 里刷新")
    void dialTokenCannotRefreshOnViewStore() {
        MaskContext.setHeaderRole(MaskRole.CS);
        String dialToken = dialTickets.issueDialToken(context, new UnmaskRequest(1L, "phone"));

        ViewTicketService viewTickets = ViewTicketService.demo(Clock.systemUTC());

        assertThatThrownBy(() -> viewTickets.refreshView(context, dialToken))
                .isInstanceOf(com.learn.mask.tutorial.ch14.viewwithcrypto.UnknownTicketException.class);
    }

    @Test
    void userCannotIssueDialToken() {
        MaskContext.setHeaderRole(MaskRole.USER);
        assertThatThrownBy(() -> dialTickets.issueDialToken(context, new UnmaskRequest(1L, "phone")))
                .isInstanceOf(UnmaskDeniedException.class);
    }
}
