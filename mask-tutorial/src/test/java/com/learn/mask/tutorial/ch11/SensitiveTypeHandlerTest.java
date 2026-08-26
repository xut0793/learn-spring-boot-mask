package com.learn.mask.tutorial.ch11;

import com.learn.mask.tutorial.ch04.MaskStrategyRegistry;
import com.learn.mask.tutorial.ch05.MaskContext;
import com.learn.mask.tutorial.ch05.RoleProperties;
import com.learn.mask.tutorial.ch06.AlreadyMaskedDetector;
import com.learn.mask.tutorial.ch06.MaskEngine;
import com.learn.mask.tutorial.ch06.MaskRecorder;
import com.learn.mask.tutorial.ch06.MaskResultCache;
import com.learn.mask.tutorial.ch07.MaskingProperties;
import com.learn.mask.tutorial.ch09.ChannelProperties;
import com.learn.mask.tutorial.ch09.MaskingSpringBridge;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.TypeHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.sql.PreparedStatement;
import java.sql.ResultSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SensitiveTypeHandlerTest {

    private ChannelProperties channels;
    private PhoneSensitiveTypeHandler phoneHandler;

    @BeforeEach
    void setUp() {
        MaskingProperties properties = new MaskingProperties();
        channels = new ChannelProperties();
        channels.setMybatis(true);
        MaskEngine engine = new MaskEngine(properties, MaskStrategyRegistry.withBuiltins(),
                MaskResultCache.NO_OP, new AlreadyMaskedDetector(), MaskRecorder.NO_OP);
        MaskingSpringBridge.bind(engine, properties, new MaskContext(new RoleProperties()), channels);
        phoneHandler = new PhoneSensitiveTypeHandler();
    }

    @AfterEach
    void tearDown() {
        MaskingSpringBridge.unbind();
    }

    @Nested
    @DisplayName("只脱读出，不改写入")
    class ReadVsWrite {

        @Test
        void getNullableResultMasks() throws Exception {
            ResultSet rs = mock(ResultSet.class);
            when(rs.getString("phone")).thenReturn("13812345678");

            assertThat(phoneHandler.getNullableResult(rs, "phone")).isEqualTo("138****5678");
        }

        @Test
        @DisplayName("写入 PreparedStatement 时原样透传 —— 绝不能把星号写进库")
        void setNonNullParameterWritesPlaintext() throws Exception {
            PreparedStatement ps = mock(PreparedStatement.class);

            phoneHandler.setNonNullParameter(ps, 1, "13812345678", JdbcType.VARCHAR);

            verify(ps).setString(1, "13812345678");
        }

        @Test
        void nullResultStaysNull() throws Exception {
            ResultSet rs = mock(ResultSet.class);
            when(rs.getString(1)).thenReturn(null);
            assertThat(phoneHandler.getNullableResult(rs, 1)).isNull();
        }
    }

    @Nested
    @DisplayName("按子类区分类型")
    class TypedSubclasses {

        @Test
        void emailHandlerUsesEmailRule() throws Exception {
            ResultSet rs = mock(ResultSet.class);
            when(rs.getString("email")).thenReturn("alice@example.com");

            assertThat(new EmailSensitiveTypeHandler().getNullableResult(rs, "email"))
                    .isEqualTo("a****@example.com");
        }

        @Test
        void customCodeHandler() throws Exception {
            SensitiveTypeHandler handler = new SensitiveTypeHandler("PHONE");
            ResultSet rs = mock(ResultSet.class);
            when(rs.getString("mobile")).thenReturn("13812345678");
            assertThat(handler.getNullableResult(rs, "mobile")).isEqualTo("138****5678");
        }
    }

    @Nested
    @DisplayName("通道关闭则透传")
    class Gates {

        @Test
        void mybatisChannelOffReturnsRaw() throws Exception {
            channels.setMybatis(false);
            ResultSet rs = mock(ResultSet.class);
            when(rs.getString("phone")).thenReturn("13812345678");

            assertThat(phoneHandler.getNullableResult(rs, "phone")).isEqualTo("13812345678");
        }

        @Test
        void unboundBridgeReturnsRaw() throws Exception {
            MaskingSpringBridge.unbind();
            ResultSet rs = mock(ResultSet.class);
            when(rs.getString("phone")).thenReturn("13812345678");

            assertThat(phoneHandler.getNullableResult(rs, "phone")).isEqualTo("13812345678");
        }
    }

    @Nested
    @DisplayName("注册成 String 的默认 TypeHandler 会误伤所有列")
    class GlobalRegistrationHazard {

        @Test
        @DisplayName("Configuration.register(String.class, PhoneHandler) 之后 name 也会被当手机号")
        void defaultStringHandlerMasksEveryColumn() throws Exception {
            Configuration configuration = new Configuration();
            configuration.getTypeHandlerRegistry().register(String.class, PhoneSensitiveTypeHandler.class);

            TypeHandler<String> defaultString = configuration.getTypeHandlerRegistry()
                    .getTypeHandler(String.class);
            assertThat(defaultString)
                    .as("一旦按 String.class 注册，所有字符串列都拿到这个 handler")
                    .isInstanceOf(PhoneSensitiveTypeHandler.class);

            ResultSet nameColumn = mock(ResultSet.class);
            when(nameColumn.getString("name")).thenReturn("张三丰");
            // 长度 3 ≤ keepPrefix 3 + keepSuffix 4，整段打星
            assertThat(new PhoneSensitiveTypeHandler().getNullableResult(nameColumn, "name"))
                    .isEqualTo("***");
        }
    }
}
