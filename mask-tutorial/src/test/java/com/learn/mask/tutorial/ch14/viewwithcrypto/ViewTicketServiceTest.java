package com.learn.mask.tutorial.ch14.viewwithcrypto;

import com.learn.mask.tutorial.ch05.MaskContext;
import com.learn.mask.tutorial.ch05.MaskRole;
import com.learn.mask.tutorial.ch05.RoleProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ViewTicketServiceTest {

    private RoleProperties roles;
    private MaskContext context;
    private MutableClock clock;
    private ViewTicketService viewTickets;

    @BeforeEach
    void setUp() {
        roles = new RoleProperties();
        roles.getDebug().setHeaderRoleEnabled(true);
        context = new MaskContext(roles);
        clock = new MutableClock(Instant.parse("2026-08-26T12:00:00Z"));
        viewTickets = ViewTicketService.demo(clock);
    }

    @AfterEach
    void tearDown() {
        MaskContext.clearHeaderRole();
    }

    @Test
    void encryptAndDecryptRoundTrip() {
        AesGcmReversibleMasker masker = new AesGcmReversibleMasker(ReversibleOptions.demo());
        String cipher = masker.encrypt("ticket-payload");
        assertThat(masker.decrypt(cipher)).isEqualTo("ticket-payload");
    }

    @Test
    @DisplayName("同一票面两次加密结果不同 —— IV 随机，不能当缓存键")
    void samePlaintextYieldsDifferentTokens() {
        AesGcmReversibleMasker masker = new AesGcmReversibleMasker(ReversibleOptions.demo());
        String first = masker.encrypt("same");
        String second = masker.encrypt("same");
        assertThat(first).isNotEqualTo(second);
    }

    @Test
    @DisplayName("篡改密文 fail-loud")
    void tamperedTokenFailsLoud() {
        AesGcmReversibleMasker masker = new AesGcmReversibleMasker(ReversibleOptions.demo());
        String token = masker.encrypt("payload");
        byte[] bytes = Base64.getDecoder().decode(token);
        bytes[bytes.length - 1] ^= 0x01;
        String tampered = Base64.getEncoder().encodeToString(bytes);
        assertThatThrownBy(() -> masker.decrypt(tampered))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("decrypt");
    }

    @Test
    @DisplayName("短密钥补 0 到 32 字节")
    void shortKeysArePaddedWithZeros() {
        AesGcmReversibleMasker a = new AesGcmReversibleMasker(new ReversibleOptions(true, "short"));
        AesGcmReversibleMasker b = new AesGcmReversibleMasker(new ReversibleOptions(true, "short"));
        assertThat(a.rawKeyBytes()).isEqualTo(b.rawKeyBytes());
        assertThat(a.rawKeyBytes()).hasSize(32);
        assertThat(new String(a.rawKeyBytes(), StandardCharsets.UTF_8)).startsWith("short");
    }

    @Test
    @DisplayName("查看后 60 秒内持票刷新；明文仍从用户 Map 读")
    void viewThenRefreshWithinTtl() {
        MaskContext.setHeaderRole(MaskRole.CS);

        ViewTicketService.RevealResult first =
                viewTickets.revealForView(context, new UnmaskRequest(1L, "phone"));
        clock.plusSeconds(30);
        ViewTicketService.RevealResult again = viewTickets.refreshView(context, first.token());

        assertThat(first.value()).isEqualTo("13812345678");
        assertThat(again.value()).isEqualTo("13812345678");
        assertThat(first.token()).isNotEqualTo("13812345678");
        assertThat(viewTickets.auditTrail())
                .extracting(ViewTicketService.AuditEvent::action)
                .containsExactly("REVEAL", "REFRESH");
    }

    @Test
    @DisplayName("票过期后刷新失败，须重新 reveal")
    void refreshAfterTtlFails() {
        MaskContext.setHeaderRole(MaskRole.CS);
        String token = viewTickets.revealForView(context, new UnmaskRequest(1L, "phone")).token();
        clock.plusSeconds(60);

        assertThatThrownBy(() -> viewTickets.refreshView(context, token))
                .isInstanceOf(TicketExpiredException.class);
    }

    @Test
    @DisplayName("刷新时角色已收回则拒绝")
    void refreshRechecksRole() {
        MaskContext.setHeaderRole(MaskRole.CS);
        String token = viewTickets.revealForView(context, new UnmaskRequest(1L, "phone")).token();
        MaskContext.setHeaderRole(MaskRole.USER);

        assertThatThrownBy(() -> viewTickets.refreshView(context, token))
                .isInstanceOf(UnmaskDeniedException.class)
                .hasMessageContaining("cannot unmask");
    }

    @Test
    void userCannotReveal() {
        MaskContext.setHeaderRole(MaskRole.USER);
        assertThatThrownBy(() -> viewTickets.revealForView(context, new UnmaskRequest(1L, "phone")))
                .isInstanceOf(UnmaskDeniedException.class);
    }

    @Test
    @DisplayName("未签发的 token 在 Redis Map 中不存在")
    void unknownTokenRejected() {
        MaskContext.setHeaderRole(MaskRole.CS);
        assertThatThrownBy(() -> viewTickets.refreshView(context, "not-issued"))
                .isInstanceOf(UnknownTicketException.class);
    }
}
