package com.learn.mask.crypto;

import com.learn.mask.config.MaskingProperties;
import com.learn.mask.context.MaskContext;
import com.learn.mask.context.MaskRole;
import com.learn.mask.testsupport.MutableClock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UnmaskTicketServiceTest {

    private MaskingProperties properties;
    private MaskContext context;
    private MutableClock clock;
    private UnmaskTicketService tickets;

    @BeforeEach
    void setUp() {
        properties = new MaskingProperties();
        properties.getDebug().setHeaderRoleEnabled(true);
        context = new MaskContext(properties);
        clock = new MutableClock(Instant.parse("2026-08-26T12:00:00Z"));
        tickets = new UnmaskTicketService(
                new AesGcmReversibleMasker(properties),
                (id, field) -> Map.of(
                        "phone", "13812345678",
                        "idCard", "110101199003078515",
                        "email", "alice@example.com"
                ).get(field),
                properties,
                context,
                Duration.ofSeconds(60),
                clock);
    }

    @AfterEach
    void tearDown() {
        MaskContext.clearHeaderRole();
    }

    @Nested
    @DisplayName("查看票：签发后限时刷新")
    class ViewTickets {

        @Test
        @DisplayName("CS 查看后 60 秒内持票刷新，明文仍从库读，token 不含明文")
        void revealThenRefresh() {
            MaskContext.setHeaderRole(MaskRole.CS);

            UnmaskTicketService.RevealResult first = tickets.revealForView("1", "phone");
            clock.plus(Duration.ofSeconds(30));
            UnmaskTicketService.RevealResult again = tickets.refreshView(first.token());

            assertThat(first.value()).isEqualTo("13812345678");
            assertThat(again.value()).isEqualTo("13812345678");
            assertThat(first.token()).doesNotContain("13812345678");
            assertThat(tickets.auditTrail())
                    .extracting(UnmaskTicketService.AuditEvent::action)
                    .containsExactly("REVEAL", "REFRESH");
        }

        @Test
        @DisplayName("票刚好到期后刷新失败")
        void refreshAfterTtlFails() {
            MaskContext.setHeaderRole(MaskRole.CS);
            String token = tickets.revealForView("1", "phone").token();

            clock.plus(Duration.ofSeconds(60));

            assertThatThrownBy(() -> tickets.refreshView(token))
                    .isInstanceOf(UnmaskTicketService.TicketExpiredException.class);
        }

        @Test
        @DisplayName("刷新时角色已收回，即使票没过期也拒绝")
        void refreshRechecksRole() {
            MaskContext.setHeaderRole(MaskRole.CS);
            String token = tickets.revealForView("1", "phone").token();

            MaskContext.setHeaderRole(MaskRole.USER);

            assertThatThrownBy(() -> tickets.refreshView(token))
                    .isInstanceOf(UnmaskTicketService.UnmaskDeniedException.class)
                    .hasMessageContaining("cannot unmask");
        }
    }

    @Nested
    @DisplayName("外呼票：签发时不露明文")
    class DialTickets {

        @Test
        @DisplayName("点拨只签发 token，核销才拿到明文")
        void dialTokenHidesPlaintext() {
            MaskContext.setHeaderRole(MaskRole.CS);

            String token = tickets.issueDialToken("1", "phone");

            assertThat(token).doesNotContain("13812345678");
            assertThat(tickets.redeemForDial(token)).isEqualTo("13812345678");
            assertThat(tickets.auditTrail())
                    .extracting(UnmaskTicketService.AuditEvent::action)
                    .containsExactly("ISSUE_DIAL", "REDEEM_DIAL");
        }
    }

    @Nested
    @DisplayName("用途、白名单与角色")
    class Authorization {

        @Test
        @DisplayName("查看票不能拿去外呼，外呼票不能拿去刷新弹层")
        void purposeIsNotInterchangeable() {
            MaskContext.setHeaderRole(MaskRole.CS);
            String viewToken = tickets.revealForView("1", "phone").token();
            String dialToken = tickets.issueDialToken("1", "phone");

            assertThatThrownBy(() -> tickets.redeemForDial(viewToken))
                    .isInstanceOf(UnmaskTicketService.TicketPurposeMismatchException.class);
            assertThatThrownBy(() -> tickets.refreshView(dialToken))
                    .isInstanceOf(UnmaskTicketService.TicketPurposeMismatchException.class);
        }

        @Test
        @DisplayName("email 不在 masking.reversible.fields 白名单里，即使 CS 也不能还")
        void emailIsNotAllowlisted() {
            MaskContext.setHeaderRole(MaskRole.CS);
            assertThatThrownBy(() -> tickets.revealForView("1", "email"))
                    .isInstanceOf(UnmaskTicketService.UnmaskDeniedException.class)
                    .hasMessageContaining("not reversible");
        }

        @Test
        @DisplayName("USER 不能签发任何票据")
        void userCannotIssueTickets() {
            MaskContext.setHeaderRole(MaskRole.USER);
            assertThatThrownBy(() -> tickets.revealForView("1", "phone"))
                    .isInstanceOf(UnmaskTicketService.UnmaskDeniedException.class);
            assertThatThrownBy(() -> tickets.issueDialToken("1", "phone"))
                    .isInstanceOf(UnmaskTicketService.UnmaskDeniedException.class);
        }

        @Test
        @DisplayName("可逆开关关闭时拒绝签发")
        void reversibleDisabledDenies() {
            MaskContext.setHeaderRole(MaskRole.CS);
            properties.getReversible().setEnabled(false);

            assertThatThrownBy(() -> tickets.revealForView("1", "phone"))
                    .isInstanceOf(UnmaskTicketService.UnmaskDeniedException.class)
                    .hasMessageContaining("disabled");
        }

        @Test
        @DisplayName("ADMIN 也可以还原身份证")
        void adminCanUnmaskIdCard() {
            MaskContext.setHeaderRole(MaskRole.ADMIN);
            assertThat(tickets.revealForView("1", "idCard").value())
                    .isEqualTo("110101199003078515");
        }
    }
}
