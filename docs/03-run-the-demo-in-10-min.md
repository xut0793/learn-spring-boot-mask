# 第 3 章 先跑起来：10 分钟体验 Demo

> **本章目标**：把 `mask-demo` 跑起来，用七个实验把第 2 章讲的每个结论亲眼看一遍，为后面所有章节建立「现象锚点」。
> 
> **前置知识**：会用命令行，装了 JDK 21 和 Maven。
> 
> **预计时长**：25 分钟。
> 
> **本章所有输出都是在真机上实际跑出来的**，你看到的应该和文档完全一致。

---

## 3.1 启动

### 先确认环境

```bash
java -version    # 需要 21
mvn -v           # 需要 3.9+
```

不需要装数据库——Demo 用 H2 内存库，启动时自动执行 `schema.sql` 和 `data.sql` 建表灌数据。

### 两步启动

```bash
# 第一步：把 mask-starter 安装到本地仓库，mask-demo 依赖它
mvn -q -DskipTests install

# 第二步：进入 demo 模块启动
cd mask-demo
mvn spring-boot:run
```

> **为什么不能直接 `mvn -pl mask-demo -am spring-boot:run`？**
> 这是很多人第一次会踩的坑。`-am`（also make）会把依赖的模块也加入构建，包括**父工程**。而 `spring-boot:run` 是个 goal，会在反应堆里的每个项目上执行——轮到父工程（`packaging` 是 `pom`，没有主类）时就报错：
>
> ```
> [ERROR] Failed to execute goal ...spring-boot-maven-plugin:4.0.7:run (default-cli)
>         on project learn-spring-boot-mask: Unable to find a suitable main class,
>         please add a 'mainClass' property
> ```
>
> 所以多模块项目跑 Boot 应用的正确姿势是：先 `install` 让依赖模块进本地仓库，再单独在应用模块里跑。

### 启动日志里有一条 WARN，这是故意的

```
WARN c.l.m.config.MaskingChannelValidator - masking.channels: jackson is enabled
     together with aop and/or mybatis; mutating channels change in-memory values.
     Idempotent skip will prevent double masking.
```

回顾第 2 章的结论：Jackson 是出口通道，AOP 和 MyBatis 是突变通道，同时开启有风险。`mask-demo` 的默认配置为了能在一个进程里演示全部四个通道，把它们全打开了，所以启动校验器如约报警。

生产环境不该看到这条 WARN。它对应的配置是：

```yaml
masking:
  channels:
    jackson: true
    logback: true
    aop: true      # 生产应为 false
    mybatis: true  # 生产应为 false
    strict: false  # 改成 true 时，出现这种冲突会直接启动失败
```

**第 13 章**会专门讲这三层防线。现在你只需要知道：看到这条 WARN 是正常的。

看到下面这行就说明起来了：

```
INFO c.l.mask.demo.MaskDemoApplication - Started MaskDemoApplication in 5.484 seconds
```

---

## 3.2 三个演示账号

| 用户名 | 密码 | 角色 | 脱敏行为 |
| --- | --- | --- | --- |
| `admin` | `admin123` | `ADMIN` | **旁路**，看到明文 |
| `user` | `user123` | `USER` | **始终脱敏** |
| `cs` | `cs123` | `CS` | 默认脱敏，可调还原接口取明文 |

认证方式是 HTTP Basic，所以 curl 直接 `-u 用户名:密码` 就行。

数据库里只有一个用户，数据如下（来自 `mask-demo/src/main/resources/data.sql`）：

| 字段 | 明文值 |
| --- | --- |
| `name` | `Zhang San` |
| `phone` | `13812345678` |
| `id_card` | `110101199003078515` |
| `email` | `zhangsan@example.com` |
| `bank_card` | `6222021234567890123` |
| `city` | `Beijing` |
| `address_detail` | `Chaoyang Road 88` |
| `express_no` | `SF1234567890123` |
| 联系方式 | `13900001111`（home）、`13700002222`（work） |

> **Windows 用户注意**
> PowerShell 里 `curl` 是 `Invoke-WebRequest` 的别名，参数完全不兼容。请用 `curl.exe`（Windows 10 起自带），并且 JSON 请求体里的双引号要转义成 `\"`：
>
> ```powershell
> curl.exe -s -u cs:cs123 -H "Content-Type: application/json" `
>   -d '{\"userId\":1,\"field\":\"phone\"}' http://localhost:8080/api/unmask
> ```

---

## 3.3 七个实验

下面的 JSON 为了便于阅读做了换行，实际返回是压缩的一行。字段顺序和真实输出保持一致。

### 实验一：`USER` 请求 → 全部打码

```bash
curl -s -u user:user123 http://localhost:8080/api/jackson/users/1
```

```json
{
  "address": { "city": "Beijing", "detail": "Chaoyang Road **" },
  "bankCard": "6222***********0123",
  "contacts": [
    { "type": "home", "value": "139****1111" },
    { "type": "work", "value": "137****2222" }
  ],
  "email": "z*******@example.com",
  "expressNo": "SF12*******0123",
  "id": 1,
  "idCard": "110101********8515",
  "name": "Zhang San",
  "phone": "138****5678"
}
```

对着 `application.yml` 里的规则逐个核对，你会发现每一个值都是算出来的，不是写死的：

| 字段 | 明文 | 规则 | 打码结果 | 怎么算的 |
| --- | --- | --- | --- | --- |
| `phone` | `13812345678` | 保留前 3 后 4 | `138****5678` | 11 位 − 3 − 4 = 4 颗星 |
| `idCard` | `110101199003078515` | 保留前 6 后 4 | `110101********8515` | 18 位 − 6 − 4 = 8 颗星 |
| `bankCard` | `6222021234567890123` | 保留前 4 后 4 | `6222***********0123` | 19 位 − 4 − 4 = 11 颗星 |
| `email` | `zhangsan@example.com` | 保留前 1，域名不动 | `z*******@example.com` | 本地段 `zhangsan` 8 位 − 1 = 7 颗星 |
| `expressNo` | `SF1234567890123` | 数字段保留前 2 后 4 | `SF12*******0123` | 字母 `SF` 保留，13 位数字 − 2 − 4 = 7 颗星 |
| `address.detail` | `Chaoyang Road 88` | 门牌号打星 | `Chaoyang Road **` | 只遮末尾数字 |
| `contacts[].value` | `13900001111` 等 | 保留前 3 后 4 | `139****1111` 等 | 同 `phone` |

三个值得注意的细节：

1. **`name` 和 `address.city` 没被脱敏。** 因为它们的字段上没有 `@Sensitive` 注解。脱敏是**显式声明**的，不会误伤。
2. **`contacts` 是一个 List，里面的每个元素都被处理了。** 嵌套对象（`address`）和列表元素（`contacts`）都能递归生效，这是第 9 章 9.6 节的内容。
3. **`expressNo` 和 `address.detail` 用的是 `mask-demo` 自己写的策略**，`mask-starter` 里根本没有「快递单号」这个类型。它们是通过实现策略接口 + `@Component` 加进来的，一行 starter 代码都没改。这就是第 2 章说的「可扩展」，第 4 章会带你写一遍。

### 实验二：`ADMIN` 请求同一个接口 → 全是明文

```bash
curl -s -u admin:admin123 http://localhost:8080/api/jackson/users/1
```

```json
{
  "address": { "city": "Beijing", "detail": "Chaoyang Road 88" },
  "bankCard": "6222021234567890123",
  "contacts": [
    { "type": "home", "value": "13900001111" },
    { "type": "work", "value": "13700002222" }
  ],
  "email": "zhangsan@example.com",
  "expressNo": "SF1234567890123",
  "id": 1,
  "idCard": "110101199003078515",
  "name": "Zhang San",
  "phone": "13812345678"
}
```

**同一个接口、同一份数据、同一段代码，因为请求者的角色不同，返回了完全不同的结果。** 这就是第 1 章 1.4 节说的动态脱敏的核心能力——静态脱敏永远做不到这件事，因为数据只有一份而且已经被改掉了。

对应的配置是：

```yaml
masking:
  bypass-roles:
    - ADMIN      # 这个列表里的角色直接旁路，不走脱敏
```

第 5 章讲角色是怎么解析出来的，第 6 章讲旁路判定在引擎里的哪一步。

### 实验三：加一个请求头就能改变角色

```bash
curl -s -u user:user123 -H "X-User-Role: ADMIN" \
  http://localhost:8080/api/jackson/users/1
```

返回结果和实验二**完全一样**——明文。

注意这里的诡异之处：**Basic 认证用的还是 `user` 账号**，Spring Security 认定的角色是 `USER`，但脱敏引擎按 `ADMIN` 处理了。

这是一个**本地调试开关**：

```yaml
masking:
  debug:
    header-role-enabled: true    # 生产环境必须是 false
    header-name: X-User-Role
```

它的价值是让你在本地不用反复切换登录账号就能验证各种角色的行为。它的危险也很明显：**如果生产环境忘了关，任何人加一个请求头就能拿到全部明文。**

第 5 章会讲它的实现（ThreadLocal + 过滤器）和为什么 `finally` 里必须清理；第 17 章会把「生产残留这个开关」列为一条高危事故。

### 实验四：还原接口 —— `CS` 可以，`USER` 不行

客服场景：用户来电要核对身份，客服需要看到完整手机号。

```bash
curl -s -u cs:cs123 -H "Content-Type: application/json" \
  -d '{"userId":1,"field":"phone"}' http://localhost:8080/api/unmask
```

```json
{
  "userId": 1,
  "field": "phone",
  "value": "13812345678",
  "token": "H9AEy0qVTIlaK+36LmJsWdf8cpa61frshFLuQ0X2Kv9Cn3sYGU2S"
}
```

同一个请求换成 `user` 账号：

```bash
curl -s -o /dev/null -w "HTTP %{http_code}\n" -u user:user123 \
  -H "Content-Type: application/json" \
  -d '{"userId":1,"field":"phone"}' http://localhost:8080/api/unmask
```

```
HTTP 403
```

三个设计要点：

1. **还原走独立接口，不是「给客服的接口返回明文」。** 这样明文出口只有一个，容易审计、容易加风控（比如限流、留操作日志）。
2. **`token` 是 AES-GCM 加密后的密文**（Base64 编码，含随机 IV）。多请求几次你会发现每次的 `token` 都不一样——因为 IV 是随机的。这也解释了为什么可逆加密**绝对不能走缓存**（第 8 章 8.3 节）。
3. **权限是双重校验的**：Spring Security 的路径规则（`/api/unmask` 要求 `ADMIN` 或 `CS`）+ 运行时的 `MaskContext.canUnmask()` 判定。第 14 章详解。

配置对应：

```yaml
masking:
  unmask-roles:
    - ADMIN
    - CS
```

### 实验五：看控制台日志 —— 接口脱敏管不到这里

现在回头看你启动应用的那个终端窗口，翻到刚才几次请求产生的日志。

`UserService.loadPlain()` 里有这么一行代码（`mask-demo/src/main/java/com/learn/mask/demo/service/UserService.java` 第 35 行）：

```java
log.info("loaded user phone={} idCard={} email={}",
        user.getPhone(), user.getIdentityCard(), user.getEmail());
```

注意：`user.getPhone()` 返回的是**明文** `13812345678`——因为 Jackson 是出口通道，它没有改内存里的对象（第 2 章 2.3 节）。所以这行代码传给日志框架的是实实在在的明文。

但日志输出是这样的：

```
09:59:30.573 [http-nio-8080-exec-1] INFO c.l.mask.demo.service.UserService
  - loaded user phone=138****5678 idCard=110101********8515 email=z*******@example.com
```

明文被 Logback 通道拦住了。机制是 `logback-spring.xml` 里把 `%msg` 换成了 `%sensitiveMsg`：

```xml
<pattern>%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %sensitiveMsg%n</pattern>
```

**这是一个完全独立于接口的出口。** 如果没有这个通道，接口返回得再干净，日志文件里也是一行明文。第 10 章详解。

#### 一个反直觉的发现

现在把日志翻仔细一点。你会看到这样两行紧挨着：

```
09:59:30.573 [exec-1]  - loaded user phone=138****5678 idCard=110101********8515 ...
09:59:30.932 [exec-5]  - loaded user phone=13812345678 idCard=110101199003078515 ...
```

**第二行是明文！**

原因：第二行来自实验二的 `ADMIN` 请求。Logback 通道和其他三个通道一样，最终都调用同一个 `MaskEngine.apply()`，而引擎里的**角色旁路判定对所有通道一视同仁**——`ADMIN` 旁路，所以日志也不脱敏。

这个行为好不好，取决于你怎么看：

- **合理的一面**：脱敏语义在全项目保持一致，只有一处角色判定逻辑（第 2 章的「高内聚」）。
- **需要警惕的一面**：日志是**持久化并且长期保存**的。接口响应看完就没了，但一条 ADMIN 请求产生的明文日志会一直躺在日志平台里，被全公司检索。日志的敏感度模型和接口其实不一样。

如果你的项目要求「日志永远不出明文，不管谁请求」，就需要让 Logback 通道跳过角色判定。这是第 10 章的一道练习题。

**这就是本章存在的意义**——先看到真实行为，再去理解设计，而不是反过来。

### 实验六：热更新 —— 不重启改规则

先看当前规则：

```bash
curl -s -u admin:admin123 http://localhost:8080/api/admin/masking/rules
```

```json
{
  "enabled": true,
  "ruleVersion": 1,
  "channels": { "aop": true, "jackson": true, "logback": true, "mybatis": true, "strict": false },
  "rules": {
    "phone":    { "enabled": true, "keepPrefix": 3, "keepSuffix": 4, "maskChar": "*" },
    "idCard":   { "enabled": true, "keepPrefix": 6, "keepSuffix": 4, "maskChar": "*" },
    "bankCard": { "enabled": true, "keepPrefix": 4, "keepSuffix": 4, "maskChar": "*" },
    "email":    { "enabled": true, "keepPrefix": 1, "keepSuffix": 0, "maskChar": "*" },
    "custom":   { "enabled": true, "keepPrefix": 1, "keepSuffix": 1, "maskChar": "*" },
    "address":  { "enabled": true, "keepPrefix": 3, "keepSuffix": 0, "maskChar": "*" },
    "extras": { "EXPRESS": { "enabled": true, "keepPrefix": 2, "keepSuffix": 4, "maskChar": "*" } }
  },
  "bypassRoles": ["ADMIN"],
  "unmaskRoles": ["ADMIN", "CS"]
}
```

现在改手机号规则：只保留前 3 位，后 4 位也遮掉，并且掩码字符换成 `#`。

```bash
curl -s -u admin:admin123 -H "Content-Type: application/json" \
  -d '{"rules":{"PHONE":{"keepPrefix":3,"keepSuffix":0,"maskChar":"#","enabled":true}}}' \
  http://localhost:8080/api/admin/masking/reload
```

```json
{ "ruleVersion": 2, "enabled": true, "channels": { ... } }
```

注意 `ruleVersion` 从 1 变成了 2。**再请求一次接口，不重启任何东西：**

```bash
curl -s -u user:user123 http://localhost:8080/api/jackson/users/1
```

```json
{
  "phone": "138########",
  "contacts": [
    { "type": "home", "value": "139########" },
    { "type": "work", "value": "137########" }
  ],
  "idCard": "110101********8515",
  ...
}
```

立即生效了。而且注意 `idCard` 完全没变——只改了 `PHONE` 规则，其他规则不受影响。

日志里也跟着变了：

```
09:59:46.138 [exec-9] - loaded user phone=138######## idCard=110101********8515 ...
```

那个 `ruleVersion` 是关键。缓存的 key 里带了这个版本号，形如 `2:PHONE:13812345678`。版本一变，所有旧缓存条目的 key 自然对不上了，**不需要遍历清理就实现了整体失效**。第 7 章 7.5 节会详细讲这个设计。

记得改回来，不然后面的实验对不上：

```bash
curl -s -u admin:admin123 -H "Content-Type: application/json" \
  -d '{"rules":{"PHONE":{"keepPrefix":3,"keepSuffix":4,"maskChar":"*","enabled":true}}}' \
  http://localhost:8080/api/admin/masking/reload
```

### 实验七（加分）：四个通道横向对比

`mask-demo` 给每个通道都留了一个接口，可以直接对照。全部用 `user` 账号请求：

```bash
curl -s -u user:user123 http://localhost:8080/api/jackson/users/1          # Jackson 通道，DTO
curl -s -u user:user123 http://localhost:8080/api/jackson/users/1/as-map   # Jackson 通道，Map
curl -s -u user:user123 http://localhost:8080/api/aop/users/1              # AOP 通道
curl -s -u user:user123 http://localhost:8080/api/db/users/1               # MyBatis 通道
```

**`as-map` 的输出**（Map 脱敏，没有任何 DTO 和注解）：

```json
{
  "id": 1, "name": "Zhang San",
  "phone": "138****5678",
  "idCard": "110101********8515",
  "email": "z*******@example.com",
  "bankCard": "6222***********0123",
  "expressNo": "SF12*******0123",
  "address": { "city": "Beijing", "detail": "Chaoyang Road **" },
  "contacts": [
    { "type": "home", "phone": "139****1111" },
    { "type": "work", "phone": "137****2222" }
  ]
}
```

这里的数据是一个 `Map<String, Object>`，**没有 DTO、没有注解**，脱敏是靠**字段名**匹配的。配置里的 `map-keys` 和 `extra-map-keys` 定义了这个映射：

```yaml
masking:
  extra-map-keys:
    expressNo: EXPRESS
    express_no: EXPRESS
```

顺便注意 `contacts` 里的 key 叫 `phone`（DTO 版本里叫 `value`），依然被正确识别——因为它就叫 `phone`，命中了内置映射。反过来说，如果一个 Map 里手机号的 key 叫 `mobile`，默认就漏了，得自己配。这是字段名映射相比注解的固有弱点（第 9 章 9.5 节）。

**`/api/db/users/1` 的输出**（MyBatis 通道），有一个地方不一样，请你先自己找出来：

```json
{
  "addressDetail": "Chaoyang Road 88",
  "bankCard": "6222***********0123",
  "city": "Beijing",
  "email": "z*******@example.com",
  "expressNo": "SF12*******0123",
  "id": 1,
  "idCard": "110101********8515",
  "name": "Zhang San",
  "phone": "138****5678"
}
```

`addressDetail` 是 **`Chaoyang Road 88`——明文没有被脱敏**。

原因在 `UserMaskedMapper.findByIdMasked` 的注解里（`mask-demo/src/main/java/com/learn/mask/demo/mapper/UserMaskedMapper.java`）：

```java
@Results({
        @Result(column = "phone", property = "phone", typeHandler = PhoneSensitiveTypeHandler.class),
        @Result(column = "id_card", property = "idCard", typeHandler = IdCardSensitiveTypeHandler.class),
        @Result(column = "email", property = "email", typeHandler = EmailSensitiveTypeHandler.class),
        @Result(column = "bank_card", property = "bankCard", typeHandler = BankCardSensitiveTypeHandler.class),
        @Result(column = "address_detail", property = "addressDetail"),   // ← 没有 typeHandler
        @Result(column = "express_no", property = "expressNo", typeHandler = ExpressSensitiveTypeHandler.class)
})
```

`address_detail` 那一行**没有指定 `typeHandler`**。

这暴露了 MyBatis 通道的核心弱点：**它是「逐列显式指定」的，漏一列就是一次泄露，而且编译器不会告诉你。** 对比 Jackson 通道——注解写在 DTO 字段上，任何接口返回这个 DTO 都自动生效，漏的可能性小得多。

这也是为什么 `UserEntity` 上没有 `@Sensitive` 注解（第 11 章会讲这个设计），以及为什么第 2 章 2.4 节的对比表里，MyBatis 通道的缺点写的是「灵活性差」。

---

## 3.4 脱敏是可观测的

最后看一眼指标。Demo 开了 Actuator：

```bash
curl -s -u admin:admin123 http://localhost:8080/actuator/metrics/masking.invoke
```

```json
{
  "availableTags": [
    { "tag": "result", "values": ["bypass", "mask", "skip_already_masked"] },
    { "tag": "role",   "values": ["CS", "USER", "ADMIN"] },
    { "tag": "type",   "values": ["EMAIL", "ID_CARD", "EXPRESS", "ADDRESS", "PHONE", "BANK_CARD"] }
  ],
  "measurements": [ { "statistic": "COUNT", "value": 82.0 } ],
  "name": "masking.invoke"
}
```

刚才那几次请求一共触发了 **82 次**脱敏调用。三个标签可以任意组合下钻：

```bash
# USER 角色实际执行了多少次脱敏
curl -s -u admin:admin123 \
  "http://localhost:8080/actuator/metrics/masking.invoke?tag=role:USER&tag=result:mask"
# → COUNT: 49

# ADMIN 旁路了多少次
curl -s -u admin:admin123 \
  "http://localhost:8080/actuator/metrics/masking.invoke?tag=result:bypass"
# → COUNT: 24

# 因为「已经是打码值」而跳过了多少次
curl -s -u admin:admin123 \
  "http://localhost:8080/actuator/metrics/masking.invoke?tag=result:skip_already_masked"
# → COUNT: 6
```

> **注意 `result` 标签的值是小写的**（`mask` 而不是 `MASK`）。写成大写查不到任何数据，返回空响应体——这是我第一次查的时候踩的坑。

那 6 次 `skip_already_masked` 就是第 2 章 2.3 节讲的**幂等跳过**在起作用：`/api/aop/users/1` 请求时，AOP 先把对象改成了打码值，Jackson 再序列化时发现「这已经是打码形态了」，于是跳过。**这 6 次跳过就是防止二次打码的那道防线的实际计数。**

耗时也有记录：

```bash
curl -s -u admin:admin123 http://localhost:8080/actuator/metrics/masking.duration
```

```json
{
  "baseUnit": "seconds",
  "measurements": [
    { "statistic": "COUNT", "value": 82.0 },
    { "statistic": "TOTAL_TIME", "value": 0.0153623 },
    { "statistic": "MAX", "value": 0.0069581 }
  ],
  "name": "masking.duration"
}
```

82 次调用总耗时 15.4 毫秒，平均单次约 **0.19 毫秒**。`MAX` 是 6.96 毫秒——那是第一次调用，包含了类加载和 JIT 预热的成本。这个数据在第 16 章压测时会用到。

第 8 章会讲这些指标是怎么打出来的，以及怎么用它们回答「脱敏到底有没有在工作」这类问题。

---

## 3.5 现象锚点表

这一章你亲眼看到的每个现象，都对应后面的某一章。往后读到卡住的时候，回来看这张表：

| 你看到的现象 | 解释它的章节 |
| --- | --- |
| 启动时那条 `MaskingChannelValidator` WARN | 第 13 章 |
| `USER` 看到星号、`ADMIN` 看到明文 | 第 5 章（角色）、第 6 章（旁路判定） |
| 每种类型的星号数量都不一样 | 第 4 章（策略） |
| `expressNo` / `address.detail` 用了 starter 里没有的类型 | 第 4 章（扩展） |
| `name` / `city` 没被脱敏 | 第 9 章（注解驱动） |
| `contacts` 列表和 `address` 嵌套对象都生效了 | 第 9 章 9.6 节 |
| `X-User-Role` 头能覆盖角色 | 第 5 章 5.3 / 5.5 节 |
| `/api/unmask` 返回明文 + 每次不同的 `token` | 第 14 章 |
| `USER` 调还原接口返回 403 | 第 14 章 |
| 日志里的明文被拦住了 | 第 10 章 |
| **但 `ADMIN` 请求产生的日志是明文** | 第 10 章练习 |
| 改规则不重启立即生效 | 第 7 章 |
| `ruleVersion` 是什么、为什么要有 | 第 7 章 7.5 节、第 8 章 |
| `as-map` 没有注解也能脱敏 | 第 9 章 9.5 节 |
| `/api/db` 的 `addressDetail` 漏了 | 第 11 章 |
| `skip_already_masked` 计数为 6 | 第 6 章 6.5 节、第 13 章 |
| `masking.invoke` / `masking.duration` 指标 | 第 8 章 |

---

## 本章小结

- 多模块项目启动 Boot 应用：先 `mvn install`，再进应用模块 `mvn spring-boot:run`；`-pl xxx -am` 配 `spring-boot:run` 会因为父工程没有主类而失败
- 同一个接口、同一份数据，`USER` 看到星号、`ADMIN` 看到明文——这是动态脱敏区别于静态脱敏的核心能力
- 打码结果不是写死的，是「保留位数 + 掩码字符」按值的实际长度算出来的
- 日志是一个**完全独立的出口**，接口脱敏对它无效，必须单独处理
- 角色旁路对**所有通道**生效，包括日志——这可能不是你想要的，需要根据项目要求评估
- 规则可以运行时热更新，靠 `ruleVersion` 参与缓存 key 实现旧缓存自动失效
- MyBatis 通道是「逐列显式指定」的，漏一列就泄露一列，Demo 里的 `addressDetail` 就是活例子
- 脱敏全过程有 Micrometer 打点，可按 `type` / `role` / `result` 三个维度下钻

下一章开始动手写代码。我们会从一个会腐烂的 `if-else` 出发，一步步重构成策略模式，并且给项目加一个 starter 里根本没有的脱敏类型。

---

## 课后练习

**练习 3.1** 不看第 4 章，只根据 `application.yml` 里的规则，推算下面几个值的脱敏结果。然后往数据库里插一条数据（或者直接改 `data.sql` 重启）验证你的答案。

| 明文 | 类型 | 你的答案 |
| --- | --- | --- |
| `13800138000` | PHONE（前 3 后 4） | ? |
| `1381234` | PHONE（前 3 后 4） | ? |
| `138` | PHONE（前 3 后 4） | ? |
| `a@b.com` | EMAIL（前 1） | ? |
| `@example.com` | EMAIL（前 1） | ? |

**练习 3.2** 用 `cs` 账号请求 `/api/jackson/users/1`，预测返回的是明文还是打码值，然后实际验证。如果和你预期不符，用 `application.yml` 解释为什么。

**练习 3.3** 连续调用三次 `/api/unmask` 拿同一个手机号，观察 `token` 字段。它每次都不一样。请回答：

1. 为什么要这样设计？
2. 如果 `token` 每次都相同会有什么风险？
3. 这个特性对「缓存」意味着什么？

**练习 3.4** 用不带认证的 curl 请求 `/api/jackson/users/1`，看返回什么。然后请求 `/actuator/health`，对比结果，并在 `SecurityConfig` 里找到原因。

---

## 练习答案

### 练习 3.1

| 明文 | 长度 | 计算过程 | 结果 |
| --- | --- | --- | --- |
| `13800138000` | 11 | 11 > 3+4，`138` + 4 颗星 + `8000` | `138****8000` |
| `1381234` | 7 | 7 == 3+4，**没有中间段可遮** | `*******`（全星） |
| `138` | 3 | 3 < 3+4，长度不足 | `***`（全星） |
| `a@b.com` | — | 本地段 `a` 长度 1，等于 keepPrefix，走全星分支 | `*@b.com` |
| `@example.com` | 12 | `@` 在下标 0，`at <= 0` 成立，**回落到通用 `keepMask`** | `@***********` |

前三行的关键逻辑在 `MaskUtils.keepMask`：

```java
if (len <= prefix + suffix) {
    return String.valueOf(maskChar).repeat(len);   // 长度不够就全遮
}
```

这个「长度不足则全星」的兜底非常重要。如果按位置硬切，`"138"` 会直接抛 `StringIndexOutOfBoundsException`——正是第 1 章 1.1 节的第二个 bug。**安全的默认行为是「遮得更多」，而不是「报错」或「原样返回」。**

第四行也值得注意：本地段长度等于 keepPrefix 时，`EmailMaskStrategy` 走的是全星分支，而不是「原样返回 `a@b.com`」。如果原样返回，这个字段就等于没脱敏。

最后一行有个容易算错的地方。`@example.com` 因为 `@` 在下标 0，`EmailMaskStrategy` 判定 `at <= 0` 后**放弃邮箱专用逻辑，回落到通用的 `keepMask`**。此时 EMAIL 规则是 keepPrefix=1、keepSuffix=0，所以保留的第 1 个字符就是 `@` 本身，后面 11 个字符全部打星，得到 `@***********`。

如果你算出的是 `*` + 11 颗星，说明把「保留前 1 位」理解成了「保留本地段的第 1 位」——但这条路径上已经没有本地段的概念了，回落逻辑只认「字符串的前 1 位」。这个细节说明：**兜底逻辑的行为也需要被测试覆盖**，否则很容易在脏数据上产生和预期不符的结果。

### 练习 3.2

`cs` 账号看到的是**打码值**，和 `user` 完全一样。

原因在配置：

```yaml
masking:
  bypass-roles:
    - ADMIN          # 只有 ADMIN 旁路
  unmask-roles:
    - ADMIN
    - CS             # CS 只是「有权调还原接口」
```

`CS` 在 `unmask-roles` 里但不在 `bypass-roles` 里。这两个列表是**两种不同的权限**：

- `bypass-roles`：常规接口直接返回明文
- `unmask-roles`：常规接口仍然打码，但可以通过独立的还原接口按需取明文

这个区分是有意的，也是第 14 章的核心设计：**客服的默认视图应该是打码的**，只有在确有需要（用户来电核对身份）时才主动发起一次还原请求。这样明文访问是「按次、可审计」的，而不是「常态、无痕迹」的。

如果把 `CS` 也加进 `bypass-roles`，客服后台的每一次列表查询都会返回全量明文，一次批量导出就是一起数据泄露事件。

### 练习 3.3

**1. 为什么每次不同：** AES-GCM 每次加密都用一个新的随机 IV（初始化向量），IV 和密文一起 Base64 编码返回。相同明文 + 相同密钥 + 不同 IV = 完全不同的密文。这是 GCM 模式的正确用法，**IV 重用会直接导致 GCM 的安全性崩塌**（可以推导出密钥流）。

**2. 如果每次相同的风险：**

- **可以做等值判断。** 攻击者不需要解密，只要看到两条记录的 token 相同，就知道它们的手机号是同一个。这能把匿名数据重新关联起来（去匿名化攻击）。
- **可以建字典。** 中国大陆手机号空间是有限的（10 亿量级）。如果加密是确定性的，攻击者拿一批已知手机号加密建成字典，然后反查所有 token 就能全部还原。这和第 1 章练习 1.2 里哈希手机号被彩虹表破解是**完全同一个问题**。

**3. 对缓存的意味：可逆加密绝对不能走缓存。**

缓存的前提是「相同输入产生相同输出」，而随机 IV 恰恰打破了这个前提。如果把加密结果缓存起来复用，就等于人为把随机 IV 退化成了固定 IV，前面两条风险全部回来了。

所以本项目的缓存只服务于**展示脱敏**（`138****5678` 这种确定性结果），加密路径完全不碰缓存。这是第 8 章 8.3 节的内容，也是 `plan.md` 第 9 节「实现避坑」里明确列出的一条。

### 练习 3.4

不带认证请求业务接口返回 **401 Unauthorized**（Basic 认证要求，响应头里带 `WWW-Authenticate: Basic`）。

请求 `/actuator/health` 返回 **200**：

```json
{ "groups": ["liveness", "readiness"], "status": "UP" }
```

而 `/actuator/metrics` 不带认证返回 **401**。

原因在 `mask-demo/src/main/java/com/learn/mask/demo/config/SecurityConfig.java`：

```java
.authorizeHttpRequests(auth -> auth
        .requestMatchers("/actuator/health", "/actuator/info").permitAll()
        .requestMatchers("/api/admin/**").hasRole("ADMIN")
        .requestMatchers("/api/unmask").hasAnyRole("ADMIN", "CS")
        .anyRequest().authenticated())
```

`health` 和 `info` 显式放开，是因为它们通常要给 K8s 的存活/就绪探针或负载均衡器调用，那些调用方没法带认证信息。

注意 `/actuator/metrics` 和 `/actuator/prometheus` **不在放开列表里**，落到 `anyRequest().authenticated()`——所以 3.4 节查指标时必须带 `-u admin:admin123`。这是对的：指标里会暴露 `type` 标签（系统里有哪些敏感字段类型）和调用量，属于不该匿名公开的信息。
