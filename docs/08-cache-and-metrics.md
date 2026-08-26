# 第 8 章 缓存与指标：把 NO_OP 换成真东西

> **本章目标**：补上第 6 章引擎里留着 `NO_OP` 的两个位置。然后**实测**它们到底值不值——结果和直觉相反。
> **前置知识**：第 6、7 章。
> **预计时长**：60 分钟。
> **本章代码**：`mask-tutorial/src/main/java/com/learn/mask/tutorial/ch08/`

---

## 8.1 问题场景：两个还没回答的问题

第 6 章的引擎依赖两个接口，当前都接的是 `NO_OP`：

```java
public interface MaskResultCache {
    String get(String typeCode, String raw);
    void put(String typeCode, String raw, String masked);
    void invalidateAll();
}

public interface MaskRecorder {
    void record(String typeCode, MaskRole role, MaskAction action, Duration duration);
}
```

本章把它们换成 Caffeine 和 Micrometer。**引擎一行都不用改**——这是第 6 章 6.2 节那个「提前留接口」决定的回报。

但更重要的是回答两个问题：

1. **缓存到底有没有用？** 直觉是「有，脱敏是计算，缓存能省」。本章 8.4 节的实测数据会推翻这个直觉。
2. **指标要打哪些、代价是多少？** 8.7 节会看到指标打点是整条链路上**最贵**的一步。

先写实现，再看数据。

---

## 8.2 动手写：Caffeine 缓存

```java
public class CaffeineMaskCache implements MaskResultCache {

    private final CacheOptions options;
    private final LongSupplier ruleVersion;
    private final Cache<String, String> cache;

    public CaffeineMaskCache(CacheOptions options, LongSupplier ruleVersion, Ticker ticker) {
        this.options = options.sanitized();
        this.ruleVersion = ruleVersion;
        this.cache = Caffeine.newBuilder()
                .maximumSize(this.options.maxSize())
                .expireAfterAccess(this.options.expireAfterAccess())
                .ticker(ticker)
                .recordStats()
                .build();
    }

    private String key(String typeCode, String raw) {
        return ruleVersion.getAsLong() + ":" + MaskStrategyRegistry.normalize(typeCode) + ":" + raw;
    }
}
```

### key 的三个组成部分

```
ruleVersion : typeCode : raw
     1      :  PHONE   : 13812345678
```

| 部分 | 为什么需要 |
| --- | --- |
| `ruleVersion` | 规则一改，所有旧 key 立刻不可达。第 7 章 7.6 节讲的「用不变量代替动作」 |
| `typeCode` | 同一个明文在不同类型下结果不同。`13812345678` 按 `PHONE` 是 `138****5678`，按 `CUSTOM` 是 `1*********8` |
| `raw` | 明文本身 |

版本号的效果可以直接测出来：

```java
@Test
@DisplayName("规则版本变化后旧条目立刻不可达 —— 不依赖任何清理动作")
void ruleVersionInvalidatesWithoutCleanup() {
    cache.put("PHONE", "13812345678", "138****5678");
    assertThat(cache.get("PHONE", "13812345678")).isEqualTo("138****5678");

    ruleVersion.incrementAndGet();

    assertThat(cache.get("PHONE", "13812345678")).isNull();
    assertThat(cache.estimatedSize())
            .as("但旧条目还占着内存，所以 invalidateAll 仍然有价值")
            .isEqualTo(1);
}
```

最后那个断言很关键：**版本号让旧条目不可达，但没有释放内存。** 这印证了第 7 章 7.6 节的职责划分——版本号保证正确性，`invalidateAll()` 保证内存回收。

还有一个测试把这个机制的本质暴露得更清楚：

```java
@Test
@DisplayName("版本号回退后旧条目又能命中 —— 说明失效是靠 key 而不是删除")
void revertingVersionRestoresVisibility() {
    cache.put("PHONE", "13812345678", "138****5678");
    ruleVersion.incrementAndGet();
    assertThat(cache.get("PHONE", "13812345678")).isNull();

    ruleVersion.decrementAndGet();

    assertThat(cache.get("PHONE", "13812345678")).isEqualTo("138****5678");
}
```

条目一直在那里，只是「查不到」。理解这一点很重要：**如果哪天有人实现了「版本号回退」功能（比如配置回滚），就会意外命中一批老结果。**

### 方向很重要：key 是明文，value 是打码值

```
key   = "1:PHONE:13812345678"      ← 明文在这里
value = "138****5678"              ← 打码值
```

**绝对不能反过来。** 「key 是打码值、value 是明文」的缓存，本质上是一张「打码值 → 明文」的还原表——任何拿到 heap dump 或能访问缓存的人都能批量还原。

即使是当前这个方向，明文出现在 key 里也是需要警惕的：

| 风险 | 说明 | 缓解 |
| --- | --- | --- |
| heap dump 泄露 | 生产环境 OOM 时自动 dump，dump 文件里能直接搜到明文 | dump 文件按敏感数据管理；或对高敏感类型不缓存 |
| 缓存诊断端点 | 有些团队会加一个「查看缓存内容」的调试接口 | 不要加，或者只暴露统计不暴露内容 |
| 日志 | 如果有人 debug 时把 key 打进日志 | code review 拦住 |

第 6 章练习 6.3 讨论过：对密码、密钥这类字段，更彻底的做法是**直接不缓存**。

### 可注入的时钟

```java
public CaffeineMaskCache(CacheOptions options, LongSupplier ruleVersion, Ticker ticker) {
```

多了一个 `Ticker` 参数。这不是为了灵活性，是为了**能测过期行为**。

如果不留这个口子，测 `expireAfterAccess` 只有两条路：`Thread.sleep(10 分钟)`（不可能），或者把过期时间配成 1 毫秒（那测的就不是真实配置了）。结果是这条分支永远不会被测到。

有了 `Ticker`：

```java
static class ManualTicker implements Ticker {
    private long nanos;

    @Override
    public long read() {
        return nanos;
    }

    void advance(Duration duration) {
        nanos += duration.toNanos();
    }
}
```

```java
@Test
@DisplayName("过期时间到了就淘汰 —— 用可注入时钟测，不用真的等")
void expiresAfterAccess() {
    ManualTicker ticker = new ManualTicker();
    CaffeineMaskCache cache = new CaffeineMaskCache(
            new CacheOptions(true, 100, Duration.ofMinutes(10)), ruleVersion::get, ticker);

    cache.put("PHONE", "13812345678", "138****5678");
    assertThat(cache.get("PHONE", "13812345678")).isEqualTo("138****5678");

    ticker.advance(Duration.ofMinutes(9));
    assertThat(cache.get("PHONE", "13812345678")).isEqualTo("138****5678");

    ticker.advance(Duration.ofMinutes(11));
    assertThat(cache.get("PHONE", "13812345678")).isNull();
}
```

**「时间」是最典型的需要被注入的依赖。** 任何依赖 `System.currentTimeMillis()`、`Instant.now()`、`LocalDate.now()` 的逻辑，如果不把时间源抽出来，相关分支就测不了。Java 标准库为此提供了 `java.time.Clock`，Caffeine 提供了 `Ticker`。

### `expireAfterAccess` 而不是 `expireAfterWrite`

两者的区别：

| | 语义 | 效果 |
| --- | --- | --- |
| `expireAfterWrite` | 写入后 N 分钟过期，不管被访问多少次 | 热点数据也会被定期赶走，然后重算 |
| `expireAfterAccess` | 最后一次访问后 N 分钟过期 | **热点数据一直留着**，冷数据自动淘汰 |

脱敏缓存的访问模式是明显的长尾分布：少数明文被访问成千上万次（热门商家的客服电话、系统账号），大量明文只被访问一两次。`expireAfterAccess` 正好匹配这个模式。

测试：

```java
@Test
@DisplayName("expireAfterAccess 会被访问刷新 —— 热点数据不会被时间赶走")
void accessRefreshesExpiry() {
    cache.put("PHONE", "13812345678", "138****5678");

    // 每 9 分钟访问一次，连续 5 次共 45 分钟，远超 10 分钟的过期时间
    for (int i = 0; i < 5; i++) {
        ticker.advance(Duration.ofMinutes(9));
        assertThat(cache.get("PHONE", "13812345678")).isEqualTo("138****5678");
    }
}
```

代价是：**一个明文只要持续被访问就永远不会过期**，所以「规则改了但忘了提版本号」的错误会持续更久。不过版本号机制已经处理了这个问题。

### 配置钳制

```java
public CacheOptions sanitized() {
    long size = Math.max(maxSize, 1);
    Duration ttl = expireAfterAccess == null || expireAfterAccess.isZero() || expireAfterAccess.isNegative()
            ? Duration.ofMinutes(1)
            : expireAfterAccess;
    return new CacheOptions(enabled, size, ttl);
}
```

`Caffeine.maximumSize(0)` 是合法的（等于不缓存），但 `expireAfterAccess(Duration.ZERO)` 会让每个条目立刻过期——一个「写进去就查不到」的缓存，行为看起来像 bug 但不会报错。

和第 4 章 `MaskUtils` 里的 `Math.max(..., 0)` 是同一个思路：**把非法配置钳制成一个可预测的行为，而不是让它变成一个诡异的运行时表现。**

---

## 8.3 动手写：Micrometer 指标

```java
@Override
public void record(String typeCode, MaskRole role, MaskAction action, Duration duration) {
    String typeTag = (typeCode == null || typeCode.isBlank()) ? UNKNOWN : typeCode;
    String roleTag = role == null ? UNKNOWN : role.name();
    String resultTag = action == null ? UNKNOWN : action.name().toLowerCase(Locale.ROOT);

    Counter.builder(INVOKE)
            .tag("type", typeTag)
            .tag("role", roleTag)
            .tag("result", resultTag)
            .register(registry)
            .increment();

    Timer.builder(DURATION)
            .tag("type", typeTag)
            .tag("role", roleTag)
            .record(duration);

    if (action == MaskAction.FAIL) {
        Counter.builder(FAIL).tag("type", typeTag).register(registry).increment();
    }
    // BYPASS / SKIP_ALREADY_MASKED 同理
}
```

五个指标：

| 指标 | 类型 | 标签 | 回答什么问题 |
| --- | --- | --- | --- |
| `masking.invoke` | Counter | `type` / `role` / `result` | 全景：谁、对什么类型、结果如何 |
| `masking.duration` | Timer | `type` / `role` | 脱敏耗时分布 |
| `masking.fail` | Counter | `type` | **告警用**：哪个类型的策略在报错 |
| `masking.bypass` | Counter | `role` | **审计用**：谁在旁路脱敏 |
| `masking.skipped` | Counter | `type` | **诊断用**：哪些类型出现了通道叠加 |

### 为什么已经有 `masking.invoke` 了还要三个独立计数器

`masking.invoke{result=fail}` 已经包含了 `masking.fail` 的信息。冗余吗？

不完全是。差别在**告警规则的复杂度和可靠性**：

```promql
# 用 invoke + 标签过滤
sum(rate(masking_invoke_total{result="fail"}[5m])) > 0

# 用独立计数器
sum(rate(masking_fail_total[5m])) > 0
```

两者等价，但第一个依赖标签值 `"fail"` 拼写正确。如果哪天 `MaskAction.FAIL` 被重命名成 `ERROR`，第一条告警会**静默失效**——不再触发，但也不报错。而独立的指标名重命名会在 Grafana 上表现为「指标消失」，更容易被发现。

对**关键告警**（脱敏失败意味着接口在 500）来说，少一层依赖是值得的。

### `duration` 上刻意不带 `result` 标签

```java
Timer.builder(DURATION)
        .tag("type", typeTag)
        .tag("role", roleTag)      // 没有 .tag("result", ...)
```

两个理由：

**基数。** Timer 比 Counter 贵得多——它内部维护直方图桶。加一个 4 取值的标签，时间序列数量翻 4 倍。假设有 10 个类型 × 3 个角色 = 30 条序列，加上 result 就是 120 条。

**没有分析价值。** 「旁路的耗时」是多少？就是几次判断的时间，恒定且无意义。真正需要看的是「实际脱敏的耗时」，而那个可以用 `masking.invoke{result=mask}` 的量配合总耗时估算。

测试固化了这个决定：

```java
@Test
@DisplayName("耗时记进 Timer，且不带 result 标签")
void durationHasNoResultTag() {
    recorder.record("PHONE", MaskRole.USER, MaskAction.MASK, micros(40));
    recorder.record("PHONE", MaskRole.USER, MaskAction.BYPASS, micros(10));

    assertThat(registry.get("masking.duration").tag("type", "PHONE").tag("role", "USER")
            .timer().count()).isEqualTo(2);
    assertThat(registry.get("masking.duration").timer()
            .totalTime(TimeUnit.MICROSECONDS)).isEqualTo(50);
}
```

### 标签值的大小写：一个真实踩过的坑

```java
String roleTag = role.name();                              // USER（大写）
String resultTag = action.name().toLowerCase(Locale.ROOT);  // mask（小写）
```

**不一致，而且是有意的**（这里遵循了 `mask-starter` 的行为）。`result` 小写、`type` 和 `role` 大写。

第 3 章实验七我就踩了这个坑：

```bash
# 查不到，返回空
curl ".../actuator/metrics/masking.invoke?tag=role:USER&tag=result:MASK"

# 能查到
curl ".../actuator/metrics/masking.invoke?tag=role:USER&tag=result:mask"
```

测试把这个行为钉死了，免得下次改代码时不小心统一了大小写而破坏已有的看板：

```java
@Test
@DisplayName("result 标签是小写，type 和 role 保持大写")
void resultTagIsLowercased() {
    recorder.record("PHONE", MaskRole.USER, MaskAction.SKIP_ALREADY_MASKED, micros(5));

    assertThat(registry.get("masking.invoke").tag("result", "skip_already_masked")
            .counter().count()).isEqualTo(1);
    assertThat(registry.find("masking.invoke").tag("result", "SKIP_ALREADY_MASKED").counter())
            .as("大写查不到 —— 这是第 3 章实验里踩过的坑")
            .isNull();
}
```

**如果重新设计，应该统一成小写**（Prometheus 生态的惯例是小写标签值）。现在改会破坏已有看板和告警，属于需要迁移路径的变更。

### 标签绝不能放高基数值

```java
// 绝对不要这么做
.tag("field", fieldName)        // 字段名可能有几百个
.tag("user", userId)            // 用户 ID 有几百万个
.tag("value", raw)              // 明文！既高基数又泄露
```

Micrometer 的每个标签组合是一条独立的时间序列，每条序列在内存里都有一份状态。高基数标签会导致：

- **应用内存爆掉**：几百万条序列 × 每条几十字节到几 KB（Timer 更贵）
- **Prometheus 存储爆掉**：这叫「基数爆炸」，是监控系统最常见的故障原因
- **明文泄露到监控系统**：而监控系统的访问控制通常比业务系统宽松得多

本项目的三个标签基数都很低：`type` 大约 10 个、`role` 3 个、`result` 4 个，组合上限 120 条序列。这是健康的。

### null 防护是必须的

```java
@Test
@DisplayName("null 标签值不会让打点抛异常")
void nullTagsFallBackToUnknown() {
    recorder.record(null, null, null, micros(1));

    assertThat(registry.get("masking.invoke")
            .tag("type", "unknown").tag("role", "unknown").tag("result", "unknown")
            .counter().count()).isEqualTo(1);
}
```

Micrometer 遇到 null 标签值会抛 `NullPointerException`。而 `record()` 是在引擎的 `try` 块里被调用的（第 6 章 6.4 节），所以**一次指标打点的异常会变成一次业务请求的失败**。

「监控把业务搞挂了」是运维事故里的经典条目。所有打点代码都应该对自己的输入做防护。

---

## 8.4 缓存指标：回答「配得对不对」

第 6 章 6.6 节说过「缓存命中率应该由缓存组件自己度量」。落地：

```java
public static void bind(MeterRegistry registry, CaffeineMaskCache cache) {
    Gauge.builder("masking.cache.size", cache, CaffeineMaskCache::estimatedSize)
            .register(registry);
    Gauge.builder("masking.cache.hit.rate", cache, c -> c.stats().hitRate())
            .register(registry);
    Gauge.builder("masking.cache.eviction", cache, c -> c.stats().evictionCount())
            .register(registry);
}
```

用 `Gauge` 而不是 `Counter`：这些值是「当前状态的快照」，由 Caffeine 内部维护，我们只是读出来。`Counter` 的语义是「我们自己累加」。

**注意 `.recordStats()` 必须在建缓存时开启**，否则 `hitRate()` 永远返回 0。这是一个很容易漏的开关——指标存在、能查、值是 0，看起来像「缓存完全没命中」而不是「统计没开」。

三个指标怎么一起看：

| `hitRate` | `eviction` 增长 | 结论 |
| --- | --- | --- |
| 高（> 0.8） | 慢 | 配置合适 |
| 高 | **快** | 容量偏小，但热点集中所以还能撑。可以适当加大 |
| **低**（< 0.3） | 快 | **容量明显不够**，或者明文重复度本来就低——后者说明缓存不该开 |
| 低 | 慢 | 明文几乎不重复。**缓存是纯开销，应该关掉** |

最后一行是本章 8.5 节的重点。

---

## 8.5 实测：缓存到底值不值

现在回答 8.1 节的第一个问题。我写了一个粗糙的基准程序（`MaskingBenchmark`），跑法：

```bash
mvn -f mask-tutorial/pom.xml test-compile
mvn -f mask-tutorial/pom.xml exec:java \
  -Dexec.mainClass=com.learn.mask.tutorial.ch08.MaskingBenchmark \
  -Dexec.classpathScope=test
```

**先说明它不是 JMH**：没有 fork 隔离、没有严格的死代码消除防护、单次运行。结果只能看**数量级和相对关系**，不要当精确数字。

本机（Windows / JDK 21）两次运行的结果：

| 场景 | 第一次 ns/op | 第二次 ns/op | 相对基线 |
| --- | --- | --- | --- |
| 无缓存 + 无指标（基线） | 409 | 344 | 1.00x |
| 无缓存 + Micrometer | 1313 | 1255 | **约 3.4x** |
| 缓存 + 无指标（100 个热点明文） | 541 | 564 | **约 1.5x** |
| 缓存 + 无指标（每次都是新明文） | 886 | 849 | 约 2.3x |
| 缓存 + Micrometer（100 个热点明文） | 1373 | 1334 | 约 3.6x |

### 结论一：缓存让手机号脱敏变慢了

**即使 100% 命中，加缓存后从约 380ns 变成约 550ns。**

这和直觉完全相反。原因要算一下两条路径各做了什么：

```
不用缓存：
  isBlank  →  ruleOf（哈希查找）  →  alreadyMasked（遍历 4 个字符）
  →  keepMask（2 次 substring + 1 次 repeat + 字符串拼接）

用缓存（命中）：
  isBlank  →  ruleOf（哈希查找）  →  alreadyMasked（遍历 4 个字符）
  →  构造 key（"1:" + "PHONE" + ":" + 明文，一次字符串拼接 + 分配）
  →  Caffeine.getIfPresent（哈希 + 读屏障 + 访问队列维护）
```

缓存**只省掉了 `keepMask`**（两次 substring 加一次 repeat），却增加了「构造一个约 20 字符的 key 字符串」和「一次 Caffeine 查找」。

关键在于：**`keepMask` 本身太便宜了。** 它是几次字符串操作，而构造缓存 key 也是字符串操作。省下的和付出的在同一个数量级，而 Caffeine 的读路径维护（它要更新访问顺序以支持 `expireAfterAccess` 和 W-TinyLFU 淘汰）是净增加。

而在「明文几乎不重复」的场景下更糟：约 380ns → 约 870ns，**翻了一倍以上**。因为每次都是 key 构造 + 查找失败 + 写入 + 可能触发淘汰。

### 那缓存什么时候有用

判断标准是**被缓存的计算有多贵**。

| 策略类型 | `mask()` 的开销 | 缓存收益 |
| --- | --- | --- |
| 保留前后缀（手机号、身份证、卡号） | 几次 substring，约 100ns | **负收益** |
| 邮箱 | 一次 indexOf + 几次 substring，约 150ns | 负收益 |
| 正则类（第 4 章的快递单号） | 一次正则匹配，约 500ns ~ 数微秒 | **可能正收益** |
| AES-GCM 可逆脱敏（第 14 章） | 一次加密 + Base64，约 1~10 微秒 | **明确正收益** |
| 需要查库/查缓存的（比如脱敏后要查映射表） | 毫秒级 | **必须缓存** |

**所以这个项目默认开启缓存是一个值得质疑的决定。** 内置的四个策略全都是「保留前后缀」这一类，缓存对它们全是负收益。真正需要缓存的是 AES-GCM 可逆脱敏，而那条路径（第 14 章）走的是另一套代码。

我倾向于认为更好的默认是：

```yaml
masking:
  cache:
    enabled: false     # 默认关闭
```

然后在文档里说明「只有在使用了昂贵的自定义策略（正则、加密、外部查询）时才开启，并用 `masking.cache.hit.rate` 验证收益」。

（第 17 章会把这条列入改进项。这也是第 15 章性能测试的一个重点验证目标——在真实 HTTP 链路上，脱敏的这几百纳秒会被网络和序列化的开销淹没，所以**这个结论对总体 QPS 的影响可能微乎其微**。但「默认开一个负收益的功能」本身仍然值得修正。）

### 这一节最重要的收获

**不要凭直觉判断优化的方向。**

「加缓存能提速」是一条几乎不会被质疑的经验，但它成立的前提是「被缓存的计算比查缓存贵」。当计算本身只有几百纳秒时，这个前提就不成立了。

而验证这件事的成本很低——一个 100 行的基准程序，跑 30 秒。相比之下，「因为直觉正确所以不验证」的代价是一个默认开启的负收益功能，在所有使用方身上持续生效。

---

## 8.6 实测：指标打点是最贵的一步

第二个结论更值得注意：

**Micrometer 打点约 900ns，是脱敏本身（约 380ns）的 2.4 倍。**

一次 `record()` 做了什么：

```java
Counter.builder(INVOKE).tag(...).tag(...).tag(...).register(registry).increment();
Timer.builder(DURATION).tag(...).tag(...).register(registry).record(duration);
```

问题在 `Counter.builder(...).register(registry)`——**每次调用都在构造 builder、构造 Tags 对象、然后去 registry 里查找对应的 Meter**。查找需要计算标签集合的哈希。

这是 Micrometer 的一个常见误用。正确的写法是**把 Meter 缓存起来**：

```java
// 优化后
private final Map<MeterKey, Counter> counters = new ConcurrentHashMap<>();

public void record(String typeCode, MaskRole role, MaskAction action, Duration duration) {
    counters.computeIfAbsent(new MeterKey(typeCode, role, action), this::createCounter).increment();
    // ...
}
```

因为标签组合数很少（约 120 种），这个 Map 会很快填满并稳定，之后每次就是一次哈希查找加一次原子递增。

预期能省掉大部分开销。**不过要注意 `computeIfAbsent` 在这里是安全的**——和第 7 章 7.10 节批评 `MaskingProperties` 里那个 `computeIfAbsent` 不同：这里用的是 `ConcurrentHashMap`（线程安全），而且 key 的取值空间是有界的（枚举组合），不会无界增长。**同一个 API，在不同的容器和不同的 key 空间下，安全性完全不同。**

### 那要不要关掉指标

不。这是本节和上一节的区别所在：

| | 缓存 | 指标 |
| --- | --- | --- |
| 开销 | 约 +170ns | 约 +900ns |
| 收益 | 对内置策略是**零**（甚至负） | **不可替代**：没有 `masking.fail` 就不知道策略在报错，没有 `masking.bypass` 就无法审计谁在看明文 |
| 有没有优化空间 | 没有（key 构造是必需的） | **有**（缓存 Meter 引用） |

指标的开销是为了**可观测性**付的，而可观测性在安全组件上不是可选项。第 6 章 6.8 节说过「安全组件出错要 fail-loud」，指标就是「loud」的一部分。

所以正确的动作是**优化指标打点的实现**，而不是关掉它。

（另外要有个数量级感：900ns 放在一次完整 HTTP 请求（通常 1~50 毫秒）里是万分之几。一个返回 100 条记录 × 5 个字段的接口有 500 次调用，合计 450 微秒——仍然远小于一次数据库查询。**所以这个优化的优先级不高，但实现方式的改进是几乎免费的。**）

---

## 8.7 验证

```bash
mvn -f mask-tutorial/pom.xml test
```

本章 27 个测试分三个文件：

| 测试类 | 数量 | 覆盖什么 |
| --- | --- | --- |
| `CaffeineMaskCacheTest` | 13 | 读写、关闭为 no-op、null 忽略、清空；**key 的三个组成部分**（类型隔离、编码归一化、版本号失效、版本回退恢复可见）；容量淘汰、**过期（可注入时钟）**、访问刷新过期、非法配置钳制；命中率统计 |
| `MicrometerMaskRecorderTest` | 9 | 三个标签、**result 小写**、duration 不带 result、三个独立计数器的标签维度、null 防护、空白编码、重复打点累加 |
| `EngineWithCacheAndMetricsTest` | 5 | **第 4~8 章全部零件组装**：完整链路、热更新失效缓存、幂等跳过不碰缓存、缓存 Gauge 已绑定、总开关关闭 |

`EngineWithCacheAndMetricsTest` 是第二部分的收尾。它把真实的 `MaskingProperties`（第 7 章）、真实的 Caffeine 缓存、真实的 Micrometer 指标、真实的热更新服务全装在一起：

```java
@BeforeEach
void setUp() {
    properties = new MaskingProperties();
    cache = new CaffeineMaskCache(CacheOptions.defaults(), properties::getRuleVersion);
    registry = new SimpleMeterRegistry();
    MaskCacheMetrics.bind(registry, cache);
    engine = new MaskEngine(properties, MaskStrategyRegistry.withBuiltins(), cache,
            new AlreadyMaskedDetector(), new MicrometerMaskRecorder(registry));
    reloadService = new MaskingReloadService(properties, cache);
}
```

**注意 `MaskEngine` 的构造函数和第 6 章完全一样。** 从 `NO_OP` 换成 Caffeine 和 Micrometer，引擎代码一行没动。这是第 6 章 6.2 节那个「窄接口 + 提前留位」决定的直接回报。

一个把多个章节的性质串起来的测试：

```java
@Test
@DisplayName("幂等跳过既不查缓存也不占缓存，但会被单独计数")
void idempotentSkipIsVisibleInMetrics() {
    engine.apply("138****5678", SensitiveType.PHONE, null);

    assertThat(cache.estimatedSize()).isZero();
    assertThat(cache.stats().requestCount()).as("连缓存都没查").isZero();
    assertThat(registry.get("masking.skipped").tag("type", "PHONE").counter().count()).isEqualTo(1);
}
```

三个断言分别验证了：第 6 章 6.6 节的判定顺序（幂等在缓存之前）、缓存不被污染、以及这件事在指标上可观测。

跑完第二部分全部章节，应该看到：

```
[INFO] Tests run: 160, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

分布是：第 4 章 27 个、第 5 章 43 个、第 6 章 29 个、第 7 章 34 个、第 8 章 27 个。**这 160 个测试全部不需要启动 Spring 容器**（除了第 7 章那 7 个用 `ApplicationContextRunner` 的绑定测试），所以整个套件跑完约 14 秒。这是第 6 章「窄接口 + 手写测试替身」带来的另一个好处：反馈快到可以在每次保存后都跑一遍。

---

## 8.8 对照真实实现

| 方面 | 你的 `ch08` | `mask-starter` | 评价 |
| --- | --- | --- | --- |
| 缓存 key 结构 | `version:type:raw` | `version:type:raw`，完全一致 | — |
| 缓存配置来源 | `CacheOptions` record | `MaskingProperties.Cache` 内部类 | 教程版让缓存不依赖大配置类，可独立测试 |
| 版本号获取 | `LongSupplier` | 直接依赖 `MaskingProperties` | 教程版更松耦合 |
| 时钟 | 可注入 `Ticker` | 不可注入 | **教程版能测过期，真实版测不了** |
| `recordStats()` | 开启 | **未开启** | 真实版 `hitRate()` 永远是 0，无法判断缓存配得对不对 |
| 缓存统计指标 | `MaskCacheMetrics` 绑定三个 Gauge | **没有** | 真实版缺少「缓存值不值」的观测手段 |
| Meter 引用缓存 | 没做（和真实版一致） | 没做 | 两边都有 8.6 节说的优化空间 |
| 指标名与标签 | 一致 | 一致 | — |

真实实现的缓存：

```18:24:mask-starter/src/main/java/com/learn/mask/cache/MaskCache.java
    public MaskCache(MaskingProperties properties) {
        this.properties = properties;
        this.cache = Caffeine.newBuilder()
                .maximumSize(Math.max(properties.getCache().getMaxSize(), 1))
                .expireAfterAccess(Duration.ofMinutes(Math.max(properties.getCache().getExpireAfterAccessMinutes(), 1)))
                .build();
    }
```

### 差异一：没有 `recordStats()`，也没有缓存指标

这是本章发现的最实际的一个缺口。

**后果**：无法回答「缓存有没有用」这个问题。而 8.5 节的实测说明，这个问题的答案很可能是「没用，还拖慢了」。也就是说——**真实项目开启了一个可能是负收益的功能，而且没有任何手段能发现这一点。**

改法几乎免费：

```java
.recordStats()      // 加一行
```

然后绑三个 Gauge（约 15 行）。`recordStats()` 本身有轻微开销（几个原子计数器的递增），但相比它提供的信息完全值得。

**这是我认为整个项目里性价比最高的可观测性改进。** 因为它不只是「多了几个指标」，而是让一个已有的设计决策（默认开缓存）第一次变得可验证。

### 差异二：`expireAfterAccessMinutes` 用 `long` 而不是 `Duration`

```java
private long expireAfterAccessMinutes = 10;
```

配置项名字里带单位，值是数字。这个写法的问题：

- **改单位要改字段名。** 如果哪天需要「30 秒」，得加一个 `expireAfterAccessSeconds`，或者把这个字段改成 `expireAfterAccessMillis`（破坏性变更）
- **Spring Boot 原生支持 `Duration` 绑定**，可以写 `expire-after-access: 10m`、`30s`、`1h`，甚至 `PT10M`。表达力强得多，也不需要在字段名里编码单位

教程版用 `Duration`：

```java
public record CacheOptions(boolean enabled, long maxSize, Duration expireAfterAccess) { }
```

严重性：低。这是一个 API 设计的整洁问题，不影响正确性。但它属于「一开始花五分钟就能做对、后面改要破坏兼容」的那类决定，值得在写配置类时就注意。

### 差异三：`maxSize` 是条目数，不是内存量

两边都一样，但值得指出这个配置项的实际含义：

`maximumSize(10000)` 是**一万个条目**，不是一万字节。每个条目的内存占用大约是：

```
key   ≈ "1:PHONE:13812345678"           约 20 字符 → 约 60 字节（String 对象 + char 数组）
value ≈ "138****5678"                   约 11 字符 → 约 50 字节
Caffeine 的节点开销                       约 50~80 字节
                                        ──────────────
                                        约 170 字节/条目
```

一万条约 1.7 MB。这个量级完全可以接受。但如果有人把 `max-size` 配成 1000000（一百万），就是约 170 MB——**足以让一个 512 MB 堆的服务 OOM**。

而配置项名字 `max-size` 完全没有提示这一点。更好的做法是在配置类的 javadoc 里写明单位和内存估算（教程版的 `CacheOptions` 里加了一句），或者干脆改用 `maximumWeight` 按实际字符数计算。

---

## 本章小结

- 从 `NO_OP` 换成 Caffeine + Micrometer，**引擎代码一行没改**。这是第 6 章「窄接口 + 提前留位」的回报
- 缓存 key 是 `版本号:类型:明文`，value 是打码值。**方向绝不能反**——反过来就是一张还原表
- 版本号让旧条目**不可达**但不释放内存；`invalidateAll()` 负责内存。两者职责分离
- 「版本号回退能让旧条目重新可见」揭示了这个机制的本质：失效靠 key 而不是删除
- **「时间」是最典型的需要注入的依赖。** 不注入 `Ticker`，过期分支就永远测不了
- `expireAfterAccess` 匹配脱敏的长尾访问模式：热点数据留着，冷数据自动淘汰
- 指标标签**绝不能放高基数值**（字段名、用户 ID、明文）。基数爆炸是监控系统最常见的故障原因
- 打点代码必须做 null 防护。「监控把业务搞挂了」是真实的事故类别
- **实测结论一：缓存让内置策略变慢了**（约 +170ns，即使 100% 命中）。因为 `keepMask` 本身太便宜，省下的和 key 构造付出的在同一数量级。缓存只对昂贵策略（正则、加密、外部查询）有正收益
- **实测结论二：Micrometer 打点约 900ns，是脱敏本身的 2.4 倍**。原因是每次都重新 `builder().register()`，应该缓存 Meter 引用
- 但两者的处理方式不同：缓存应该**默认关闭**（收益为零），指标应该**优化实现**（收益不可替代）
- `recordStats()` 不开，`hitRate()` 永远是 0。真实项目缺了这一行，导致「缓存值不值」无法验证
- **不要凭直觉判断优化方向。** 「加缓存能提速」的前提是「计算比查缓存贵」，几百纳秒的计算不满足这个前提。验证成本是 30 秒

第二部分（引擎核心）到此结束。你现在有一个完整可用的脱敏引擎，但它还没有接到任何一个通道上——所有测试都是直接调 `engine.apply()`。第三部分开始把它接进 Spring：Jackson、Logback、MyBatis、AOP。

---

## 课后练习

**练习 8.1** 按 8.6 节的思路优化 `MicrometerMaskRecorder`：把 Meter 引用缓存起来。

1. 写出实现（注意 `MeterKey` 的 `equals` / `hashCode`）
2. 用 `MaskingBenchmark` 实测优化效果
3. 这个改动会不会引入内存泄漏风险？在什么条件下会？

**练习 8.2** 8.5 节的结论是「缓存对内置策略是负收益」。设计一个方案，让缓存**只对昂贵的策略生效**。

1. 怎么判断一个策略「昂贵」？给出至少两种方案
2. 实现你选的方案
3. 这个方案会不会让「缓存命中率」这个指标失去意义？

**练习 8.3** 现在要给缓存加一个能力：**高敏感类型（密码、密钥）不进缓存**（第 6 章练习 6.3 的延伸）。

1. 这个开关应该放在 `CaffeineMaskCache` 里还是 `MaskEngine` 里？为什么
2. 写出实现
3. 如果放错了位置，会有什么后果

**练习 8.4** `masking.cache.hit.rate` 是一个 Gauge，它报告的是**自缓存创建以来的累计命中率**。

1. 这个语义在什么场景下会误导人？
2. 更有用的是什么指标？怎么实现？
3. 这个问题在 `masking.cache.eviction` 上也存在吗？

---

## 练习答案

### 练习 8.1

**1. 实现**

```java
public class MicrometerMaskRecorder implements MaskRecorder {

    private record MeterKey(String type, String role, String result) { }

    private final MeterRegistry registry;
    private final Map<MeterKey, Counter> invokeCounters = new ConcurrentHashMap<>();
    private final Map<String, Timer> timers = new ConcurrentHashMap<>();
    private final Map<String, Counter> failCounters = new ConcurrentHashMap<>();
    private final Map<String, Counter> bypassCounters = new ConcurrentHashMap<>();
    private final Map<String, Counter> skippedCounters = new ConcurrentHashMap<>();

    @Override
    public void record(String typeCode, MaskRole role, MaskAction action, Duration duration) {
        String typeTag = (typeCode == null || typeCode.isBlank()) ? UNKNOWN : typeCode;
        String roleTag = role == null ? UNKNOWN : role.name();
        String resultTag = action == null ? UNKNOWN : action.name().toLowerCase(Locale.ROOT);

        invokeCounters.computeIfAbsent(new MeterKey(typeTag, roleTag, resultTag),
                key -> Counter.builder(INVOKE)
                        .tag("type", key.type())
                        .tag("role", key.role())
                        .tag("result", key.result())
                        .register(registry))
                .increment();

        timers.computeIfAbsent(typeTag + "|" + roleTag,
                key -> Timer.builder(DURATION)
                        .tag("type", typeTag)
                        .tag("role", roleTag)
                        .register(registry))
                .record(duration);

        if (action == MaskAction.FAIL) {
            failCounters.computeIfAbsent(typeTag,
                    t -> Counter.builder(FAIL).tag("type", t).register(registry)).increment();
        }
        // bypass / skipped 同理
    }
}
```

**`MeterKey` 用 record 就自动有了正确的 `equals` / `hashCode`**——这是 record 最实用的场景之一。手写的话很容易漏掉某个字段，或者写出和 `equals` 不一致的 `hashCode`。

Timer 的 key 用字符串拼接（`typeTag + "|" + roleTag`）而不是再定义一个 record，是因为只有两个字段。分隔符选 `|` 而不是 `:`，因为类型编码里理论上可能出现 `:`（虽然归一化后不太可能），而 `|` 更不可能。**拼接式复合 key 一定要选一个不会出现在各部分里的分隔符**，否则 `("A|B", "C")` 和 `("A", "B|C")` 会碰撞。

**2. 实测**

在本机（Windows / JDK 21）预期结果：打点开销从约 900ns 降到约 100~200ns 量级，因为热路径变成了「一次 record 构造 + 一次 `ConcurrentHashMap.get` + 一次原子递增」×2。

准确数字需要读者自己跑——这正是本章 8.5 节强调的：**别信预测，跑一遍。** 把 `MaskingBenchmark` 里的 `MicrometerMaskRecorder` 换成优化版，对比 `无缓存 + Micrometer` 那一行。

（一个值得注意的细节：`computeIfAbsent` 在 key 已存在时也有开销，它比纯 `get` 略贵。如果要压到极致，可以先 `get`，null 时才 `computeIfAbsent`。这个「double-check」模式在高频路径上是有意义的。）

**3. 内存泄漏风险**

**在当前设计下没有风险，但条件很明确。**

Map 的大小上界 = 标签组合数：

- `type`：内置 5 个 + 业务自定义的若干个
- `role`：4 个（3 个枚举 + `unknown`）
- `result`：5 个（4 个枚举 + `unknown`）

如果业务有 20 个自定义类型，上界是 25 × 4 × 5 = 500 条。稳定后不再增长，完全安全。

**什么条件下会泄漏：如果 `typeCode` 的取值空间是无界的。**

具体场景：

| 场景 | 说明 |
| --- | --- |
| 类型编码来自不可信输入 | 比如某个接口允许调用方通过参数指定脱敏类型（`?maskType=xxx`）。攻击者可以构造无限多个编码 |
| 编码里混入了动态内容 | 比如有人为了「更细粒度的监控」把编码改成 `PHONE_userService_loadUser`，那么类型数就等于「类型 × 调用点」 |
| 归一化失效 | 如果哪天 `normalize` 被改动，`phone`、`PHONE`、`Phone` 变成三个不同的 key |

**注意这三个场景对原实现（不缓存 Meter）也是灾难**，而且更严重：Micrometer 的 registry 本身就会为每个新标签组合创建一个 Meter 并永久保留。所以 Map 缓存并没有引入新风险，它只是**让已有的风险更明显**（多了一个能看到大小的 Map）。

真正的防护应该在源头：类型编码必须来自受控的集合。可以加一道校验：

```java
if (invokeCounters.size() > MAX_METER_KEYS) {
    // 超过阈值就归到 unknown，并打一次 WARN
    typeTag = UNKNOWN;
}
```

这个「基数熔断」在有大量业务方接入的 starter 里值得加，因为你无法约束所有使用方怎么定义类型编码。

### 练习 8.2

**1. 怎么判断一个策略「昂贵」**

| 方案 | 做法 | 优点 | 缺点 |
| --- | --- | --- | --- |
| **A. 策略自己声明** | 给 `MaskStrategy` 加一个 `default boolean cacheable() { return false; }`，昂贵的策略覆写成 `true` | 简单直接；策略作者最清楚自己贵不贵 | 依赖作者的判断，而作者往往会高估自己代码的开销（「我这有个正则，肯定得缓存」）。第 4 章的邮箱策略作者大概会觉得该缓存，但实测是负收益 |
| **B. 按配置的类型白名单** | `masking.cache.cacheable-types: [EXPRESS, REVERSIBLE]` | 运维可以按实测结果调整，不需要改代码；配合热更新可以在线试 | 需要人工维护；新增类型时容易忘 |
| **C. 运行时自适应** | 引擎统计每个类型 `strategy.mask()` 的平均耗时，超过阈值才启用缓存 | 完全自动，不依赖人的判断 | 复杂度高得多：要维护 per-type 的耗时统计、要处理冷启动、要防抖动。而且统计本身有开销——**为了省几百纳秒引入一个统计系统，可能得不偿失** |
| **D. 按策略类型静态判断** | 检查策略是不是 `AbstractKeepMaskStrategy` 的子类，是就不缓存 | 零配置 | 太脆弱。一个自定义的昂贵策略如果恰好继承了这个基类就会被误判 |

**我选 B（配置白名单），并把默认值设为空列表。**

理由：

- **它把决定权交给了能验证的人。** 运维手里有 `masking.cache.hit.rate` 和 `masking.duration`，可以实测后再决定。而策略作者（方案 A）和自动算法（方案 C）都是在没有生产数据的情况下猜
- **默认空列表意味着默认不缓存**，符合 8.5 节的结论
- **配合第 7 章的热更新可以在线试**：加一个类型进白名单，看 `masking.duration` 的 P99 有没有改善，没有就撤掉。这是最低风险的验证方式

方案 A 可以作为补充（提供一个默认建议），但配置应该能覆盖它。

**2. 实现**

扩展 `MaskSettings`（第 6 章的窄接口），加一个查询：

```java
public interface MaskSettings {
    boolean isEnabled();
    MaskRule ruleOf(String code);

    /** 这些类型的脱敏结果值得缓存。默认空集合，即不缓存任何类型。 */
    default Set<String> cacheableCodes() {
        return Set.of();
    }
}
```

引擎的第 6、8 步加一个判断：

```java
// 第 6 步
boolean cacheable = isCacheable(resolvedCode);
if (cacheable) {
    String cached = cache.get(resolvedCode, raw);
    if (cached != null) {
        return record(cached, resolvedCode, role, MaskAction.MASK, start);
    }
}

// 第 7 步
String masked = strategy.mask(raw, rule);

// 第 8 步
if (cacheable) {
    cache.put(resolvedCode, raw, masked);
}
return record(masked, resolvedCode, role, MaskAction.MASK, start);

private boolean isCacheable(String resolvedCode) {
    Set<String> codes = settings.cacheableCodes();
    return codes != null && codes.contains(resolvedCode);
}
```

注意 `cacheable` 只算一次并复用，而不是在第 6 步和第 8 步各算一次——那会多一次 Set 查找，而这一整个优化就是为了省几百纳秒的。

配置形态：

```yaml
masking:
  cache:
    enabled: true
    cacheable-types: [EXPRESS, REVERSIBLE]
```

`cacheable-types` 里的编码要归一化后存储（和第 6 章练习 6.3 的 `neverBypassCodes` 一样）。

**判断放在引擎里而不是缓存里**，理由和练习 8.3 是同一个，见下题。

**3. 会不会让「命中率」失去意义**

**不会，反而让它变得更有意义。**

当前的 `hitRate` 是「所有类型混在一起的命中率」。假设有两个类型：

- `PHONE`：调用 10000 次，明文几乎不重复，命中 100 次
- `EXPRESS`：调用 100 次，明文高度重复，命中 90 次

混合命中率 = (100 + 90) / 10100 ≈ 1.9%。看起来「缓存完全没用」，于是运维可能会关掉整个缓存——**但 `EXPRESS` 的 90% 命中率是真实收益，被淹没了。**

加了白名单之后，缓存里只有 `EXPRESS`，命中率变成 90%。这个数字**准确反映了缓存的实际价值**。

不过要注意一个副作用：**改了白名单之后，历史的 `hitRate` 数据不可比。** 因为 `hitRate()` 是累计值（这正是练习 8.4 要讨论的问题），改配置后需要重启或者等足够长时间才能看到新的稳态值。

更进一步的改进是**给缓存指标加 `type` 标签**，这样每个类型的命中率可以单独看。Caffeine 的统计是全局的，所以这需要自己维护 per-type 的计数器，或者干脆为每个 cacheable 类型建一个独立的 Caffeine 实例。后者更干净，代价是多几个缓存对象——考虑到 cacheable 类型本来就很少（白名单机制保证了这一点），这个代价可以接受。

### 练习 8.3

**1. 应该放在 `MaskEngine` 里**

理由有三层，从表面到本质：

**表面理由：职责。** `CaffeineMaskCache` 的职责是「按 key 存取字符串」，它不应该知道「哪些业务类型是高敏感的」。把业务规则放进基础设施组件，是分层混乱。

**实际理由：`MaskResultCache` 是接口，有多个实现。** 如果判断放在 `CaffeineMaskCache` 里，那么：

- `MaskResultCache.NO_OP` 不需要这个判断（它什么都不做）
- 测试用的 `CountingCache` / `TrackingCache` 需要**重复实现**这个判断，否则测试行为和生产不一致
- 将来如果有人实现一个 Redis 版的缓存，又要再写一遍

**每个实现都要重复的逻辑，说明它不属于实现，属于调用方。** 这和第 6 章 6.1 节「四个通道各自拼装会导致语义有四份实现」是完全相同的推理。

**根本理由：这是一条安全策略，安全策略必须集中。** 「哪些字段不能进缓存」和「哪些角色能旁路」「哪些类型即使 ADMIN 也要脱敏」是同一类决定，它们都应该在引擎里，因为引擎是**唯一入口**。分散到各处的安全策略必然漂移。

**2. 实现**

```java
// MaskSettings 扩展
default Set<String> neverCacheCodes() {
    return Set.of();
}
```

```java
// MaskEngine 第 6、8 步
boolean cacheable = !isNeverCached(resolvedCode);

if (cacheable) {
    String cached = cache.get(resolvedCode, raw);
    if (cached != null) {
        return record(cached, resolvedCode, role, MaskAction.MASK, start);
    }
}

String masked = strategy.mask(raw, rule);

if (cacheable) {
    cache.put(resolvedCode, raw, masked);
}
return record(masked, resolvedCode, role, MaskAction.MASK, start);
```

配置：

```yaml
masking:
  cache:
    never-cache-types: [PASSWORD, API_KEY, FINGERPRINT]
```

测试要覆盖两个方向：

```java
@Test
@DisplayName("高敏感类型不进缓存")
void neverCachedTypeIsNotStored() {
    settings.neverCache("PASSWORD");
    engine.apply("hunter2", null, "PASSWORD", null);

    assertThat(cache.estimatedSize()).isZero();
    assertThat(cache.stats().requestCount()).as("连查都没查").isZero();
}

@Test
@DisplayName("其他类型照常缓存")
void normalTypeStillCached() {
    settings.neverCache("PASSWORD");
    engine.apply("13812345678", SensitiveType.PHONE, null);

    assertThat(cache.estimatedSize()).isEqualTo(1);
}
```

注意 `requestCount()` 那个断言：**不只是「没写进去」，而是「连读都没读」。** 如果只在 `put` 前判断而忘了 `get`，每次调用都会去查一个永远不存在的 key——功能上没错（安全目标达到了），但白付了 key 构造的开销。

**3. 放错位置的后果**

**如果放在 `CaffeineMaskCache` 里**（而 `NO_OP` 和测试替身没有这个判断）：

| 后果 | 严重程度 |
| --- | --- |
| **测试行为和生产不一致。** 单元测试用 `CountingCache`，它会缓存 `PASSWORD`；生产用 Caffeine，不缓存。于是「密码不进缓存」这个安全性质**在测试里根本没被验证** | **高。** 这是最危险的一种情况：安全机制存在，但保护它的测试是假的。将来有人重构 `CaffeineMaskCache` 时删掉这个判断，所有测试依然绿 |
| 换缓存实现时安全性质丢失 | 高。如果哪天换成 Redis 实现，写的人如果不知道有这条规则，密码就进了 Redis——**而 Redis 是跨进程共享的、通常有更宽松的访问控制、而且经常没开持久化加密** |
| 配置对象要传给缓存 | 中。`CaffeineMaskCache` 得依赖 `MaskSettings` 或至少一个类型集合，破坏了它「只管存取」的简洁性，也让它更难独立测试 |
| 引擎仍然构造了 key 并调用了 `get` | 低。白付一点开销 |

第一条最值得警惕。它是一个更一般问题的例子：**当一个安全性质的实现在「可替换的组件」里，而测试用的替换品没有实现它，那么这个性质就没有被测试保护。** 判断方法很简单：问自己「如果我把这段代码删了，有测试会红吗？」

把判断放在引擎里，答案是「会」——因为引擎是唯一实现，所有测试都经过它。

### 练习 8.4

**1. 累计命中率在什么场景下会误导人**

`CacheStats.hitRate()` = `hitCount / requestCount`，两个都是**自缓存创建以来的累计值**。这意味着它是一个**永不遗忘的平均数**。

三个具体的误导场景：

**场景一：启动初期的冷启动被永久摊薄。**

服务刚启动时缓存是空的，前几千次请求全是 miss。假设启动后 10 分钟内有 10000 次请求、1000 次命中（10%），之后稳定运行到 100 万次请求、90 万次命中。

累计命中率 = (1000 + 900000) / (10000 + 1000000) ≈ 89.2%。

看起来不错，但如果服务刚启动 10 分钟你去看，会看到 10%——**同一个健康的缓存，在不同时间点看到的数字差了 8 倍**，而这个差异纯粹来自采样时刻。

**场景二：流量模式变化被历史数据掩埋。**

服务运行了一周，累计 1 亿次请求，命中率 90%。今天上午某个上游系统改了逻辑，明文的重复度骤降，现在的实际命中率是 5%。

累计命中率的变化：从 90% 降到……大约 89.9%。因为 1 亿次历史数据把今天的几十万次完全淹没了。

**故障发生了，但指标几乎没动。** 这是累计指标最致命的问题——它对**变化**不敏感，而运维关心的恰恰是变化。

**场景三：热更新后的数据不可比。**

第 7 章的热更新会调 `invalidateAll()`，但**不会重置统计**。所以改了规则之后，`hitRate` 里混着「改规则前的命中」和「改规则后的命中」。如果新规则让明文的重复度变了（比如缩短了保留位数，让更多明文映射到同一个结果），这个变化在累计命中率上看不出来。

**2. 更有用的指标**

**核心思路：把累计值换成可以求速率的计数器。**

```java
// 不要暴露 hitRate，而是暴露原始计数
Gauge.builder("masking.cache.hits", cache, c -> c.stats().hitCount())
        .register(registry);
Gauge.builder("masking.cache.misses", cache, c -> c.stats().missCount())
        .register(registry);
```

然后在 Prometheus 里算**窗口内的命中率**：

```promql
rate(masking_cache_hits[5m])
  /
(rate(masking_cache_hits[5m]) + rate(masking_cache_misses[5m]))
```

这个表达式回答的是「**最近 5 分钟**的命中率」，三个误导场景全部解决：

- 冷启动只影响启动后的前 5 分钟
- 流量模式变化在 5 分钟内就反映出来
- 热更新前后的数据自然分开

**实现上有一个细节要注意**：`hitCount()` 是单调递增的累计值，所以应该用 `Gauge` 还是 `Counter`？

严格来说应该用 `FunctionCounter`：

```java
FunctionCounter.builder("masking.cache.hits", cache, c -> c.stats().hitCount())
        .register(registry);
```

`FunctionCounter` 告诉 Micrometer「这是一个单调递增的计数器，只是值由外部函数提供」。这样导出到 Prometheus 时会带上 `_total` 后缀并被识别为 counter 类型，`rate()` 函数才能正确处理（`rate()` 会处理计数器重置，比如服务重启后归零）。

用 `Gauge` 的话，Prometheus 会把它当成可上可下的量，`rate()` 的语义就不对了——这是一个很容易犯的错，而且错了之后 Grafana 图表看起来"正常"，只是在服务重启时会出现巨大的负值尖刺。

**教程版的 `MaskCacheMetrics` 用的是 `Gauge`，这是一个应该修正的地方。**

**3. `eviction` 上也存在这个问题吗**

**存在，但性质不同，而且更容易处理。**

`evictionCount()` 也是累计值，所以「累计淘汰了 100 万次」这个数字本身没有意义——它只说明服务跑了很久。

但和 `hitRate` 有一个关键区别：**`evictionCount` 是单调递增的原始计数，而 `hitRate` 是一个已经被计算过的比率。**

这意味着 `eviction` 的问题是**可以在查询侧解决的**：

```promql
rate(masking_cache_eviction_total[5m])    # 每秒淘汰多少条
```

而 `hitRate` 的问题**无法在查询侧解决**——比率一旦算出来，原始的 hit 和 miss 计数就丢了，你无法从「累计命中率 89.2%」反推出「最近 5 分钟的命中率」。

**结论：暴露原始计数，让查询侧去算比率。** 这是一条通用的指标设计原则：

| | 应该暴露 | 不应该暴露 |
| --- | --- | --- |
| 形式 | 原始计数（单调递增） | 已计算的比率、平均值、百分比 |
| 理由 | 查询侧可以按任意窗口聚合、可以处理重启、可以跨实例求和 | 比率无法再分解；平均值无法跨实例合并；百分比丢失了分母 |

同样的道理适用于「平均耗时」：不要暴露 `avgDuration`，而要暴露 `Timer`（它内部同时记录 count 和 totalTime），让查询侧算 `rate(sum) / rate(count)`。这也是为什么 8.3 节用的是 `Timer` 而不是自己算平均值。

**所以 `MaskCacheMetrics` 的正确实现是：**

```java
public static void bind(MeterRegistry registry, CaffeineMaskCache cache) {
    // size 是真正的「当前状态」，Gauge 是对的
    Gauge.builder("masking.cache.size", cache, CaffeineMaskCache::estimatedSize)
            .register(registry);

    // 三个累计计数，用 FunctionCounter，让查询侧算速率和比率
    FunctionCounter.builder("masking.cache.hits", cache, c -> c.stats().hitCount())
            .register(registry);
    FunctionCounter.builder("masking.cache.misses", cache, c -> c.stats().missCount())
            .register(registry);
    FunctionCounter.builder("masking.cache.evictions", cache, c -> c.stats().evictionCount())
            .register(registry);
}
```

`size` 保持 `Gauge` 是对的——它确实是一个可上可下的当前值，不是累计量。**区分「当前状态」和「累计事件」，是选对指标类型的关键。**
