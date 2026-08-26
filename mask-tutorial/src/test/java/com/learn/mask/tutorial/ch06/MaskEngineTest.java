package com.learn.mask.tutorial.ch06;

import com.learn.mask.tutorial.ch04.ExpressNoMaskStrategy;
import com.learn.mask.tutorial.ch04.MaskRule;
import com.learn.mask.tutorial.ch04.MaskStrategy;
import com.learn.mask.tutorial.ch04.MaskStrategyRegistry;
import com.learn.mask.tutorial.ch04.SensitiveType;
import com.learn.mask.tutorial.ch05.MaskContext;
import com.learn.mask.tutorial.ch05.MaskRole;
import com.learn.mask.tutorial.ch05.RoleProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MaskEngineTest {

    private SimpleMaskSettings settings;
    private MaskStrategyRegistry registry;
    private CountingCache cache;
    private RecordingRecorder recorder;
    private RoleProperties roleProperties;
    private MaskContext context;
    private MaskEngine engine;

    @BeforeEach
    void setUp() {
        settings = SimpleMaskSettings.withDemoDefaults();
        registry = MaskStrategyRegistry.withBuiltins();
        cache = new CountingCache();
        recorder = new RecordingRecorder();
        roleProperties = new RoleProperties();
        context = new MaskContext(roleProperties);
        engine = new MaskEngine(settings, registry, cache, new AlreadyMaskedDetector(), recorder);
    }

    @AfterEach
    void cleanUp() {
        MaskContext.clearHeaderRole();
        SecurityContextHolder.clearContext();
    }

    private void authenticateAs(String username, String... authorities) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(username, "n/a",
                        List.of(authorities).stream().map(SimpleGrantedAuthority::new).toList()));
    }

    @Nested
    @DisplayName("正常路径")
    class HappyPath {

        @Test
        void masksAccordingToRule() {
            authenticateAs("dave", "ROLE_USER");

            assertThat(engine.apply("13812345678", SensitiveType.PHONE, context)).isEqualTo("138****5678");
            assertThat(recorder.lastAction()).isEqualTo(MaskAction.MASK);
        }

        @Test
        @DisplayName("指标里带上了类型和角色")
        void recordsTypeAndRole() {
            authenticateAs("dave", "ROLE_USER");
            engine.apply("13812345678", SensitiveType.PHONE, context);

            assertThat(recorder.last().typeCode()).isEqualTo("PHONE");
            assertThat(recorder.last().role()).isEqualTo(MaskRole.USER);
            assertThat(recorder.last().duration()).isGreaterThanOrEqualTo(Duration.ZERO);
        }

        @Test
        @DisplayName("每种内置类型都按各自规则处理")
        void allBuiltinTypes() {
            authenticateAs("dave", "ROLE_USER");

            assertThat(engine.apply("110101199003078515", SensitiveType.ID_CARD, context))
                    .isEqualTo("110101********8515");
            assertThat(engine.apply("6222021234567890123", SensitiveType.BANK_CARD, context))
                    .isEqualTo("6222***********0123");
            assertThat(engine.apply("zhangsan@example.com", SensitiveType.EMAIL, context))
                    .isEqualTo("z*******@example.com");
        }
    }

    @Nested
    @DisplayName("第 1 步：空值与总开关")
    class BlankAndGlobalSwitch {

        @Test
        void nullAndBlankBypass() {
            authenticateAs("dave", "ROLE_USER");

            assertThat(engine.apply(null, SensitiveType.PHONE, context)).isNull();
            assertThat(engine.apply("", SensitiveType.PHONE, context)).isEmpty();
            assertThat(engine.apply("   ", SensitiveType.PHONE, context)).isEqualTo("   ");
            assertThat(recorder.actions())
                    .containsExactly(MaskAction.BYPASS, MaskAction.BYPASS, MaskAction.BYPASS);
        }

        @Test
        @DisplayName("总开关关闭时全部旁路")
        void globalSwitchOff() {
            authenticateAs("dave", "ROLE_USER");
            settings.setEnabled(false);

            assertThat(engine.apply("13812345678", SensitiveType.PHONE, context)).isEqualTo("13812345678");
            assertThat(recorder.lastAction()).isEqualTo(MaskAction.BYPASS);
        }

        @Test
        @DisplayName("空值分支在查缓存之前，所以完全没碰缓存")
        void blankDoesNotTouchCache() {
            authenticateAs("dave", "ROLE_USER");
            engine.apply(null, SensitiveType.PHONE, context);

            assertThat(cache.getCount).isZero();
            assertThat(cache.putCount).isZero();
        }
    }

    @Nested
    @DisplayName("第 2 步：角色旁路")
    class RoleBypass {

        @Test
        void adminSeesPlainText() {
            authenticateAs("alice", "ROLE_ADMIN");

            assertThat(engine.apply("13812345678", SensitiveType.PHONE, context)).isEqualTo("13812345678");
            assertThat(recorder.lastAction()).isEqualTo(MaskAction.BYPASS);
        }

        @Test
        @DisplayName("CS 不旁路 —— 出口依然打星")
        void csIsStillMasked() {
            authenticateAs("carol", "ROLE_CS");

            assertThat(engine.apply("13812345678", SensitiveType.PHONE, context)).isEqualTo("138****5678");
            assertThat(recorder.lastAction()).isEqualTo(MaskAction.MASK);
        }

        @Test
        @DisplayName("context 为 null 时按 USER 处理，不旁路")
        void nullContextIsMasked() {
            assertThat(engine.apply("13812345678", SensitiveType.PHONE, null)).isEqualTo("138****5678");
            assertThat(recorder.last().role()).isEqualTo(MaskRole.USER);
        }

        @Test
        @DisplayName("旁路分支在查缓存之前，明文不会被写进缓存")
        void bypassDoesNotPopulateCache() {
            authenticateAs("alice", "ROLE_ADMIN");
            engine.apply("13812345678", SensitiveType.PHONE, context);

            assertThat(cache.putCount).isZero();
            assertThat(cache.size()).isZero();
        }

        @Test
        @DisplayName("角色旁路优先于规则查找：即使规则不存在，ADMIN 的行为也一致")
        void bypassHappensBeforeRuleLookup() {
            authenticateAs("alice", "ROLE_ADMIN");
            settings.remove("PHONE");

            assertThat(engine.apply("13812345678", SensitiveType.PHONE, context)).isEqualTo("13812345678");
        }
    }

    @Nested
    @DisplayName("第 4 步：规则缺失或禁用")
    class RuleMissingOrDisabled {

        @Test
        @DisplayName("没配规则时旁路 —— 注意这里会返回明文")
        void missingRuleBypasses() {
            authenticateAs("dave", "ROLE_USER");
            settings.remove("PHONE");

            assertThat(engine.apply("13812345678", SensitiveType.PHONE, context)).isEqualTo("13812345678");
            assertThat(recorder.lastAction()).isEqualTo(MaskAction.BYPASS);
        }

        @Test
        void disabledRuleBypasses() {
            authenticateAs("dave", "ROLE_USER");
            settings.put(SensitiveType.PHONE, MaskRule.disabled());

            assertThat(engine.apply("13812345678", SensitiveType.PHONE, context)).isEqualTo("13812345678");
            assertThat(recorder.lastAction()).isEqualTo(MaskAction.BYPASS);
        }
    }

    @Nested
    @DisplayName("第 5 步：幂等跳过")
    class IdempotentSkip {

        @Test
        @DisplayName("已打码的值原样返回，不会二次打星")
        void alreadyMaskedIsSkipped() {
            authenticateAs("dave", "ROLE_USER");

            assertThat(engine.apply("138****5678", SensitiveType.PHONE, context)).isEqualTo("138****5678");
            assertThat(recorder.lastAction()).isEqualTo(MaskAction.SKIP_ALREADY_MASKED);
        }

        @Test
        @DisplayName("两个通道叠加：第二次调用被幂等跳过")
        void twoChannelsInARow() {
            authenticateAs("dave", "ROLE_USER");

            String afterAop = engine.apply("13812345678", SensitiveType.PHONE, context);
            String afterJackson = engine.apply(afterAop, SensitiveType.PHONE, context);

            assertThat(afterAop).isEqualTo("138****5678");
            assertThat(afterJackson).isEqualTo("138****5678");
            assertThat(recorder.actions())
                    .containsExactly(MaskAction.MASK, MaskAction.SKIP_ALREADY_MASKED);
        }

        @Test
        @DisplayName("幂等跳过在查缓存之前，已打码值不会占用缓存空间")
        void skipDoesNotTouchCache() {
            authenticateAs("dave", "ROLE_USER");
            engine.apply("138****5678", SensitiveType.PHONE, context);

            assertThat(cache.getCount).isZero();
            assertThat(cache.putCount).isZero();
        }

        @Test
        @DisplayName("邮箱的幂等判定也生效（策略各自实现）")
        void emailIdempotent() {
            authenticateAs("dave", "ROLE_USER");

            assertThat(engine.apply("z*******@example.com", SensitiveType.EMAIL, context))
                    .isEqualTo("z*******@example.com");
            assertThat(recorder.lastAction()).isEqualTo(MaskAction.SKIP_ALREADY_MASKED);
        }
    }

    @Nested
    @DisplayName("第 6、8 步：缓存")
    class Caching {

        @Test
        @DisplayName("第一次未命中并回填，第二次命中")
        void secondCallHitsCache() {
            authenticateAs("dave", "ROLE_USER");

            assertThat(engine.apply("13812345678", SensitiveType.PHONE, context)).isEqualTo("138****5678");
            assertThat(cache.getCount).isEqualTo(1);
            assertThat(cache.putCount).isEqualTo(1);

            assertThat(engine.apply("13812345678", SensitiveType.PHONE, context)).isEqualTo("138****5678");
            assertThat(cache.getCount).isEqualTo(2);
            assertThat(cache.putCount).as("命中后不该重复回填").isEqualTo(1);
        }

        @Test
        @DisplayName("缓存命中也记成 MASK，调用方无需区分")
        void cacheHitIsRecordedAsMask() {
            authenticateAs("dave", "ROLE_USER");
            engine.apply("13812345678", SensitiveType.PHONE, context);
            engine.apply("13812345678", SensitiveType.PHONE, context);

            assertThat(recorder.actions()).containsExactly(MaskAction.MASK, MaskAction.MASK);
        }

        @Test
        @DisplayName("不同类型的同一明文互不干扰")
        void cacheKeyIncludesType() {
            authenticateAs("dave", "ROLE_USER");
            engine.apply("13812345678", SensitiveType.PHONE, context);
            engine.apply("13812345678", SensitiveType.CUSTOM, context);

            assertThat(cache.size()).isEqualTo(2);
        }

        @Test
        @DisplayName("传 null 缓存时引擎用 NO_OP，不会 NPE")
        void nullCacheFallsBackToNoOp() {
            MaskEngine noCacheEngine = new MaskEngine(
                    settings, registry, null, new AlreadyMaskedDetector(), null);

            assertThat(noCacheEngine.apply("13812345678", SensitiveType.PHONE, null))
                    .isEqualTo("138****5678");
        }
    }

    @Nested
    @DisplayName("自定义编码")
    class CustomCode {

        @Test
        @DisplayName("code 优先于 type")
        void codeWinsOverType() {
            authenticateAs("dave", "ROLE_USER");
            registry.register(new ExpressNoMaskStrategy());
            settings.put(ExpressNoMaskStrategy.CODE, MaskRule.of(2, 4));

            String result = engine.apply("SF1234567890123", SensitiveType.CUSTOM,
                    ExpressNoMaskStrategy.CODE, context);

            assertThat(result).isEqualTo("SF12*******0123");
            assertThat(recorder.last().typeCode()).isEqualTo("EXPRESS");
        }

        @Test
        @DisplayName("code 和 type 都为空时归一化成 CUSTOM")
        void bothNullFallsBackToCustom() {
            authenticateAs("dave", "ROLE_USER");

            assertThat(engine.apply("E12345678", null, null, context)).isEqualTo("E*******8");
            assertThat(recorder.last().typeCode()).isEqualTo("CUSTOM");
        }

        @Test
        @DisplayName("编码大小写不敏感")
        void codeIsNormalized() {
            authenticateAs("dave", "ROLE_USER");
            registry.register(new ExpressNoMaskStrategy());
            settings.put(ExpressNoMaskStrategy.CODE, MaskRule.of(2, 4));

            assertThat(engine.apply("SF1234567890123", null, "express", context))
                    .isEqualTo("SF12*******0123");
        }
    }

    @Nested
    @DisplayName("异常处理")
    class Failure {

        private MaskStrategy explodingStrategy() {
            return new MaskStrategy() {
                @Override
                public SensitiveType type() {
                    return SensitiveType.PHONE;
                }

                @Override
                public String mask(String raw, MaskRule rule) {
                    throw new IllegalStateException("strategy blew up");
                }

                @Override
                public boolean alreadyMasked(String raw, MaskRule rule) {
                    return false;
                }
            };
        }

        @Test
        @DisplayName("策略抛异常时记 FAIL 并重新抛出，绝不返回明文")
        void rethrowsInsteadOfLeaking() {
            authenticateAs("dave", "ROLE_USER");
            registry.register(explodingStrategy());

            assertThatThrownBy(() -> engine.apply("13812345678", SensitiveType.PHONE, context))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("strategy blew up");

            assertThat(recorder.lastAction()).isEqualTo(MaskAction.FAIL);
        }

        @Test
        @DisplayName("异常时不会往缓存里写脏数据")
        void failureDoesNotPopulateCache() {
            authenticateAs("dave", "ROLE_USER");
            registry.register(explodingStrategy());

            assertThatThrownBy(() -> engine.apply("13812345678", SensitiveType.PHONE, context))
                    .isInstanceOf(IllegalStateException.class);

            assertThat(cache.size()).isZero();
        }
    }

    @Nested
    @DisplayName("判定顺序")
    class DecisionOrder {

        /**
         * 这个测试固化了「角色旁路必须在幂等判定之前」这个顺序。
         * 如果有人把两步调换，ADMIN 拿到一个已打码的值时会被记成
         * SKIP_ALREADY_MASKED 而不是 BYPASS，指标就失真了。
         */
        @Test
        @DisplayName("ADMIN 遇到已打码值时记 BYPASS，不是 SKIP")
        void bypassBeforeIdempotentCheck() {
            authenticateAs("alice", "ROLE_ADMIN");

            assertThat(engine.apply("138****5678", SensitiveType.PHONE, context))
                    .isEqualTo("138****5678");
            assertThat(recorder.lastAction()).isEqualTo(MaskAction.BYPASS);
        }

        @Test
        @DisplayName("空值优先于角色：ADMIN 传 null 也记 BYPASS")
        void blankCheckIsFirst() {
            authenticateAs("alice", "ROLE_ADMIN");

            assertThat(engine.apply(null, SensitiveType.PHONE, context)).isNull();
            assertThat(recorder.lastAction()).isEqualTo(MaskAction.BYPASS);
        }

        @Test
        @DisplayName("调试头覆盖角色后旁路也跟着变 —— 引擎不关心角色从哪来")
        void debugHeaderAffectsBypass() {
            roleProperties.getDebug().setHeaderRoleEnabled(true);
            authenticateAs("dave", "ROLE_USER");
            MaskContext.setHeaderRole(MaskRole.ADMIN);

            assertThat(engine.apply("13812345678", SensitiveType.PHONE, context))
                    .isEqualTo("13812345678");
            assertThat(recorder.lastAction()).isEqualTo(MaskAction.BYPASS);
        }
    }
}
