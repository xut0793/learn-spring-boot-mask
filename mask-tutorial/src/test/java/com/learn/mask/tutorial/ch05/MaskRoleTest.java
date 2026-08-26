package com.learn.mask.tutorial.ch05;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class MaskRoleTest {

    @ParameterizedTest
    @CsvSource({
            "ADMIN, ADMIN",
            "admin, ADMIN",
            "Admin, ADMIN",
            "ROLE_ADMIN, ADMIN",
            "role_admin, ADMIN",
            "USER, USER",
            "ROLE_USER, USER",
            "CS, CS",
            "cs, CS",
            "CUSTOMER_SERVICE, CS",
            "ROLE_CUSTOMER_SERVICE, CS"
    })
    @DisplayName("大小写、空格、ROLE_ 前缀、同义写法都能解析")
    void parsesAllCommonForms(String raw, MaskRole expected) {
        assertThat(MaskRole.parse(raw)).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"OPERATOR", "ROLE_GUEST", "admin ish", "ROLE_", "管理员"})
    @DisplayName("无法识别的角色返回 null，而不是悄悄当成 USER")
    void unknownRoleReturnsNull(String raw) {
        assertThat(MaskRole.parse(raw)).isNull();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void blankReturnsNull(String raw) {
        assertThat(MaskRole.parse(raw)).isNull();
    }

    @Test
    @DisplayName("前后空格会被 trim 掉")
    void trimsWhitespace() {
        assertThat(MaskRole.parse("  Admin  ")).isEqualTo(MaskRole.ADMIN);
        assertThat(MaskRole.parse("\tROLE_CS\n")).isEqualTo(MaskRole.CS);
    }

    @Test
    @DisplayName("ROLE_ 前缀只剥一层，ROLE_ROLE_ADMIN 不该被识别")
    void stripsOnlyOnePrefix() {
        assertThat(MaskRole.parse("ROLE_ROLE_ADMIN")).isNull();
    }
}
