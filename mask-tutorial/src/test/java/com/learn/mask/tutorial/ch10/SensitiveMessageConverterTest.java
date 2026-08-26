package com.learn.mask.tutorial.ch10;

import ch.qos.logback.classic.spi.LoggingEvent;
import com.learn.mask.tutorial.ch04.MaskStrategyRegistry;
import com.learn.mask.tutorial.ch05.MaskContext;
import com.learn.mask.tutorial.ch05.MaskRole;
import com.learn.mask.tutorial.ch05.RoleProperties;
import com.learn.mask.tutorial.ch06.AlreadyMaskedDetector;
import com.learn.mask.tutorial.ch06.MaskEngine;
import com.learn.mask.tutorial.ch06.MaskRecorder;
import com.learn.mask.tutorial.ch06.MaskResultCache;
import com.learn.mask.tutorial.ch07.MaskingProperties;
import com.learn.mask.tutorial.ch09.ChannelProperties;
import com.learn.mask.tutorial.ch09.MaskingSpringBridge;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SensitiveMessageConverterTest {

    private final SensitiveMessageConverter converter = new SensitiveMessageConverter();
    private MaskingProperties properties;
    private ChannelProperties channels;
    private MaskContext maskContext;

    @BeforeEach
    void setUp() {
        properties = new MaskingProperties();
        channels = new ChannelProperties();
        RoleProperties roles = new RoleProperties();
        roles.getDebug().setHeaderRoleEnabled(true);
        maskContext = new MaskContext(roles);
        MaskEngine engine = new MaskEngine(properties, MaskStrategyRegistry.withBuiltins(),
                MaskResultCache.NO_OP, new AlreadyMaskedDetector(), MaskRecorder.NO_OP);
        MaskingSpringBridge.bind(engine, properties, maskContext, channels);
    }

    @AfterEach
    void tearDown() {
        MaskContext.clearHeaderRole();
        MaskingSpringBridge.unbind();
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
        void masksPhoneAndEmailInOneLine() {
            String converted = convert("loaded user phone=13812345678 email=alice@example.com");

            assertThat(converted).doesNotContain("13812345678");
            assertThat(converted).doesNotContain("alice@example.com");
            assertThat(converted).contains("138****5678");
            assertThat(converted).contains("a****@example.com");
        }

        @Test
        void masksIdCardAndBankCard() {
            String converted = convert("id=110101199001011234 card=6222021234567890");

            assertThat(converted).contains("110101********1234");
            assertThat(converted).contains("6222********7890");
            assertThat(converted).doesNotContain("110101199001011234");
            assertThat(converted).doesNotContain("6222021234567890");
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
            String converted = convert("id=110101199001011234");

            assertThat(converted).isEqualTo("id=110101********1234");
            assertThat(converted)
                    .as("如果先走银行卡规则，会变成保留前 4 后 4")
                    .doesNotContain("1101**********1234");
        }
    }

    @Nested
    @DisplayName("通道开关与旁路")
    class Gates {

        @Test
        void logbackChannelOffReturnsOriginal() {
            channels.setLogback(false);
            assertThat(convert("phone=13812345678")).isEqualTo("phone=13812345678");
        }

        @Test
        void unboundBridgeReturnsOriginal() {
            MaskingSpringBridge.unbind();
            assertThat(convert("phone=13812345678")).isEqualTo("phone=13812345678");
        }

        @Test
        @DisplayName("ADMIN 旁路对日志同样生效 —— 第 3 章实验五看到的现象")
        void adminBypassLeavesPlaintextInLogs() {
            MaskContext.setHeaderRole(MaskRole.ADMIN);
            assertThat(convert("phone=13812345678")).isEqualTo("phone=13812345678");
        }
    }
}
