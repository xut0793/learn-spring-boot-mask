package com.learn.mask.tutorial.ch13;

import com.learn.mask.tutorial.ch04.MaskStrategyRegistry;
import com.learn.mask.tutorial.ch04.SensitiveType;
import com.learn.mask.tutorial.ch05.MaskContext;
import com.learn.mask.tutorial.ch05.MaskRole;
import com.learn.mask.tutorial.ch05.RoleProperties;
import com.learn.mask.tutorial.ch06.AlreadyMaskedDetector;
import com.learn.mask.tutorial.ch06.MaskAction;
import com.learn.mask.tutorial.ch06.MaskEngine;
import com.learn.mask.tutorial.ch06.MaskRecorder;
import com.learn.mask.tutorial.ch07.MaskingProperties;
import com.learn.mask.tutorial.ch09.ChannelProperties;
import com.learn.mask.tutorial.ch09.MaskingSpringBridge;
import com.learn.mask.tutorial.ch09.Sensitive;
import com.learn.mask.tutorial.ch12.SensitiveObjectWalker;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChannelCooperationTest {

    static class UserView {
        private String name = "张三";
        @Sensitive(type = SensitiveType.PHONE)
        private String phone = "13812345678";

        public String getName() {
            return name;
        }

        public String getPhone() {
            return phone;
        }
    }

    static class RecordingRecorder implements MaskRecorder {
        final List<MaskAction> actions = new ArrayList<>();

        @Override
        public void record(String typeCode, MaskRole role, MaskAction action, Duration duration) {
            actions.add(action);
        }
    }

    @Nested
    @DisplayName("第三层防线：启动时校验通道组合")
    class StartupGuard {

        @Test
        void strictModeFailsWhenJacksonConflictsWithAop() {
            ChannelProperties channels = new ChannelProperties();
            channels.setJackson(true);
            channels.setAop(true);
            channels.setStrict(true);

            assertThatThrownBy(() -> new MaskingChannelValidator(channels).afterPropertiesSet())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("jackson");
        }

        @Test
        void strictModeFailsWhenJacksonConflictsWithMybatis() {
            ChannelProperties channels = new ChannelProperties();
            channels.setJackson(true);
            channels.setMybatis(true);
            channels.setStrict(true);

            assertThatThrownBy(() -> new MaskingChannelValidator(channels).afterPropertiesSet())
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        void nonStrictOnlyWarns() {
            ChannelProperties channels = new ChannelProperties();
            channels.setJackson(true);
            channels.setAop(true);
            channels.setStrict(false);

            new MaskingChannelValidator(channels).afterPropertiesSet();
        }

        @Test
        @DisplayName("推荐组合不冲突：Jackson + Logback，突变通道关闭")
        void defaultWebComboIsFine() {
            new MaskingChannelValidator(new ChannelProperties()).afterPropertiesSet();
        }

        @Test
        @DisplayName("无 HTTP 组合：只开 AOP，Jackson 关掉")
        void aopOnlyComboIsFine() {
            ChannelProperties channels = new ChannelProperties();
            channels.setJackson(false);
            channels.setAop(true);
            channels.setStrict(true);

            new MaskingChannelValidator(channels).afterPropertiesSet();
        }
    }

    @Nested
    @DisplayName("第一层 / 第二层防线：关掉通道 vs 幂等跳过")
    class RuntimeDefense {

        private final JsonMapper mapper = JsonMapper.builder().build();

        @AfterEach
        void tearDown() {
            MaskingSpringBridge.unbind();
        }

        @Test
        @DisplayName("AOP 改过内存后，Jackson 再序列化走 SKIP，不会打成两层星号")
        void jacksonAfterAopIsSkippedNotRemasked() {
            MaskingProperties properties = new MaskingProperties();
            ChannelProperties channels = new ChannelProperties();
            channels.setJackson(true);
            channels.setAop(true);
            RecordingRecorder recorder = new RecordingRecorder();
            MaskEngine engine = new MaskEngine(properties, MaskStrategyRegistry.withBuiltins(),
                    null, new AlreadyMaskedDetector(), recorder);
            MaskContext context = new MaskContext(new RoleProperties());
            MaskingSpringBridge.bind(engine, properties, context, channels);

            UserView view = new UserView();
            new SensitiveObjectWalker(engine, properties, context).mask(view);
            assertThat(view.getPhone()).isEqualTo("138****5678");
            assertThat(recorder.actions).contains(MaskAction.MASK);
            recorder.actions.clear();

            JsonNode json = mapper.readTree(mapper.writeValueAsString(view));

            assertThat(json.get("phone").asText()).isEqualTo("138****5678");
            assertThat(recorder.actions)
                    .as("Jackson 看到的已经是打码值，引擎记 SKIP 而不是再 MASK 一次")
                    .contains(MaskAction.SKIP_ALREADY_MASKED)
                    .doesNotContain(MaskAction.MASK);
        }

        @Test
        @DisplayName("关掉 Jackson 后，AOP 改过的对象序列化不再进引擎")
        void disablingJacksonStopsSecondPass() {
            MaskingProperties properties = new MaskingProperties();
            ChannelProperties channels = new ChannelProperties();
            channels.setJackson(false);
            channels.setAop(true);
            RecordingRecorder recorder = new RecordingRecorder();
            MaskEngine engine = new MaskEngine(properties, MaskStrategyRegistry.withBuiltins(),
                    null, new AlreadyMaskedDetector(), recorder);
            MaskContext context = new MaskContext(new RoleProperties());
            MaskingSpringBridge.bind(engine, properties, context, channels);

            UserView view = new UserView();
            new SensitiveObjectWalker(engine, properties, context).mask(view);
            recorder.actions.clear();

            JsonNode json = mapper.readTree(mapper.writeValueAsString(view));

            assertThat(json.get("phone").asText()).isEqualTo("138****5678");
            assertThat(recorder.actions)
                    .as("Jackson 通道关了，序列化器直接写字段，引擎一次都不会被叫到")
                    .isEmpty();
        }
    }
}
