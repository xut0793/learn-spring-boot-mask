package com.learn.mask.tutorial.ch05;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 反面教材：会把角色泄漏到下一个请求的过滤器。
 * <p>
 * 和 {@link HeaderRoleFilter} 有两处不同，**必须两处同时出错才会泄漏**：
 * <ol>
 *   <li>没有 {@code finally} 里的 {@code clearHeaderRole()}</li>
 *   <li>用了「头存在才 set」的写法（下面的 {@code if (raw != null)}）</li>
 * </ol>
 * 只犯第 1 个错不会泄漏，因为 {@link MaskContext#setHeaderRole(MaskRole)} 收到 null 时
 * 会执行 {@code remove()}，下一个不带头的请求会把上一个请求的值顶掉。
 * 这是 {@code MaskContext} 里一个不显眼但很关键的设计。
 * <p>
 * 而「头存在才 set」是一种非常自然的写法——很多人会觉得「没有头就别动 ThreadLocal」
 * 更干净。恰恰是这个直觉配上缺失的 clear，构成了一次真实的越权。
 * <p>
 * 这个类只用于测试，不要在任何地方注册它。
 */
public class LeakyHeaderRoleFilter extends OncePerRequestFilter {

    private final RoleProperties properties;

    public LeakyHeaderRoleFilter(RoleProperties properties) {
        this.properties = properties;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (properties.getDebug().isHeaderRoleEnabled()) {
            String raw = request.getHeader(properties.getDebug().getHeaderName());
            // 错误 1：只在头存在时才写 ThreadLocal，于是不带头的请求不会覆盖旧值
            if (raw != null) {
                MaskContext.setHeaderRole(MaskRole.parse(raw));
            }
        }
        filterChain.doFilter(request, response);
        // 错误 2：少了 finally { MaskContext.clearHeaderRole(); }
    }
}
