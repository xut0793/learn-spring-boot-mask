# 第 12 章 通道四 · `@Sensitive` + AOP 显式脱敏

> **本章目标**：在方法返回值上就地打码。不依赖 HTTP，适合 RPC、消息、定时任务。代价是内存里的明文被改掉。
> **前置知识**：第 2、9、11 章。知道 `@Around` 能拦住方法返回值。
> **预计时长**：50 分钟。
> **本章代码**：`mask-tutorial/src/main/java/com/learn/mask/tutorial/ch12/`

---

## 12.1 问题场景：没有 JSON 出口的时候

Jackson 只管 MVC 写 HTTP 响应。gRPC / Dubbo / 消息消费者 / 定时任务没有这条路。这些场景要脱敏，只能在应用层动手。

AOP 的切点是一个方法注解：

```java
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface SensitiveMethod {

    /** 返回值为 String 时使用的内置类型。Bean / List / Map 则按字段上的 @Sensitive 递归。 */
    SensitiveType type() default SensitiveType.CUSTOM;

    /** 返回值为 String 时的业务自定义编码，优先于 type。 */
    String code() default "";
}
```

业务方法只标这一下，自己不写脱敏代码：

```java
@SensitiveMethod
public UserDto loadForAop(Long id) {
    return loadPlain(id);
}
```

切面在 `proceed()` 之后改返回值：

```java
@Aspect
public class SensitiveMethodAspect {

    private final MaskEngine engine;
    private final MaskingProperties properties;
    private final ChannelProperties channels;
    private final MaskContext maskContext;
    private final SensitiveObjectWalker walker;

    public SensitiveMethodAspect(MaskEngine engine,
                                 MaskingProperties properties,
                                 ChannelProperties channels,
                                 MaskContext maskContext,
                                 SensitiveObjectWalker walker) {
        this.engine = engine;
        this.properties = properties;
        this.channels = channels;
        this.maskContext = maskContext;
        this.walker = walker;
    }

    @Around("@annotation(sensitiveMethod)")
    public Object around(ProceedingJoinPoint joinPoint, SensitiveMethod sensitiveMethod) throws Throwable {
        Object result = joinPoint.proceed();
        if (result == null || !properties.isEnabled() || channels == null || !channels.isAop()) {
            return result;
        }
        if (result instanceof String text) {
            return engine.apply(text, sensitiveMethod.type(), sensitiveMethod.code(), maskContext);
        }
        return walker.mask(result);
    }
}
```

返回 `String` 时，类型来自 `@SensitiveMethod` 自己（方法级，没有字段）。返回对象时，类型来自字段上的 `@Sensitive`——和第 9 章同一个注解。

`channels.aop` 默认 `false`。开之前必须清楚：这会改内存。

---

## 12.2 动手写：就地改写对象图

```java
public Object mask(Object target) {
    walk(target, new IdentityHashMap<>());
    return target;   // 同一个引用，字段已经被 set 成打码值
}
```

```java
@Test
@DisplayName("就地改写：返回的是同一个对象，明文已经没了")
void mutatesInPlace() {
    User user = new User();
    Object returned = walker.mask(user);
    assertThat(returned).isSameAs(user);
    assertThat(user.phone).isEqualTo("138****5678");
}
```

和 Jackson 对照：第 9 章序列化之后 `view.getPhone()` 仍是明文。这里已经是星号。后续再发短信、再写缓存、再 `UPDATE`，用的都是打码值。

Walker 要处理四类节点：

| 节点 | 做法 |
| --- | --- |
| Bean | 反射字段；有 `@Sensitive` 且是 String 就 `field.set`；否则递归 |
| `Collection` | 遍历元素 |
| 数组 | 按下标遍历 |
| `Map` | 键在 `map-keys` 里的字符串值就地 `entry.setValue`；值再递归 |

`List<String>` 字段即使标了 `@Sensitive` 也不会打码——注解在 List 上，元素是 String，Walker 对 String 直接 skip。要打码的是「Bean 里的 String 字段」或「Map 里按键名」。这是和 Jackson 的差异：Jackson 递归的是序列化树，字段注解在嵌套 Bean 上仍然生效；Walker 同样能进嵌套 Bean，但对 `List<String>` 无能为力。

---

## 12.3 三个工程细节

### IdentityHashMap 防循环引用

```java
a.next = b;
b.next = a;
walker.mask(a);   // 不能 StackOverflow
```

用 `IdentityHashMap` 而不是 `HashMap`：比较的是引用，不调 `equals`。业务对象的 `equals` 常常按 id 比较，两个不同实例 id 相同会被误判成「见过」，漏脱；循环引用的两个节点必须靠 `==` 识别。

### 跳过 JDK / 框架包

对 `LocalDate`、`byte[]`、Jackson 的 `JsonNode` 做字段反射，轻则无意义，重则改掉框架内部状态。`shouldSkip` 按包名前缀过滤。

**顺序很重要。** 教程版把 Collection / Map / 数组的判断放在 `shouldSkip` **之前**：

```java
if (target instanceof Collection<?> collection) { ... return; }
if (target instanceof Map<?, ?> map) { ... return; }
if (shouldSkip(target.getClass())) { return; }
```

`ArrayList`、`LinkedHashMap` 都在 `java.util` 下。如果先 skip，嵌套 `List<Contact>` 和返回值是 `Map` 的整棵子树都不会被走。starter 就是先 skip 再判断 Collection——`List` / `Map` 作为**根对象或字段值**时会漏。本章测试 `nestedBeanAndListAreMasked` 和 `masksMappedKeysAndRecurses` 就是为这个顺序写的。对照节会再提一次。

### 遍历父类字段

```java
Class<?> type = target.getClass();
while (type != null && type != Object.class) {
    for (Field field : type.getDeclaredFields()) { ... }
    type = type.getSuperclass();
}
```

`getDeclaredFields()` 不含父类。电话号定义在 `Person`、返回值是 `Employee` 时，只看子类会漏脱。

静态字段、合成字段（`this$0`）跳过。`setAccessible(true)` 是为了改 private；模块系统下失败就 skip，不让整次请求 500。这又是 fail-open：个别字段脱敏失败会泄漏明文。练习 12.2 讨论要不要 fail-closed。

---

## 12.4 突变之后再走 Jackson

```java
@Test
@DisplayName("AOP 改过的对象再走 Jackson：JSON 仍是一层星号，但内存已经是打码值")
void jacksonAfterAopStillOneLayerOfStars() throws Throwable {
    aspect.around(pjp, method(...));          // 内存变成 138****5678
    JsonNode json = mapper.readTree(mapper.writeValueAsString(view));
    assertThat(json.get("phone").asText()).isEqualTo("138****5678");
    assertThat(view.getPhone()).isEqualTo("138****5678");
}
```

JSON 看起来「没出事」，因为 `keepMask` 对已打码的手机号再套一次结果不变，加上引擎的 `alreadyMasked` 会直接 SKIP。**真正出事的是明文没了。** 还原、发短信、写回库都会用星号。

第 13 章把「看起来没出事」升级成三层防线：能关的通道关掉，关不掉时靠幂等，启动时再拦冲突组合。

切面本身可以注入引擎，不需要静态桥——它是 Spring Bean。Walker 也是。和 Jackson / Logback / MyBatis 不同，AOP 是四个通道里唯一「框架允许依赖注入」的。

---

## 12.5 优缺点

| | |
| --- | --- |
| 优点 | 不依赖 HTTP；复用 `@Sensitive`；嵌套 Bean / 父类 / 循环引用都处理了 |
| 缺点 | 改内存；反射有成本；`List<String>` 不打码；默认关；和 Jackson 同时开要靠第 13 章 |
| 适用 | RPC、消息、定时任务、没有 Jackson 出口的服务 |
| 不适用 | 典型 Web API（用第 9 章）；还要在返回后用明文做业务 |

---

## 12.6 验证

```bash
mvn -f mask-tutorial/pom.xml test "-Dtest=SensitiveObjectWalkerTest,SensitiveMethodAspectTest"
```

10 个测试：就地改写、嵌套 Bean 与 List、父类字段、循环引用、跳过 JDK、Map 键名；切面对 String / 对象 / 通道关闭；AOP 后再走 Jackson。

---

## 12.7 对照真实实现

| 方面 | 你的 `ch12` | `mask-starter` | 评价 |
| --- | --- | --- | --- |
| 切面 / Walker 结构 | 相同 | 相同 | — |
| Collection 与 skip 的顺序 | **先 Collection/Map，再 skip** | **先 skip**，`java.util.List` 会被整棵跳过 | **教程修了真实项目的漏脱** |
| `IdentityHashMap` | 有 | 有 | — |
| 父类字段 | 有 | 有 | — |
| 切面依赖注入 | 有，外加 `ChannelProperties` | 从 `MaskingProperties.getChannels()` 读 | — |

starter 的顺序问题：

```38:47:mask-starter/src/main/java/com/learn/mask/aop/SensitiveObjectWalker.java
        if (target == null || shouldSkip(target.getClass()) || seen.containsKey(target)) {
            return;
        }
        seen.put(target, Boolean.TRUE);
        if (target instanceof Collection<?> collection) {
            for (Object item : collection) {
                walk(item, seen);
            }
            return;
        }
```

`ArrayList` 在 `shouldSkip` 就被 return 了，后面的 `instanceof Collection` 是死代码。返回 `List<UserDto>` 的 `@SensitiveMethod` 在 starter 里**不会脱敏**。教程测试如果按 starter 原顺序写，`nestedBeanAndListAreMasked` 的第二条会红——这正是我们改顺序的证据。

第 17 章改进项应加上这一条。

---

## 本章小结

- AOP 是突变通道：返回值对象被就地改写，明文不在了
- `@SensitiveMethod` 标方法；字段规则仍用第 9 章的 `@Sensitive`
- `IdentityHashMap`、跳过 JDK 包、遍历父类，三条都缺一不可
- **先处理 Collection/Map，再 `shouldSkip`**，否则 `java.util` 容器整棵漏脱
- 和 Jackson 叠在一起时 JSON 可能仍正确，但内存已经没明文——下一章专门处理这种组合

---

## 课后练习

**练习 12.1** 给 Walker 补上 `List<String>`：字段标了 `@Sensitive(type=PHONE)` 且类型是 `List<String>` 时，把每个元素打码后写回（注意 List 可能不可变）。

**练习 12.2** `IllegalAccessException` 时 starter 和教程都选择 skip。改成 fail-closed（抛出）的利弊是什么？有没有第三种做法？

**练习 12.3** 切面目前只处理返回值。如果敏感数据在**方法参数**里（比如往 MQ 发送之前），该怎么扩展？不要在 `around` 里再复制一套 Walker 调用。

---

## 练习答案

### 练习 12.1

在 `maskFields` 里，若 `value instanceof List<?> list` 且字段有 `@Sensitive`：

```java
if (sensitive != null && value instanceof List<?> list) {
    List<Object> copy = new ArrayList<>(list.size());
    for (Object item : list) {
        copy.add(item instanceof String text
                ? engine.apply(text, sensitive.type(), sensitive.code(), maskContext)
                : item);
    }
    try {
        field.set(target, copy);
    } catch (IllegalAccessException ignored) { }
    continue;
}
```

不可变 List（`List.of`）不能 `list.set(i, masked)`，所以写回新 ArrayList。调用方如果依赖「返回的就是原 List 实例」，行为会变——文档里写清楚。

对 List 里的 Bean 仍走原来的 `walk`，不要和 `List<String>` 混成一条。

### 练习 12.2

skip（fail-open）：单个模块字段打不开时请求仍 200，但那一列明文出站。生产里 JPMS / SecurityManager 变严时会静默失效。

抛出（fail-closed）：装配问题立刻变成 500，符合第 6 章。代价是一个加了 `--add-opens` 才能跑的库把整个接口打挂。

第三种：skip 的同时 `recorder.record(..., FAIL)` 或打 ERROR 日志。指标能告警，请求还能成功。比纯 skip 可观测，比抛出更不容易误伤。和第 8 章「监控把业务搞挂」相反，这里是「脱敏失败被当成成功」。

### 练习 12.3

给 `@SensitiveMethod` 加 `boolean includeArgs() default false`。切面里：

```java
Object result = joinPoint.proceed();
if (sensitiveMethod.includeArgs()) {
    for (Object arg : joinPoint.getArgs()) {
        walker.mask(arg);
    }
}
return walker.mask(result);
```

Walker 已经是唯一的「改对象图」入口，参数和返回值共用，不要各写一套反射。注意：改参数是就地的，调用方传入的对象出方法后也被改了——对「发送前打码」正合适，对「方法内部还要用明文」是事故。默认 false。
