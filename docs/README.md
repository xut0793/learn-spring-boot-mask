# Spring Boot 数据脱敏：从零到生产

一份面向零基础读者的动态数据脱敏实战教程。以本仓库的 `mask-starter` / `mask-demo` 为唯一示例，带你从「一个 if-else 的打码函数」一路写到「可配置、可热更新、可观测、支持角色控制与可逆还原的企业级脱敏 starter」。

## 这份教程适合谁

| 你的情况 | 建议 |
| --- | --- |
| 会写 Java，用过 Spring Boot 写过 Controller，没做过脱敏 | 正好，从第 1 章顺读 |
| 只知道「脱敏就是把手机号中间改成星号」 | 正好，第 1~3 章会把认知补齐 |
| 已经用注解 + AOP 做过脱敏，想知道怎么做成 starter | 可从第 6 章直接切入 |
| 只想抄一段能用的代码 | 看第 3 章跑通 Demo，再看第 9 章 |

前置知识只要求：Java 基础语法、Maven 基本用法、知道 `@RestController` 是干什么的。注解、反射、AOP、策略模式、Jackson 序列化这些都会在用到时从零讲起。

## 环境要求

| 项 | 版本 | 说明 |
| --- | --- | --- |
| JDK | 21 | 教程用到 `record`、`switch` 表达式、文本块 |
| Maven | 3.9+ | |
| Spring Boot | 4.0.7 | 注意 Web 依赖是 `spring-boot-starter-webmvc`，不是 Boot 3 的 `starter-web` |
| Jackson | 3.x | 包名是 `tools.jackson.*`，API 与 Jackson 2 有断裂式变化，第 9 章详述 |
| 数据库 | H2 内存库 | Demo 自带 `schema.sql` / `data.sql`，无需安装 |

不需要 Docker、不需要外部 Prometheus / Grafana。

## 怎么读

**路径 A · 完整学习（推荐，约 10~14 小时）**
第 1 章 → 第 18 章顺读。每章都有「动手写」和「验证」环节，跟着敲完你会得到一个自己写的脱敏 starter。

**路径 B · 快速上手（约 1.5 小时）**
第 3 章跑通 Demo → 第 2 章建立心智模型 → 第 9 章 Jackson 通道 → 第 13 章通道协同 → 第 17 章避坑。够你在自己项目里落地一个 Web API 脱敏。

**路径 C · 查阅**
直接看附录 A（配置项全表）、附录 B（接口速查）、附录 D（章节↔源码对照）。

## 教程代码放在哪

本仓库的 `mask-starter/` 和 `mask-demo/` 是**成品**，教程不修改它们一行代码。

教程里「跟着敲」的代码单独放在 `mask-tutorial/` 模块：

```
mask-tutorial/
  pom.xml                                    # 独立 parent，不挂进根 pom 的 <modules>
  src/main/java/com/learn/mask/tutorial/
    ch04/ ...  # 策略模式与内置策略
    ch05/ ...  # 角色上下文
    ch06/ ...  # 脱敏引擎
    ch07/ ...  # 配置化与热更新
    ch08/ ...  # 缓存与指标
    ch09/ ...  # Jackson 通道
    ch10/ ...  # Logback 通道
    ch11/ ...  # MyBatis 通道
    ch12/ ...  # AOP 通道
    ch14/ ...  # AES 可逆脱敏
  src/test/java/com/learn/mask/tutorial/
    ch04/ ... ch14/                          # 各章验证用例 + 课后练习答案
```

三条重要约定：

1. **原项目零改动。** `mask-tutorial/pom.xml` 的 parent 直接指向 `spring-boot-starter-parent`，**不挂进根 `pom.xml` 的 `<modules>`**。所以在项目根跑 `mvn clean test` 的行为和现在完全一样，教程代码不会污染原有构建。教程代码单独跑：`mvn -f mask-tutorial/pom.xml test`。
2. **按章分包，每包是该章结束时的完整快照。** 代价是有少量重复（比如 `MaskUtils` 在多个章节包里各有一份），换来的好处是每章都能独立编译跑测试，而且你可以直接 `diff ch05 ch06` 看清这一章到底改了什么。
3. **每章末尾有「对照真实实现」一节**，列出你写的版本与 `mask-starter` 对应文件的差异——成品多做了什么、为什么。这样既有从零的手感，最后又能收敛到生产级代码。

文档、教程代码都在 `docs/tutorial` 分支上，`main` 分支保持原始代码不动，随时可 diff、可回滚。

## 每章的固定结构

```
本章目标 / 前置知识 / 预计时长
1. 问题场景        —— 先讲痛点，不直接上代码
2. 原理            —— 含 Mermaid 图
3. 动手写：第一版   —— 可直接敲的完整代码
4. 暴露的问题       —— 用一个失败的实验暴露出来
5. 重构到生产可用   —— 完整代码
6. 验证            —— curl 或单测，给出预期输出
7. 对照真实实现     —— 与 mask-starter 的差异 + 文件行号
8. 优缺点与适用场景  —— 仅第 9~12 章
9. 课后练习         —— 2~3 题，附答案
```

---

# 目录

## 第一部分 · 认知篇：先搞清楚在做什么

### [第 1 章 五分钟看懂数据脱敏](01-what-is-data-masking.md)

- 1.1 一个例子讲透：`13812345678` → `138****5678`，为什么不是简单 `replace`
- 1.2 脱敏 ≠ 加密 ≠ 哈希（三者用途对比）
- 1.3 为什么必须做：合规要求、防泄漏、风险兜底
- 1.4 静态脱敏 vs 动态脱敏，以及本项目为什么选动态
- 1.5 五种常用脱敏算法（替换、截断、加密、混淆、掩码）
- 1.6 一个好的脱敏方案要满足什么（不可逆、业务一致、高性能、可控、可扩展）

### [第 2 章 在 Spring Boot 里，脱敏该在哪一刀切下去](02-where-to-hook-in-spring.md)

- 2.1 画一条数据流：DB → MyBatis → Service → Controller → Jackson → HTTP，外加一条日志支线
- 2.2 四个切入点各自拦在哪个环节
- 2.3 **本教程最重要的心智模型：出口通道 vs 突变通道**
  - 出口通道（Jackson / Logback）：不改内存对象，只改写出去的字节
  - 突变通道（AOP / MyBatis）：把 Java 对象里的明文真的替换掉了
  - 后面几乎所有坑都从这个区分派生
- 2.4 四方案优缺点与适用场景对比表
- 2.5 为什么项目要把四个都实现，而不是挑一个
- 2.6 设计三原则：低侵入、高内聚、可扩展

### [第 3 章 先跑起来：10 分钟体验 Demo](03-run-the-demo-in-10-min.md)

- 3.1 启动：`mvn -q -DskipTests install` 然后 `cd mask-demo && mvn spring-boot:run`
  （附：为什么 `-pl mask-demo -am spring-boot:run` 会失败）
- 3.2 三个演示账号：`admin/admin123`、`user/user123`、`cs/cs123`
- 3.3 七个实验，先看现象不问原理
  - 实验一：`user` 请求用户接口 → 六类敏感字段全部打码，并逐个核对星号数量是怎么算出来的
  - 实验二：`admin` 请求同一接口 → 全是明文（角色旁路）
  - 实验三：带 `X-User-Role: ADMIN` 头 → 调试覆盖生效
  - 实验四：`cs` 调 `/api/unmask` → 拿回明文 + 每次不同的 token；`user` 调 → 403
  - 实验五：看控制台日志 → 明文被 Logback 通道拦住了；**但 `ADMIN` 请求产生的日志是明文**（角色旁路对日志同样生效，这个行为需要评估）
  - 实验六：`POST /api/admin/masking/reload` 改规则 → 不重启，下一次请求立即生效，`ruleVersion` 自增
  - 实验七（加分）：四通道横向对比 → `/api/db` 的 `addressDetail` 漏了脱敏，暴露 MyBatis 通道「逐列显式指定」的固有弱点
- 3.4 看一眼 `/actuator/metrics/masking.invoke`：脱敏是可观测的（含 `result` 标签必须小写这个坑）
- 3.5 现象锚点表：把你看到的每个现象映射到解释它的章节

## 第二部分 · 内核篇：脱敏引擎是怎么造出来的

### [第 4 章 从一个 if-else 到策略模式](04-strategy-pattern-engine.md)

- 4.1 反面教材：`if (type == PHONE) ... else if ...` 为什么会腐烂
- 4.2 策略接口只需 4 个方法：`type()` / `code()` / `mask()` / `alreadyMasked()`
- 4.3 为什么要有第 4 个方法 `alreadyMasked`（伏笔，第 6 章揭晓）
- 4.4 通用能力下沉：`keepPrefix + maskChar 填充 + keepSuffix`
- 4.5 `AbstractKeepMaskStrategy` + 四个内置策略几乎零代码
- 4.6 特例：邮箱为什么不能继承基类（要保留 `@domain`）
- 4.7 策略注册表：按 code 索引、大小写归一、未命中回落 `CUSTOM`
- 4.8 扩展实战：加一个快递单号策略，**不改枚举、不改 starter**，只加一个 `@Component`

### [第 5 章 谁能看明文：角色上下文](05-role-context.md)

- 5.1 三种角色：ADMIN 旁路 / USER 始终脱敏 / CS 可申请还原
- 5.2 角色解析的健壮性：去 `ROLE_` 前缀、大小写不敏感、`CUSTOMER_SERVICE` 视作 `CS`
- 5.3 两条来源与优先级：调试 Header > Spring Security
- 5.4 `catch (NoClassDefFoundError)` 这个「奇怪」的写法——为什么它让 Security 成为可选依赖
- 5.5 ThreadLocal 与过滤器：**为什么 `finally` 里必须 `remove`**，附一个线程池串角色的事故复盘
- 5.6 生产环境必须关闭 Header 覆盖

### [第 6 章 MaskEngine：把所有决策收拢到一个方法](06-mask-engine.md)

- 6.1 为什么需要一个「唯一入口」：四个通道不能各写一套逻辑
- 6.2 逐行读 `apply()` 的判定链（含流程图）：
  空值/总开关 → 角色旁路 → 策略与规则查找 → 幂等跳过 → 缓存命中 → 执行脱敏 → 写缓存 → 异常计数
- 6.3 判定顺序本身就是设计：便宜的判断放前面
- 6.4 `MaskAction` 四态如何变成可观测的指标标签
- 6.5 幂等跳过的价值：多通道叠加时不会打成 `138*********`
- 6.6 为什么 `AlreadyMaskedDetector` 这么「薄」——判定下沉到各策略自己

### [第 7 章 配置化与热更新](07-configuration-and-hot-reload.md)

- 7.1 硬编码规则的问题：改个「保留后 4 位」要重新发版
- 7.2 `@ConfigurationProperties` 全景：`enabled` / `channels` / `bypass-roles` / `cache` / `debug` / `reversible` / `rules`
- 7.3 规则查找的三级回落：自定义 extras → 内置枚举 → 动态创建
- 7.4 `map-keys` / `extra-map-keys`：没有注解也能按字段名脱敏
- 7.5 热更新怎么做到免重启：改内存配置 + 版本号自增 + 清缓存
- 7.6 **缓存 key 为什么要带 ruleVersion**：规则一变旧结果自动失效，比手动清缓存可靠

### [第 8 章 缓存与指标：让脱敏不成为瓶颈](08-cache-and-metrics.md)

- 8.1 脱敏到底慢在哪：字符串拼接、反射、正则
- 8.2 Caffeine 上手：`maxSize` / `expireAfterAccess`
- 8.3 什么能缓存、什么绝不能缓存（可逆加密用随机 IV，缓存即错误）
- 8.4 缓存 key 的安全考量：明文进了内存缓存，风险边界在哪
- 8.5 Micrometer 打点：`masking.invoke{type,role,result}` + `duration` + `fail` / `bypass` / `skipped`
- 8.6 从 `/actuator/metrics` 与 `/actuator/prometheus` 读出四个业务问题的答案

## 第三部分 · 实战篇：四个通道逐个手写

### [第 9 章 通道一 · Jackson 3 序列化脱敏（主路径，最常用）](09-channel-jackson.md)

- 9.1 Jackson 序列化流程与 `@JsonSerialize` 的作用点
- 9.2 **Boot 4 / Jackson 3 的三个断裂变化**：包名变 `tools.jackson.*`、`ValueSerializer` 取代 `JsonSerializer`、`ContextualSerializer` 被移除
- 9.3 组合注解：`@JacksonAnnotationsInside` 怎么把 `@JsonSerialize` 藏进业务注解里
- 9.4 核心难点：`createContextual` 为什么必须返回**新实例**——序列化器是共享的，字段规则是各自的
- 9.5 字段规则解析的两级优先：注解 → 字段名映射
- 9.6 复杂结构：嵌套对象、`List<T>`、`Map`（递归序列化器 + 包装类型）
- 9.7 Jackson 拿不到 Spring Bean 的难题，与「静态桥」这个折中方案
- 9.8 优缺点：集中管理、不改内存对象 / 管不到内部服务调用

### [第 10 章 通道二 · Logback 日志脱敏（最容易被忽视的泄漏口）](10-channel-logback.md)

- 10.1 为什么接口脱敏挡不住 `log.info("phone={}", user.getPhone())`
- 10.2 `ClassicConverter` 与 `%msg` 的关系，自定义 `%sensitiveMsg`
- 10.3 正则识别四类敏感数据，以及**为什么 EMAIL 必须排在 BANK_CARD 之前**
- 10.4 注册链路：starter 的 `masking-converter.xml` → 业务的 `logback-spring.xml`
- 10.5 用 `ListAppender` 断言「日志里不出现明文」
- 10.6 优缺点：无侵入、覆盖全部日志 / 正则有误伤和漏判、有性能成本

### [第 11 章 通道三 · MyBatis TypeHandler 查询即脱敏](11-channel-mybatis.md)

- 11.1 `BaseTypeHandler` 在结果映射流程中的位置
- 11.2 只脱「读出」不改「写入」：`setNonNullParameter` 为什么必须原样透传
- 11.3 **最大的坑：TypeHandler 绝不能注册成 Spring Bean**（会变成全局 String 处理器，所有字符串都被打码）
- 11.4 正确用法：`@Result(typeHandler = ...)`，以及为什么要预置四个子类
- 11.5 代价：应用层再也拿不到明文 → 可逆还原失效、缓存与回写风险
- 11.6 优缺点与适用场景

### [第 12 章 通道四 · `@Sensitive` + AOP 显式脱敏](12-channel-aop.md)

- 12.1 `@SensitiveMethod` 切点与 `@Around` 环绕通知
- 12.2 反射递归改写内存对象：Bean 字段、Collection、数组、Map 各怎么处理
- 12.3 三个必须处理的工程细节：`IdentityHashMap` 防循环引用、跳过 JDK / 框架包、遍历父类字段
- 12.4 突变语义的后果演示：改过的对象再走一次 Jackson 会怎样
- 12.5 优缺点：不依赖 HTTP、适合 RPC 与内部服务 / 改内存、反射有成本

### [第 13 章 四通道协同：`masking.channels.*` 与三层防线](13-multi-channel-cooperation.md)

- 13.1 同一个字段被脱敏两次会发生什么（先看事故）
- 13.2 第一层 · 配置层（主控）：关闭的通道直接透传，不进引擎
- 13.3 第二层 · 引擎层（兜底）：幂等跳过，指标记 `skipped_already_masked`
- 13.4 第三层 · 启动层（护栏）：冲突组合打 WARN，`strict=true` 直接启动失败
- 13.5 三种推荐组合与理由：典型 Web API / 无 HTTP 只对返回值打码 / 查询即打码
- 13.6 用五个 profile 逐个验证：默认 / `jackson` / `aop` / `mybatis` / `conflict`

## 第四部分 · 生产篇：从「能用」到「敢上线」

### [第 14 章 可逆脱敏：客服要看完整手机号怎么办](14-reversible-masking.md)

- 14.1 展示脱敏与可逆脱敏是**两种语义，不要混用**
- 14.2 AES-GCM 实现：为什么选 GCM、为什么 IV 必须随机、`Base64(IV + 密文)` 的封装格式
- 14.3 `reversible = true` 只是一个「许可标记」——响应里永远是打码值
- 14.4 还原接口的双重校验：Security 路径规则 + 运行时角色判定
- 14.5 密钥管理的现实差距：项目当前是「补齐 / 截断到 32 字节」，生产该上 KMS 或 Vault
- 14.6 为什么不能把密文直接吐给前端

### [第 15 章 测试策略：怎么证明脱敏是对的](15-testing-strategy.md)

- 15.1 测试反模式：只断言「值变了」而不断言「变成了什么」
- 15.2 单元测试：策略正确性、`alreadyMasked` 幂等、AES 往返、通道冲突校验
- 15.3 集成测试：`MaskingApiTest` 11 个用例逐个解读（三角色、Header 覆盖、嵌套 / List / Map、单通道生效、通道关闭透传、热更新、还原鉴权）
- 15.4 日志测试：断言明文不出现在任何 appender
- 15.5 一份脱敏功能的测试用例检查清单

### [第 16 章 性能与压测](16-performance-and-load-test.md)

- 16.1 Gatling 脚本与 `perf` profile 的跑法，以及 surefire 为什么要排除 `**/perf/**`
- 16.2 四组对照实验：脱敏开 / 关 × 缓存开 / 关
- 16.3 怎么读压测报告：QPS、RT 分位、CPU、GC 各看什么
- 16.4 15000 QPS 该怎么理解——它是环境相关的记录项，不是功能验收线
- 16.5 优化手段清单：缓存、非敏感接口关闭通道、减少反射、避免正则回溯
- 16.6 一份可直接填的压测记录表

### [第 17 章 避坑指南](17-pitfalls-and-troubleshooting.md)

每条按「现象 → 排查路径 → 根因 → 修法」组织。

- 17.1 注解不生效：三步定位（通道开关 → 自动配置是否加载 → Jackson 是 3 还是 2）
- 17.2 日期、数字等非字符串类型怎么处理（默认不动，避免破坏 JSON 类型）
- 17.3 脱敏后数据无法回显 / 打码值被写回了数据库
- 17.4 二次脱敏成 `138*******`
- 17.5 线程池导致的角色错乱
- 17.6 生产环境残留了 `header-role-enabled: true`
- 17.7 MyBatis TypeHandler 让所有字符串都被打码
- 17.8 **成品代码现存的几个可改进点**（留给读者的进阶练习）：缓存命中未单独打点、未知 code 会无界写入规则表、`ADDRESS` 类型在 starter 里没有默认策略、`@Sensitive` 混用了 Jackson 2 与 Jackson 3 两套注解包

### [第 18 章 复盘：为什么这个 starter 长这样](18-starter-design-review.md)

- 18.1 依赖边界：`annotation` / `strategy` / `engine` 零框架依赖，通道依赖全部 `optional`
- 18.2 三个条件注解的分工：`@ConditionalOnClass` / `@ConditionalOnBean` / `@ConditionalOnMissingBean`
- 18.3 `AutoConfiguration.imports` 与自动配置的装载顺序
- 18.4 静态桥模式：什么时候是必要的折中，什么时候是坏味道
- 18.5 移植到自己项目的检查清单
- 18.6 如果重新设计，我会改哪三个地方

## 附录

- [附录 A · `masking.*` 配置项全表](appendix-a-config-reference.md)（含默认值、生效通道、热更新是否支持）
- [附录 B · Demo 接口与账号速查表](appendix-b-demo-api-cheatsheet.md)（含可直接复制的 curl）
- [附录 C · 术语表](appendix-c-glossary.md)（脱敏 / 打码 / 旁路 / 幂等 / 通道 / 突变 / 出口）
- [附录 D · 章节 ↔ 源码对照表](appendix-d-chapter-to-source-map.md)

---

## 章节与源码对照速览

| 章 | 主要源码 |
| --- | --- |
| 4 | `mask-starter/.../strategy/*`、`support/MaskUtils`、`mask-demo/.../mask/*` |
| 5 | `context/MaskContext`、`MaskRole`、`HeaderRoleFilter` |
| 6 | `engine/MaskEngine`、`AlreadyMaskedDetector`、`MaskAction` |
| 7 | `config/MaskingProperties`、`MaskRule`、`web/MaskingReloadController` |
| 8 | `cache/MaskCache`、`metrics/MaskingMetrics` |
| 9 | `annotation/Sensitive`、`jackson/*`、`config/JacksonMaskingAutoConfiguration` |
| 10 | `logback/SensitiveMessageConverter`、`masking-converter.xml`、`mask-demo` 的 `logback-spring.xml` |
| 11 | `mybatis/*`、`mask-demo/.../mapper/UserMaskedMapper` |
| 12 | `aop/*`、`config/AopMaskingAutoConfiguration` |
| 13 | `config/MaskingChannelValidator`、`mask-demo` 的 `application-*.yml` |
| 14 | `crypto/*`、`mask-demo/.../web/UnmaskController`、`config/SecurityConfig` |
| 15 | 两个模块的 `src/test/**` |
| 16 | `mask-demo/.../perf/MaskingSimulation`、`mask-demo/pom.xml` 的 `perf` profile |
| 18 | 全部 `config/*`、两个 `pom.xml`、`AutoConfiguration.imports` |

## 全书 Mermaid 图清单

| 图 | 所在章节 |
| --- | --- |
| 数据流总览：从 DB 到 HTTP 响应与日志 | 2.1 |
| 四个切入点在数据流上的位置 | 2.2 |
| 出口通道 vs 突变通道的对象状态变化 | 2.3 |
| 策略注册与查找（含回落 CUSTOM） | 4.7 |
| 角色解析优先级判定 | 5.3 |
| `MaskEngine.apply` 完整判定链流程图 | 6.2 |
| 缓存 key 与 ruleVersion 的关系、热更新时序 | 7.5 |
| Jackson `createContextual` 时序图 | 9.4 |
| Logback 日志转换链 | 10.2 |
| MyBatis 结果映射时序 | 11.1 |
| AOP 环绕通知与对象递归遍历 | 12.2 |
| 三层防线 | 13.2 |

## 写作进度

- [x] `README.md` 索引与大纲
- [x] 第一批：第 1~3 章（认知 + 跑起来）
- [ ] 第二批：第 4~8 章（引擎内核，代码量最大）
- [ ] 第三批：第 9~13 章（四通道 + 协同）
- [ ] 第四批：第 14~18 章 + 四个附录
- [ ] `mask-tutorial/` 模块骨架与各章代码

## 参考资料

- 本仓库 [`requirement.txt`](../requirement.txt)：原始教学大纲
- 本仓库 [`plan.md`](../plan.md)：工程实现规格与决策记录
- [SpringBoot 数据脱敏实战：构建企业级敏感信息保护体系](https://cloud.tencent.com/developer/article/2654477)
- [通用数据脱敏设计方案（Spring Boot 企业级实践）](https://blog.csdn.net/suprezheng/article/details/156912575)
