package com.learn.mask.tutorial.ch07;

import com.learn.mask.tutorial.ch04.SensitiveType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证配置真的能从 YAML/properties 绑定进来。
 * <p>
 * 用 {@link ApplicationContextRunner} 而不是 {@code @SpringBootTest}：
 * 它只启动一个最小容器，一个用例几十毫秒，而且每个用例可以有完全不同的配置，
 * 不需要为每种组合建一个 properties 文件。
 */
class MaskingPropertiesBindingTest {

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(MaskingProperties.class)
    static class TestConfig {
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of())
            .withUserConfiguration(TestConfig.class);

    @Test
    @DisplayName("不写任何配置时用默认值")
    void defaultsApplyWhenNothingConfigured() {
        runner.run(context -> {
            MaskingProperties properties = context.getBean(MaskingProperties.class);
            assertThat(properties.isEnabled()).isTrue();
            assertThat(properties.ruleOf(SensitiveType.PHONE).keepPrefix()).isEqualTo(3);
        });
    }

    @Test
    @DisplayName("宽松绑定：YAML 的 keep-prefix 映射到 keepPrefix")
    void relaxedBindingWorks() {
        runner.withPropertyValues("masking.rules.phone.keep-prefix=0")
                .run(context -> {
                    MaskingProperties properties = context.getBean(MaskingProperties.class);
                    assertThat(properties.ruleOf(SensitiveType.PHONE).keepPrefix()).isZero();
                });
    }

    @Test
    @DisplayName("只覆盖一个字段时，同一条规则的其他字段保留默认值")
    void partialOverrideKeepsOtherFields() {
        runner.withPropertyValues("masking.rules.phone.keep-prefix=0")
                .run(context -> {
                    MaskingProperties properties = context.getBean(MaskingProperties.class);
                    assertThat(properties.ruleOf(SensitiveType.PHONE).keepSuffix())
                            .as("没写 keep-suffix，应保留默认的 4")
                            .isEqualTo(4);
                    assertThat(properties.ruleOf(SensitiveType.PHONE).maskChar()).isEqualTo('*');
                });
    }

    @Test
    @DisplayName("afterPropertiesSet 会在绑定后重建快照 —— 否则配置改了引擎看不见")
    void snapshotIsRebuiltAfterBinding() {
        runner.withPropertyValues(
                        "masking.rules.phone.keep-prefix=2",
                        "masking.rules.phone.keep-suffix=2",
                        "masking.rules.phone.mask-char=#")
                .run(context -> {
                    MaskingProperties properties = context.getBean(MaskingProperties.class);
                    assertThat(properties.ruleOf(SensitiveType.PHONE).keepPrefix()).isEqualTo(2);
                    assertThat(properties.ruleOf(SensitiveType.PHONE).keepSuffix()).isEqualTo(2);
                    assertThat(properties.ruleOf(SensitiveType.PHONE).maskChar()).isEqualTo('#');
                });
    }

    @Test
    @DisplayName("extras 里的业务自定义类型能绑定")
    void extrasBinding() {
        runner.withPropertyValues(
                        "masking.rules.extras.EXPRESS.keep-prefix=2",
                        "masking.rules.extras.EXPRESS.keep-suffix=4")
                .run(context -> {
                    MaskingProperties properties = context.getBean(MaskingProperties.class);
                    assertThat(properties.ruleOf("EXPRESS").keepPrefix()).isEqualTo(2);
                    assertThat(properties.ruleOf("EXPRESS").keepSuffix()).isEqualTo(4);
                });
    }

    @Test
    @DisplayName("总开关可配置")
    void enabledIsBindable() {
        runner.withPropertyValues("masking.enabled=false")
                .run(context -> assertThat(context.getBean(MaskingProperties.class).isEnabled()).isFalse());
    }

    @Test
    @DisplayName("mapKeys 可以整体覆盖")
    void mapKeysOverride() {
        runner.withPropertyValues("masking.map-keys.mobileNo=PHONE")
                .run(context -> {
                    MaskingProperties properties = context.getBean(MaskingProperties.class);
                    assertThat(properties.typeCodeOf("mobileNo")).isEqualTo("PHONE");
                });
    }
}
