package com.learn.mask.config;

import com.learn.mask.engine.MaskEngine;
import org.apache.ibatis.type.TypeHandler;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;

/**
 * MyBatis 通道占位配置。TypeHandler 只能在 Mapper {@code @Result} 里写类名，
 * 禁止注册为 Spring Bean，否则可能被当成全局 {@code String} 处理器，把所有字符串都按某一种类型脱敏。
 */
@AutoConfiguration(after = MaskingAutoConfiguration.class)
@ConditionalOnClass(TypeHandler.class)
@ConditionalOnBean(MaskEngine.class)
public class MybatisMaskingAutoConfiguration {
}
