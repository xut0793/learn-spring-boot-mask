package com.learn.mask.tutorial.ch04;

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
    private final MaskRule expressRule = MaskRule.of(2, 4);

    @Nested
    @DisplayName("保留前后缀类策略")
    class KeepStrategies {

        @Test
        void phone() {
            MaskStrategy strategy = new PhoneMaskStrategy();
            assertThat(strategy.mask("13812345678", phoneRule)).isEqualTo("138****5678");
            // 第 1 章的 bug 数据，现在是对的
            assertThat(strategy.mask("13812341234", phoneRule)).isEqualTo("138****1234");
        }

        @Test
        void idCard() {
            assertThat(new IdCardMaskStrategy().mask("110101199003078515", idCardRule))
                    .isEqualTo("110101********8515");
        }

        @Test
        void bankCard() {
            MaskStrategy strategy = new BankCardMaskStrategy();
            // 19 位
            assertThat(strategy.mask("6222021234567890123", bankCardRule))
                    .isEqualTo("6222***********0123");
            // 16 位，星号数量自动少 3 个，不需要为不同长度写分支
            assertThat(strategy.mask("6222021234567890", bankCardRule))
                    .isEqualTo("6222********7890");
        }

        @Test
        @DisplayName("长度不足时全部打星，而不是抛异常")
        void tooShortIsFullyMasked() {
            MaskStrategy strategy = new PhoneMaskStrategy();
            assertThat(strategy.mask("1381234", phoneRule)).isEqualTo("*******");
            assertThat(strategy.mask("138", phoneRule)).isEqualTo("***");
        }

        @Test
        @DisplayName("空值与规则关闭时原样返回")
        void blankOrDisabledPassesThrough() {
            MaskStrategy strategy = new PhoneMaskStrategy();
            assertThat(strategy.mask(null, phoneRule)).isNull();
            assertThat(strategy.mask("", phoneRule)).isEmpty();
            assertThat(strategy.mask("   ", phoneRule)).isEqualTo("   ");
            assertThat(strategy.mask("13812345678", MaskRule.disabled())).isEqualTo("13812345678");
        }

        @Test
        @DisplayName("掩码字符可配置")
        void maskCharIsConfigurable() {
            assertThat(new PhoneMaskStrategy().mask("13812345678", MaskRule.of(3, 4, '#')))
                    .isEqualTo("138####5678");
        }
    }

    @Nested
    @DisplayName("邮箱策略：域名必须保留")
    class Email {

        private final MaskStrategy strategy = new EmailMaskStrategy();

        @Test
        void masksLocalPartOnly() {
            assertThat(strategy.mask("zhangsan@example.com", emailRule))
                    .isEqualTo("z*******@example.com");
        }

        @Test
        @DisplayName("local 段长度不超过保留位数时整段打星，不能原样返回")
        void shortLocalPartIsFullyMasked() {
            assertThat(strategy.mask("a@b.com", emailRule)).isEqualTo("*@b.com");
        }

        @Test
        @DisplayName("没有 @ 或 @ 在开头时回落到通用打星")
        void fallsBackWhenNotAnEmail() {
            // at == -1
            assertThat(strategy.mask("not-an-email", emailRule)).isEqualTo("n***********");
            // at == 0，保留的第 1 个字符就是 @ 本身
            assertThat(strategy.mask("@example.com", emailRule)).isEqualTo("@***********");
        }
    }

    @Nested
    @DisplayName("幂等判定 alreadyMasked")
    class AlreadyMasked {

        @Test
        void recognizesOwnOutput() {
            MaskStrategy phone = new PhoneMaskStrategy();
            assertThat(phone.alreadyMasked("138****5678", phoneRule)).isTrue();
            assertThat(phone.alreadyMasked("13812345678", phoneRule)).isFalse();

            MaskStrategy email = new EmailMaskStrategy();
            assertThat(email.alreadyMasked("z*******@example.com", emailRule)).isTrue();
            assertThat(email.alreadyMasked("zhangsan@example.com", emailRule)).isFalse();
        }

        @Test
        @DisplayName("换了掩码字符就认不出来了 —— 这是幂等判定的固有边界")
        void differentMaskCharIsNotRecognized() {
            MaskStrategy phone = new PhoneMaskStrategy();
            // 值是用 # 打的码，但当前规则的掩码字符是 *
            assertThat(phone.alreadyMasked("138####5678", phoneRule)).isFalse();
        }

        @Test
        void blankIsNeverConsideredMasked() {
            MaskStrategy phone = new PhoneMaskStrategy();
            assertThat(phone.alreadyMasked(null, phoneRule)).isFalse();
            assertThat(phone.alreadyMasked("", phoneRule)).isFalse();
        }
    }

    @Nested
    @DisplayName("业务自定义策略：不改枚举也能扩展")
    class CustomExtension {

        private final MaskStrategy strategy = new ExpressNoMaskStrategy();

        @Test
        void codeIsNotAnEnumName() {
            assertThat(strategy.type()).isEqualTo(SensitiveType.CUSTOM);
            assertThat(strategy.code()).isEqualTo("EXPRESS");
        }

        @Test
        void keepsCarrierPrefixAndTailDigits() {
            assertThat(strategy.mask("SF1234567890123", expressRule)).isEqualTo("SF12*******0123");
        }

        @Test
        @DisplayName("国际单号末尾的国家代码也保留")
        void keepsTrailingCountryCode() {
            assertThat(strategy.mask("EA123456789CN", expressRule)).isEqualTo("EA12***6789CN");
        }

        @Test
        @DisplayName("格式不认识时退回通用打星，绝不原样返回明文")
        void unknownFormatStillMasked() {
            assertThat(strategy.mask("SF-1234-5678", expressRule)).isEqualTo("SF******5678");
        }

        @Test
        void alreadyMaskedDetection() {
            assertThat(strategy.alreadyMasked("SF1234567890123", expressRule)).isFalse();
            assertThat(strategy.alreadyMasked("SF12*******0123", expressRule)).isTrue();
        }
    }

    @Nested
    @DisplayName("通用策略是兜底")
    class Custom {

        @Test
        void keepsOneCharOnEachSide() {
            assertThat(new CustomPatternMaskStrategy().mask("E12345678", customRule))
                    .isEqualTo("E*******8");
        }
    }
}
