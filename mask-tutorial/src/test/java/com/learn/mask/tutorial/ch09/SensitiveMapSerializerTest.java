package com.learn.mask.tutorial.ch09;

import com.learn.mask.tutorial.ch04.MaskStrategyRegistry;
import com.learn.mask.tutorial.ch05.MaskContext;
import com.learn.mask.tutorial.ch05.RoleProperties;
import com.learn.mask.tutorial.ch06.AlreadyMaskedDetector;
import com.learn.mask.tutorial.ch06.MaskEngine;
import com.learn.mask.tutorial.ch06.MaskRecorder;
import com.learn.mask.tutorial.ch06.MaskResultCache;
import com.learn.mask.tutorial.ch07.MaskingProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SensitiveMapSerializerTest {

    private final JsonMapper mapper = JsonMapper.builder().build();
    private MaskingProperties properties;
    private ChannelProperties channels;

    @BeforeEach
    void setUp() {
        properties = new MaskingProperties();
        channels = new ChannelProperties();
        MaskEngine engine = new MaskEngine(properties, MaskStrategyRegistry.withBuiltins(),
                MaskResultCache.NO_OP, new AlreadyMaskedDetector(), MaskRecorder.NO_OP);
        MaskingSpringBridge.bind(engine, properties, new MaskContext(new RoleProperties()), channels);
    }

    @AfterEach
    void tearDown() {
        MaskingSpringBridge.unbind();
    }

    @Test
    @DisplayName("按 map-keys 的字段名打码，认不出的键保持明文")
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
    @DisplayName("嵌套 Map 和 List 里的同名键也会被打码")
    void recursesIntoNestedMapAndList() {
        Map<String, Object> nested = new LinkedHashMap<>();
        nested.put("phone", "13900001111");
        nested.put("title", "紧急联系人");

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("phone", "13812345678");
        data.put("contact", nested);
        data.put("phones", List.of("13600000001", "13600000002"));

        JsonNode json = mapper.readTree(mapper.writeValueAsString(new SensitiveMapView(data)));

        assertThat(json.get("phone").asText()).isEqualTo("138****5678");
        assertThat(json.get("contact").get("phone").asText()).isEqualTo("139****1111");
        assertThat(json.get("contact").get("title").asText()).isEqualTo("紧急联系人");
        assertThat(json.get("phones").get(0).asText())
                .as("列表里的字符串用父键名去查 map-keys。键是 phones 不是 phone，所以不打码")
                .isEqualTo("13600000001");
        assertThat(json.get("phones").get(1).asText()).isEqualTo("13600000002");
    }

    @Test
    @DisplayName("字符串列表要打码，父键名必须出现在 map-keys 里")
    void stringListUsesParentKey() {
        properties.getMapKeys().put("phones", "PHONE");
        Map<String, Object> data = Map.of("phones", List.of("13600000001", "13600000002"));

        JsonNode json = mapper.readTree(mapper.writeValueAsString(new SensitiveMapView(data)));

        assertThat(json.get("phones").get(0).asText()).isEqualTo("136****0001");
        assertThat(json.get("phones").get(1).asText()).isEqualTo("136****0002");
    }

    @Test
    @DisplayName("裸 Map 不会走脱敏 —— 这就是为什么需要 SensitiveMapView")
    void rawMapIsNotMasked() {
        Map<String, Object> data = Map.of("phone", "13812345678");
        JsonNode json = mapper.readTree(mapper.writeValueAsString(data));
        assertThat(json.get("phone").asText()).isEqualTo("13812345678");
    }

    @Test
    void jacksonChannelOffWritesPlaintext() {
        channels.setJackson(false);
        JsonNode json = mapper.readTree(mapper.writeValueAsString(
                new SensitiveMapView(Map.of("phone", "13812345678"))));
        assertThat(json.get("phone").asText()).isEqualTo("13812345678");
    }

    @Test
    @DisplayName("序列化 Map 视图不会改入参 —— 出口通道")
    void doesNotMutateInputMap() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("phone", "13812345678");
        mapper.writeValueAsString(new SensitiveMapView(data));
        assertThat(data.get("phone")).isEqualTo("13812345678");
    }
}
