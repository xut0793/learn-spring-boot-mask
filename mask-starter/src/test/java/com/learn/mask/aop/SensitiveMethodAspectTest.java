package com.learn.mask.aop;

import com.learn.mask.annotation.Sensitive;
import com.learn.mask.annotation.SensitiveMethod;
import com.learn.mask.annotation.SensitiveType;
import com.learn.mask.config.MaskingProperties;
import com.learn.mask.context.MaskContext;
import com.learn.mask.engine.MaskEngine;
import com.learn.mask.testsupport.MaskingFixtures;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.annotation.Annotation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SensitiveMethodAspectTest {

    private final MaskingProperties properties = new MaskingProperties();
    private final MaskContext context = new MaskContext(properties);
    private final MaskEngine engine = MaskingFixtures.engine(properties);
    private final SensitiveMethodAspect aspect = new SensitiveMethodAspect(
            engine, properties, context, new SensitiveObjectWalker(engine, properties, context));

    static class UserView {
        String name = "张三";
        @Sensitive(type = SensitiveType.PHONE)
        String phone = "13812345678";
    }

    @Test
    @DisplayName("返回 String 时按 @SensitiveMethod 的类型打码")
    void stringReturnIsMaskedByAnnotationType() throws Throwable {
        properties.getChannels().setAop(true);
        ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
        when(pjp.proceed()).thenReturn("13812345678");

        Object result = aspect.around(pjp, method(SensitiveType.PHONE, ""));

        assertThat(result).isEqualTo("138****5678");
    }

    @Test
    @DisplayName("返回对象被就地改写，明文在内存里已经没了")
    void objectReturnMutatesMemory() throws Throwable {
        properties.getChannels().setAop(true);
        UserView view = new UserView();
        ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
        when(pjp.proceed()).thenReturn(view);

        Object result = aspect.around(pjp, method(SensitiveType.CUSTOM, ""));

        assertThat(result).isSameAs(view);
        assertThat(view.phone).isEqualTo("138****5678");
    }

    @Test
    @DisplayName("AOP 通道关闭时原样返回")
    void aopChannelOffReturnsOriginal() throws Throwable {
        properties.getChannels().setAop(false);
        ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
        when(pjp.proceed()).thenReturn("13812345678");

        assertThat(aspect.around(pjp, method(SensitiveType.PHONE, ""))).isEqualTo("13812345678");
    }

    @Test
    @DisplayName("总开关关闭时原样返回")
    void globalSwitchOffReturnsOriginal() throws Throwable {
        properties.getChannels().setAop(true);
        properties.setEnabled(false);
        ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
        when(pjp.proceed()).thenReturn("13812345678");

        assertThat(aspect.around(pjp, method(SensitiveType.PHONE, ""))).isEqualTo("13812345678");
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
