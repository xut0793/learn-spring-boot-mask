package com.learn.mask.config;

import com.learn.mask.cache.MaskResultCache;
import com.learn.mask.context.MaskContext;
import com.learn.mask.crypto.AesGcmReversibleMasker;
import com.learn.mask.crypto.ReversibleMasker;
import com.learn.mask.crypto.SensitiveFieldLookup;
import com.learn.mask.crypto.UnmaskTicketService;
import com.learn.mask.engine.AlreadyMaskedDetector;
import com.learn.mask.engine.MaskEngine;
import com.learn.mask.metrics.MaskRecorder;
import com.learn.mask.strategy.BankCardMaskStrategy;
import com.learn.mask.strategy.CustomPatternMaskStrategy;
import com.learn.mask.strategy.EmailMaskStrategy;
import com.learn.mask.strategy.IdCardMaskStrategy;
import com.learn.mask.strategy.MaskStrategy;
import com.learn.mask.strategy.MaskStrategyRegistry;
import com.learn.mask.strategy.PhoneMaskStrategy;
import com.learn.mask.support.MaskingSpringBridgeLifecycle;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.util.ArrayList;
import java.util.List;

/**
 * 脱敏核心自动配置：策略、引擎，以及给非 Spring 组件用的桥生命周期。
 * 缓存与指标在独立的 {@code ConditionalOnClass} 配置里，避免没有 Caffeine / Micrometer 时本类加载失败。
 */
@AutoConfiguration
@EnableConfigurationProperties(MaskingProperties.class)
public class MaskingAutoConfiguration {

    /** 内置手机号策略 Bean。 */
    @Bean
    @ConditionalOnMissingBean
    public PhoneMaskStrategy phoneMaskStrategy() {
        return new PhoneMaskStrategy();
    }

    /** 内置身份证策略 Bean。 */
    @Bean
    @ConditionalOnMissingBean
    public IdCardMaskStrategy idCardMaskStrategy() {
        return new IdCardMaskStrategy();
    }

    /** 内置银行卡策略 Bean。 */
    @Bean
    @ConditionalOnMissingBean
    public BankCardMaskStrategy bankCardMaskStrategy() {
        return new BankCardMaskStrategy();
    }

    /** 内置邮箱策略 Bean。 */
    @Bean
    @ConditionalOnMissingBean
    public EmailMaskStrategy emailMaskStrategy() {
        return new EmailMaskStrategy();
    }

    /** 内置自定义类型（保留前后缀）策略 Bean。 */
    @Bean
    @ConditionalOnMissingBean
    public CustomPatternMaskStrategy customPatternMaskStrategy() {
        return new CustomPatternMaskStrategy();
    }

    /**
     * {@code strategies} 由 Spring 注入：容器中所有 {@link MaskStrategy} Bean
     * <p>
     * 这是 spring @Bean 方法的参数注入。
     * 当参数类型是 List&lt;MaskStrategy&gt; 时，Spring 会调用 getBeansOfType(MaskStrategy.class)，把当前容器里已经有的、以及尚未创建但类型匹配的策略 Bean 全部放进这个 List。
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

    /** 幂等检测：避免对已打星字符串再次 mask。 */
    @Bean
    @ConditionalOnMissingBean
    public AlreadyMaskedDetector alreadyMaskedDetector() {
        return new AlreadyMaskedDetector();
    }

    /**  classpath 无 Caffeine 时的空缓存实现。 */
    @Bean
    @ConditionalOnMissingBean(MaskResultCache.class)
    @ConditionalOnMissingClass("com.github.benmanes.caffeine.cache.Caffeine")
    public MaskResultCache noOpMaskResultCache() {
        return MaskResultCache.NO_OP;
    }

    /** classpath 无 Micrometer 时的空指标实现。 */
    @Bean
    @ConditionalOnMissingBean(MaskRecorder.class)
    @ConditionalOnMissingClass("io.micrometer.core.instrument.MeterRegistry")
    public MaskRecorder noOpMaskRecorder() {
        return MaskRecorder.NO_OP;
    }

    /** 请求级角色与旁路判断。 */
    @Bean
    @ConditionalOnMissingBean
    public MaskContext maskContext(MaskingProperties properties) {
        return new MaskContext(properties);
    }

    /** 可逆字段 AES-GCM 加解密。 */
    @Bean
    @ConditionalOnMissingBean
    public ReversibleMasker reversibleMasker(MaskingProperties properties) {
        return new AesGcmReversibleMasker(properties);
    }

    /** 需业务提供 {@link SensitiveFieldLookup} 才注册还原票据服务。 */
    @Bean
    @ConditionalOnBean(SensitiveFieldLookup.class)
    @ConditionalOnMissingBean
    public UnmaskTicketService unmaskTicketService(ReversibleMasker masker,
                                                   SensitiveFieldLookup lookup,
                                                   MaskingProperties properties,
                                                   MaskContext maskContext) {
        return new UnmaskTicketService(masker, lookup, properties, maskContext);
    }

    /** 四通道共用的脱敏入口。 */
    @Bean
    @ConditionalOnMissingBean
    public MaskEngine maskEngine(MaskingProperties properties,
                                 MaskStrategyRegistry registry,
                                 MaskResultCache cache,
                                 AlreadyMaskedDetector detector,
                                 MaskRecorder recorder) {
        return new MaskEngine(properties, registry, cache, detector, recorder);
    }

    /** 启动 bind / 关闭 unbind 静态桥。 */
    @Bean
    public MaskingSpringBridgeLifecycle maskingSpringBridgeLifecycle(MaskEngine engine,
                                                                    MaskingProperties properties,
                                                                    MaskContext maskContext) {
        return new MaskingSpringBridgeLifecycle(engine, properties, maskContext);
    }

    /** 启动时校验 Jackson 与 AOP/MyBatis 组合是否冲突。 */
    @Bean
    @ConditionalOnMissingBean
    public MaskingChannelValidator maskingChannelValidator(MaskingProperties properties) {
        return new MaskingChannelValidator(properties);
    }
}
