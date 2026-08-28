package com.learn.mask.jackson;

import com.learn.mask.annotation.Sensitive;
import com.learn.mask.annotation.SensitiveType;
import com.learn.mask.config.MaskingProperties;
import com.learn.mask.context.MaskContext;
import com.learn.mask.context.MaskRole;
import com.learn.mask.engine.MaskEngine;
import com.learn.mask.testsupport.MaskingFixtures;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SensitiveJacksonModuleTest {

    private MaskingProperties properties;
    private MaskContext maskContext;
    private JsonMapper mapper;

    static class UserView {
        public String name = "张三";
        @Sensitive(type = SensitiveType.PHONE)
        public String phone = "13812345678";
        @Sensitive(type = SensitiveType.EMAIL)
        public String email = "alice@example.com";
        @Sensitive(type = SensitiveType.BANK_CARD)
        public String bankCard = "6222021234567890";
    }

    static class ContactView {
        public String name = "李四";
        @Sensitive(type = SensitiveType.PHONE)
        public String phone = "13900001111";
    }

    static class NestedView {
        @Sensitive(type = SensitiveType.PHONE)
        public String phone = "13812345678";
        public ContactView contact = new ContactView();
        public List<ContactView> contacts = new ArrayList<>(List.of(new ContactView()));
    }

    static class CustomCodeView {
        @Sensitive(code = "PHONE")
        public String mobile = "13812345678";
    }

    @BeforeEach
    void setUp() {
        properties = new MaskingProperties();
        properties.getDebug().setHeaderRoleEnabled(true);
        maskContext = new MaskContext(properties);
        MaskEngine engine = MaskingFixtures.engine(properties);
        mapper = JsonMapper.builder()
                .addModule(new SensitiveJacksonModule(engine, properties, maskContext))
                .build();
    }

    @AfterEach
    void tearDown() {
        MaskContext.clearHeaderRole();
        SecurityContextHolder.clearContext();
    }

    @Nested
    @DisplayName("@Sensitive 是纯标记：靠 Module 挂序列化器，字段上不必写 @JsonSerialize")
    class AnnotationPath {

        @Test
        @DisplayName("标注字段按类型打码，未标注字段保持明文")
        void annotatedFieldsAreMaskedIndependently() {
            JsonNode json = mapper.readTree(mapper.writeValueAsString(new UserView()));

            assertThat(json.get("name").asText()).isEqualTo("张三");
            assertThat(json.get("phone").asText()).isEqualTo("138****5678");
            assertThat(json.get("email").asText()).isEqualTo("a****@example.com");
            assertThat(json.get("bankCard").asText()).isEqualTo("6222********7890");
        }

        @Test
        @DisplayName("内存对象不被改写 —— 这是出口通道的定义")
        void inMemoryObjectStaysPlain() {
            UserView view = new UserView();
            mapper.writeValueAsString(view);

            assertThat(view.phone).isEqualTo("13812345678");
            assertThat(view.email).isEqualTo("alice@example.com");
        }

        @Test
        @DisplayName("code 优先于 type，自定义编码也能命中内置策略")
        void codeOverridesType() {
            JsonNode json = mapper.readTree(mapper.writeValueAsString(new CustomCodeView()));
            assertThat(json.get("mobile").asText()).isEqualTo("138****5678");
        }

        @Test
        @DisplayName("没有 Module 时纯 @Sensitive 不生效，JSON 仍是明文")
        void annotationAloneDoesNothing() {
            JsonMapper plain = JsonMapper.builder().build();
            JsonNode json = plain.readTree(plain.writeValueAsString(new UserView()));
            assertThat(json.get("phone").asText()).isEqualTo("13812345678");
        }
    }

    @Nested
    @DisplayName("嵌套对象和 List 不需要额外代码")
    class NestedStructures {

        @Test
        @DisplayName("嵌套 Bean 和 List 里的 @Sensitive 也会被打码")
        void nestedBeanAndListAreMasked() {
            JsonNode json = mapper.readTree(mapper.writeValueAsString(new NestedView()));

            assertThat(json.get("phone").asText()).isEqualTo("138****5678");
            assertThat(json.get("contact").get("phone").asText()).isEqualTo("139****1111");
            assertThat(json.get("contacts").get(0).get("phone").asText()).isEqualTo("139****1111");
            assertThat(json.get("contact").get("name").asText()).isEqualTo("李四");
        }
    }

    @Nested
    @DisplayName("通道开关、总开关、角色旁路")
    class Gates {

        @Test
        @DisplayName("Jackson 通道关闭时写出明文")
        void jacksonChannelOffWritesPlaintext() {
            properties.getChannels().setJackson(false);
            JsonNode json = mapper.readTree(mapper.writeValueAsString(new UserView()));
            assertThat(json.get("phone").asText()).isEqualTo("13812345678");
        }

        @Test
        @DisplayName("总开关关闭时写出明文")
        void globalSwitchOffWritesPlaintext() {
            properties.setEnabled(false);
            JsonNode json = mapper.readTree(mapper.writeValueAsString(new UserView()));
            assertThat(json.get("phone").asText()).isEqualTo("13812345678");
        }

        @Test
        @DisplayName("ADMIN 旁路时写出明文")
        void adminBypassWritesPlaintext() {
            MaskContext.setHeaderRole(MaskRole.ADMIN);
            JsonNode json = mapper.readTree(mapper.writeValueAsString(new UserView()));
            assertThat(json.get("phone").asText()).isEqualTo("13812345678");
        }
    }
}
