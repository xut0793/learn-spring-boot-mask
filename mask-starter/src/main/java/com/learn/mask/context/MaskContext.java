package com.learn.mask.context;

import com.learn.mask.config.MaskingProperties;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 当前请求的脱敏角色。优先读调试头，否则从 Spring Security 鉴权信息解析。
 */
public class MaskContext {

    /** 调试头 {@link HeaderRoleFilter} 写入，优先于 Security 角色。 */
    private static final ThreadLocal<MaskRole> HEADER_ROLE = new ThreadLocal<>();

    private final MaskingProperties properties;

    /** 持有配置以读取 bypass / unmask 角色列表与调试开关。 */
    public MaskContext(MaskingProperties properties) {
        this.properties = properties;
    }

    /** 供过滤器或测试设置当前线程的调试角色。 */
    public static void setHeaderRole(MaskRole role) {
        if (role == null) {
            HEADER_ROLE.remove();
        } else {
            HEADER_ROLE.set(role);
        }
    }

    /** 请求结束必须清理，避免线程池复用泄漏角色。 */
    public static void clearHeaderRole() {
        HEADER_ROLE.remove();
    }

    /** 当前生效角色：调试头优先，否则取 Security 鉴权。 */
    public MaskRole current() {
        if (properties.getDebug().isHeaderRoleEnabled()) {
            MaskRole headerRole = HEADER_ROLE.get();
            if (headerRole != null) {
                return headerRole;
            }
        }
        return fromSecurity();
    }

    /** 是否跳过脱敏（默认 ADMIN）。 */
    public boolean shouldBypass() {
        return matches(properties.getBypassRoles());
    }

    /** 是否允许调用还原接口（默认 ADMIN、CS）。 */
    public boolean canUnmask() {
        return matches(properties.getUnmaskRoles());
    }

    private boolean matches(java.util.List<String> roles) {
        MaskRole current = current();
        if (current == null || roles == null) {
            return false;
        }
        for (String role : roles) {
            if (current == MaskRole.parse(role)) {
                return true;
            }
        }
        return false;
    }

    private MaskRole fromSecurity() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null || !authentication.isAuthenticated()) {
                return MaskRole.USER;
            }
            for (GrantedAuthority authority : authentication.getAuthorities()) {
                MaskRole parsed = MaskRole.parse(authority.getAuthority());
                if (parsed != null && parsed != MaskRole.USER) {
                    return parsed;
                }
            }
            MaskRole fromName = MaskRole.parse(authentication.getName());
            return fromName != null ? fromName : MaskRole.USER;
        } catch (NoClassDefFoundError ex) {
            return MaskRole.USER;
        }
    }
}
