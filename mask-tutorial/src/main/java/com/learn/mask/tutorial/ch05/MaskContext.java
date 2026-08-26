package com.learn.mask.tutorial.ch05;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

/**
 * 当前请求的脱敏角色，以及基于角色的两个判定。
 * <p>
 * 这个类是所有通道共用的「我是谁」入口。四个通道都不自己判断角色，
 * 统一问它，这样角色语义在全项目只有一份实现。
 */
public class MaskContext {

    /**
     * 调试头解析出的角色。
     * <p>
     * 用 ThreadLocal 是因为它要跨越「过滤器 → 控制器 → 服务 → 序列化器」整条调用链，
     * 而这条链上大部分环节拿不到 HttpServletRequest（比如 Jackson 序列化器、
     * Logback 转换器）。ThreadLocal 是把请求级数据透传给这些环节的常规手段。
     * <p>
     * 代价是必须手动清理，见 {@link HeaderRoleFilter}。
     */
    private static final ThreadLocal<MaskRole> HEADER_ROLE = new ThreadLocal<>();

    private final RoleProperties properties;

    public MaskContext(RoleProperties properties) {
        this.properties = properties;
    }

    public static void setHeaderRole(MaskRole role) {
        if (role == null) {
            // 传 null 时移除而不是 set(null)：让 ThreadLocal 条目真正释放，
            // 也让 get() 的语义保持「有值 / 无值」两态，不需要区分 null 值。
            HEADER_ROLE.remove();
        } else {
            HEADER_ROLE.set(role);
        }
    }

    public static void clearHeaderRole() {
        HEADER_ROLE.remove();
    }

    /**
     * 当前生效角色。
     * <p>
     * 优先级：调试头 &gt; Spring Security。
     * <p>
     * 注意开关判断在前：{@code headerRoleEnabled} 为 false 时**根本不看** ThreadLocal，
     * 所以即使有人绕过过滤器往里塞了值，关掉开关也能兜住。
     */
    public MaskRole current() {
        if (properties.getDebug().isHeaderRoleEnabled()) {
            MaskRole headerRole = HEADER_ROLE.get();
            if (headerRole != null) {
                return headerRole;
            }
        }
        return fromSecurity();
    }

    /** 是否旁路脱敏（默认只有 ADMIN）。 */
    public boolean shouldBypass() {
        return matches(properties.getBypassRoles());
    }

    /** 是否允许调用还原接口（默认 ADMIN 和 CS）。 */
    public boolean canUnmask() {
        return matches(properties.getUnmaskRoles());
    }

    /**
     * 用 {@link MaskRole#parse} 比对而不是直接比字符串。
     * <p>
     * 这样配置里写 {@code ADMIN}、{@code admin}、{@code ROLE_ADMIN} 都能命中，
     * 运维不用记住准确写法。
     */
    private boolean matches(List<String> roles) {
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
            // 未认证时返回 USER：最严格的角色，也就是最安全的默认值
            if (authentication == null || !authentication.isAuthenticated()) {
                return MaskRole.USER;
            }
            // 一个用户可能有多个权限。跳过 USER 去找更「特殊」的角色，
            // 否则 [ROLE_USER, ROLE_ADMIN] 这种组合会因为顺序问题解析成 USER。
            for (GrantedAuthority authority : authentication.getAuthorities()) {
                MaskRole parsed = MaskRole.parse(authority.getAuthority());
                if (parsed != null && parsed != MaskRole.USER) {
                    return parsed;
                }
            }
            // 兜底：有些项目不用 GrantedAuthority，直接把角色当用户名
            MaskRole fromName = MaskRole.parse(authentication.getName());
            return fromName != null ? fromName : MaskRole.USER;
        } catch (NoClassDefFoundError ex) {
            // Spring Security 不在 classpath 上时，上面的类引用会在这里失败。
            // 这个 catch 让 security 成为真正可选的依赖：没有它整套脱敏依然可用，
            // 只是所有请求都按 USER 处理（依然是安全的默认值）。
            return MaskRole.USER;
        }
    }
}
