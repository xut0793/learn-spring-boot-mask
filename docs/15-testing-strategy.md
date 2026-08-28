# 第 15 章 测试策略：怎么证明脱敏是对的

> **本章目标**：会写「断言打成了哪一串星号」的测试，而不是「值变了就过」。把 Demo 的 11 个集成用例拆开看，再带走一份检查清单。
> **前置知识**：第 3 章现象、第 4~14 章实现。本章几乎不写新生产代码。
> **预计时长**：40 分钟。
> **对照代码**：`mask-demo/src/test/java/com/learn/mask/demo/MaskingApiTest.java`，以及 `mask-tutorial` / `mask-starter` 的单测。

---

## 15.1 反模式：只断言「值变了」

最常见的假绿：

```java
String masked = engine.apply("13812345678", PHONE, ctx);
assertThat(masked).isNotEqualTo("13812345678");
```

这证明了「函数动过字符串」，没证明脱敏。下面这些都会让断言通过：

| 实际输出 | 为什么能过 |
| --- | --- |
| `138****5678` | 碰巧对 |
| `***********` | 全掩码，业务对不上号 |
| `13812345678***` | 二次追加，还泄漏了全部数字 |
| `base64(密文)` | 可逆材料出站了 |
| 空字符串 / `null` | 字段丢了 |

脱敏测试的最小合格线是**精确字符串**，再加一条**内存有没有被改**：

```java
assertThat(json.get("phone").asText()).isEqualTo("138****5678");
assertThat(dto.getPhone()).isEqualTo("13812345678");   // Jackson 通道
```

第二条把出口通道和突变通道分开。第 2 章的心智模型在测试里就是这两行。

另一类假绿是只测 ADMIN 旁路、不测 USER 打码。旁路永远是「原样返回」，实现写错成 `return raw` 也能绿。三角色都要有：USER 打码、ADMIN 明文、CS 接口打码但还原能过。

---

## 15.2 单元测试写什么

按层，不按类名堆砌。

### 策略（第 4 章）

每个内置类型至少：

- 标准样例 → 精确打码
- 短于「前缀+后缀」时的回落（通常全掩或原样，以你的实现为准，但要有断言）
- `alreadyMasked` 对「已经是当前规则形态」返回 true，对明文返回 false
- 邮箱保留 `@domain`，不要当成 `keepMask`

starter：`mask-starter/.../strategy/MaskStrategyTest.java`  
教程：`mask-tutorial/.../ch04/MaskStrategyTest.java`

### 引擎（第 6 章）

覆盖判定链的每一岔，而不是只测 `MASK`：

| 动作 | 怎么触发 |
| --- | --- |
| `MASK` | USER + 明文 |
| `BYPASS` | ADMIN |
| `SKIP_ALREADY_MASKED` | 先 MASK 再送打码值 |
| `DISABLED` | 总开关 `masking.enabled=false`（starter；教程仍记 `BYPASS`） |
| 规则关闭 | 类型级 `enabled=false`，记 `BYPASS` |
| `FAIL` | 策略抛异常（教程有；要确认不会吞掉） |

### 角色（第 5 章）

`ROLE_ADMIN` / `admin` / 空白 / 未知值。Header 开与关。`finally` 必须 `remove`——教程用 `LeakyHeaderRoleFilter` 对照。

### AES（第 14 章）

starter 的 `AesGcmReversibleMaskerTest` **只测了往返**。这不够。教程补了：

- 同一明文两次密文不同
- 篡改 tag 必须失败
- 短密钥补零
- 换钥解不开

集成测试里的 `csCanUnmaskPlaintext` 只断言 `token != 明文`，**没断言两次 token 不同**。单元层要把 IV 随机性钉死。

### 通道冲突（第 13 章）

`strict=true` 时启动失败；`false` 时 WARN 且第二次进引擎是 SKIP。不要只测「JSON 看起来还是那串星号」。

---

## 15.3 集成测试：`MaskingApiTest` 十一问

文件：`mask-demo/src/test/java/com/learn/mask/demo/MaskingApiTest.java`。`@SpringBootTest` + MockMvc + HTTP Basic。下面按用例看它证明了什么、**没证明什么**。

### 1. `idCardRuleKeepsSixPrefix`

断言配置绑定：身份证规则前 6 后 4。这是后面 `110101********8515` 的计算依据。配置测和通道测分开，失败时能区分「规则绑错了」和「序列化器没调」。

### 2. `jacksonMasksForUser`

`user/user123` GET `/api/jackson/users/1`，逐项精确值：

| JSON 路径 | 期望 |
| --- | --- |
| `$.phone` | `138****5678` |
| `$.idCard` | `110101********8515` |
| `$.email` | `z*******@example.com` |
| `$.bankCard` | `6222***********0123` |
| `$.address.detail` | `Chaoyang Road **` |
| `$.expressNo` | `SF12*******0123` |
| `$.contacts[0].value` | `139****1111` |

这是全书最重要的一条集成测试：嵌套对象、List、自定义 `EXPRESS` 编码、地址策略，一次覆盖。缺任何一项都是漏脱。

没测：内存里 `UserDto.phone` 是否仍是明文（Jackson 应当不改）。要测得在 Controller 之外拿同一个对象，或走教程第 9 章那种纯序列化单测。

### 3. `jacksonBypassesForAdmin`

同一 URL，`admin/admin123`，phone / 地址 / 快递单号均为明文。证明旁路发生在引擎，不是「这个接口对管理员短路了序列化器」。

### 4. `headerRoleOverridesMasking`

`user` 身份却带 `X-User-Role: ADMIN`，得到明文。证明 Demo 的 `header-role-enabled: true` 生效。这是功能测试，也是**生产事故预演**：这条绿着，说明这个开关在当前配置下能让任意登录用户提权。第 17.6 节。

### 5. `jacksonMapMasksNestedKeys`

`/as-map` 走 `SensitiveMapView`。键名映射（`phone`、`detail`、`expressNo`、`contacts[].phone`）独立于 `@Sensitive`。注解漏了还能靠 `map-keys` / `extra-map-keys` 补的路径。

### 6. `aopChannelMasksInMemory`

`/api/aop/users/1`。JSON 里是打码值。**没断言「这是突变」**——从 HTTP 看，Jackson 默认也开着，AOP 改完 Jackson 再 SKIP，响应长得一样。要证明突变，需要：关 Jackson 再打 AOP，或在切面后直接读对象。教程第 12、13 章补了这条。

### 7. `dbChannelMasksWithoutJacksonAnnotations`

`/api/db/users/1` 返回的是 `UserEntity`，字段上没有 `@Sensitive`。能打码说明 TypeHandler 生效，其中 `addressDetail` 走 Demo 的 `AddressSensitiveTypeHandler`。没测写入是否透传；「漏掉某一列 Handler 会明文」是第 3、11 章的点名制教学，不是当前 Demo 的期望行为。

### 8. `csCanUnmaskPlaintext`

CS POST `/api/unmask`，`value=13812345678`，`token` 不等于明文。没测：token 可解密、两次 token 不同、`email` 字段是否拒绝、ADMIN 能否还原。

### 9. `userCannotUnmask`

USER 同一请求 403。第一重 Security 就拦了，`canUnmask()` 未必被执行。两重都要绿，需要一个「路径放行但角色不能还」的夹具——当前 Demo 没有这种账号。

### 10. `reloadBumpsRuleVersion`

POST reload 后 `ruleVersion` +1，再 reload 把 PHONE 改回 3/4。**没断言下一次 GET 的 phone 变成 `13*******78`。** 版本号自增只证明计数器动了，不证明规则被用上。教程第 7 章的单测有「reload 后打码形态变了」。

### 11. `jacksonDisabledPassesThrough`

测中途改 `properties.getChannels().setJackson(false)`，USER 看到明文，`finally` 改回去。

两个风险：

1. 并行跑集成测试会串。当前文件是一个类顺序执行，还好
2. 它证明的是「关 Jackson 后这条 URL 透传」，在 Demo 默认 **AOP 也开着** 的前提下，`loadPlain` 若没走 `@SensitiveMethod`，关 Jackson 才会是明文。这和 profile `jackson` 的组合一致，但和「默认四通道全开」的心智容易打架——读的时候要对着 `JacksonUserController`：它调 `loadPlain`，不是 `loadForAop`

---

## 15.4 日志测试：明文不准出现

HTTP 断言挡不住 `log.info("user={}", dto)`。第 10 章用 `ListAppender`：

```java
assertThat(appender.list)
        .extracting(ILoggingEvent::getFormattedMessage)
        .noneMatch(msg -> msg.contains("13812345678"));
```

清单：

- USER 请求后，appender 里没有明文手机号 / 身份证 / 卡号
- 自定义 `%sensitiveMsg` 之后，原文 pattern 不再用 `%msg` 打同一段
- ADMIN 旁路时日志**会有明文**（第 3 章实验五）。这是产品决策，测试里要显式断言，避免以后被当成 bug 修掉或反过来漏测

不要用「响应里打码了」代替日志测试。两条通道互不包含。

---

## 15.5 脱敏测试检查清单

落地时按表打勾。左列是能力，右列是本仓库对应位置。

| 能力 | 精确断言 | 本仓库 |
| --- | --- | --- |
| 各内置类型标准样例 | 是 | 教程 ch04、starter `MaskStrategyTest` |
| 自定义 code（快递单） | 是 | `jacksonMasksForUser` 的 `expressNo` |
| 嵌套 Bean / List / Map | 是 | jackson DTO、as-map |
| 出口通道不改内存 | 是 | 教程 ch09；Demo 集成未测 |
| 突变通道改内存 | 是 | 教程 ch12；Demo 集成未测 |
| USER / ADMIN / CS | 部分 | Demo 三角色；CS 只测了 unmask |
| Header 覆盖 | 是 | `headerRoleOverridesMasking` |
| 幂等 / 二次脱敏 | 是 | 教程 ch06/ch13；Demo 无 |
| 通道关闭透传 | 是 | `jacksonDisabledPassesThrough` |
| 冲突组合 WARN / strict 失败 | 是 | 教程 ch13、starter `MaskingChannelValidatorTest` |
| 热更新后打码形态变 | 教程有 | Demo 只测 version++ |
| 日志无明文 | 教程 ch10、starter converter 测 | Demo 集成无 ListAppender |
| AES 往返 + 随机 IV + 篡改 | 教程 ch14 | starter 仅往返 |
| 还原鉴权 | USER 403、CS 200 | 字段白名单仅教程 |
| TypeHandler 不注册为 Bean | 文档 + 反例测试 | 教程 ch11 |
| 指标 `result` 小写 | 教程 ch08 | 第 3 章踩过坑 |

还缺、值得补但本章不改成品代码的：

- Demo 对 `email` 还原应 403（等 `reversible` 真正生效）
- 自定义 code 未注册策略时静默回落 `CUSTOM`（第 4 章练习 4.4）
- `reload` 后再次 GET 的精确星号
- 并行安全的通道开关测试（不要改全局 `MaskingProperties`，用 profile）

---

## 本章小结

- 脱敏断言必须是精确字符串，并区分「JSON 变了」和「内存变了」
- 单元测判定链和策略；集成测三角色、嵌套、Map、还原鉴权
- `MaskingApiTest` 覆盖面够上课，但 AOP 突变、热更新形态、日志、AES 随机 IV、`reversible` 字段限制仍靠教程单测补
- 日志是独立泄漏口，用 ListAppender，不要用 HTTP 测试代替

---

## 课后练习

**练习 15.1** 给 `MaskingApiTest` 设计一条（不要在成品里真改，写在纸上即可）：reload 把 PHONE 改成前 2 后 2 之后，USER GET jackson 的 phone 必须是 `13*******78`，并在 `@AfterEach` 把规则改回 3/4。为什么必须改回？

**练习 15.2** `csCanUnmaskPlaintext` 要怎样改，才能证明 token 是 AES 往返而不是随便一串 Base64？需要注入什么 Bean？

**练习 15.3** 指出 `jacksonDisabledPassesThrough` 在「默认 AOP=true」时可能测到假明文或假打码的条件。怎样用 profile 重写这条测试更稳？

---

## 练习答案

### 练习 15.1

`reload` 请求体已有 `keepPrefix:2, keepSuffix:2`。紧接着：

```java
mockMvc.perform(get("/api/jackson/users/1").with(httpBasic("user", "user123")))
        .andExpect(jsonPath("$.phone").value("13*******78"));
```

必须改回：`MaskingProperties` 是单例，后续用例 `jacksonMasksForUser` 若跑在后面会变成 `13*******78` 而不是 `138****5678`。`@TestMethodOrder` 不能当隔离。`@AfterEach` 或 `try/finally` 再 reload 一次 3/4，并 `bumpRuleVersion`。

### 练习 15.2

`@Autowired ReversibleMasker masker`，取出 `token` 后：

```java
assertThat(masker.decrypt(token)).isEqualTo("13812345678");
```

再调一次 unmask，`token1 != token2`，但两次 decrypt 都等于明文。没有 `ReversibleMasker` Bean 时，这条只能停在 `not(明文)`，假 token 也能过。

### 练习 15.3

若有人把 `JacksonUserController.get` 改成调用 `loadForAop()`，关 Jackson 后 JSON 仍是打码（内存已被 AOP 改）。测试会失败（期望明文），这其实是**有益的失败**。反过来，若测试写成「关 Jackson 后仍是打码」，就会把错误的 Controller 改动测绿。

更稳：`@ActiveProfiles("jackson")` 只开 Jackson，或新 profile `jackson-off`。不要在跑着的单例上 `setJackson(false)`。
