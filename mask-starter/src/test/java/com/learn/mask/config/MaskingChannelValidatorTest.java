package com.learn.mask.config;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MaskingChannelValidatorTest {

    private static ListAppender<ILoggingEvent> attachAppender() {
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        ((Logger) LoggerFactory.getLogger(MaskingChannelValidator.class)).addAppender(appender);
        return appender;
    }

    private static void detach(ListAppender<ILoggingEvent> appender) {
        ((Logger) LoggerFactory.getLogger(MaskingChannelValidator.class)).detachAppender(appender);
    }

    @Nested
    @DisplayName("Jackson 与改写内存的通道叠在一起")
    class ChannelConflict {

        @Test
        @DisplayName("strict=true 时 Jackson + AOP 直接启动失败")
        void strictModeFailsWhenJacksonConflictsWithAop() {
            MaskingProperties properties = new MaskingProperties();
            properties.getChannels().setJackson(true);
            properties.getChannels().setAop(true);
            properties.getChannels().setStrict(true);

            assertThatThrownBy(() -> new MaskingChannelValidator(properties).afterPropertiesSet())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("jackson");
        }

        @Test
        @DisplayName("strict=false 时只打 WARN，不抛异常")
        void nonStrictOnlyWarns() {
            MaskingProperties properties = new MaskingProperties();
            properties.getChannels().setJackson(true);
            properties.getChannels().setMybatis(true);
            properties.getChannels().setStrict(false);
            ListAppender<ILoggingEvent> appender = attachAppender();
            try {
                assertThatCode(() -> new MaskingChannelValidator(properties).afterPropertiesSet())
                        .doesNotThrowAnyException();
                assertThat(appender.list)
                        .anyMatch(event -> event.getLevel() == Level.WARN
                                && event.getFormattedMessage().contains("jackson"));
            } finally {
                detach(appender);
            }
        }
    }

    @Nested
    @DisplayName("危险配置要打 ERROR，不能静默")
    class DangerousConfig {

        @Test
        @DisplayName("总开关关闭时打 ERROR：所有敏感字段会以明文出去")
        void enabledFalseLogsError() {
            MaskingProperties properties = new MaskingProperties();
            properties.setEnabled(false);
            ListAppender<ILoggingEvent> appender = attachAppender();
            try {
                new MaskingChannelValidator(properties).afterPropertiesSet();

                assertThat(appender.list)
                        .anyMatch(event -> event.getLevel() == Level.ERROR
                                && event.getFormattedMessage().contains("masking.enabled is false"));
            } finally {
                detach(appender);
            }
        }

        @Test
        @DisplayName("调试头打开时打 WARN：任意已认证调用方都能旁路")
        void headerRoleEnabledLogsWarn() {
            MaskingProperties properties = new MaskingProperties();
            properties.getDebug().setHeaderRoleEnabled(true);
            ListAppender<ILoggingEvent> appender = attachAppender();
            try {
                new MaskingChannelValidator(properties).afterPropertiesSet();

                assertThat(appender.list)
                        .anyMatch(event -> event.getLevel() == Level.WARN
                                && event.getFormattedMessage().contains("header-role-enabled"));
            } finally {
                detach(appender);
            }
        }
    }
}
