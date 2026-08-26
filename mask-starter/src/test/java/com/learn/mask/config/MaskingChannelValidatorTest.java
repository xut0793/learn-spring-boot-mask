package com.learn.mask.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MaskingChannelValidatorTest {

    @Test
    void strictModeFailsWhenJacksonConflictsWithAop() {
        MaskingProperties properties = new MaskingProperties();
        properties.getChannels().setJackson(true);
        properties.getChannels().setAop(true);
        properties.getChannels().setStrict(true);
        MaskingChannelValidator validator = new MaskingChannelValidator(properties);
        assertThatThrownBy(validator::afterPropertiesSet)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("jackson");
    }

    @Test
    void nonStrictOnlyWarns() {
        MaskingProperties properties = new MaskingProperties();
        properties.getChannels().setJackson(true);
        properties.getChannels().setMybatis(true);
        properties.getChannels().setStrict(false);
        new MaskingChannelValidator(properties).afterPropertiesSet();
    }
}
