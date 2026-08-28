package com.learn.mask.engine;

import com.learn.mask.annotation.SensitiveType;
import com.learn.mask.config.MaskRule;
import com.learn.mask.config.MaskingProperties;
import com.learn.mask.config.RuleConfig;
import com.learn.mask.context.MaskContext;
import com.learn.mask.context.MaskRole;
import com.learn.mask.strategy.AbstractKeepMaskStrategy;
import com.learn.mask.strategy.MaskStrategy;
import com.learn.mask.strategy.MaskStrategyRegistry;
import com.learn.mask.strategy.PhoneMaskStrategy;
import com.learn.mask.testsupport.CountingCache;
import com.learn.mask.testsupport.MaskingFixtures;
import com.learn.mask.testsupport.RecordingRecorder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MaskEngineTest {

    private MaskingProperties properties;
    private CountingCache cache;
    private RecordingRecorder recorder;
    private MaskContext context;
    private MaskEngine engine;

    @BeforeEach
    void setUp() {
        properties = new MaskingProperties();
        cache = new CountingCache();
        recorder = new RecordingRecorder();
        context = new MaskContext(properties);
        engine = MaskingFixtures.engine(properties, cache, recorder);
    }

    @AfterEach
    void tearDown() {
        MaskContext.clearHeaderRole();
        SecurityContextHolder.clearContext();
    }

    @Nested
    @DisplayName("正常路径")
    class HappyPath {

        @Test
        @DisplayName("USER 按规则打码并记 MASK")
        void masksAccordingToRule() {
            MaskingFixtures.authenticateAs("dave", "ROLE_USER");

            assertThat(engine.apply("13812345678", SensitiveType.PHONE, context)).isEqualTo("138****5678");
            assertThat(recorder.lastAction()).isEqualTo(MaskAction.MASK);
        }

        @Test
        @DisplayName("指标带上类型编码和角色")
        void recordsTypeAndRole() {
            MaskingFixtures.authenticateAs("dave", "ROLE_USER");
            engine.apply("13812345678", SensitiveType.PHONE, context);

            assertThat(recorder.last().typeCode()).isEqualTo("PHONE");
            assertThat(recorder.last().role()).isEqualTo(MaskRole.USER);
            assertThat(recorder.last().duration()).isGreaterThanOrEqualTo(Duration.ZERO);
        }

        @Test
        @DisplayName("每种内置类型都按各自默认规则处理")
        void allBuiltinTypes() {
            MaskingFixtures.authenticateAs("dave", "ROLE_USER");

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
        @DisplayName("null / 空串 / 空白记 BYPASS，原样返回")
        void nullAndBlankBypass() {
            MaskingFixtures.authenticateAs("dave", "ROLE_USER");

            assertThat(engine.apply(null, SensitiveType.PHONE, context)).isNull();
            assertThat(engine.apply("", SensitiveType.PHONE, context)).isEmpty();
            assertThat(engine.apply("   ", SensitiveType.PHONE, context)).isEqualTo("   ");
            assertThat(recorder.actions())
                    .containsExactly(MaskAction.BYPASS, MaskAction.BYPASS, MaskAction.BYPASS);
        }

        @Test
        @DisplayName("总开关关闭记 DISABLED，不和 ADMIN 旁路混在一起")
        void globalSwitchOffRecordsDisabled() {
            MaskingFixtures.authenticateAs("dave", "ROLE_USER");
            properties.setEnabled(false);

            assertThat(engine.apply("13812345678", SensitiveType.PHONE, context)).isEqualTo("13812345678");
            assertThat(recorder.lastAction()).isEqualTo(MaskAction.DISABLED);
        }

        @Test
        @DisplayName("空值分支完全不碰缓存")
        void blankDoesNotTouchCache() {
            engine.apply(null, SensitiveType.PHONE, context);
            assertThat(cache.getCount.get()).isZero();
            assertThat(cache.putCount.get()).isZero();
        }
    }

    @Nested
    @DisplayName("第 2 步：角色旁路")
    class RoleBypass {

        @Test
        @DisplayName("ADMIN 看明文，记 BYPASS")
        void adminSeesPlainText() {
            MaskingFixtures.authenticateAs("alice", "ROLE_ADMIN");

            assertThat(engine.apply("13812345678", SensitiveType.PHONE, context)).isEqualTo("13812345678");
            assertThat(recorder.lastAction()).isEqualTo(MaskAction.BYPASS);
        }

        @Test
        @DisplayName("CS 不旁路，出口仍打星")
        void csIsStillMasked() {
            MaskingFixtures.authenticateAs("carol", "ROLE_CS");

            assertThat(engine.apply("13812345678", SensitiveType.PHONE, context)).isEqualTo("138****5678");
            assertThat(recorder.lastAction()).isEqualTo(MaskAction.MASK);
        }

        @Test
        @DisplayName("context 为 null 时按 USER 处理")
        void nullContextIsMasked() {
            assertThat(engine.apply("13812345678", SensitiveType.PHONE, null)).isEqualTo("138****5678");
            assertThat(recorder.last().role()).isEqualTo(MaskRole.USER);
        }

        @Test
        @DisplayName("旁路发生在写缓存之前，明文不会进缓存")
        void bypassDoesNotPopulateCache() {
            MaskingFixtures.authenticateAs("alice", "ROLE_ADMIN");
            engine.apply("13812345678", SensitiveType.PHONE, context);

            assertThat(cache.putCount.get()).isZero();
            assertThat(cache.size()).isZero();
        }
    }

    @Nested
    @DisplayName("第 4 步：规则禁用；未知编码回落 CUSTOM")
    class RuleDisabledOrUnknown {

        @Test
        @DisplayName("类型级 enabled=false 记 BYPASS")
        void disabledRuleBypasses() {
            MaskingFixtures.authenticateAs("dave", "ROLE_USER");
            properties.getRules().getPhone().setEnabled(false);
            properties.rebuildSnapshot();

            assertThat(engine.apply("13812345678", SensitiveType.PHONE, context)).isEqualTo("13812345678");
            assertThat(recorder.lastAction()).isEqualTo(MaskAction.BYPASS);
        }

        @Test
        @DisplayName("未知 code 回落 CUSTOM 规则，不会因为漏配 extras 就返回明文")
        void unknownCodeUsesCustomRule() {
            MaskingFixtures.authenticateAs("dave", "ROLE_USER");

            // registry 和 ruleOf 都回落 CUSTOM（1,1），所以是 E*******8 这种形态，不是原文
            assertThat(engine.apply("E12345678", null, "PASSPORT", context)).isEqualTo("E*******8");
            assertThat(recorder.last().typeCode()).isEqualTo("PASSPORT");
            assertThat(recorder.lastAction()).isEqualTo(MaskAction.MASK);
        }
    }

    @Nested
    @DisplayName("第 5 步：幂等跳过")
    class IdempotentSkip {

        @Test
        @DisplayName("已打码值原样返回，不会二次打星")
        void alreadyMaskedIsSkipped() {
            MaskingFixtures.authenticateAs("dave", "ROLE_USER");

            assertThat(engine.apply("138****5678", SensitiveType.PHONE, context)).isEqualTo("138****5678");
            assertThat(recorder.lastAction()).isEqualTo(MaskAction.SKIP_ALREADY_MASKED);
        }

        @Test
        @DisplayName("两个通道叠加：第二次被幂等跳过")
        void twoChannelsInARow() {
            MaskingFixtures.authenticateAs("dave", "ROLE_USER");

            String afterAop = engine.apply("13812345678", SensitiveType.PHONE, context);
            String afterJackson = engine.apply(afterAop, SensitiveType.PHONE, context);

            assertThat(afterAop).isEqualTo("138****5678");
            assertThat(afterJackson).isEqualTo("138****5678");
            assertThat(recorder.actions()).containsExactly(MaskAction.MASK, MaskAction.SKIP_ALREADY_MASKED);
        }

        @Test
        @DisplayName("幂等跳过在查缓存之前，已打码值不占缓存")
        void skipDoesNotTouchCache() {
            MaskingFixtures.authenticateAs("dave", "ROLE_USER");
            engine.apply("138****5678", SensitiveType.PHONE, context);

            assertThat(cache.getCount.get()).isZero();
            assertThat(cache.putCount.get()).isZero();
        }
    }

    @Nested
    @DisplayName("第 6、8 步：缓存")
    class Caching {

        @Test
        @DisplayName("第一次未命中并回填，第二次命中且不再 put")
        void secondCallHitsCache() {
            MaskingFixtures.authenticateAs("dave", "ROLE_USER");

            assertThat(engine.apply("13812345678", SensitiveType.PHONE, context)).isEqualTo("138****5678");
            assertThat(cache.getCount.get()).isEqualTo(1);
            assertThat(cache.putCount.get()).isEqualTo(1);

            assertThat(engine.apply("13812345678", SensitiveType.PHONE, context)).isEqualTo("138****5678");
            assertThat(cache.getCount.get()).isEqualTo(2);
            assertThat(cache.putCount.get()).as("命中后不该重复回填").isEqualTo(1);
        }

        @Test
        @DisplayName("缓存命中也记 MASK")
        void cacheHitIsRecordedAsMask() {
            MaskingFixtures.authenticateAs("dave", "ROLE_USER");
            engine.apply("13812345678", SensitiveType.PHONE, context);
            engine.apply("13812345678", SensitiveType.PHONE, context);

            assertThat(recorder.actions()).containsExactly(MaskAction.MASK, MaskAction.MASK);
        }

        @Test
        @DisplayName("不同类型的同一明文互不干扰")
        void cacheKeyIncludesType() {
            MaskingFixtures.authenticateAs("dave", "ROLE_USER");
            engine.apply("13812345678", SensitiveType.PHONE, context);
            engine.apply("13812345678", SensitiveType.CUSTOM, context);

            assertThat(cache.size()).isEqualTo(2);
        }

        @Test
        @DisplayName("构造参数 cache/recorder 为 null 时落到 NO_OP，不会 NPE")
        void nullCacheFallsBackToNoOp() {
            MaskEngine isolated = new MaskEngine(
                    properties, new MaskStrategyRegistry(List.of(new PhoneMaskStrategy())),
                    null, new AlreadyMaskedDetector(), null);

            assertThat(isolated.apply("13812345678", SensitiveType.PHONE, null)).isEqualTo("138****5678");
        }
    }

    @Nested
    @DisplayName("自定义编码")
    class CustomCode {

        @Test
        @DisplayName("code 优先于 type，并按 extras 规则打码")
        void codeWinsOverType() {
            MaskingFixtures.authenticateAs("dave", "ROLE_USER");
            properties.getRules().getExtras().put("EXPRESS", new RuleConfig(2, 4));
            properties.rebuildSnapshot();

            MaskStrategy express = new AbstractKeepMaskStrategy() {
                @Override
                public SensitiveType type() {
                    return SensitiveType.CUSTOM;
                }

                @Override
                public String code() {
                    return "EXPRESS";
                }
            };
            MaskEngine local = new MaskEngine(
                    properties,
                    new MaskStrategyRegistry(List.of(new PhoneMaskStrategy(), express)),
                    cache,
                    new AlreadyMaskedDetector(),
                    recorder);

            String result = local.apply("SF1234567890123", SensitiveType.CUSTOM, "EXPRESS", context);
            // extras 配的是保留前 2 后 4，不是 CUSTOM 默认的 1/1
            assertThat(result).isEqualTo("SF*********0123");
            assertThat(recorder.last().typeCode()).isEqualTo("EXPRESS");
        }

        @Test
        @DisplayName("code 和 type 都空时归一化成 CUSTOM")
        void bothNullFallsBackToCustom() {
            MaskingFixtures.authenticateAs("dave", "ROLE_USER");

            assertThat(engine.apply("E12345678", null, null, context)).isEqualTo("E*******8");
            assertThat(recorder.last().typeCode()).isEqualTo("CUSTOM");
        }
    }

    @Nested
    @DisplayName("异常处理")
    class Failure {

        @Test
        @DisplayName("策略抛异常时记 FAIL 并重新抛出，绝不吞掉改返回明文")
        void rethrowsInsteadOfLeaking() {
            MaskingFixtures.authenticateAs("dave", "ROLE_USER");
            MaskStrategy boom = new MaskStrategy() {
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
            MaskEngine local = new MaskEngine(
                    properties,
                    new MaskStrategyRegistry(List.of(boom)),
                    cache,
                    new AlreadyMaskedDetector(),
                    recorder);

            assertThatThrownBy(() -> local.apply("13812345678", SensitiveType.PHONE, context))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("strategy blew up");
            assertThat(recorder.lastAction()).isEqualTo(MaskAction.FAIL);
            assertThat(cache.size()).isZero();
        }
    }

    @Nested
    @DisplayName("判定顺序")
    class DecisionOrder {

        /**
         * 角色旁路必须在幂等判定之前。调换后 ADMIN 拿到已打码值会被记成 SKIP，旁路看板失真。
         */
        @Test
        @DisplayName("ADMIN 遇到已打码值时记 BYPASS，不是 SKIP")
        void bypassBeforeIdempotentCheck() {
            MaskingFixtures.authenticateAs("alice", "ROLE_ADMIN");

            assertThat(engine.apply("138****5678", SensitiveType.PHONE, context)).isEqualTo("138****5678");
            assertThat(recorder.lastAction()).isEqualTo(MaskAction.BYPASS);
        }

        @Test
        @DisplayName("空值优先于总开关和角色：ADMIN 传 null 也记 BYPASS")
        void blankCheckIsFirst() {
            MaskingFixtures.authenticateAs("alice", "ROLE_ADMIN");
            properties.setEnabled(false);

            assertThat(engine.apply(null, SensitiveType.PHONE, context)).isNull();
            assertThat(recorder.lastAction()).isEqualTo(MaskAction.BYPASS);
        }

        @Test
        @DisplayName("调试头覆盖角色后，引擎只看 shouldBypass 的结果")
        void debugHeaderAffectsBypass() {
            properties.getDebug().setHeaderRoleEnabled(true);
            MaskingFixtures.authenticateAs("dave", "ROLE_USER");
            MaskContext.setHeaderRole(MaskRole.ADMIN);

            assertThat(engine.apply("13812345678", SensitiveType.PHONE, context)).isEqualTo("13812345678");
            assertThat(recorder.lastAction()).isEqualTo(MaskAction.BYPASS);
        }
    }
}
