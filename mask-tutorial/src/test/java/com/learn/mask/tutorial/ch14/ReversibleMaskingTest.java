package com.learn.mask.tutorial.ch14;

import com.learn.mask.tutorial.ch05.MaskContext;
import com.learn.mask.tutorial.ch05.MaskRole;
import com.learn.mask.tutorial.ch05.RoleProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReversibleMaskingTest {

    @Nested
    class AesGcmReversibleMaskerTest {

        private AesGcmReversibleMasker masker;

        @BeforeEach
        void setUp() {
            masker = new AesGcmReversibleMasker(ReversibleOptions.demo());
        }

        @Test
        void encryptAndDecryptRoundTrip() {
            String cipher = masker.encrypt("13812345678");
            assertThat(cipher).isNotEqualTo("13812345678");
            assertThat(masker.decrypt(cipher)).isEqualTo("13812345678");
        }

        @Test
        @DisplayName("同一明文两次加密结果不同 —— IV 是随机的，所以不能缓存")
        void samePlaintextYieldsDifferentTokens() {
            String first = masker.encrypt("13812345678");
            String second = masker.encrypt("13812345678");
            assertThat(first).isNotEqualTo(second);
            assertThat(masker.decrypt(first)).isEqualTo("13812345678");
            assertThat(masker.decrypt(second)).isEqualTo("13812345678");
        }

        @Test
        void nullsPassThrough() {
            assertThat(masker.encrypt(null)).isNull();
            assertThat(masker.decrypt(null)).isNull();
        }

        @Test
        @DisplayName("篡改密文会失败，而不是解出乱码")
        void tamperedTokenFailsLoud() {
            String token = masker.encrypt("13812345678");
            byte[] bytes = Base64.getDecoder().decode(token);
            bytes[bytes.length - 1] ^= 0x01;
            String tampered = Base64.getEncoder().encodeToString(bytes);

            assertThatThrownBy(() -> masker.decrypt(tampered))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("decrypt");
        }

        @Test
        @DisplayName("短密钥被补 0 到 32 字节 —— 两个短密钥前缀相同就会撞钥")
        void shortKeysArePaddedWithZeros() {
            AesGcmReversibleMasker a = new AesGcmReversibleMasker(new ReversibleOptions(true, "short"));
            AesGcmReversibleMasker b = new AesGcmReversibleMasker(new ReversibleOptions(true, "short"));
            assertThat(a.rawKeyBytes()).isEqualTo(b.rawKeyBytes());
            assertThat(a.rawKeyBytes()).hasSize(32);
            assertThat(new String(a.rawKeyBytes(), StandardCharsets.UTF_8)).startsWith("short");
            // 后面全是 0，有效熵只有 5 个字符
            assertThat(a.rawKeyBytes()[5]).isZero();
        }

        @Test
        void differentKeysCannotDecrypt() {
            String token = masker.encrypt("13812345678");
            AesGcmReversibleMasker other = new AesGcmReversibleMasker(
                    new ReversibleOptions(true, "another-key-not-for-prod-32b!"));
            assertThatThrownBy(() -> other.decrypt(token)).isInstanceOf(IllegalStateException.class);
        }
    }

    @Nested
    class UnmaskServiceTest {

        private RoleProperties roles;
        private MaskContext context;
        private UnmaskService service;

        @BeforeEach
        void setUp() {
            roles = new RoleProperties();
            roles.getDebug().setHeaderRoleEnabled(true);
            context = new MaskContext(roles);
            service = UnmaskService.demo(new AesGcmReversibleMasker(ReversibleOptions.demo()));
        }

        @AfterEach
        void tearDown() {
            MaskContext.clearHeaderRole();
        }

        @Test
        @DisplayName("线程 A：不加 AES 也能还原，token 为 null")
        void withoutCryptoReturnsPlainAndNoToken() {
            service = UnmaskService.withoutCrypto();
            MaskContext.setHeaderRole(MaskRole.CS);

            UnmaskService.UnmaskResult result = service.unmask(context, "phone", "13812345678");

            assertThat(result.value()).isEqualTo("13812345678");
            assertThat(result.token()).isNull();
        }

        @Test
        @DisplayName("线程 B：CS 可以还原，响应是库里的明文 + 每次不同的 token")
        void csCanUnmask() {
            MaskContext.setHeaderRole(MaskRole.CS);

            UnmaskService.UnmaskResult first = service.unmask(context, "phone", "13812345678");
            UnmaskService.UnmaskResult second = service.unmask(context, "phone", "13812345678");

            assertThat(first.value()).isEqualTo("13812345678");
            assertThat(first.token()).isNotEqualTo("13812345678");
            assertThat(first.token()).isNotEqualTo(second.token());
        }

        @Test
        void userIsDenied() {
            MaskContext.setHeaderRole(MaskRole.USER);
            assertThatThrownBy(() -> service.unmask(context, "phone", "13812345678"))
                    .isInstanceOf(UnmaskService.UnmaskDeniedException.class)
                    .hasMessageContaining("cannot unmask");
        }

        @Test
        @DisplayName("email 没标 reversible，即使 CS 也不能还")
        void fieldMustBeAllowlisted() {
            MaskContext.setHeaderRole(MaskRole.CS);
            assertThatThrownBy(() -> service.unmask(context, "email", "alice@example.com"))
                    .isInstanceOf(UnmaskService.UnmaskDeniedException.class)
                    .hasMessageContaining("not reversible");
        }

        @Test
        void adminCanUnmask() {
            MaskContext.setHeaderRole(MaskRole.ADMIN);
            assertThat(service.unmask(context, "idCard", "110101199001011234").value())
                    .isEqualTo("110101199001011234");
        }
    }

    @Nested
    class UnmaskTicketServiceTest {

        private RoleProperties roles;
        private MaskContext context;
        private MutableClock clock;
        private UnmaskTicketService tickets;

        @BeforeEach
        void setUp() {
            roles = new RoleProperties();
            roles.getDebug().setHeaderRoleEnabled(true);
            context = new MaskContext(roles);
            clock = new MutableClock(Instant.parse("2026-08-26T12:00:00Z"));
            SensitiveFieldStore store = SensitiveFieldStore.memory(Map.of(
                    "1", Map.of("phone", "13812345678", "idCard", "110101199003078515")));
            tickets = new UnmaskTicketService(
                    new AesGcmReversibleMasker(ReversibleOptions.demo()),
                    store,
                    Set.of("phone", "idCard"),
                    Duration.ofSeconds(60),
                    clock);
        }

        @AfterEach
        void tearDown() {
            MaskContext.clearHeaderRole();
        }

        @Test
        @DisplayName("场景一：查看后 60 秒内持票刷新，不必再点字段；明文仍从库读")
        void viewThenRefreshWithinTtl() {
            MaskContext.setHeaderRole(MaskRole.CS);

            UnmaskTicketService.RevealResult first = tickets.revealForView(context, "1", "phone");
            clock.plusSeconds(30);
            UnmaskTicketService.RevealResult again = tickets.refreshView(context, first.token());

            assertThat(first.value()).isEqualTo("13812345678");
            assertThat(again.value()).isEqualTo("13812345678");
            assertThat(first.token()).isNotEqualTo("13812345678");
            assertThat(tickets.auditTrail())
                    .extracting(UnmaskTicketService.AuditEvent::action)
                    .containsExactly("REVEAL", "REFRESH");
        }

        @Test
        @DisplayName("场景一：票过期后必须重新 reveal，刷新失败")
        void refreshAfterTtlFails() {
            MaskContext.setHeaderRole(MaskRole.CS);
            String token = tickets.revealForView(context, "1", "phone").token();

            clock.plusSeconds(60);

            assertThatThrownBy(() -> tickets.refreshView(context, token))
                    .isInstanceOf(UnmaskTicketService.TicketExpiredException.class);
        }

        @Test
        @DisplayName("场景一：刷新时角色已收回，即使票没过期也拒绝")
        void refreshRechecksRole() {
            MaskContext.setHeaderRole(MaskRole.CS);
            String token = tickets.revealForView(context, "1", "phone").token();

            MaskContext.setHeaderRole(MaskRole.USER);

            assertThatThrownBy(() -> tickets.refreshView(context, token))
                    .isInstanceOf(UnmaskService.UnmaskDeniedException.class)
                    .hasMessageContaining("cannot unmask");
        }

        @Test
        @DisplayName("场景二：点拨只签发 token，浏览器看不到号码；外呼核销才拿到明文")
        void dialTokenNeverExposesPlainToIssuer() {
            MaskContext.setHeaderRole(MaskRole.CS);

            String token = tickets.issueDialToken(context, "1", "phone");

            assertThat(token).isNotEqualTo("13812345678");
            assertThat(token).doesNotContain("13812345678");
            assertThat(tickets.redeemForDial(token)).isEqualTo("13812345678");
            assertThat(tickets.auditTrail())
                    .extracting(UnmaskTicketService.AuditEvent::action)
                    .containsExactly("ISSUE_DIAL", "REDEEM_DIAL");
        }

        @Test
        @DisplayName("查看票不能拿去外呼，外呼票不能拿去刷新弹层")
        void purposeIsNotInterchangeable() {
            MaskContext.setHeaderRole(MaskRole.CS);
            String viewToken = tickets.revealForView(context, "1", "phone").token();
            String dialToken = tickets.issueDialToken(context, "1", "phone");

            assertThatThrownBy(() -> tickets.redeemForDial(viewToken))
                    .isInstanceOf(UnmaskTicketService.TicketPurposeMismatchException.class);
            assertThatThrownBy(() -> tickets.refreshView(context, dialToken))
                    .isInstanceOf(UnmaskTicketService.TicketPurposeMismatchException.class);
        }

        @Test
        @DisplayName("USER 不能签发任何票据")
        void userCannotIssueTickets() {
            MaskContext.setHeaderRole(MaskRole.USER);
            assertThatThrownBy(() -> tickets.revealForView(context, "1", "phone"))
                    .isInstanceOf(UnmaskService.UnmaskDeniedException.class);
            assertThatThrownBy(() -> tickets.issueDialToken(context, "1", "phone"))
                    .isInstanceOf(UnmaskService.UnmaskDeniedException.class);
        }
    }
}
