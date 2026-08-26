package com.learn.mask.tutorial.ch05;

import java.util.ArrayList;
import java.util.List;

/**
 * 角色相关配置。
 * <p>
 * 本章只需要这几项，所以先单独放一个类。第 7 章会把它并入完整的
 * {@code MaskingProperties}，并接上 {@code @ConfigurationProperties}。
 * <p>
 * 注意这里用的是可变 JavaBean 而不是 record：配置对象需要被 Spring 绑定，
 * 也需要支持热更新时原地修改，第 7 章会详细解释这个取舍。
 */
public class RoleProperties {

    /** 旁路脱敏的角色，这些角色直接看明文。 */
    private List<String> bypassRoles = new ArrayList<>(List.of("ADMIN"));

    /** 允许调用还原接口的角色。 */
    private List<String> unmaskRoles = new ArrayList<>(List.of("ADMIN", "CS"));

    private final Debug debug = new Debug();

    public List<String> getBypassRoles() {
        return bypassRoles;
    }

    public void setBypassRoles(List<String> bypassRoles) {
        this.bypassRoles = bypassRoles;
    }

    public List<String> getUnmaskRoles() {
        return unmaskRoles;
    }

    public void setUnmaskRoles(List<String> unmaskRoles) {
        this.unmaskRoles = unmaskRoles;
    }

    public Debug getDebug() {
        return debug;
    }

    /** 调试开关。生产环境 {@link #headerRoleEnabled} 必须为 false。 */
    public static class Debug {

        /**
         * 默认 false。这个默认值是安全设计的一部分：忘记配置时是安全的，
         * 只有显式打开才有风险。永远不要把危险开关的默认值设成 true。
         */
        private boolean headerRoleEnabled = false;

        private String headerName = "X-User-Role";

        public boolean isHeaderRoleEnabled() {
            return headerRoleEnabled;
        }

        public void setHeaderRoleEnabled(boolean headerRoleEnabled) {
            this.headerRoleEnabled = headerRoleEnabled;
        }

        public String getHeaderName() {
            return headerName;
        }

        public void setHeaderName(String headerName) {
            this.headerName = headerName;
        }
    }
}
