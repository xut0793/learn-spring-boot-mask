# 第 17 章 避坑指南

> **本章目标**：线上「没打码 / 打成两层星 / 明文进了库」时，按现象走到文件，而不是凭感觉改注解。
> **前置知识**：第 2 章出口 vs 突变，第 5~14 章。
> **预计时长**：45 分钟。
> **每条结构**：现象 → 排查路径 → 根因 → 修法。

---

## 17.1 注解不生效：接口仍是明文

**现象**：字段写了 `@Sensitive(type = PHONE)`，USER 调接口，JSON 里还是 `13812345678`。

**排查路径**（按顺序，不要跳）：

1. **通道开关**  
   `GET /api/admin/masking/rules`（需 ADMIN）看 `channels.jackson`。或对一下当前 profile：`application-aop.yml` 会关 Jackson。关了 Jackson、方法又没 `@SensitiveMethod`，注解只是废纸。
2. **自动配置是否加载**  
   启动日志有没有 `MaskingAutoConfiguration`。依赖是否真的是 `mask-starter`。本机多模块有没有忘了 `install`。
3. **Jackson 是 3 还是 2**  
   Boot 4 的包是 `tools.jackson.databind.annotation.JsonSerialize`。若业务模块仍用 `com.fasterxml.jackson.databind.annotation.JsonSerialize`，组合注解挂不上，`createContextual` 不会跑。第 9 章。
4. **漏了 `@JacksonAnnotationsInside`**（仅教程第 9 章的组合注解路径）  
   starter 的 `@Sensitive` 已不含 `@JsonSerialize`。自己包一层业务注解且仍走教程写法时，必须有这一行。
5. **静态桥没绑上**  
   `@Sensitive` JSON 走 Module，未就绪会抛错而不是写明文。`SensitiveMapView` / 启动早期日志仍依赖桥；Logback 未 bind 会盖星号。
6. **角色旁路**  
   其实是 ADMIN，或 `X-User-Role: ADMIN` 还开着。先确认当前角色，再查序列化器。

**根因**：最常见是 1 + 3 + 5。注解在，通道不在；或 Jackson 版本对不上；或桥为空。

**修法**：对应打开 Jackson；统一 Jackson 3；保证 `MaskingAutoConfiguration` 先于第一次序列化 bind。生产关闭 Header。不要先怀疑 `keepMask`。

---

## 17.2 日期、数字等非字符串

**现象**：`LocalDate birthday`、`BigDecimal amount` 加了 `@Sensitive`，序列化报错，或 JSON 类型从数字变成了字符串星号。

**排查路径**：看序列化器的泛型。starter 的 `SensitiveValueSerializer extends ValueSerializer<String>`。非 String 字段不会走它；硬套会导致 Jackson 选错序列化器。

**根因**：脱敏算法按字符保留前后缀，对日期和金额没有合法「打码形态」。改成字符串会破坏契约（前端 `typeof`、OpenAPI）。

**修法**：默认**不动**这些字段。金额用权限（能看 / 不能看），不要改成 `***`。必须遮日期时，单独做 `ValueSerializer<LocalDate>`，输出仍是 JSON 字符串且格式稳定（例如只留年份），不要复用 `keepMask`。

---

## 17.3 打码值被写回数据库 / 回显失败

**现象**：编辑页打开是 `138****5678`，用户没改手机号就保存，库变成星号。或短信发到 `138****5678`。

**排查路径**：

1. 这条数据最后一次出站走的是哪个通道？AOP / MyBatis 会改内存或查询结果
2. 更新接口的入参是不是「把 GET 的 JSON 原样 POST 回来」
3. 还原接口是否误从已打码的对象取字段

**根因**：突变通道的语义就是「对象里不再有明文」。出口通道（Jackson）不会造成这个事故。

**修法**：

- Web 回显：只用 Jackson 通道，GET 打码、POST 的 body 不要写回敏感列，除非用户真的改了（用「未变更」哨兵或分字段 PATCH）
- 内部要明文：不要对该 Service 开 AOP / MyBatis
- 已经写进库的星号：只能从备份或未走突变通道的副本恢复，打码不可逆

---

## 17.4 二次脱敏成 `138*******`

**现象**：手机号变成前 3 后一串星，或快递单正则套了两次。

**排查路径**：

1. `masking.channels`：jackson 是否和 aop/mybatis 同时为 true（Demo 默认就是，靠幂等撑着）
2. 指标 `masking.invoke` 的 `result=skipped_already_masked` 是否随请求涨。不涨说明第二次没被判定为「已打码」
3. 自定义策略有没有实现 `alreadyMasked`。`CUSTOM` / 正则最容易漏
4. 缓存是否在幂等之前写入——引擎必须先 SKIP 再 cache put

**根因**：`keepMask` 对「已经是 138****5678」再跑一次，前 3 后 4 仍是同一串，**接口测试可能仍绿**。非 keepMask（加密、正则替换）第二次会明显变坏。内存明文已丢（17.3）。

**修法**：配置层关掉冲突组合；`strict: true` 让错误组合起不来。补 `alreadyMasked`。不要用「再套一层星号更安全」当设计。

---

## 17.5 线程池里角色错乱

**现象**：偶发 USER 看到明文，或 ADMIN 看到打码；压测时更明显。

**排查路径**：

1. 业务是否 `@Async`、线程池、`CompletableFuture` 里读 `MaskContext`
2. `HeaderRoleFilter` 的 `finally` 有没有 `clearHeaderRole()`（starter 有）
3. 自己写的 Filter / TaskDecorator 有没有把 ThreadLocal **拷到子线程还没清**

**根因**：`MaskContext` 的 Header 角色是 ThreadLocal。线程复用后，上一个请求的 `ADMIN` 还在。或者子线程拿不到父线程的角色，回落到 USER / 空。

**修法**：Filter `finally` 必须清。跨线程传递要显式 wrap，并在子线程 `finally` 再清。不要在公共线程池里依赖「请求开始时 set、结束时自然消失」。第 5 章反例 `LeakyHeaderRoleFilter`。

---

## 17.6 生产还开着 `header-role-enabled`

**现象**：任意登录用户加 `X-User-Role: ADMIN` 就能看明文。`MaskingApiTest.headerRoleOverridesMasking` 在 Demo 里是绿的。

**排查路径**：`application.yml` / 环境变量 `masking.debug.header-role-enabled`。生产 profile 必须是 `false`（starter 默认已是 false，**Demo 为了第 3 章实验改成了 true**）。

**根因**：调试开关当功能用。Security 认证过了，角色却被请求头覆盖。

**修法**：生产配置显式写 `false`。网关丢掉 `X-User-Role`。审计：日志里打「本次实际角色」，和 Security 主体对不上就告警。不要用这个头做真实鉴权。

---

## 17.7 MyBatis TypeHandler 让所有字符串都被打码

**现象**：用户名、城市、订单状态全部变成星号；或插入后库里就是星号。

**排查路径**：有没有 `@Bean` / `@MappedTypes(String.class)` 注册了 `SensitiveTypeHandler`。`MybatisMaskingAutoConfiguration` 是空的，就是为了阻止这件事。

**根因**：Handler 一旦成为全局 `String` 处理器，`getNullableResult` 对每一列调用 `engine.apply`。写入若误实现成打码，库被毁。

**修法**：只在 `@Result(typeHandler = PhoneSensitiveTypeHandler.class)` 上按列声明。Handler 不做 Spring Bean。`setNonNullParameter` 必须原样透传。第 11 章。

---

## 17.8 成品代码现存的可改进点

这些不是读者配错。表里划掉的是 starter 已经改掉的；没划掉的仍适合当进阶练习。教程正文不改 `mask-tutorial` 的教学路径。

| # | 点 | 何处 | 后果 |
| --- | --- | --- | --- |
| 1 | ~~缓存默认开~~ | starter 已 `cache.enabled=false`；Demo 为上课打开 | 生产跟 starter |
| 2 | ~~`MaskCache` 未 `recordStats()`~~ | 已开启，Gauge 在 `MaskingCacheMetricsAutoConfiguration` | — |
| 3 | Micrometer 每次 `builder().register()` | 已按标签缓存 Meter 引用 | 再优化空间小 |
| 4 | ~~`ruleOf` 对未知 code `computeIfAbsent`~~ | 已改为只读快照，未命中回落 `CUSTOM` | — |
| 5 | 自定义 code 未注册策略 | `MaskStrategyRegistry` | 回落 `CUSTOM`；`extras` 规则仍可能命中，结果不符且无 WARN |
| 6 | ~~`@Sensitive` 混用两套 Jackson 包~~ | 注解已纯净；Jackson 走 `SensitiveJacksonModule` | 教程第 9 章仍教组合注解 |
| 7 | ~~桥未 bind 则 Jackson fail-open~~ | `@Sensitive` 走 Module；未 bind 抛错；Logback 盖星号 | Map 视图仍走桥 |
| 8 | ~~Walker 先 skip 再 Collection~~ | starter 已先处理 Collection/Map | — |
| 9 | ~~reload 无条件覆盖 `maskChar` / `enabled`~~ | 已改为 `RulePatch`，`null` 表示不改 | — |
| 10 | `reversible` 注解不被 Unmask 使用 | `UnmaskTicketService` 读 `masking.reversible.fields` | 与注解两套名单 |
| 11 | ~~密钥补零 / 截断~~ | 非 16/32 字节直接启动失败 | — |
| 12 | Demo 默认 `header-role-enabled: true` | `application.yml` | 拷贝 Demo 配置上生产即提权 |
| 13 | Demo 默认四通道全开且 `strict: false` | 同上 | 靠幂等掩盖突变，内存明文可能已丢 |

这些不是读者配错。表里划掉的是 starter 已经改掉的；没划掉的仍适合当进阶练习。教程 walker 与 starter 顺序已经对齐。

---

## 本章小结

- 没打码：先通道和 Jackson 版本，再桥和角色，最后才怀疑算法
- 回写和二次星号：先分清出口还是突变
- ThreadLocal 和 Header 开关是生产级事故，不是小配置
- TypeHandler 不做 Bean
- 17.8 的表是读完本书后对成品最有用的一张清单

---

## 课后练习

**练习 17.1** 用户反馈「偶发明文」。给出你的前三个检查项（不要一上来重写策略）。

**练习 17.2** 为第 8 条（Walker 顺序）写一条**失败测试的断言意图**（不要求改 starter）：返回 `List<UserDto>` 的 `@SensitiveMethod`，starter 现状应得到什么、教程应得到什么。

**练习 17.3** 17.8 第 4 条：把 `computeIfAbsent` 改成什么，才能既允许合法 extras，又不被乱码 code 撑爆 Map？

---

## 练习答案

### 练习 17.1

1. 该请求的角色和 Header：是不是 ADMIN / 调试头。看日志里的角色，对一下 Security 主体  
2. `MaskingSpringBridge.engine()` 是否偶发 null（启动早期、热部署、测试污染）  
3. 是否只有部分实例 / 部分通道关了（多实例配置不一致）

不要先改 `keepMask`。偶发几乎不是算法问题。

### 练习 17.2

```text
方法：List<UserDto> list()
starter：list[0].phone 仍是 13812345678（ArrayList 被 shouldSkip）
教程：list[0].phone 是 138****5678
```

HTTP 若再走 Jackson，starter 也可能「响应看起来对」——所以断言必须打在切面返回值上，不能只比 JSON。

### 练习 17.3

未知 code **不要写入** extras：

```java
MaskRule extra = extras.get(normalized);
if (extra != null) return extra;
try {
    return ruleOf(SensitiveType.valueOf(normalized));
} catch (IllegalArgumentException ex) {
    return rules.getCustom();   // 或立即 FAIL
}
```

合法自定义类型只通过配置 `masking.rules.extras.EXPRESS` 放入。运行时拼出来的 code 最多打指标 `unknown`，不占 Map。
