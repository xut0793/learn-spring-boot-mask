package com.learn.mask.support;

import com.learn.mask.config.MaskingProperties;
import com.learn.mask.context.MaskContext;
import com.learn.mask.engine.MaskEngine;
import org.springframework.beans.factory.DisposableBean;

/**
 * 与 {@link MaskingSpringBridge} 成对：创建时 bind，容器关闭时 unbind，避免关掉的引擎仍挂在静态位上。
 */
public final class MaskingSpringBridgeLifecycle implements DisposableBean {

    /** 构造即 {@link MaskingSpringBridge#bind}，保证 Converter 首次打日志前引擎已就绪。 */
    public MaskingSpringBridgeLifecycle(MaskEngine engine,
                                        MaskingProperties properties,
                                        MaskContext maskContext) {
        MaskingSpringBridge.bind(engine, properties, maskContext);
    }

    /** 容器销毁时 {@link MaskingSpringBridge#unbind()}。 */
    @Override
    public void destroy() {
        MaskingSpringBridge.unbind();
    }
}
