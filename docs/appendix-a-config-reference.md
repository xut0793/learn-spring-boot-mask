# 附录 A · `masking.*` 配置项全表

前缀均为 `masking`。绑定类：`mask-starter/.../config/MaskingProperties.java`。  
热更新指 `POST /api/admin/masking/reload`（`MaskingReloadController`）能否改内存中的该项。未标明的项改 YAML 后需重启。

---

## 总开关与通道

| 配置项 | 默认 | 生效位置 | 热更新 |
| --- | --- | --- | --- |
| `enabled` | `true` | 引擎入口；false 则各通道仍在但 `apply` 原样返回 | 是 |
| `channels.jackson` | `true` | Jackson 序列化器 | 是（改的是内存开关，已创建的序列化器下次 serialize 会读到） |
| `channels.logback` | `true` | `%sensitiveMsg` | 是 |
| `channels.aop` | `false` | `@SensitiveMethod` 切面 | 是 |
| `channels.mybatis` | `false` | TypeHandler | 是 |
| `channels.strict` | `false` | 仅启动时 `MaskingChannelValidator` | 是（**不重新校验**；改它不会让已启动的进程再 fail） |

Demo 的 `application.yml` 把 aop/mybatis 改成了 `true`。移植时以本表默认值为准，见第 18.5 节。

冲突：`jackson && (aop || mybatis)` 时启动 WARN；`strict=true` 则启动失败。第 13 章。

---

## 角色

| 配置项 | 默认 | 生效位置 | 热更新 |
| --- | --- | --- | --- |
| `bypass-roles` | `[ADMIN]` | `MaskContext.shouldBypass()` | 否（reload 响应里会回显当前值，但不接收修改） |
| `unmask-roles` | `[ADMIN, CS]` | `MaskContext.canUnmask()` | 否 |

角色名不要带 `ROLE_`。解析时会去掉此前缀且忽略大小写。第 5 章。

---

## 缓存

| 配置项 | 默认 | 生效位置 | 热更新 |
| --- | --- | --- | --- |
| `cache.enabled` | `true` | `MaskCache.get/put` | 否（Caffeine 已按启动参数建好） |
| `cache.max-size` | `10000` | Caffeine `maximumSize` | 否 |
| `cache.expire-after-access-minutes` | `10` | `expireAfterAccess` | 否 |

第 8 章结论：对内置 keepMask **默认开是负收益**。成品未 `recordStats()`。可逆 AES **不要**走这条缓存。

---

## 调试

| 配置项 | 默认 | 生效位置 | 热更新 |
| --- | --- | --- | --- |
| `debug.header-role-enabled` | **`false`**（Demo 为 `true`） | `HeaderRoleFilter` | 否 |
| `debug.header-name` | `X-User-Role` | 同上 | 否 |

生产必须保持 `false`。第 5、17.6 节。

---

## 可逆

| 配置项 | 默认 | 生效位置 | 热更新 |
| --- | --- | --- | --- |
| `reversible.enabled` | `true` | 属性在，Controller **未读此开关** | 否 |
| `reversible.secret-key` | `demo-key-not-for-prod-32b!!` | `AesGcmReversibleMasker`；Demo 可用 `MASKING_AES_KEY` 覆盖 | 否 |

密钥按 UTF-8 **补 0 或截断到 32 字节**。第 14.5 节。没有 `reversible.fields`——字段限制要自己加。

---

## 规则

JavaBean 默认：`enabled=true`，`keep-prefix=1`，`keep-suffix=1`，`mask-char=*`。  
下列是 `RuleSet` 对各类型的**再覆盖**。

| 配置项 | 默认 keep | 策略 |
| --- | --- | --- |
| `rules.phone` | 前 3 后 4 | 内置 `PHONE` |
| `rules.id-card` | 前 6 后 4 | 内置 `ID_CARD` |
| `rules.bank-card` | 前 4 后 4 | 内置 `BANK_CARD` |
| `rules.email` | 前 1 后 0 | 内置；保留 `@domain`，不是纯 keepMask |
| `rules.custom` | 前 1 后 1 | `CUSTOM` / 未识别回落 |
| `rules.extras.<CODE>` | 无 | 业务 `MaskStrategy.code()`。Demo 配了 `ADDRESS`、`EXPRESS` |

每条规则字段：`enabled`、`keep-prefix`、`keep-suffix`、`mask-char`。

热更新：`reload.rules` 按类型名（`PHONE` 等）写入**已有** `MaskRule` 对象。  
已知 bug：请求体没带的 `maskChar` / `enabled` 会被 JavaBean 默认值覆盖（第 7.7 节）。`keepPrefix/Suffix` 用 `>= 0` 判断，未出现时若 JSON 反序列化成 0 也会被写进去——取决于是否省略字段。

`ruleOf(未知 code)` 只读快照，未命中回落 `CUSTOM`，**不会**往 extras 写入。extras 只在 YAML 绑定或热更新 `configOf` 时出现。

---

## Map 键

| 配置项 | 默认 | 热更新 |
| --- | --- | --- |
| `map-keys` | 见下表 | 否 |
| `extra-map-keys` | 空；Demo 配了地址三个别名 + 快递四个别名 | 否 |

内置 `map-keys`：

| JSON / Map 键 | 类型 |
| --- | --- |
| `phone` | PHONE |
| `identityCard` `idCard` `id_card` `id-card` | ID_CARD |
| `email` | EMAIL |
| `bankCard` `bank_card` | BANK_CARD |

`extra-map-keys` 的值是策略 **code 字符串**（如 `EXPRESS`、`ADDRESS`），不是枚举。Demo 把 `detail` / `addressDetail` / `address_detail` 映到 `ADDRESS`。

---

## 规则版本

| 项 | 说明 |
| --- | --- |
| `ruleVersion` | 内存 `AtomicLong`，初始 `1`，不是 YAML 项 |
| `bumpRuleVersion()` | reload 成功后 +1；缓存 key 带版本，旧条目不可达 |

`GET /api/admin/masking/rules` 只读：`enabled`、`ruleVersion`、`channels`、`rules`、`bypassRoles`、`unmaskRoles`。
