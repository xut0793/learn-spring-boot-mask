package com.learn.mask.tutorial.ch05;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MaskContextTest {

    private final RoleProperties properties = new RoleProperties();
    private final MaskContext context = new MaskContext(properties);

    @AfterEach
    void cleanUp() {
        // 每个用例都要清，否则 ThreadLocal 会串到下一个用例 ——
        // 这正是 HeaderRoleFilter 需要 finally 的同一个原因
        MaskContext.clearHeaderRole();
        SecurityContextHolder.clearContext();
    }

    private void authenticateAs(String username, String... authorities) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(username, "n/a",
                        List.of(authorities).stream().map(SimpleGrantedAuthority::new).toList()));
    }

    @Nested
    @DisplayName("从 Spring Security 解析角色")
    class FromSecurity {

        @Test
        void readsRoleFromAuthority() {
            authenticateAs("alice", "ROLE_ADMIN");
            assertThat(context.current()).isEqualTo(MaskRole.ADMIN);
        }

        @Test
        @DisplayName("未认证时返回 USER —— 最安全的默认值")
        void unauthenticatedIsUser() {
            assertThat(context.current()).isEqualTo(MaskRole.USER);
        }

        @Test
        @DisplayName("匿名认证也按 USER 处理")
        void anonymousIsUser() {
            SecurityContextHolder.getContext().setAuthentication(
                    new AnonymousAuthenticationToken("key", "anonymousUser",
                            List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))));
            assertThat(context.current()).isEqualTo(MaskRole.USER);
        }

        @Test
        @DisplayName("多个权限时跳过 USER，取更特殊的角色")
        void picksMoreSpecificRoleAmongMany() {
            // 如果实现里不跳过 USER，这个顺序会解析成 USER，ADMIN 就失效了
            authenticateAs("bob", "ROLE_USER", "ROLE_ADMIN");
            assertThat(context.current()).isEqualTo(MaskRole.ADMIN);
        }

        @Test
        @DisplayName("权限里没有可识别角色时，回落到用户名解析")
        void fallsBackToUserName() {
            authenticateAs("CS", "ROLE_SOMETHING_ELSE");
            assertThat(context.current()).isEqualTo(MaskRole.CS);
        }

        @Test
        @DisplayName("权限和用户名都认不出来时返回 USER")
        void unknownEverythingIsUser() {
            authenticateAs("alice", "ROLE_OPERATOR");
            assertThat(context.current()).isEqualTo(MaskRole.USER);
        }
    }

    @Nested
    @DisplayName("调试头覆盖")
    class HeaderOverride {

        @Test
        @DisplayName("开关关闭时，ThreadLocal 里有值也不生效")
        void ignoredWhenSwitchOff() {
            properties.getDebug().setHeaderRoleEnabled(false);
            authenticateAs("alice", "ROLE_USER");
            MaskContext.setHeaderRole(MaskRole.ADMIN);

            assertThat(context.current()).isEqualTo(MaskRole.USER);
        }

        @Test
        @DisplayName("开关打开时，调试头优先级高于 Security")
        void overridesSecurityWhenSwitchOn() {
            properties.getDebug().setHeaderRoleEnabled(true);
            authenticateAs("alice", "ROLE_USER");
            MaskContext.setHeaderRole(MaskRole.ADMIN);

            assertThat(context.current()).isEqualTo(MaskRole.ADMIN);
        }

        @Test
        @DisplayName("开关打开但头里没有有效角色时，回落到 Security")
        void fallsBackWhenHeaderAbsent() {
            properties.getDebug().setHeaderRoleEnabled(true);
            authenticateAs("alice", "ROLE_ADMIN");
            MaskContext.setHeaderRole(MaskRole.parse("NOT_A_ROLE"));   // parse 返回 null

            assertThat(context.current()).isEqualTo(MaskRole.ADMIN);
        }
    }

    @Nested
    @DisplayName("两个判定：旁路与还原")
    class Decisions {

        @Test
        @DisplayName("默认配置下只有 ADMIN 旁路")
        void onlyAdminBypassesByDefault() {
            authenticateAs("alice", "ROLE_ADMIN");
            assertThat(context.shouldBypass()).isTrue();

            SecurityContextHolder.clearContext();
            authenticateAs("bob", "ROLE_CS");
            assertThat(context.shouldBypass()).isFalse();
        }

        @Test
        @DisplayName("CS 不旁路但可以还原 —— 这是两个不同维度的权限")
        void csCannotBypassButCanUnmask() {
            authenticateAs("carol", "ROLE_CS");
            assertThat(context.shouldBypass()).isFalse();
            assertThat(context.canUnmask()).isTrue();
        }

        @Test
        void userCanNeitherBypassNorUnmask() {
            authenticateAs("dave", "ROLE_USER");
            assertThat(context.shouldBypass()).isFalse();
            assertThat(context.canUnmask()).isFalse();
        }

        @Test
        @DisplayName("配置里的角色写法也走 parse，所以大小写和前缀都不敏感")
        void configuredRoleNamesAreParsedToo() {
            properties.setBypassRoles(List.of("role_admin"));
            authenticateAs("alice", "ROLE_ADMIN");

            assertThat(context.shouldBypass()).isTrue();
        }

        @Test
        @DisplayName("配置成空列表时谁都不能旁路")
        void emptyBypassListBlocksEveryone() {
            properties.setBypassRoles(List.of());
            authenticateAs("alice", "ROLE_ADMIN");

            assertThat(context.shouldBypass()).isFalse();
        }
    }
}
