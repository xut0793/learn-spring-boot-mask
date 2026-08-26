package com.learn.mask.tutorial.ch12;

import com.learn.mask.tutorial.ch04.MaskStrategyRegistry;
import com.learn.mask.tutorial.ch04.SensitiveType;
import com.learn.mask.tutorial.ch05.MaskContext;
import com.learn.mask.tutorial.ch05.RoleProperties;
import com.learn.mask.tutorial.ch06.AlreadyMaskedDetector;
import com.learn.mask.tutorial.ch06.MaskEngine;
import com.learn.mask.tutorial.ch06.MaskRecorder;
import com.learn.mask.tutorial.ch06.MaskResultCache;
import com.learn.mask.tutorial.ch07.MaskingProperties;
import com.learn.mask.tutorial.ch09.ChannelProperties;
import com.learn.mask.tutorial.ch09.MaskingSpringBridge;
import com.learn.mask.tutorial.ch09.Sensitive;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.lang.annotation.Annotation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SensitiveMethodAspectTest {

    private MaskingProperties properties;
    private ChannelProperties channels;
    private SensitiveMethodAspect aspect;
    private final JsonMapper mapper = JsonMapper.builder().build();

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

    @BeforeEach
    void setUp() {
        properties = new MaskingProperties();
        channels = new ChannelProperties();
        channels.setAop(true);
        MaskContext context = new MaskContext(new RoleProperties());
        MaskEngine engine = new MaskEngine(properties, MaskStrategyRegistry.withBuiltins(),
                MaskResultCache.NO_OP, new AlreadyMaskedDetector(), MaskRecorder.NO_OP);
        SensitiveObjectWalker walker = new SensitiveObjectWalker(engine, properties, context);
        aspect = new SensitiveMethodAspect(engine, properties, channels, context, walker);
        MaskingSpringBridge.bind(engine, properties, context, channels);
    }

    @AfterEach
    void tearDown() {
        MaskingSpringBridge.unbind();
    }

    @Test
    void stringReturnIsMaskedByAnnotationType() throws Throwable {
        ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
        when(pjp.proceed()).thenReturn("13812345678");

        Object result = aspect.around(pjp, method(SensitiveType.PHONE, ""));

        assertThat(result).isEqualTo("138****5678");
    }

    @Test
    @DisplayName("返回对象被就地改写，明文在内存里已经没了")
    void objectReturnMutatesMemory() throws Throwable {
        UserView view = new UserView();
        ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
        when(pjp.proceed()).thenReturn(view);

        Object result = aspect.around(pjp, method(SensitiveType.CUSTOM, ""));

        assertThat(result).isSameAs(view);
        assertThat(view.getPhone()).isEqualTo("138****5678");
    }

    @Test
    void aopChannelOffReturnsOriginal() throws Throwable {
        channels.setAop(false);
        ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
        when(pjp.proceed()).thenReturn("13812345678");

        assertThat(aspect.around(pjp, method(SensitiveType.PHONE, ""))).isEqualTo("13812345678");
    }

    @Test
    @DisplayName("AOP 改过的对象再走 Jackson：JSON 仍是一层星号，但内存已经是打码值")
    void jacksonAfterAopStillOneLayerOfStars() throws Throwable {
        UserView view = new UserView();
        ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
        when(pjp.proceed()).thenReturn(view);
        aspect.around(pjp, method(SensitiveType.CUSTOM, ""));

        JsonNode json = mapper.readTree(mapper.writeValueAsString(view));

        assertThat(json.get("phone").asText()).isEqualTo("138****5678");
        assertThat(view.getPhone())
                .as("和纯 Jackson 通道不同：内存里也是打码值，明文回不来了")
                .isEqualTo("138****5678");
    }

    private static SensitiveMethod method(SensitiveType type, String code) {
        return new SensitiveMethod() {
            @Override
            public Class<? extends Annotation> annotationType() {
                return SensitiveMethod.class;
            }

            @Override
            public SensitiveType type() {
                return type;
            }

            @Override
            public String code() {
                return code;
            }
        };
    }
}
