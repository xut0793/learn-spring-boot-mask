package com.learn.mask.context;

import com.learn.mask.config.MaskingProperties;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HeaderRoleFilterTest {

    private final MaskingProperties properties = new MaskingProperties();
    private final MaskContext context = new MaskContext(properties);

    @AfterEach
    void cleanUp() {
        MaskContext.clearHeaderRole();
        SecurityContextHolder.clearContext();
    }

    /** 跑一次请求，返回过滤器链内部观察到的角色。 */
    private MaskRole runRequest(Filter filter, String headerValue) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/users/1");
        if (headerValue != null) {
            request.addHeader("X-User-Role", headerValue);
        }
        List<MaskRole> observed = new ArrayList<>();
        FilterChain chain = (req, res) -> observed.add(context.current());
        filter.doFilter(request, new MockHttpServletResponse(), chain);
        return observed.getFirst();
    }

    @Test
    @DisplayName("开关打开时请求头生效")
    void headerTakesEffect() throws Exception {
        properties.getDebug().setHeaderRoleEnabled(true);
        assertThat(runRequest(new HeaderRoleFilter(properties), "ADMIN")).isEqualTo(MaskRole.ADMIN);
    }

    @Test
    @DisplayName("开关关闭时请求头被忽略")
    void headerIgnoredWhenSwitchOff() throws Exception {
        properties.getDebug().setHeaderRoleEnabled(false);
        assertThat(runRequest(new HeaderRoleFilter(properties), "ADMIN")).isEqualTo(MaskRole.USER);
    }

    @Test
    @DisplayName("自定义头名生效")
    void customHeaderName() throws Exception {
        properties.getDebug().setHeaderRoleEnabled(true);
        properties.getDebug().setHeaderName("X-Debug-Role");

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/users/1");
        request.addHeader("X-Debug-Role", "CS");
        List<MaskRole> observed = new ArrayList<>();
        new HeaderRoleFilter(properties).doFilter(request, new MockHttpServletResponse(),
                (req, res) -> observed.add(context.current()));

        assertThat(observed).containsExactly(MaskRole.CS);
    }

    @Test
    @DisplayName("请求结束后 ThreadLocal 已清理")
    void threadLocalClearedAfterRequest() throws Exception {
        properties.getDebug().setHeaderRoleEnabled(true);
        runRequest(new HeaderRoleFilter(properties), "ADMIN");

        assertThat(context.current()).isEqualTo(MaskRole.USER);
    }

    @Test
    @DisplayName("链上抛异常时也要清理 —— finally 的价值")
    void threadLocalClearedEvenWhenChainThrows() {
        properties.getDebug().setHeaderRoleEnabled(true);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/users/1");
        request.addHeader("X-User-Role", "ADMIN");

        assertThatThrownBy(() -> new HeaderRoleFilter(properties)
                .doFilter(request, new MockHttpServletResponse(), (req, res) -> {
                    throw new IllegalStateException("boom");
                }))
                .isInstanceOf(IllegalStateException.class);

        assertThat(context.current()).isEqualTo(MaskRole.USER);
    }

    /**
     * Servlet 容器会复用同一个工作线程。第二个请求不带头，绝不能继承上一个请求的 ADMIN。
     */
    @Test
    @DisplayName("同线程复用时不串角色")
    void doesNotLeakAcrossRequestsOnSameThread() throws Exception {
        properties.getDebug().setHeaderRoleEnabled(true);
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            Filter filter = new HeaderRoleFilter(properties);
            MaskRole first = pool.submit(() -> runRequest(filter, "ADMIN")).get(5, TimeUnit.SECONDS);
            MaskRole second = pool.submit(() -> runRequest(filter, null)).get(5, TimeUnit.SECONDS);

            assertThat(first).isEqualTo(MaskRole.ADMIN);
            assertThat(second).isEqualTo(MaskRole.USER);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("setHeaderRole(null) 执行 remove，本身就是一道防线")
    void settingNullRemovesInsteadOfStoringNull() {
        properties.getDebug().setHeaderRoleEnabled(true);
        MaskContext.setHeaderRole(MaskRole.ADMIN);
        assertThat(context.current()).isEqualTo(MaskRole.ADMIN);

        MaskContext.setHeaderRole(MaskRole.parse(null));
        assertThat(context.current()).isEqualTo(MaskRole.USER);
    }
}
