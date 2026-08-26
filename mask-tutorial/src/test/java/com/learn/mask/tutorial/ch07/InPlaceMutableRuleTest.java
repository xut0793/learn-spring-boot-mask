package com.learn.mask.tutorial.ch07;

import com.learn.mask.tutorial.ch04.MaskUtils;
import com.learn.mask.tutorial.ch04.SensitiveType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 量化「原地修改共享规则」的风险。
 * <p>
 * 这些测试是确定性的：不用起线程，直接在两个 setter 之间读一次，
 * 就等价于「另一个线程恰好在这个瞬间读到了规则」。
 */
class InPlaceMutableRuleTest {

    private static final String PHONE = "13812345678";

    @Test
    @DisplayName("热更新把 3/4 改成 6/2，中间态是从未配置过的 6/4")
    void intermediateCombinationIsNeverConfigured() {
        InPlaceMutableRule rule = new InPlaceMutableRule(3, 4);
        assertThat(rule.mask(PHONE)).isEqualTo("138****5678");

        // 热更新的第一个 setter 执行完
        rule.setKeepPrefix(6);

        // 此刻另一个线程读到的规则是 6/4 —— 运维从来没配过这个组合
        assertThat(rule.getKeepPrefix()).isEqualTo(6);
        assertThat(rule.getKeepSuffix()).isEqualTo(4);

        // 第二个 setter 执行完，规则才完整
        rule.setKeepSuffix(2);
        assertThat(rule.mask(PHONE)).isEqualTo("138123***78");
    }

    @Test
    @DisplayName("这个中间态会让 11 位手机号只剩 1 个星号 —— 实质是一次泄露")
    void intermediateStateLeaksAlmostEverything() {
        InPlaceMutableRule rule = new InPlaceMutableRule(3, 4);
        rule.setKeepPrefix(6);   // 中间态：6/4

        String leaked = rule.mask(PHONE);

        assertThat(leaked).isEqualTo("138123*5678");
        assertThat(leaked.chars().filter(c -> c == '*').count())
                .as("11 位数字只遮住了 1 位")
                .isEqualTo(1);
        assertThat(leaked).hasSameSizeAs(PHONE);
    }

    @Test
    @DisplayName("对照：快照方案下不存在这个瞬间")
    void snapshotApproachHasNoIntermediateState() {
        MaskingProperties properties = new MaskingProperties();
        MaskingReloadService service = new MaskingReloadService(properties, null);

        String before = maskWith(properties, PHONE);

        // reload 内部也是逐个 setter 写 RuleConfig，但引擎读的是快照，
        // 快照只在全部字段写完之后才整体替换
        service.reload(ReloadRequest.ofRules(Map.of("PHONE", new RuleConfig(6, 2))));

        assertThat(before).isEqualTo("138****5678");
        assertThat(maskWith(properties, PHONE)).isEqualTo("138123***78");
    }

    private static String maskWith(MaskingProperties properties, String raw) {
        return MaskUtils.keepMask(raw, properties.ruleOf(SensitiveType.PHONE));
    }
}
