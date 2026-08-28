package com.learn.mask.support;

import com.learn.mask.config.MaskingProperties;
import com.learn.mask.context.MaskContext;
import com.learn.mask.engine.MaskEngine;
import org.springframework.beans.factory.DisposableBean;

/**
 * 与 {@link MaskingSpringBridge} 成对：创建时 bind，容器关闭时 unbind，避免关掉的引擎仍挂在静态位上。
 */
public final class MaskingSpringBridgeLifecycle implements DisposableBean {

    public MaskingSpringBridgeLifecycle(MaskEngine engine,
                                        MaskingProperties properties,
                                        MaskContext maskContext) {
        MaskingSpringBridge.bind(engine, properties, maskContext);
    }

    @Override
    public void destroy() {
        MaskingSpringBridge.unbind();
    }
}
