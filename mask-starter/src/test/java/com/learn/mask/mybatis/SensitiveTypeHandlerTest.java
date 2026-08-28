package com.learn.mask.mybatis;

import com.learn.mask.config.MaskingProperties;
import com.learn.mask.context.MaskContext;
import com.learn.mask.engine.MaskEngine;
import com.learn.mask.support.MaskingSpringBridge;
import com.learn.mask.testsupport.MaskingFixtures;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SensitiveTypeHandlerTest {

    private MaskingProperties properties;
    private PhoneSensitiveTypeHandler phoneHandler;

    @BeforeEach
    void setUp() {
        properties = new MaskingProperties();
        properties.getChannels().setMybatis(true);
        MaskEngine engine = MaskingFixtures.engine(properties);
        MaskingSpringBridge.bind(engine, properties, new MaskContext(properties));
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
        @DisplayName("从 ResultSet 读出后按类型打码")
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
        @DisplayName("null 结果保持 null")
        void nullResultStaysNull() throws Exception {
            ResultSet rs = mock(ResultSet.class);
            when(rs.getString(1)).thenReturn(null);
            assertThat(phoneHandler.getNullableResult(rs, 1)).isNull();
        }
    }

    @Nested
    @DisplayName("按子类 / 自定义编码区分类型")
    class TypedSubclasses {

        @Test
        @DisplayName("Email 子类走邮箱规则")
        void emailHandlerUsesEmailRule() throws Exception {
            ResultSet rs = mock(ResultSet.class);
            when(rs.getString("email")).thenReturn("alice@example.com");

            assertThat(new EmailSensitiveTypeHandler().getNullableResult(rs, "email"))
                    .isEqualTo("a****@example.com");
        }

        @Test
        @DisplayName("字符串编码构造也能命中内置策略")
        void customCodeHandler() throws Exception {
            SensitiveTypeHandler handler = new SensitiveTypeHandler("PHONE");
            ResultSet rs = mock(ResultSet.class);
            when(rs.getString("mobile")).thenReturn("13812345678");
            assertThat(handler.getNullableResult(rs, "mobile")).isEqualTo("138****5678");
        }
    }

    @Nested
    @DisplayName("通道关闭透传；未 bind 时 fail-closed")
    class Gates {

        @Test
        @DisplayName("MyBatis 通道关闭时返回原文")
        void mybatisChannelOffReturnsRaw() throws Exception {
            properties.getChannels().setMybatis(false);
            ResultSet rs = mock(ResultSet.class);
            when(rs.getString("phone")).thenReturn("13812345678");

            assertThat(phoneHandler.getNullableResult(rs, "phone")).isEqualTo("13812345678");
        }

        @Test
        @DisplayName("总开关关闭时返回原文")
        void globalSwitchOffReturnsRaw() throws Exception {
            properties.setEnabled(false);
            ResultSet rs = mock(ResultSet.class);
            when(rs.getString("phone")).thenReturn("13812345678");

            assertThat(phoneHandler.getNullableResult(rs, "phone")).isEqualTo("13812345678");
        }

        @Test
        @DisplayName("静态桥未 bind 时抛异常，不把明文当成功返回")
        void unboundThrowsInsteadOfLeaking() throws Exception {
            MaskingSpringBridge.unbind();
            ResultSet rs = mock(ResultSet.class);
            when(rs.getString("phone")).thenReturn("13812345678");

            assertThatThrownBy(() -> phoneHandler.getNullableResult(rs, "phone"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Masking engine is not bound");
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
