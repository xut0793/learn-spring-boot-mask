package com.learn.mask.context;

import com.learn.mask.config.MaskingProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 调试用角色头过滤器。开启 {@code masking.debug.header-role-enabled} 后，用 {@code X-User-Role} 覆盖当前角色。
 */
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class HeaderRoleFilter extends OncePerRequestFilter {

    private final MaskingProperties properties;

    public HeaderRoleFilter(MaskingProperties properties) {
        this.properties = properties;
    }

    /** 解析 {@code masking.debug.header-name}，写入 {@link MaskContext} 并在 finally 中清除。 */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            if (properties.getDebug().isHeaderRoleEnabled()) {
                MaskContext.setHeaderRole(MaskRole.parse(request.getHeader(properties.getDebug().getHeaderName())));
            }
            filterChain.doFilter(request, response);
        } finally {
            MaskContext.clearHeaderRole();
        }
    }
}
