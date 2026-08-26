package com.learn.mask.tutorial.ch09;

import com.learn.mask.tutorial.ch04.MaskStrategyRegistry;
import com.learn.mask.tutorial.ch04.SensitiveType;
import com.learn.mask.tutorial.ch05.MaskContext;
import com.learn.mask.tutorial.ch05.MaskRole;
import com.learn.mask.tutorial.ch05.RoleProperties;
import com.learn.mask.tutorial.ch06.AlreadyMaskedDetector;
import com.learn.mask.tutorial.ch06.MaskEngine;
import com.learn.mask.tutorial.ch06.MaskRecorder;
import com.learn.mask.tutorial.ch06.MaskResultCache;
import com.learn.mask.tutorial.ch07.MaskingProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleModule;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SensitiveValueSerializerTest {

    private final JsonMapper mapper = JsonMapper.builder().build();
    private MaskingProperties properties;
    private ChannelProperties channels;
    private MaskContext maskContext;
    private MaskEngine engine;

    @BeforeEach
    void setUp() {
        properties = new MaskingProperties();
        channels = new ChannelProperties();
        RoleProperties roles = new RoleProperties();
        roles.getDebug().setHeaderRoleEnabled(true);
        maskContext = new MaskContext(roles);
        engine = new MaskEngine(properties, MaskStrategyRegistry.withBuiltins(),
                MaskResultCache.NO_OP, new AlreadyMaskedDetector(), MaskRecorder.NO_OP);
        MaskingSpringBridge.bind(engine, properties, maskContext, channels);
    }

    @AfterEach
    void tearDown() {
        MaskContext.clearHeaderRole();
        MaskingSpringBridge.unbind();
    }

    static class UserView {
        private String name = "张三";
        @Sensitive(type = SensitiveType.PHONE)
        private String phone = "13812345678";
        @Sensitive(type = SensitiveType.EMAIL)
        private String email = "alice@example.com";
        @Sensitive(type = SensitiveType.BANK_CARD)
        private String bankCard = "6222021234567890";

        public String getName() {
            return name;
        }

        public String getPhone() {
            return phone;
        }

        public String getEmail() {
            return email;
        }

        public String getBankCard() {
            return bankCard;
        }

        public void setPhone(String phone) {
            this.phone = phone;
        }
    }

    static class ContactView {
        private String name = "李四";
        @Sensitive(type = SensitiveType.PHONE)
        private String phone = "13900001111";

        public String getName() {
            return name;
        }

        public String getPhone() {
            return phone;
        }
    }

    static class NestedView {
        @Sensitive(type = SensitiveType.PHONE)
        private String phone = "13812345678";
        private ContactView contact = new ContactView();
        private List<ContactView> contacts = new ArrayList<>(List.of(new ContactView()));

        public String getPhone() {
            return phone;
        }

        public ContactView getContact() {
            return contact;
        }

        public List<ContactView> getContacts() {
            return contacts;
        }
    }

    static class CustomCodeView {
        @Sensitive(code = "PHONE")
        private String mobile = "13812345678";

        public String getMobile() {
            return mobile;
        }
    }

    static class BareView {
        @BareSensitive(type = SensitiveType.PHONE)
        private String phone = "13812345678";

        public String getPhone() {
            return phone;
        }
    }

    /** 没有字段注解。类型由全局序列化器按属性名决定，用来暴露「共享可变实例」的 bug。 */
    static class NamedFieldsView {
        private String name = "张三";
        private String phone = "13812345678";
        private String email = "alice@example.com";

        public String getName() {
            return name;
        }

        public String getPhone() {
            return phone;
        }

        public String getEmail() {
            return email;
        }
    }

    @Nested
    @DisplayName("注解字段按类型打码，未标注字段保持明文")
    class AnnotationPath {

        @Test
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

            assertThat(view.getPhone()).isEqualTo("13812345678");
            assertThat(view.getEmail()).isEqualTo("alice@example.com");
        }

        @Test
        @DisplayName("code 优先于 type，自定义编码也能命中内置策略")
        void codeOverridesType() {
            JsonNode json = mapper.readTree(mapper.writeValueAsString(new CustomCodeView()));
            assertThat(json.get("mobile").asText()).isEqualTo("138****5678");
        }
    }

    @Nested
    @DisplayName("嵌套对象和 List 不需要额外代码")
    class NestedStructures {

        @Test
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
        void jacksonChannelOffWritesPlaintext() {
            channels.setJackson(false);
            JsonNode json = mapper.readTree(mapper.writeValueAsString(new UserView()));
            assertThat(json.get("phone").asText()).isEqualTo("13812345678");
        }

        @Test
        void globalSwitchOffWritesPlaintext() {
            properties.setEnabled(false);
            JsonNode json = mapper.readTree(mapper.writeValueAsString(new UserView()));
            assertThat(json.get("phone").asText()).isEqualTo("13812345678");
        }

        @Test
        void adminBypassWritesPlaintext() {
            MaskContext.setHeaderRole(MaskRole.ADMIN);
            JsonNode json = mapper.readTree(mapper.writeValueAsString(new UserView()));
            assertThat(json.get("phone").asText()).isEqualTo("13812345678");
        }
    }

    @Nested
    @DisplayName("静态桥：Jackson 走无参构造")
    class Bridge {

        @Test
        @DisplayName("没 bind 时引擎是 null，序列化器 fail-open 写出明文")
        void unboundBridgeLeaksPlaintext() {
            MaskingSpringBridge.unbind();
            JsonNode json = mapper.readTree(mapper.writeValueAsString(new UserView()));
            assertThat(json.get("phone").asText())
                    .as("启动早期或测试漏了 bind，明文会从 JSON 出口漏出去")
                    .isEqualTo("13812345678");
        }
    }

    @Nested
    @DisplayName("组合注解必须带 JacksonAnnotationsInside")
    class MetaAnnotation {

        @Test
        @DisplayName("漏掉 JacksonAnnotationsInside 时 @JsonSerialize 不生效")
        void withoutJacksonAnnotationsInsideNothingHappens() {
            JsonNode json = mapper.readTree(mapper.writeValueAsString(new BareView()));
            assertThat(json.get("phone").asText()).isEqualTo("13812345678");
        }
    }

    @Nested
    @DisplayName("createContextual 必须返回新实例")
    class ContextualIdentity {

        @Test
        @DisplayName("正确实现：同一 Bean 上 phone 和 email 各用各的规则")
        void newInstanceKeepsRulesIndependent() {
            JsonNode json = mapper.readTree(mapper.writeValueAsString(new UserView()));
            assertThat(json.get("phone").asText()).isEqualTo("138****5678");
            assertThat(json.get("email").asText()).isEqualTo("a****@example.com");
        }

        @Test
        @DisplayName("错误实现：全局共享一个可变实例时，后处理的字段覆盖先处理的")
        void mutatingThisMixesRulesWhenSharedGlobally() {
            SimpleModule module = new SimpleModule();
            module.addSerializer(String.class, new MutatingSensitiveValueSerializer());
            JsonMapper leakyMapper = JsonMapper.builder().addModule(module).build();

            JsonNode json = leakyMapper.readTree(leakyMapper.writeValueAsString(new NamedFieldsView()));
            boolean phoneOk = "138****5678".equals(json.get("phone").asText());
            boolean emailOk = "a****@example.com".equals(json.get("email").asText());

            assertThat(phoneOk && emailOk)
                    .as("共享可变实例后两个字段不可能同时正确。实际 JSON: " + json)
                    .isFalse();
        }
    }
}
