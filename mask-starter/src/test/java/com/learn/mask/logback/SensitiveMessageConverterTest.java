package com.learn.mask.logback;

import ch.qos.logback.classic.spi.LoggingEvent;
import com.learn.mask.config.MaskingProperties;
import com.learn.mask.context.MaskContext;
import com.learn.mask.context.MaskRole;
import com.learn.mask.engine.MaskEngine;
import com.learn.mask.support.MaskingSpringBridge;
import com.learn.mask.testsupport.MaskingFixtures;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;

class SensitiveMessageConverterTest {

    private final SensitiveMessageConverter converter = new SensitiveMessageConverter();
    private MaskingProperties properties;

    @BeforeEach
    void setUp() {
        properties = new MaskingProperties();
        properties.getDebug().setHeaderRoleEnabled(true);
        MaskEngine engine = MaskingFixtures.engine(properties);
        MaskingSpringBridge.bind(engine, properties, new MaskContext(properties));
    }

    @AfterEach
    void tearDown() {
        MaskingSpringBridge.unbind();
        MaskContext.clearHeaderRole();
        SecurityContextHolder.clearContext();
    }

    private String convert(String message) {
        LoggingEvent event = new LoggingEvent();
        event.setMessage(message);
        return converter.convert(event);
    }

    @Nested
    @DisplayName("按类型扫出明文并交给引擎")
    class MasksEachType {

        @Test
        @DisplayName("同一行里的手机号和邮箱都会打码")
        void masksPhoneAndEmailInOneLine() {
            String converted = convert("loaded user phone=13812345678 email=alice@example.com");

            assertThat(converted).doesNotContain("13812345678");
            assertThat(converted).doesNotContain("alice@example.com");
            assertThat(converted).contains("138****5678");
            assertThat(converted).contains("a****@example.com");
        }

        @Test
        @DisplayName("身份证和银行卡按各自规则打码")
        void masksIdCardAndBankCard() {
            String converted = convert("id=110101199003078515 card=6222021234567890123");

            assertThat(converted).contains("110101********8515");
            assertThat(converted).contains("6222***********0123");
            assertThat(converted).doesNotContain("110101199003078515");
        }

        @Test
        @DisplayName("左右是数字时不从更长串里切出手机号")
        void phoneLookaroundPreventsInnerMatch() {
            String converted = convert("order=13812345678001");
            assertThat(converted).contains("order=13812345678001");
        }
    }

    @Nested
    @DisplayName("替换顺序：BANK_CARD 必须最后")
    class ReplacementOrder {

        @Test
        @DisplayName("EMAIL 先于 BANK_CARD：本地段 16 位数字不会被当成卡号")
        void emailBeforeBankCard() {
            String converted = convert("login=1234567890123456@example.com");

            assertThat(converted)
                    .as("整封邮箱按 EMAIL 规则处理，而不是本地段按银行卡切走")
                    .isEqualTo("login=1***************@example.com");
            assertThat(converted).doesNotContain("1234********3456@example.com");
        }

        @Test
        @DisplayName("ID_CARD 先于 BANK_CARD：18 位身份证不会被当成卡号")
        void idCardBeforeBankCard() {
            String converted = convert("id=110101199003078515");

            assertThat(converted).isEqualTo("id=110101********8515");
            assertThat(converted)
                    .as("如果先走银行卡规则，会变成保留前 4 后 4")
                    .doesNotContain("1101**********8515");
        }
    }

    @Nested
    @DisplayName("通道开关、旁路、未 bind")
    class Gates {

        @Test
        @DisplayName("Logback 通道关闭时原样返回")
        void logbackChannelOffReturnsOriginal() {
            properties.getChannels().setLogback(false);
            assertThat(convert("phone=13812345678")).isEqualTo("phone=13812345678");
        }

        @Test
        @DisplayName("总开关关闭时原样返回")
        void globalSwitchOffReturnsOriginal() {
            properties.setEnabled(false);
            assertThat(convert("phone=13812345678")).isEqualTo("phone=13812345678");
        }

        @Test
        @DisplayName("ADMIN 旁路对日志同样生效")
        void adminBypassLeavesPlaintextInLogs() {
            MaskContext.setHeaderRole(MaskRole.ADMIN);
            assertThat(convert("phone=13812345678")).isEqualTo("phone=13812345678");
        }

        @Test
        @DisplayName("引擎未 bind 时仍用星号盖住匹配段，启动窗口不能写明文")
        void unboundStillRedactsPlaintext() {
            MaskingSpringBridge.unbind();
            String converted = convert("loaded user phone=13812345678 email=alice@example.com");

            assertThat(converted).doesNotContain("13812345678");
            assertThat(converted).doesNotContain("alice@example.com");
            assertThat(converted).contains("***********");
        }
    }
}
