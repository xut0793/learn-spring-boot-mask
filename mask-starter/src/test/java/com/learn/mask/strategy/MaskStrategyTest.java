package com.learn.mask.strategy;

import com.learn.mask.config.MaskRule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MaskStrategyTest {

    private final MaskRule phoneRule = MaskRule.of(3, 4);
    private final MaskRule idCardRule = MaskRule.of(6, 4);
    private final MaskRule bankCardRule = MaskRule.of(4, 4);
    private final MaskRule emailRule = MaskRule.of(1, 0);
    private final MaskRule customRule = MaskRule.of(1, 1);

    @Nested
    @DisplayName("保留前后缀类策略")
    class KeepStrategies {

        @Test
        @DisplayName("手机号保留前 3 后 4")
        void phone() {
            PhoneMaskStrategy strategy = new PhoneMaskStrategy();
            assertThat(strategy.mask("13812345678", phoneRule)).isEqualTo("138****5678");
            // 中间段与前缀重叠时也不能误伤后 4 位
            assertThat(strategy.mask("13812341234", phoneRule)).isEqualTo("138****1234");
        }

        @Test
        @DisplayName("身份证保留前 6 后 4")
        void idCard() {
            assertThat(new IdCardMaskStrategy().mask("110101199003078515", idCardRule))
                    .isEqualTo("110101********8515");
        }

        @Test
        @DisplayName("银行卡按实际长度填星，16 位和 19 位共用同一规则")
        void bankCard() {
            BankCardMaskStrategy strategy = new BankCardMaskStrategy();
            assertThat(strategy.mask("6222021234567890123", bankCardRule))
                    .isEqualTo("6222***********0123");
            assertThat(strategy.mask("6222021234567890", bankCardRule))
                    .isEqualTo("6222********7890");
        }

        @Test
        @DisplayName("长度不足前缀+后缀时全部打星，不抛异常")
        void tooShortIsFullyMasked() {
            PhoneMaskStrategy strategy = new PhoneMaskStrategy();
            assertThat(strategy.mask("1381234", phoneRule)).isEqualTo("*******");
            assertThat(strategy.mask("138", phoneRule)).isEqualTo("***");
        }

        @Test
        @DisplayName("空值与规则关闭时原样返回")
        void blankOrDisabledPassesThrough() {
            PhoneMaskStrategy strategy = new PhoneMaskStrategy();
            assertThat(strategy.mask(null, phoneRule)).isNull();
            assertThat(strategy.mask("", phoneRule)).isEmpty();
            assertThat(strategy.mask("   ", phoneRule)).isEqualTo("   ");
            assertThat(strategy.mask("13812345678", MaskRule.disabled())).isEqualTo("13812345678");
            assertThat(strategy.mask("13812345678", null)).isEqualTo("13812345678");
        }

        @Test
        @DisplayName("掩码字符可配置")
        void maskCharIsConfigurable() {
            assertThat(new PhoneMaskStrategy().mask("13812345678", MaskRule.of(3, 4, '#')))
                    .isEqualTo("138####5678");
        }
    }

    @Nested
    @DisplayName("邮箱：只打本地段，域名原样保留")
    class Email {

        private final EmailMaskStrategy strategy = new EmailMaskStrategy();

        @Test
        @DisplayName("本地段超过保留位数时只打后面")
        void masksLocalPartOnly() {
            assertThat(strategy.mask("zhangsan@example.com", emailRule))
                    .isEqualTo("z*******@example.com");
        }

        @Test
        @DisplayName("本地段不超过保留位数时整段打星，不能把明文邮箱放出去")
        void shortLocalPartIsFullyMasked() {
            assertThat(strategy.mask("a@b.com", emailRule)).isEqualTo("*@b.com");
        }

        @Test
        @DisplayName("没有 @ 或 @ 在开头时回落到 keepMask")
        void fallsBackWhenNotAnEmail() {
            assertThat(strategy.mask("not-an-email", emailRule)).isEqualTo("n***********");
            // at == 0，keepMask 会保留第一个字符，也就是 @
            assertThat(strategy.mask("@example.com", emailRule)).isEqualTo("@***********");
        }
    }

    @Nested
    @DisplayName("幂等判定 alreadyMasked")
    class AlreadyMasked {

        @Test
        @DisplayName("能认出自己的输出，认不出明文")
        void recognizesOwnOutput() {
            PhoneMaskStrategy phone = new PhoneMaskStrategy();
            assertThat(phone.alreadyMasked("138****5678", phoneRule)).isTrue();
            assertThat(phone.alreadyMasked("13812345678", phoneRule)).isFalse();

            EmailMaskStrategy email = new EmailMaskStrategy();
            assertThat(email.alreadyMasked("z*******@example.com", emailRule)).isTrue();
            assertThat(email.alreadyMasked("zhangsan@example.com", emailRule)).isFalse();
        }

        @Test
        @DisplayName("换了掩码字符就认不出来——热更新 maskChar 后的固有边界")
        void differentMaskCharIsNotRecognized() {
            assertThat(new PhoneMaskStrategy().alreadyMasked("138####5678", phoneRule)).isFalse();
        }

        @Test
        @DisplayName("空白永远不算已打码")
        void blankIsNeverConsideredMasked() {
            PhoneMaskStrategy phone = new PhoneMaskStrategy();
            assertThat(phone.alreadyMasked(null, phoneRule)).isFalse();
            assertThat(phone.alreadyMasked("", phoneRule)).isFalse();
        }
    }

    @Nested
    @DisplayName("通用策略是兜底")
    class Custom {

        @Test
        @DisplayName("CUSTOM 默认保留前后各 1 位")
        void keepsOneCharOnEachSide() {
            assertThat(new CustomPatternMaskStrategy().mask("E12345678", customRule))
                    .isEqualTo("E*******8");
        }
    }
}
