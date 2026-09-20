# 附录 D · 章节 ↔ 源码对照

教程代码在 `mask-tutorial/src/main/java/com/learn/mask/tutorial/chNN/`。成品在 `mask-starter` / `mask-demo`。路径从模块根写起。

| 章 | 教程代码 | 成品主要文件 | 测试 |
| --- | --- | --- | --- |
| 1~3 | 无（跑 Demo） | `mask-demo` 全模块；`docs/03` 的启动方式 | 第 3 章用 curl |
| 4 | `ch04/*` 策略、`MaskRule`、Registry | `strategy/*`、`support/MaskUtils.java`、`annotation/SensitiveType.java`；Demo `mask/ExpressNoMaskStrategy` 等 | 教程 `ch04/*Test`；starter `MaskStrategyTest` |
| 5 | `ch05` MaskContext / Role / Header 滤器 | `context/MaskContext.java`、`MaskRole.java`、`HeaderRoleFilter.java` | 教程 `ch05/*Test` |
| 6 | `ch06` Engine、Action、Settings、Detector | `engine/MaskEngine.java`、`MaskAction.java`、`AlreadyMaskedDetector.java` | 教程 `ch06/MaskEngineTest`；starter `MaskEngineTest` |
| 7 | `ch07` Properties、RuleSet、Reload | `config/MaskingProperties.java`、`MaskRule.java`、`web/MaskingReloadController.java` | 教程 `ch07/*Test` |
| 8 | `ch08` Caffeine、Micrometer、Benchmark | `cache/MaskCache.java`、`metrics/MaskingMetrics.java` | 教程 `ch08/*`；Benchmark 用 `exec:java` |
| 9 | `ch09` `@Sensitive`、序列化器、桥、MapView | `annotation/Sensitive.java`、`jackson/*`、`support/MaskingSpringBridge.java`、`config/JacksonMaskingAutoConfiguration.java` | 教程 `ch09/*Test` |
| 10 | `ch10` Converter | `logback/SensitiveMessageConverter.java`、`resources/.../masking-converter.xml`；Demo `logback-spring.xml` | 教程 / starter converter 测 |
| 11 | `ch11` TypeHandler | `mybatis/*`、`config/MybatisMaskingAutoConfiguration.java`（空）；Demo `mapper/UserMaskedMapper` | 教程 `ch11` |
| 12 | `ch12` Method、Aspect、Walker | `annotation/SensitiveMethod.java`、`aop/*`、`config/AopMaskingAutoConfiguration.java`；Demo `AopUserController`、`loadForAop` | 教程 `ch12` |
| 13 | `ch13` 通道开关与校验 | `config/MaskingChannelValidator.java`；Demo `application-*.yml` | 教程 ch13；starter `MaskingChannelValidatorTest` |
| 14 | `immediatewithoutcrypto` / `viewwithcrypto` / `dialwithcrypto`（三包各自完整） | `crypto/*`；Demo `UnmaskController` 只签发不核销 | `UnmaskServiceTest` / `ViewTicketServiceTest` / `DialTicketServiceTest`；starter 仅往返；Demo CS/USER 两条 |
| 15 | 无新生产代码 | 上表全部 `src/test` | `mask-demo/.../MaskingApiTest.java` |
| 16 | 无新生产代码 | `mask-demo/.../perf/MaskingSimulation.java`、`mask-demo/pom.xml` 的 `perf` profile | 不进 surefire |
| 17 | 无 | 指向各章文件 + 17.8 表 | — |
| 18 | 无 | `mask-starter/pom.xml`、`config/*AutoConfiguration.java`、`META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` | — |

附录 A 对应 `MaskingProperties` 与 `additional-spring-configuration-metadata.json`。  
附录 B 对应 `mask-demo/.../web/*Controller.java`、`SecurityConfig.java`、`application.yml`、`data.sql`。

教程模块**不在**根 `pom.xml` 的 `<modules>` 里，单独跑：

```bash
mvn -f mask-tutorial/pom.xml test
```
