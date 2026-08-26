package com.learn.mask.tutorial.ch13;

import com.learn.mask.tutorial.ch09.ChannelProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;

/**
 * 启动时检查通道组合。Jackson（出口）和 AOP / MyBatis（突变）同时开启时，
 * 内存里的值会被改掉再拿去序列化，依赖引擎幂等跳过才能不打成两层星号。
 * <p>
 * {@code strict=true} 直接启动失败；否则打 WARN。第 13.4 节。
 */
public class MaskingChannelValidator implements InitializingBean {

    private static final Logger log = LoggerFactory.getLogger(MaskingChannelValidator.class);

    static final String CONFLICT_MESSAGE =
            "masking.channels: jackson is enabled together with aop and/or mybatis; "
                    + "mutating channels change in-memory values. Idempotent skip will prevent double masking.";

    private final ChannelProperties channels;

    public MaskingChannelValidator(ChannelProperties channels) {
        this.channels = channels;
    }

    @Override
    public void afterPropertiesSet() {
        boolean conflict = channels.isJackson() && (channels.isAop() || channels.isMybatis());
        if (!conflict) {
            return;
        }
        if (channels.isStrict()) {
            throw new IllegalStateException(CONFLICT_MESSAGE);
        }
        log.warn(CONFLICT_MESSAGE);
    }
}
