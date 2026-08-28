# 第 16 章 性能与压测

> **本章目标**：会跑 Demo 自带的 Gatling 脚本，会读 QPS/RT，并和第 8 章微基准对上号——脱敏本身通常不是瓶颈，错误的缓存和打点才是。
> **前置知识**：第 8 章实测数据，第 3 章能启动 Demo。
> **预计时长**：40 分钟（含一次压测等待）。
> **对照代码**：`mask-demo/src/test/java/com/learn/mask/demo/perf/MaskingSimulation.java`

---

## 16.1 怎么跑

脚本故意写得很短：50 用户/秒 × 20 秒，打 `GET /api/jackson/users/1`，Basic `user/user123`。

```java
HttpProtocolBuilder httpProtocol = http
        .baseUrl("http://localhost:8080")
        .acceptHeader("application/json")
        .basicAuth("user", "user123");

ScenarioBuilder jackson = scenario("jackson-user")
        .exec(http("jackson user").get("/api/jackson/users/1"));

{
    setUp(jackson.injectOpen(constantUsersPerSec(50).during(20)))
            .protocols(httpProtocol);
}
```

**两段式启动**，和第 3 章一样：Gatling 是独立 JVM，要打已经起来的进程。

```bash
# 终端 1
mvn -q -DskipTests install
cd mask-demo && mvn spring-boot:run

# 终端 2（仓库根）
mvn -pl mask-demo -Pperf gatling:test
```

报告在 `mask-demo/target/gatling/` 下最新一次目录的 `index.html`。

### 为什么 surefire 排除 `**/perf/**`

`mask-demo/pom.xml`：

```xml
<plugin>
    <artifactId>maven-surefire-plugin</artifactId>
    <configuration>
        <excludes>
            <exclude>**/perf/**</exclude>
        </excludes>
    </configuration>
</plugin>
```

`MaskingSimulation` 继承的是 Gatling 的 `Simulation`，不是 JUnit。普通 `mvn test` 扫到它会当单元测试加载，然后失败或空跑。`perf` profile 才启用 `gatling-maven-plugin`。

不要把压测类放进 `src/test/java` 还不排除——CI 每次都会试图跑 20 秒负载，或者直接报错。

### 默认 50/s 不是目标 QPS

`constantUsersPerSec(50)` 是**注入速率**，不是系统吞吐上限。笔记本上 50 并发用户打一个已启动的 Demo，瓶颈往往在客户端或本机回环，不在脱敏。要逼近「这台机器的上限」，加大 `usersPerSec`，并在独立硬件上跑。脚本注释里的 15000 QPS 见 16.4。

---

## 16.2 四组对照实验

脱敏开销被两层东西包着：HTTP + Jackson + Security + H2。只跑「开着脱敏」的一组，看不出脱敏占多少。至少四组：

| 组 | 配置 | 看什么 |
| --- | --- | --- |
| A | `masking.enabled: true`，`cache.enabled: true`（Demo 默认） | 基线，现状 |
| B | `enabled: true`，`cache.enabled: false` | 和第 8 章「缓存负收益」是否在 HTTP 层仍可见 |
| C | `enabled: false` | 上限：引擎整条链不走 |
| D | `enabled: true`，`channels.jackson: false` | 通道关 vs 总开关。Jackson 透传但 Logback 可能仍开 |

改法：停 Demo，改 `application.yml` 或启动参数，再跑同一条 Gatling 命令。一次只改一个旋钮。

```bash
cd mask-demo
mvn spring-boot:run -Dspring-boot.run.arguments="--masking.cache.enabled=false"
```

记录表在 16.6。四组的注入参数必须相同，否则 QPS 不能比。

第 8 章微基准已经给出**引擎内部**的数量级（本机 JDK 21，非正式 JMH）：

| 场景 | 大约 ns/op |
| --- | --- |
| 无缓存无指标 | 350~400 |
| 缓存命中、无指标 | 550（**更慢**） |
| 无缓存 + Micrometer（每次 `builder().register()`） | 1250~1300 |

HTTP 一次请求是毫秒级。**四组的 QPS 差很可能看不出来。** 这不是实验失败：它证明「别在没测的情况下为了 QPS 关掉脱敏」。能看出来的差异，优先来自数据库、序列化大对象、日志正则（第 10 章），而不是 `keepMask`。

---

## 16.3 怎么读报告

Gatling HTML 里优先看这些，不要只截一张「多少 QPS」的图。

| 指标 | 看什么 | 脱敏场景里的含义 |
| --- | --- | --- |
| 成功请求数 / KO | KO 必须是 0 | 有 403/500 说明账号、路径或热更新测脏了数据 |
| 吞吐（req/s） | 均值接近注入速率 → 系统跟得上；明显低于注入 → 饱和 | 50/s 时若只有 40/s，先查 Demo 是否没起来、是否打错端口 |
| RT p50 / p95 / p99 | 尾延迟 | 脱敏是 CPU 同步计算，不应单独制造长尾；长尾更像 GC、H2、日志 |
| 响应体时间分布 | 是否双峰 | 缓存「有时快有时慢」在 keepMask 上几乎不会出现；双峰更像连接或编译 |

同时看进程：

- **CPU**：脱敏 + Jackson 是 CPU。CPU 打满而 RT 仍低，说明到了这台机器的上限
- **GC**：堆里每个请求有 DTO、JSON 缓冲、可能的打码新字符串。Young GC 频繁但 pause 短是正常的；Full GC 要查有没有把明文当缓存 key 无限涨
- **Micrometer**：`/actuator/metrics/masking.invoke` 的 `result` 必须小写（`mask` 不是 `MASK`）。压测前后差值应约等于「请求数 × 每请求敏感字段数」。对不上说明有的字段没进引擎，或旁路了

不要用压测证明「打码算法正确」。那是第 15 章的精确字符串。压测只证明「这个负载下还活着、延迟可接受」。

---

## 16.4 15000 QPS 该怎么理解

脚本注释：

> Target 15000 QPS is hardware-dependent: raise `usersPerSec` on a dedicated box and record QPS/RT/CPU/GC.

它**不是**验收线。本教程不要求你的笔记本打到这个数。把它写进需求文档当 KPI 会变成：

- 换一台机器就「性能回退」
- 有人关掉脱敏或关掉日志通道来刷数字
- 和正确性测试脱钩

正确用法：在**冻结的环境**（机型、JDK、堆大小、是否连真网卡）上记下四组对照的 QPS/p99，作为**记录**，不是门槛。环境变了就重测，并在表头写清硬件。

若你真的要探上限：逐步加 `usersPerSec`（50 → 200 → 1000…），直到 KO 出现或 p99 不可接受。那一点才是这台机器上这个 Demo 的饱和点。脱敏开/关如果在饱和点附近差 2%，通常不值得为此改引擎。

---

## 16.5 优化手段清单（按优先级）

结合第 8 章和第 10 章，而不是「先加缓存」。

| 手段 | 何时值得 | 注意 |
| --- | --- | --- |
| 默认**关掉** keepMask 的结果缓存 | 几乎总是 | 第 8 章：命中也负收益。AES 可逆路径不要进这个缓存 |
| Meter 引用缓存起来 | 几乎总是 | 现在每次 `Counter.builder().register()`，打点比 `keepMask` 贵 |
| 非 HTTP 内部接口关掉 Jackson | 有大量内部 JSON | `masking.channels.jackson=false`，不要依赖「没加注解」 |
| 日志通道对热路径关或采样 | `%sensitiveMsg` 有多条正则 | 误伤和 CPU 都在这里，不在 `keepMask` |
| 减少 AOP 反射 | 突变通道开着 | IdentityHashMap 仍要走；能用 Jackson 就别开 AOP |
| 自定义策略才考虑缓存 | 正则/加密确实贵 | 策略自己声明 `cacheable()`，见第 8 章练习 |
| 关脱敏冲 QPS | **不要** | 正确性优先；16.2 若 C 组也没快多少，更没理由关 |

Walker 先 skip `java.util` 再处理 Collection 是正确性 bug（第 12 章），修它可能让更多对象被遍历，CPU 略升。这是该付的成本。

---

## 16.6 压测记录表

复制到你的笔记里填。注入保持 `constantUsersPerSec(50).during(20)`，除非你在探上限并在「注入」列写明。

**环境**：OS / JDK / 机器型号 / 堆参数 / Demo 是否本机回环  
**日期**：  
**Git commit**：

| 组 | masking.enabled | cache.enabled | jackson | 注入 (users/s) | 成功数 | KO | 吞吐 req/s | p50 ms | p95 ms | p99 ms | CPU 约 | GC 备注 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| A 默认 | true | true | true | 50 |  |  |  |  |  |  |  |  |
| B 无缓存 | true | false | true | 50 |  |  |  |  |  |  |  |  |
| C 总开关关 | false | * | * | 50 |  |  |  |  |  |  |  |  |
| D 关 Jackson | true | true | false | 50 |  |  |  |  |  |  |  |  |

本教程不填写具体数字：那是你的机器的记录。若 A、B 几乎一样，与第 8 章「HTTP 层看不出缓存负收益」一致。若 B 反而更好，不要惊讶。

---

## 本章小结

- Gatling 在 `perf` profile 里跑，surefire 必须排除 `Simulation`
- 先起 Demo 再压；默认 50/s × 20s 是演示注入，不是 KPI
- 用四组开关对照，不要只跑「开着脱敏」
- 15000 QPS 是环境相关记录项。微基准里缓存和 Micrometer 的结论，到 HTTP 层可能被淹没，但默认开负收益缓存仍然该改
- 优化先打点实现和缓存策略，再动正则和反射，最后才是关通道

---

## 课后练习

**练习 16.1** 若把 `constantUsersPerSec(50)` 改成 `atOnceUsers(10000)`，报告会看起来「更差」。这能说明脱敏更慢了吗？

**练习 16.2** 压测时 `/actuator/prometheus` 里 `masking_invoke_total` 的增量，如何估算「每个请求进了几次引擎」？Demo 的 jackson 用户接口你预期是几次？

**练习 16.3** 有人建议「压测把 `masking.channels.logback=false`，避免正则拖 QPS，上线再打开」。为什么这会让压测失去意义？

---

## 练习答案

### 练习 16.1

不能。`atOnceUsers(10000)` 是瞬间灌 10000 个并发，测的是**队列和线程池崩溃**，不是稳态吞吐。p99 爆炸、KO 飙升是过载形态。脱敏的 CPU 贡献仍然是每请求几百纳秒到几微秒。要比快慢，用相同的开放注入模型，只改 `masking.*`。

### 练习 16.2

压测前后 scrape 两次，差值 ÷ 成功请求数 ≈ 每请求 `apply` 次数。USER 无旁路。`UserDto` 上 phone、idCard、email、bankCard、expressNo，加 address.detail、contacts[0]（Demo 数据一条联系人），大约 **7 次**量级；Map 接口键更多。对不上时查：ADMIN 误用、通道关了、字段没注解也没进 map-keys。

### 练习 16.3

日志通道是生产泄漏面和 CPU 双料来源。压测关掉它，得到的 QPS 是「没有日志脱敏的系统」。上线打开后延迟和误伤都没被测到。若生产就是关 Logback，压测也关，并在记录表写明。默认 Demo 是开的，对照实验应单独加一组「仅关 logback」，不要偷偷关。
