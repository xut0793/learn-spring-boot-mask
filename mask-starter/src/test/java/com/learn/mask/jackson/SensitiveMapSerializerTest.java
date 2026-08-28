package com.learn.mask.jackson;

import com.learn.mask.config.MaskingProperties;
import com.learn.mask.context.MaskContext;
import com.learn.mask.engine.MaskEngine;
import com.learn.mask.support.MaskingSpringBridge;
import com.learn.mask.testsupport.MaskingFixtures;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SensitiveMapSerializerTest {

    private final JsonMapper mapper = JsonMapper.builder().build();
    private MaskingProperties properties;

    @BeforeEach
    void setUp() {
        properties = new MaskingProperties();
        MaskEngine engine = MaskingFixtures.engine(properties);
        MaskingSpringBridge.bind(engine, properties, new MaskContext(properties));
    }

    @AfterEach
    void tearDown() {
        MaskingSpringBridge.unbind();
    }

    @Nested
    @DisplayName("按 map-keys / extra-map-keys 打码")
    class MappedKeys {

        @Test
        @DisplayName("认得出的键打码，认不出的键保持明文")
        void masksMappedKeysOnly() {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("name", "张三");
            data.put("phone", "13812345678");
            data.put("nickname", "小张");

            JsonNode json = mapper.readTree(mapper.writeValueAsString(new SensitiveMapView(data)));

            assertThat(json.get("name").asText()).isEqualTo("张三");
            assertThat(json.get("phone").asText()).isEqualTo("138****5678");
            assertThat(json.get("nickname").asText()).isEqualTo("小张");
        }

        @Test
        @DisplayName("嵌套 Map 里的同名键也会被打码")
        void recursesIntoNestedMap() {
            Map<String, Object> nested = new LinkedHashMap<>();
            nested.put("phone", "13900001111");
            nested.put("title", "紧急联系人");

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("phone", "13812345678");
            data.put("contact", nested);

            JsonNode json = mapper.readTree(mapper.writeValueAsString(new SensitiveMapView(data)));

            assertThat(json.get("phone").asText()).isEqualTo("138****5678");
            assertThat(json.get("contact").get("phone").asText()).isEqualTo("139****1111");
            assertThat(json.get("contact").get("title").asText()).isEqualTo("紧急联系人");
        }

        @Test
        @DisplayName("字符串列表用父键名去查 map-keys，键是 phones 时默认不打码")
        void stringListUsesParentKey() {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("phones", List.of("13600000001", "13600000002"));

            JsonNode json = mapper.readTree(mapper.writeValueAsString(new SensitiveMapView(data)));

            assertThat(json.get("phones").get(0).asText()).isEqualTo("13600000001");
        }

        @Test
        @DisplayName("把父键写进 extra-map-keys 后，列表项也会打码")
        void extraMapKeysCoverListParent() {
            properties.getExtraMapKeys().put("phones", "PHONE");
            Map<String, Object> data = Map.of("phones", List.of("13600000001", "13600000002"));

            JsonNode json = mapper.readTree(mapper.writeValueAsString(new SensitiveMapView(data)));

            assertThat(json.get("phones").get(0).asText()).isEqualTo("136****0001");
            assertThat(json.get("phones").get(1).asText()).isEqualTo("136****0002");
        }

        @Test
        @DisplayName("裸 Map 不会走脱敏 —— 这就是为什么需要 SensitiveMapView")
        void rawMapIsNotMasked() {
            JsonNode json = mapper.readTree(mapper.writeValueAsString(Map.of("phone", "13812345678")));
            assertThat(json.get("phone").asText()).isEqualTo("13812345678");
        }
    }

    @Nested
    @DisplayName("出口通道：不改入参；未 bind 时 fail-closed")
    class Safety {

        @Test
        @DisplayName("序列化 Map 视图不会改入参")
        void doesNotMutateInputMap() {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("phone", "13812345678");
            mapper.writeValueAsString(new SensitiveMapView(data));
            assertThat(data.get("phone")).isEqualTo("13812345678");
        }

        @Test
        @DisplayName("Jackson 通道关闭时写出明文")
        void jacksonChannelOffWritesPlaintext() {
            properties.getChannels().setJackson(false);
            JsonNode json = mapper.readTree(mapper.writeValueAsString(
                    new SensitiveMapView(Map.of("phone", "13812345678"))));
            assertThat(json.get("phone").asText()).isEqualTo("13812345678");
        }

        @Test
        @DisplayName("静态桥未 bind 时序列化失败，不会写出带明文的 JSON")
        void unboundThrowsInsteadOfLeaking() {
            MaskingSpringBridge.unbind();
            // SensitiveMapView 从桥取出的 properties 为 null，会在 typeCodeOf 处 NPE；
            // 引擎已注入但未 bind 时才走 IllegalStateException。两种路径都不能写出明文。
            assertThatThrownBy(() -> mapper.writeValueAsString(
                    new SensitiveMapView(Map.of("phone", "13812345678"))))
                    .isInstanceOf(NullPointerException.class);
        }

    }
}
