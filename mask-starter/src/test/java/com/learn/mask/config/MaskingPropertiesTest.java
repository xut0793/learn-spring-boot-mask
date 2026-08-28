package com.learn.mask.config;

import com.learn.mask.annotation.SensitiveType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MaskingPropertiesTest {

    private final MaskingProperties properties = new MaskingProperties();

    @Nested
    @DisplayName("默认值与查找")
    class Defaults {

        @Test
        @DisplayName("内置类型默认规则与引擎约定一致")
        void defaultRulesMatchBuiltins() {
            assertThat(properties.ruleOf(SensitiveType.PHONE)).isEqualTo(MaskRule.of(3, 4));
            assertThat(properties.ruleOf(SensitiveType.ID_CARD)).isEqualTo(MaskRule.of(6, 4));
            assertThat(properties.ruleOf(SensitiveType.BANK_CARD)).isEqualTo(MaskRule.of(4, 4));
            assertThat(properties.ruleOf(SensitiveType.EMAIL)).isEqualTo(MaskRule.of(1, 0));
            assertThat(properties.ruleOf(SensitiveType.CUSTOM)).isEqualTo(MaskRule.of(1, 1));
        }

        @Test
        @DisplayName("编码查找会 trim + 大写")
        void lookupIsNormalized() {
            assertThat(properties.ruleOf("phone")).isEqualTo(properties.ruleOf("PHONE"));
            assertThat(properties.ruleOf("  Phone ")).isEqualTo(properties.ruleOf("PHONE"));
        }

        @Test
        @DisplayName("未知编码回落 CUSTOM，不会因为漏配 extras 就返回 null")
        void unknownCodeFallsBackToCustom() {
            assertThat(properties.ruleOf("PASSPORT")).isEqualTo(properties.ruleOf(SensitiveType.CUSTOM));
        }

        @Test
        @DisplayName("null / 空编码归一化成 CUSTOM")
        void blankCodeFallsBackToCustom() {
            assertThat(properties.ruleOf((String) null)).isEqualTo(properties.ruleOf("CUSTOM"));
            assertThat(properties.ruleOf("")).isEqualTo(properties.ruleOf("CUSTOM"));
        }
    }

    @Nested
    @DisplayName("快照语义")
    class Snapshot {

        @Test
        @DisplayName("改了 RuleSet 但没重建快照时，引擎读到的还是旧规则")
        void mutatingRuleSetDoesNotLeakBeforeRebuild() {
            properties.getRules().getPhone().setKeepPrefix(0);

            assertThat(properties.ruleOf(SensitiveType.PHONE).keepPrefix())
                    .as("快照未重建，引擎应仍看到旧值")
                    .isEqualTo(3);

            properties.rebuildSnapshot();

            assertThat(properties.ruleOf(SensitiveType.PHONE).keepPrefix()).isZero();
        }

        @Test
        @DisplayName("快照是不可变的，拿到引用也改不了")
        void snapshotIsImmutable() {
            assertThatThrownBy(() -> properties.currentRules().put("HACK", MaskRule.of(0, 0)))
                    .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        @DisplayName("已经取出的 MaskRule 不会因为后续热更新而变化")
        void handedOutRuleIsStable() {
            MaskRule before = properties.ruleOf(SensitiveType.PHONE);

            properties.getRules().getPhone().setKeepPrefix(0);
            properties.getRules().getPhone().setKeepSuffix(0);
            properties.rebuildSnapshot();

            assertThat(before.keepPrefix()).isEqualTo(3);
            assertThat(before.keepSuffix()).isEqualTo(4);
            assertThat(properties.ruleOf(SensitiveType.PHONE).keepPrefix()).isZero();
        }

        @Test
        @DisplayName("extras 的键会被归一化后再写进快照")
        void extrasAreNormalizedIntoSnapshot() {
            properties.getRules().getExtras().put("express", new RuleConfig(2, 4));
            properties.rebuildSnapshot();

            assertThat(properties.ruleOf("EXPRESS")).isEqualTo(MaskRule.of(2, 4));
        }
    }

    @Nested
    @DisplayName("规则版本")
    class Version {

        @Test
        @DisplayName("版本号从 1 起步，bump 后加一")
        void startsAtOneAndIncrements() {
            assertThat(properties.getRuleVersion()).isEqualTo(1);
            properties.bumpRuleVersion();
            assertThat(properties.getRuleVersion()).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("Map 键映射：内置 map-keys 优先，其次 extra-map-keys")
    class MapKeys {

        @Test
        @DisplayName("同一语义的多种命名风格都能识别")
        void multipleNamingStyles() {
            assertThat(properties.typeCodeOf("idCard")).isEqualTo("ID_CARD");
            assertThat(properties.typeCodeOf("id_card")).isEqualTo("ID_CARD");
            assertThat(properties.typeCodeOf("identityCard")).isEqualTo("ID_CARD");
        }

        @Test
        @DisplayName("认不出的键返回 null，Jackson / Walker 会据此不处理该字段")
        void unknownKeyReturnsNull() {
            assertThat(properties.typeCodeOf("nickname")).isNull();
            assertThat(properties.typeCodeOf(null)).isNull();
        }

        @Test
        @DisplayName("键本身大小写敏感 —— 因为它来自上游 JSON，不该猜")
        void keyLookupIsCaseSensitive() {
            assertThat(properties.typeCodeOf("phone")).isEqualTo("PHONE");
            assertThat(properties.typeCodeOf("Phone")).isNull();
        }

        @Test
        @DisplayName("extra-map-keys 补上 starter 枚举里没有的业务键")
        void extraMapKeysFillCustomCodes() {
            properties.setExtraMapKeys(Map.of("trackingNo", "express"));

            assertThat(properties.typeCodeOf("trackingNo")).isEqualTo("EXPRESS");
        }

        @Test
        @DisplayName("同一键同时出现时 map-keys 赢")
        void builtinMapKeysWinOverExtras() {
            properties.setExtraMapKeys(Map.of("phone", "CUSTOM"));

            assertThat(properties.typeCodeOf("phone")).isEqualTo("PHONE");
        }
    }

    @Nested
    @DisplayName("可逆字段白名单")
    class ReversibleAllowlist {

        @Test
        @DisplayName("默认只允许 phone / idCard / identityCard，大小写不敏感")
        void defaultAllowlist() {
            assertThat(properties.getReversible().allows("phone")).isTrue();
            assertThat(properties.getReversible().allows("IDCARD")).isTrue();
            assertThat(properties.getReversible().allows("email")).isFalse();
            assertThat(properties.getReversible().allows(null)).isFalse();
        }
    }
}
