# 第 19 章 mask-starter 构建、发布与全新项目接入

> **本章目标**：在本仓库把 `mask-starter` 构建并安装/发布到制品库；在一个**与教程无关**的新 Spring Boot 工程里完成最小可用接入，并知道各通道还要加哪些依赖。
> **前置知识**：Maven 基本用法、会新建 Spring Boot 项目。设计细节见 [第 18 章](18-starter-design-review.md)。
> **预计时长**：30~45 分钟。

---

## 19.1 坐标与环境

| 项 | 值 |
| --- | --- |
| `groupId` | `com.learn.mask` |
| `artifactId` | `mask-starter` |
| 当前版本 | `1.0.0-SNAPSHOT`（以根 `pom.xml` 为准） |
| JDK | **21** |
| 对齐的 Spring Boot | **4.0.7**（本 starter 的 parent 版本） |
| Jackson | **3.x**（包名 `tools.jackson.*`） |

Starter 通过 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 注册自动配置，**不需要**在业务项目里 `@Import` 任何配置类。

---

## 19.2 在本仓库构建 mask-starter

### 只构建 starter 模块

在仓库根目录：

```bash
mvn -q -pl mask-starter clean test
```

需要连同父 POM 一起解析时：

```bash
mvn -q -pl mask-starter -am clean test
```

### 安装到本机 Maven 仓库（最常用）

多模块项目里，业务模块依赖 `mask-starter` 时，需先把 starter **install** 进 `~/.m2`，否则新版本解析不到：

```bash
mvn -q -DskipTests install
```

仅安装 starter（不跑 demo 测试）：

```bash
mvn -q -pl mask-starter -am -DskipTests install
```

安装成功后，本机应有：

`~/.m2/repository/com/learn/mask/mask-starter/1.0.0-SNAPSHOT/mask-starter-1.0.0-SNAPSHOT.jar`

### 验证 JAR 内容（可选）

```bash
jar tf ~/.m2/repository/com/learn/mask/mask-starter/1.0.0-SNAPSHOT/mask-starter-1.0.0-SNAPSHOT.jar | findstr AutoConfiguration
```

应能看到 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 以及 `com/learn/mask/logback/masking-converter.xml` 等资源。

---

## 19.3 发布到远程制品库

本仓库**默认未配置** `distributionManagement`，不会自动 `deploy` 到 Nexus / Artifactory。按团队规范任选一种方式即可。

### 方式 A · 私服 `mvn deploy`（推荐团队内网）

1. 在**发布用**的 POM（通常是根 POM 或单独的 `mask-starter` 发布 POM）增加：

```xml
<distributionManagement>
    <repository>
        <id>company-releases</id>
        <url>https://nexus.example.com/repository/maven-releases/</url>
    </repository>
    <snapshotRepository>
        <id>company-snapshots</id>
        <url>https://nexus.example.com/repository/maven-snapshots/</url>
    </snapshotRepository>
</distributionManagement>
```

2. 在 `~/.m2/settings.xml` 里为上述 `id` 配置账号密码或 token。

3. 发布（示例：只发 starter 模块）：

```bash
mvn -pl mask-starter -am clean deploy -DskipTests
```

4. 业务项目的 `pom.xml` 增加 `<repository>`（或使用公司 parent BOM），依赖写法：

```xml
<dependency>
    <groupId>com.learn.mask</groupId>
    <artifactId>mask-starter</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

**版本建议**：对内 SNAPSHOT 联调；对外发版去掉 `-SNAPSHOT` 并打 Git tag，与 Maven 坐标一致。

### 方式 B · 不建私服，仅本机 / CI 缓存

- 开发者：`mvn install` 后，其他模块或本地新建工程通过同一 Maven 本地库消费。
- CI：在 pipeline 里 `mvn install`，下游 job 用同一 workspace 的 `.m2` 缓存，或把 `mask-starter` 构件上传到 CI 制品缓存。

### 方式 C · 尚未发版时的临时依赖

| 做法 | 适用 |
| --- | --- |
| `mvn install` 后按坐标引用 | 本机或已同步 `.m2` 的环境 |
| `system` 作用域指向 JAR | 仅临时演示，不推荐生产 |
| 把本仓库作为 Git submodule，业务工程 `<module>` 依赖 |  monorepo 式开发 |

---

## 19.4 全新 Spring Boot 项目：最小接入（仅 JSON 出口）

下面假设你已经用 [Spring Initializr](https://start.spring.io/) 或 IDE 建好一个 **Spring Boot 4.0.x + Java 21** 的空 Web 项目（包名自定）。

### 步骤 1 · 引入 mask-starter

在业务 `pom.xml` 中：

```xml
<properties>
    <java.version>21</java.version>
</properties>

<dependencies>
    <dependency>
        <groupId>com.learn.mask</groupId>
        <artifactId>mask-starter</artifactId>
        <version>1.0.0-SNAPSHOT</version>
    </dependency>
    <!-- Boot 4 Web 栈；Jackson 3 会随 starter 传递 -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-webmvc</artifactId>
    </dependency>
</dependencies>
```

若尚未 `install` 过 starter，先回到本教程仓库执行 [19.2](#192-在本仓库构建-mask-starter) 的安装命令。

### 步骤 2 · 打开 Jackson 通道（默认已开）

`src/main/resources/application.yml`：

```yaml
masking:
  enabled: true
  channels:
    jackson: true
    logback: false   # 未配 Logback 转换器前可先关
    aop: false
    mybatis: false
```

完整配置项见 [附录 A](appendix-a-config-reference.md)。

### 步骤 3 · DTO 上标注敏感字段

```java
import com.learn.mask.annotation.Sensitive;
import com.learn.mask.annotation.SensitiveType;

public record UserView(
        Long id,
        @Sensitive(type = SensitiveType.PHONE) String phone,
        @Sensitive(type = SensitiveType.EMAIL) String email
) {}
```

Controller 正常返回该对象即可，**无需** `@JsonSerialize`：starter 的 `SensitiveJacksonModule` 会通过内省器挂上序列化器（见 [第 9 章](09-channel-jackson.md)）。

### 步骤 4 · 启动并验证

```bash
mvn spring-boot:run
```

用 HTTP 客户端访问你的接口，响应 JSON 中 `phone` / `email` 应为打码形态（如 `138****5678`）。

**启动期自检**（详见 [第 17 章](17-pitfalls-and-troubleshooting.md)）：

- 日志里无 `MaskingAutoConfiguration` 相关报错。
- 若字段仍是明文：查 `masking.enabled`、`masking.channels.jackson`，以及是否误用 **Jackson 2** 依赖（与本 starter 不兼容）。

---

## 19.5 按通道扩展依赖

Starter 里各通道依赖多为 **`optional`**：只加 `mask-starter` **不会**自动带上 MyBatis、AspectJ、Caffeine 等。业务 POM 需要**显式**添加你想用的栈。

| 能力 | 业务项目额外依赖 | 配置 / 代码 |
| --- | --- | --- |
| **Jackson JSON 出口** | `spring-boot-starter-webmvc`（通常已够） | `masking.channels.jackson: true`；字段 `@Sensitive` |
| **Logback 日志** | 默认 Spring Boot 已带 Logback | `logback-spring.xml` include `com/learn/mask/logback/masking-converter.xml`；pattern 用 `%sensitiveMsg` / `%sensitiveKvp`（见 [第 10 章](10-channel-logback.md)） |
| **本地缓存** | `com.github.ben-manes.caffeine:caffeine` | `masking.cache.enabled: true` |
| **Micrometer 指标** | `spring-boot-starter-actuator` + registry（如 Prometheus） | 自动注册 `masking.*` 指标（见 [第 8 章](08-cache-and-metrics.md)） |
| **AOP 突变通道** | `spring-boot-starter-aspectj` | `masking.channels.aop: true`；方法 `@SensitiveMethod`（见 [第 12 章](12-channel-aop.md)） |
| **MyBatis 突变通道** | `mybatis-spring-boot-starter` | `masking.channels.mybatis: true`；Mapper `@Result(typeHandler = PhoneSensitiveTypeHandler.class)` 等（见 [第 11 章](11-channel-mybatis.md)） |
| **角色旁路 / 还原** | `spring-boot-starter-security`（可选） | `masking.bypass-roles` / `unmask-roles`；可逆字段见 [第 14 章](14-reversible-masking.md) |
| **规则热更新 HTTP** | Web 应用已存在即可 | 默认暴露 `GET/POST /api/admin/masking/*`（生产请加鉴权或关闭） |

**Logback 示例**（与 `mask-demo` 一致思路）：

```xml
<configuration>
    <include resource="org/springframework/boot/logging/logback/defaults.xml"/>
    <include resource="com/learn/mask/logback/masking-converter.xml"/>
    <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
            <pattern>%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %sensitiveKvp %sensitiveMsg%n</pattern>
        </encoder>
    </appender>
    <root level="INFO">
        <appender-ref ref="CONSOLE"/>
    </root>
</configuration>
```

SLF4J 2 fluent API 的 `addKeyValue` 必须走 `%sensitiveKvp`；仅换 `%sensitiveMsg` **不会**脱敏 KVP 段。

---

## 19.6 与 mask-demo 的对照

| 你在新项目里要做的事 | 参考 |
| --- | --- |
| 最小 Web + JSON 脱敏 | 本节 [19.4](#194-全新-spring-boot-项目最小接入仅-json-出口) |
| 四通道全开 + 缓存 + 指标 + Security | `mask-demo/pom.xml`、`application.yml` |
| 自定义策略 Bean | `mask-demo/.../mask/AddressMaskStrategy.java` + `masking.rules.extras` |
| 接口行为验证 | [附录 B](appendix-b-demo-api-cheatsheet.md)、[第 3 章](03-run-the-demo-in-10-min.md) |

Demo 启动方式（验证 starter 行为是否正常）：

```bash
mvn -q -DskipTests install
cd mask-demo && mvn spring-boot:run
```

---

## 19.7 接入检查清单

复制到工单或 PR 描述里逐项打勾：

- [ ] JDK 21；Spring Boot **4.0.x**（与 starter 构建版本一致）
- [ ] `com.learn.mask:mask-starter` 版本可解析（已 `install` 或私服可下载）
- [ ] 需要 JSON 脱敏：`spring-boot-starter-webmvc` + `masking.channels.jackson=true`
- [ ] DTO 字段为 **String** 且带 `@Sensitive`（或 `masking.map-keys` 映射字段名）
- [ ] 需要日志脱敏：include `masking-converter.xml`，pattern 含 `%sensitiveMsg` / `%sensitiveKvp`
- [ ] 需要 AOP/MyBatis：已加对应 starter，且理解 [出口 vs 突变](02-where-to-hook-in-spring.md#23-本教程最重要的心智模型出口通道-vs-突变通道)
- [ ] Jackson + AOP/MyBatis 同时开：知悉 [通道冲突校验](13-multi-channel-cooperation.md) 与 `strict` 含义
- [ ] 生产关闭 `masking.debug.header-role-enabled`
- [ ] 可逆脱敏：更换 `masking.reversible.secret-key`，并实现 `SensitiveFieldLookup` Bean

更细的设计说明与条件注解分工见 [18.5 移植检查清单](18-starter-design-review.md#185-移植到自己项目的检查清单)。

---

## 19.8 常见问题（接入阶段）

| 现象 | 优先排查 |
| --- | --- |
| 依赖解析失败 `mask-starter` | 是否在本仓库执行过 `mvn install`；私服 URL / 版本号是否正确 |
| 注解完全不生效 | [17.1](17-pitfalls-and-troubleshooting.md) 三步：开关 → AutoConfiguration → Jackson 3 |
| 只有 ADMIN 能看到明文 | 正常旁路；查 `masking.bypass-roles` 与 Security 角色 |
| 日志仍是明文 | pattern 是否仍用 `%msg` / `%m`；fluent 日志是否未用 `%sensitiveKvp` |
| 启动报 Jackson 类找不到 | 未引入 `spring-boot-starter-jackson` / `webmvc`；或混入了 Jackson 2 |

---

## 本章小结

- **构建**：`-pl mask-starter` 测试；根目录 `mvn install` 供本机与其他模块使用。
- **发布**：自行增加 `distributionManagement` + `deploy`，或 CI 安装到共享 `.m2`。
- **新项目最小路径**：`mask-starter` + `webmvc` + `masking.*` + `@Sensitive`，无需改 Jackson 全局配置。
- **通道与依赖**：optional 通道要在业务 POM 里补依赖，并在 YAML 里打开对应 `masking.channels.*`。
