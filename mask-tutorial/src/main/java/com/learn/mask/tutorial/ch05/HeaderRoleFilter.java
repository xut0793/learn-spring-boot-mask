package com.learn.mask.tutorial.ch05;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 把调试请求头里的角色放进 ThreadLocal，请求结束后清理。
 * <p>
 * {@code @Order(HIGHEST_PRECEDENCE + 20)} 让它尽量靠前执行——必须在任何可能触发
 * 脱敏的环节之前把角色准备好。留 20 的余量是为了给更基础的过滤器
 * （编码、追踪 ID、请求日志）让位。
 */
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class HeaderRoleFilter extends OncePerRequestFilter {

    private final RoleProperties properties;

    public HeaderRoleFilter(RoleProperties properties) {
        this.properties = properties;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            if (properties.getDebug().isHeaderRoleEnabled()) {
                MaskContext.setHeaderRole(MaskRole.parse(request.getHeader(properties.getDebug().getHeaderName())));
            }
            filterChain.doFilter(request, response);
        } finally {
            // 必须在 finally 里清理。Servlet 容器的线程是复用的，
            // 不清理的话下一个请求会继承上一个请求的角色 —— 见 LeakyHeaderRoleFilter。
            MaskContext.clearHeaderRole();
        }
    }
}
