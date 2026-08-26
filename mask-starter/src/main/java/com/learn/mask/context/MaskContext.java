package com.learn.mask.context;

import com.learn.mask.config.MaskingProperties;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 当前请求的脱敏角色。优先读调试头，否则从 Spring Security 鉴权信息解析。
 */
public class MaskContext {

    private static final ThreadLocal<MaskRole> HEADER_ROLE = new ThreadLocal<>();

    private final MaskingProperties properties;

    public MaskContext(MaskingProperties properties) {
        this.properties = properties;
    }

    public static void setHeaderRole(MaskRole role) {
        if (role == null) {
            HEADER_ROLE.remove();
        } else {
            HEADER_ROLE.set(role);
        }
    }

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
