package com.learn.mask.tutorial.ch14.immediatewithoutcrypto;

import com.learn.mask.tutorial.ch05.MaskContext;
import com.learn.mask.tutorial.ch05.MaskRole;
import com.learn.mask.tutorial.ch05.RoleProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UnmaskServiceTest {

    private RoleProperties roles;
    private MaskContext context;
    private UnmaskService service;

    @BeforeEach
    void setUp() {
        roles = new RoleProperties();
        roles.getDebug().setHeaderRoleEnabled(true);
        context = new MaskContext(roles);
        service = UnmaskService.create();
    }

    @AfterEach
    void tearDown() {
        MaskContext.clearHeaderRole();
    }

    @Test
    @DisplayName("不加 AES：POST /api/unmask 查库还原 phone")
    void csGetsPlainFromStore() {
        MaskContext.setHeaderRole(MaskRole.CS);

        UnmaskService.UnmaskResult result = service.unmask(context, new UnmaskRequest(1L, "phone"));

        assertThat(result.userId()).isEqualTo(1L);
        assertThat(result.field()).isEqualTo("phone");
        assertThat(result.value()).isEqualTo("13812345678");
    }

    @Test
    void userIsDenied() {
        MaskContext.setHeaderRole(MaskRole.USER);
        assertThatThrownBy(() -> service.unmask(context, new UnmaskRequest(1L, "phone")))
                .isInstanceOf(UnmaskDeniedException.class)
                .hasMessageContaining("cannot unmask");
    }

    @Test
    @DisplayName("email 不在白名单，CS 也不能还")
    void fieldMustBeAllowlisted() {
        MaskContext.setHeaderRole(MaskRole.CS);
        assertThatThrownBy(() -> service.unmask(context, new UnmaskRequest(1L, "email")))
                .isInstanceOf(UnmaskDeniedException.class)
                .hasMessageContaining("not reversible");
    }

    @Test
    void adminCanUnmask() {
        MaskContext.setHeaderRole(MaskRole.ADMIN);
        assertThat(service.unmask(context, new UnmaskRequest(1L, "idCard")).value())
                .isEqualTo("110101199001011234");
    }
}
