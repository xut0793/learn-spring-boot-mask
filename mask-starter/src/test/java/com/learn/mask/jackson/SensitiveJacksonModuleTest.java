package com.learn.mask.jackson;

import com.learn.mask.annotation.Sensitive;
import com.learn.mask.annotation.SensitiveType;
import com.learn.mask.config.MaskingProperties;
import com.learn.mask.context.MaskContext;
import com.learn.mask.engine.AlreadyMaskedDetector;
import com.learn.mask.engine.MaskEngine;
import com.learn.mask.strategy.MaskStrategyRegistry;
import com.learn.mask.strategy.PhoneMaskStrategy;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SensitiveJacksonModuleTest {

    public static class UserView {
        @Sensitive(type = SensitiveType.PHONE)
        public String phone = "13812345678";
        public String name = "zhangsan";
    }

    @Test
    void moduleMasksAnnotatedFieldWithoutJsonSerialize() {
        MaskingProperties properties = new MaskingProperties();
        MaskEngine engine = new MaskEngine(
                properties,
                new MaskStrategyRegistry(List.of(new PhoneMaskStrategy())),
                null,
                new AlreadyMaskedDetector(),
                null
        );
        JsonMapper mapper = JsonMapper.builder()
                .addModule(new SensitiveJacksonModule(engine, properties, new MaskContext(properties)))
                .build();

        String json = mapper.writeValueAsString(new UserView());
        UserView inMemory = new UserView();
        mapper.writeValueAsString(inMemory);

        assertThat(json).contains("138****5678");
        assertThat(json).doesNotContain("13812345678");
        assertThat(json).contains("zhangsan");
        assertThat(inMemory.phone).isEqualTo("13812345678");
    }
}
