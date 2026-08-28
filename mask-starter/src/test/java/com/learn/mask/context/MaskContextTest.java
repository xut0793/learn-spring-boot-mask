package com.learn.mask.context;

import com.learn.mask.config.MaskingProperties;
import com.learn.mask.testsupport.MaskingFixtures;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MaskContextTest {

    private final MaskingProperties properties = new MaskingProperties();
    private final MaskContext context = new MaskContext(properties);

    @AfterEach
    void cleanUp() {
        // ThreadLocal 会串到下一个用例，和过滤器必须 finally 清理是同一件事
        MaskContext.clearHeaderRole();
        SecurityContextHolder.clearContext();
    }

    @Nested
    @DisplayName("从 Spring Security 解析角色")
    class FromSecurity {

        @Test
        @DisplayName("从 ROLE_ADMIN 权限读出 ADMIN")
        void readsRoleFromAuthority() {
            MaskingFixtures.authenticateAs("alice", "ROLE_ADMIN");
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
            MaskingFixtures.authenticateAs("bob", "ROLE_USER", "ROLE_ADMIN");
            assertThat(context.current()).isEqualTo(MaskRole.ADMIN);
        }

        @Test
        @DisplayName("权限里没有可识别角色时，回落到用户名解析")
        void fallsBackToUserName() {
            MaskingFixtures.authenticateAs("CS", "ROLE_SOMETHING_ELSE");
            assertThat(context.current()).isEqualTo(MaskRole.CS);
        }

        @Test
        @DisplayName("权限和用户名都认不出来时返回 USER")
        void unknownEverythingIsUser() {
            MaskingFixtures.authenticateAs("alice", "ROLE_OPERATOR");
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
            MaskingFixtures.authenticateAs("alice", "ROLE_USER");
            MaskContext.setHeaderRole(MaskRole.ADMIN);

            assertThat(context.current()).isEqualTo(MaskRole.USER);
        }

        @Test
        @DisplayName("开关打开时，调试头优先级高于 Security")
        void overridesSecurityWhenSwitchOn() {
            properties.getDebug().setHeaderRoleEnabled(true);
            MaskingFixtures.authenticateAs("alice", "ROLE_USER");
            MaskContext.setHeaderRole(MaskRole.ADMIN);

            assertThat(context.current()).isEqualTo(MaskRole.ADMIN);
        }

        @Test
        @DisplayName("开关打开但头里没有有效角色时，回落到 Security")
        void fallsBackWhenHeaderAbsent() {
            properties.getDebug().setHeaderRoleEnabled(true);
            MaskingFixtures.authenticateAs("alice", "ROLE_ADMIN");
            MaskContext.setHeaderRole(MaskRole.parse("NOT_A_ROLE"));

            assertThat(context.current()).isEqualTo(MaskRole.ADMIN);
        }
    }

    @Nested
    @DisplayName("两个判定：旁路与还原")
    class Decisions {

        @Test
        @DisplayName("默认配置下只有 ADMIN 旁路")
        void onlyAdminBypassesByDefault() {
            MaskingFixtures.authenticateAs("alice", "ROLE_ADMIN");
            assertThat(context.shouldBypass()).isTrue();

            SecurityContextHolder.clearContext();
            MaskingFixtures.authenticateAs("bob", "ROLE_CS");
            assertThat(context.shouldBypass()).isFalse();
        }

        @Test
        @DisplayName("CS 不旁路但可以还原 —— 这是两个不同维度的权限")
        void csCannotBypassButCanUnmask() {
            MaskingFixtures.authenticateAs("carol", "ROLE_CS");
            assertThat(context.shouldBypass()).isFalse();
            assertThat(context.canUnmask()).isTrue();
        }

        @Test
        @DisplayName("USER 既不能旁路也不能还原")
        void userCanNeitherBypassNorUnmask() {
            MaskingFixtures.authenticateAs("dave", "ROLE_USER");
            assertThat(context.shouldBypass()).isFalse();
            assertThat(context.canUnmask()).isFalse();
        }

        @Test
        @DisplayName("配置里的角色写法也走 parse，所以大小写和前缀都不敏感")
        void configuredRoleNamesAreParsedToo() {
            properties.setBypassRoles(List.of("role_admin"));
            MaskingFixtures.authenticateAs("alice", "ROLE_ADMIN");

            assertThat(context.shouldBypass()).isTrue();
        }

        @Test
        @DisplayName("配置成空列表时谁都不能旁路")
        void emptyBypassListBlocksEveryone() {
            properties.setBypassRoles(List.of());
            MaskingFixtures.authenticateAs("alice", "ROLE_ADMIN");

            assertThat(context.shouldBypass()).isFalse();
        }
    }
}
