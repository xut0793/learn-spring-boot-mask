# 附录 B · Demo 接口与账号速查

服务启动见第 3 章：`mvn -q -DskipTests install`，然后 `cd mask-demo && mvn spring-boot:run`。  
端口 `8080`，HTTP Basic。H2 用户 `id=1`：`Zhang San` / `13812345678` / `110101199003078515`。

---

## 账号

| 用户 | 密码 | 角色 | 普通接口 | `POST /api/unmask` | `POST /api/admin/**` |
| --- | --- | --- | --- | --- | --- |
| `admin` | `admin123` | ADMIN | 明文（旁路） | 200 | 200 |
| `user` | `user123` | USER | 打码 | 403 | 403 |
| `cs` | `cs123` | CS | 打码 | 200 | 403 |

---

## 接口

| 方法 | 路径 | 通道 | 返回 |
| --- | --- | --- | --- |
| GET | `/api/jackson/users/{id}` | Jackson 序列化 `UserDto` | 嵌套 address、contacts |
| GET | `/api/jackson/users/{id}/as-map` | `SensitiveMapView` | 键名映射 |
| GET | `/api/aop/users/{id}` | `@SensitiveMethod` 改内存后再 JSON | 看起来像 Jackson |
| GET | `/api/db/users/{id}` | MyBatis TypeHandler，`UserEntity` 无 `@Sensitive` | `addressDetail` **未挂 Handler，USER 下是明文** |
| POST | `/api/unmask` | 还原 | `value` + `token` |
| GET | `/api/admin/masking/rules` | 读配置 | 需 ADMIN |
| POST | `/api/admin/masking/reload` | 热更新 | 需 ADMIN |
| GET | `/actuator/metrics/masking.invoke` | Micrometer | `result` 标签为**小写** |
| GET | `/actuator/prometheus` | Prometheus 文本 | |
| GET | `/actuator/health` | 匿名可访问 | |

Demo 默认 `channels` 四开、`header-role-enabled: true`、`strict: false`。与 starter 默认值不同。

---

## curl

PowerShell 里长 JSON 建议用文件或单引号注意转义。下面按 bash / Git Bash 写。

```bash
# USER：打码
curl -u user:user123 http://localhost:8080/api/jackson/users/1

# ADMIN：明文
curl -u admin:admin123 http://localhost:8080/api/jackson/users/1

# Header 覆盖（仅 Demo 开着调试头时）
curl -u user:user123 -H "X-User-Role: ADMIN" http://localhost:8080/api/jackson/users/1

# Map 视图
curl -u user:user123 http://localhost:8080/api/jackson/users/1/as-map

# AOP / MyBatis
curl -u user:user123 http://localhost:8080/api/aop/users/1
curl -u user:user123 http://localhost:8080/api/db/users/1

# CS 还原；USER 应 403
curl -u cs:cs123 -H "Content-Type: application/json" \
  -d "{\"userId\":1,\"field\":\"phone\"}" http://localhost:8080/api/unmask

# 读规则 / 热更新 PHONE 为前 2 后 2
curl -u admin:admin123 http://localhost:8080/api/admin/masking/rules
curl -u admin:admin123 -H "Content-Type: application/json" \
  -d "{\"rules\":{\"PHONE\":{\"keepPrefix\":2,\"keepSuffix\":2,\"maskChar\":\"*\",\"enabled\":true}}}" \
  http://localhost:8080/api/admin/masking/reload

# 指标（注意 result=mask 不是 MASK）
curl -u user:user123 http://localhost:8080/actuator/metrics/masking.invoke
```

---

## USER 打码期望（id=1，默认规则）

| 字段 | 明文 | USER JSON |
| --- | --- | --- |
| phone | `13812345678` | `138****5678` |
| idCard | `110101199003078515` | `110101********8515` |
| email | `zhangsan@example.com` | `z*******@example.com` |
| bankCard | `6222021234567890123` | `6222***********0123` |
| address.detail | `Chaoyang Road 88` | `Chaoyang Road **` |
| expressNo | `SF1234567890123` | `SF12*******0123` |
| contacts[0] | `13900001111` | jackson DTO：`contacts[0].value` = `139****1111`；as-map：`contacts[0].phone` |

`/api/db` 的 `addressDetail` 对 USER 仍是 `Chaoyang Road 88`（第 3 章实验七）。

还原响应：`value` 为库中明文；`token` 为 Base64 AES-GCM，每次不同。starter **不限制** field，`email` 对 CS 也会 200。

---

## profile

| 文件 | 作用 |
| --- | --- |
| （无）`application.yml` | 四通道全开，strict false |
| `application-jackson.yml` | 第 13 章单通道 |
| `application-aop.yml` | 第 13 章 |
| `application-mybatis.yml` | 第 13 章 |
| `application-conflict.yml` | 与默认类似的冲突组合 |

```bash
cd mask-demo
mvn spring-boot:run -Dspring-boot.run.arguments="--spring.profiles.active=jackson"
```
