package com.learn.mask.tutorial.ch08;

import com.learn.mask.tutorial.ch04.MaskStrategyRegistry;
import com.learn.mask.tutorial.ch04.SensitiveType;
import com.learn.mask.tutorial.ch06.AlreadyMaskedDetector;
import com.learn.mask.tutorial.ch06.MaskEngine;
import com.learn.mask.tutorial.ch06.MaskRecorder;
import com.learn.mask.tutorial.ch06.MaskResultCache;
import com.learn.mask.tutorial.ch07.MaskingProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import java.util.Random;

/**
 * 一个粗糙的基准程序，用来回答第 8 章 8.4 节的三个问题：
 * <ol>
 *   <li>一次 {@code apply()} 大概多少纳秒</li>
 *   <li>缓存在「明文高度重复」和「明文几乎不重复」两种场景下分别值不值</li>
 *   <li>指标打点占多少开销</li>
 * </ol>
 * <p>
 * <b>这不是 JMH，结果只能看数量级</b>，不要拿它做精确对比。没有预热隔离、
 * 没有防止 JIT 死代码消除、没有多次 fork。真要做严肃的基准请用 JMH。
 * <p>
 * 它刻意**不是** JUnit 测试：基准结果依赖机器和负载，做成断言必然会 flaky。
 * 运行方式：
 * <pre>
 * mvn -f mask-tutorial/pom.xml test-compile
 * mvn -f mask-tutorial/pom.xml exec:java -Dexec.mainClass=com.learn.mask.tutorial.ch08.MaskingBenchmark -Dexec.classpathScope=test
 * </pre>
 */
public final class MaskingBenchmark {

    private static final int WARMUP = 200_000;
    private static final int ROUNDS = 2_000_000;

    public static void main(String[] args) {
        System.out.println("场景                                    ns/op    相对基线");
        System.out.println("-".repeat(60));

        double baseline = run("无缓存 + 无指标（基线）", false, false, 1);
        report("无缓存 + 无指标（基线）", baseline, baseline);

        double withMetrics = run("无缓存 + Micrometer", false, true, 1);
        report("无缓存 + Micrometer", withMetrics, baseline);

        double cachedHot = run("缓存 + 无指标（100 个热点明文）", true, false, 100);
        report("缓存 + 无指标（100 个热点明文）", cachedHot, baseline);

        double cachedCold = run("缓存 + 无指标（每次都是新明文）", true, false, ROUNDS);
        report("缓存 + 无指标（每次都是新明文）", cachedCold, baseline);

        double full = run("缓存 + Micrometer（100 个热点明文）", true, true, 100);
        report("缓存 + Micrometer（100 个热点明文）", full, baseline);
    }

    private static void report(String name, double nanos, double baseline) {
        System.out.printf("%-38s %8.1f    %5.2fx%n", name, nanos, nanos / baseline);
    }

    private static double run(String name, boolean useCache, boolean useMetrics, int distinctValues) {
        MaskingProperties properties = new MaskingProperties();
        MaskResultCache cache = useCache
                ? new CaffeineMaskCache(CacheOptions.defaults(), properties::getRuleVersion)
                : MaskResultCache.NO_OP;
        MaskRecorder recorder = useMetrics
                ? new MicrometerMaskRecorder(new SimpleMeterRegistry())
                : MaskRecorder.NO_OP;
        MaskEngine engine = new MaskEngine(properties, MaskStrategyRegistry.withBuiltins(),
                cache, new AlreadyMaskedDetector(), recorder);

        String[] inputs = phones(Math.min(distinctValues, 200_000));

        int sink = 0;
        for (int i = 0; i < WARMUP; i++) {
            sink += engine.apply(inputs[i % inputs.length], SensitiveType.PHONE, null).length();
        }

        long start = System.nanoTime();
        for (int i = 0; i < ROUNDS; i++) {
            sink += engine.apply(inputs[i % inputs.length], SensitiveType.PHONE, null).length();
        }
        long elapsed = System.nanoTime() - start;

        // 消费 sink，避免整个循环被 JIT 判定为死代码
        if (sink == Integer.MIN_VALUE) {
            System.out.println("unreachable " + name);
        }
        return (double) elapsed / ROUNDS;
    }

    private static String[] phones(int count) {
        Random random = new Random(42);
        String[] values = new String[Math.max(count, 1)];
        for (int i = 0; i < values.length; i++) {
            values[i] = "138" + String.format("%08d", random.nextInt(100_000_000));
        }
        return values;
    }
}
