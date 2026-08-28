package com.learn.mask.config;

import com.learn.mask.cache.MaskResultCache;
import com.learn.mask.context.HeaderRoleFilter;
import com.learn.mask.engine.MaskEngine;
import com.learn.mask.web.MaskingReloadController;
import jakarta.servlet.Filter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;

/**
 * Web 侧：调试角色头过滤器，以及规则查询/热更新接口。
 */
@AutoConfiguration(after = MaskingAutoConfiguration.class)
@ConditionalOnWebApplication
@ConditionalOnClass(Filter.class)
@ConditionalOnBean(MaskEngine.class)
public class WebMaskingAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public FilterRegistrationBean<HeaderRoleFilter> headerRoleFilter(MaskingProperties properties) {
        FilterRegistrationBean<HeaderRoleFilter> registration = new FilterRegistrationBean<>(new HeaderRoleFilter(properties));
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 20);
        registration.addUrlPatterns("/*");
        return registration;
    }

    @Bean
    @ConditionalOnMissingBean
    public MaskingReloadController maskingReloadController(MaskingProperties properties,
                                                           MaskResultCache maskCache) {
        return new MaskingReloadController(properties, maskCache);
    }
}
