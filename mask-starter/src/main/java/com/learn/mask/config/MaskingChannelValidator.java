package com.learn.mask.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;

/**
 * 启动时检查通道组合：Jackson（出口脱敏）与 AOP/MyBatis（改写内存）同时开启会重复处理。
 * {@code strict=true} 时直接失败，否则打 WARN，依赖引擎幂等跳过避免二次打星。
 */
public class MaskingChannelValidator implements InitializingBean {

    private static final Logger log = LoggerFactory.getLogger(MaskingChannelValidator.class);

    private final MaskingProperties properties;

    public MaskingChannelValidator(MaskingProperties properties) {
        this.properties = properties;
    }

    /** 容器启动完成后检查通道组合、调试头与总开关。 */
    @Override
    public void afterPropertiesSet() {
        MaskingProperties.Channels channels = properties.getChannels();
        boolean conflict = channels.isJackson() && (channels.isAop() || channels.isMybatis());
        if (conflict) {
            String message = "masking.channels: jackson is enabled together with aop and/or mybatis; "
                    + "mutating channels change in-memory values. Idempotent skip will prevent double masking.";
            if (channels.isStrict()) {
                throw new IllegalStateException(message);
            }
            log.warn(message);
        }
        if (properties.getDebug().isHeaderRoleEnabled()) {
            log.warn("masking.debug.header-role-enabled is true; any authenticated caller can bypass masking with the role header");
        }
        if (!properties.isEnabled()) {
            log.error("masking.enabled is false; all sensitive fields will be returned in plaintext. "
                    + "If this is not intentional, check configuration immediately");
        }
    }
}
