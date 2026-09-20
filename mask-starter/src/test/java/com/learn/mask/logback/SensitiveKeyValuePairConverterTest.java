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
import org.slf4j.event.KeyValuePair;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SensitiveKeyValuePairConverterTest {

    private final SensitiveKeyValuePairConverter converter = new SensitiveKeyValuePairConverter();

    private MaskingProperties properties;

    @BeforeEach
    void setUp() {
        properties = new MaskingProperties();
        properties.getDebug().setHeaderRoleEnabled(true);
        MaskEngine engine = MaskingFixtures.engine(properties);
        MaskingSpringBridge.bind(engine, properties, new MaskContext(properties));
        converter.start();
    }

    @AfterEach
    void tearDown() {
        MaskingSpringBridge.unbind();
        MaskContext.clearHeaderRole();
        SecurityContextHolder.clearContext();
    }

    private String convert(KeyValuePair... pairs) {
        LoggingEvent event = new LoggingEvent();
        event.setKeyValuePairs(List.of(pairs));
        return converter.convert(event);
    }

    @Nested
    @DisplayName("格式与脱敏")
    class FormatAndMask {

        @Test
        @DisplayName("默认双引号包裹 value，并对手机号打码")
        void defaultDoubleQuotesAndMasksPhone() {
            String converted = convert(new KeyValuePair("phone", "13812345678"));

            assertThat(converted).isEqualTo("phone=\"138****5678\"");
            assertThat(converted).doesNotContain("13812345678");
        }

        @Test
        @DisplayName("多个键值对以空格分隔")
        void multiplePairsSeparatedBySpace() {
            String converted = convert(
                    new KeyValuePair("phone", "13812345678"),
                    new KeyValuePair("email", "alice@example.com"));

            assertThat(converted).isEqualTo("phone=\"138****5678\" email=\"a****@example.com\"");
        }

        @Test
        @DisplayName("无键值对时输出空串")
        void emptyWhenNoPairs() {
            LoggingEvent event = new LoggingEvent();
            assertThat(converter.convert(event)).isEmpty();
        }
    }

    @Nested
    @DisplayName("通道开关、旁路、未 bind")
    class Gates {

        @Test
        @DisplayName("Logback 通道关闭时原样返回 value")
        void logbackChannelOffReturnsOriginal() {
            properties.getChannels().setLogback(false);
            assertThat(convert(new KeyValuePair("phone", "13812345678")))
                    .isEqualTo("phone=\"13812345678\"");
        }

        @Test
        @DisplayName("ADMIN 旁路对 KVP 同样生效")
        void adminBypassLeavesPlaintext() {
            MaskContext.setHeaderRole(MaskRole.ADMIN);
            assertThat(convert(new KeyValuePair("phone", "13812345678")))
                    .isEqualTo("phone=\"13812345678\"");
        }

        @Test
        @DisplayName("引擎未 bind 时仍用星号盖住匹配段")
        void unboundStillRedactsPlaintext() {
            MaskingSpringBridge.unbind();
            String converted = convert(new KeyValuePair("phone", "13812345678"));

            assertThat(converted).doesNotContain("13812345678");
            assertThat(converted).contains("***********");
        }
    }
}
