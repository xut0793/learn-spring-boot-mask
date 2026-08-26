package com.learn.mask.config;

import com.learn.mask.cache.MaskCache;
import com.learn.mask.context.MaskContext;
import com.learn.mask.crypto.AesGcmReversibleMasker;
import com.learn.mask.crypto.ReversibleMasker;
import com.learn.mask.engine.AlreadyMaskedDetector;
import com.learn.mask.engine.MaskEngine;
import com.learn.mask.metrics.MaskingMetrics;
import com.learn.mask.strategy.BankCardMaskStrategy;
import com.learn.mask.strategy.CustomPatternMaskStrategy;
import com.learn.mask.strategy.EmailMaskStrategy;
import com.learn.mask.strategy.IdCardMaskStrategy;
import com.learn.mask.strategy.MaskStrategy;
import com.learn.mask.strategy.MaskStrategyRegistry;
import com.learn.mask.strategy.PhoneMaskStrategy;
import com.learn.mask.support.MaskingSpringBridge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.util.ArrayList;
import java.util.List;

/**
 * 脱敏核心自动配置：策略、引擎、缓存、指标，以及给非 Spring 组件用的 {@link MaskingSpringBridge}。
 */
@AutoConfiguration
@EnableConfigurationProperties(MaskingProperties.class)
public class MaskingAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public PhoneMaskStrategy phoneMaskStrategy() {
        return new PhoneMaskStrategy();
    }

    @Bean
    @ConditionalOnMissingBean
    public IdCardMaskStrategy idCardMaskStrategy() {
        return new IdCardMaskStrategy();
    }

    @Bean
    @ConditionalOnMissingBean
    public BankCardMaskStrategy bankCardMaskStrategy() {
        return new BankCardMaskStrategy();
    }

    @Bean
    @ConditionalOnMissingBean
    public EmailMaskStrategy emailMaskStrategy() {
        return new EmailMaskStrategy();
    }

    @Bean
    @ConditionalOnMissingBean
    public CustomPatternMaskStrategy customPatternMaskStrategy() {
        return new CustomPatternMaskStrategy();
    }

    /**
     * {@code strategies} 由 Spring 注入：容器中所有 {@link MaskStrategy} Bean
     * <p>
     * 这是 spring @Bean 方法的参数注入。
     * 当参数类型是 List<MaskStrategy> 时，Spring 会调用 getBeansOfType(MaskStrategy.class)，把当前容器里已经有的、以及尚未创建但类型匹配的策略 Bean 全部放进这个 List。
     * 不需要人为 new ArrayList 再 add 进去；只要某个对象是 Spring Bean，并且实现了 MaskStrategy，就会出现在这个 List 里。
     * 所以 starter 项目 {@code @Bean} 的内置策略，像上面一样先声明各种类型策略的 bean，然后再声明 MaskStrategyRegistry ，
     * 以及业务项目中 {@code @Component}/{@code @Bean} 自定义策略（项目 bean 先于依赖jar包中的bean初始化）会被收集进该 List。
     * </p>
     * <p>
     * 顺序：启动项目，比如 demo
     *   → 扫描项目内 @Component（地址、快递单号策略）
     *   → 加载 MaskingAutoConfiguration（手机号等 5 个 @Bean）
     *   → 创建 maskStrategyRegistry 时，Spring 收集全部 MaskStrategy
     *   → Registry 按 code() 放进 Map
     *   → MaskEngine 只依赖 Registry，不再关心策略是 starter 还是业务注册的
     * </p>
     */
    @Bean
    @ConditionalOnMissingBean
    public MaskStrategyRegistry maskStrategyRegistry(List<MaskStrategy> strategies) {
        return new MaskStrategyRegistry(new ArrayList<>(strategies));
    }

    @Bean
    @ConditionalOnMissingBean
    public AlreadyMaskedDetector alreadyMaskedDetector() {
        return new AlreadyMaskedDetector();
    }

    @Bean
    @ConditionalOnMissingBean
    public MaskCache maskCache(MaskingProperties properties) {
        return new MaskCache(properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public MaskingMetrics maskingMetrics(ObjectProvider<MeterRegistry> meterRegistries) {
        MeterRegistry registry = meterRegistries.getIfAvailable(SimpleMeterRegistry::new);
        return new MaskingMetrics(registry);
    }

    @Bean
    @ConditionalOnMissingBean
    public MaskContext maskContext(MaskingProperties properties) {
        return new MaskContext(properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public ReversibleMasker reversibleMasker(MaskingProperties properties) {
        return new AesGcmReversibleMasker(properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public MaskEngine maskEngine(MaskingProperties properties,
                                 MaskStrategyRegistry registry,
                                 MaskCache cache,
                                 AlreadyMaskedDetector detector,
                                 MaskingMetrics metrics,
                                 MaskContext maskContext) {
        MaskEngine engine = new MaskEngine(properties, registry, cache, detector, metrics);
        MaskingSpringBridge.bind(engine, properties, maskContext);
        return engine;
    }

    @Bean
    @ConditionalOnMissingBean
    public MaskingChannelValidator maskingChannelValidator(MaskingProperties properties) {
        return new MaskingChannelValidator(properties);
    }
}
