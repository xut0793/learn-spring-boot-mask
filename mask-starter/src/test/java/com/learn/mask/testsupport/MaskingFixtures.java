package com.learn.mask.testsupport;

import com.learn.mask.cache.MaskResultCache;
import com.learn.mask.config.MaskingProperties;
import com.learn.mask.engine.AlreadyMaskedDetector;
import com.learn.mask.engine.MaskEngine;
import com.learn.mask.metrics.MaskRecorder;
import com.learn.mask.strategy.BankCardMaskStrategy;
import com.learn.mask.strategy.CustomPatternMaskStrategy;
import com.learn.mask.strategy.EmailMaskStrategy;
import com.learn.mask.strategy.IdCardMaskStrategy;
import com.learn.mask.strategy.MaskStrategyRegistry;
import com.learn.mask.strategy.PhoneMaskStrategy;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

/**
 * starter 单测共用装配：内置策略表、引擎，以及 Security 主体。
 */
public final class MaskingFixtures {

    private MaskingFixtures() {
    }

    public static MaskStrategyRegistry builtinRegistry() {
        return new MaskStrategyRegistry(List.of(
                new PhoneMaskStrategy(),
                new IdCardMaskStrategy(),
                new BankCardMaskStrategy(),
                new EmailMaskStrategy(),
                new CustomPatternMaskStrategy()
        ));
    }

    public static MaskEngine engine(MaskingProperties properties,
                                    MaskResultCache cache,
                                    MaskRecorder recorder) {
        return new MaskEngine(properties, builtinRegistry(), cache, new AlreadyMaskedDetector(), recorder);
    }

    public static MaskEngine engine(MaskingProperties properties) {
        return engine(properties, MaskResultCache.NO_OP, MaskRecorder.NO_OP);
    }

    public static void authenticateAs(String username, String... authorities) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        username,
                        "n/a",
                        List.of(authorities).stream().map(SimpleGrantedAuthority::new).toList()));
    }
}
