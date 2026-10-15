# common-pay

可嵌入的交易内核 + 可插拔支付渠道。业务项目引入 `common-pay-spring-boot-starter` 后，即可获得下单、库存预扣、支付、关单、退款补偿等能力。

## 模块

| 模块 | 说明 |
|------|------|
| `pay-api` | 支付渠道 SPI |
| `pay-channel-mock` | Mock 支付（默认可用，便于本地联调） |
| `pay-channel-wechat` | 微信 Native 扫码支付 |
| `pay-channel-alipay` | 支付宝预创建交易扫码支付 |
| `trade-core` | 库存 / 订单 / 支付编排、Outbox、定时补偿 |
| `common-pay-spring-boot-starter` | **业务方只引这一个**：自动装配 + Flyway 建表 |

## 环境要求

- JDK 17+
- Spring Boot 3.3.x
- MySQL 8+（业务方事先建库）
- Redis 单机（强制依赖）
- RocketMQ（异步下单 / 延迟关单）

## 快速接入

### 1. 安装到本地仓库

```bash
mvn -DskipTests install
```

### 2. 引入依赖

```xml
<dependency>
    <groupId>com.commonpay</groupId>
    <artifactId>common-pay-spring-boot-starter</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

### 3. 最小配置

```yaml
spring:
  datasource:
    url: jdbc:mysql://127.0.0.1:3306/your_db?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai
    username: root
    password: your_password
  data:
    redis:
      host: 127.0.0.1
      port: 6379

rocketmq:
  name-server: 127.0.0.1:9876
  producer:
    group: your-producer-group

common-pay:
  enabled: true
  auto-migrate: true   # 启动时自动执行 V1__init.sql 建表
```

启动后会写入独立 Flyway history 表 `common_pay_flyway_history`，不占用业务方自己的 Flyway。

### 4. 发起支付（示例）

```java
// 异步下单受理
CreateOrderResult result = orderService.createOrderRedis(request);

// 创建支付凭证（默认 MOCK；也可传 WECHAT / ALIPAY）
CreatePayResponse pay = payAppService.pay(orderId, "MOCK");
// pay.getPayPayload() → 前端生成二维码 / 打开支付页
```

## 支付渠道

| 渠道 | 默认 | 说明 |
|------|------|------|
| `MOCK` | 开启 | 本地联调，无需商户号 |
| `WECHAT` | 关闭 | Native `code_url` 扫码 |
| `ALIPAY` | 关闭 | 预创建交易 `qr_code` 扫码 |

微信 / 支付宝启用后会校验必填配置，并注册回调：

- 微信：`POST /common-pay/notify/wechat`、`POST /common-pay/notify/wechat/refund`
- 支付宝：`POST /common-pay/notify/alipay`

**不要把真实商户密钥、证书或个人敏感信息提交到仓库。** 本地密钥用 `application-local.yml`（已忽略）或环境变量。

## 一致性要点（实现已对齐）

- 事务消息未确认（`UNKNOW`/发送异常）时**不会**错误归还 Redis 预扣
- 关单认领与 CAS 统一为 `pay_deadline <= now`，并兜底时间片滑窗外的漏单
- 主动查单入账要求金额，并使用稳定 `QUERY:{channelTradeNo}` 作为 notify 幂等键
- 支付回调对不可重试业务错误 ACK，避免渠道无限重试（需监控日志）

更多行为说明与已知限制见 [docs/USAGE.md](docs/USAGE.md)。

## 文档

- [docs/USAGE.md](docs/USAGE.md) — 引入、配置、API、表结构、注意事项

## 构建与测试

```bash
mvn clean test-compile   # 当前无单元测试源码；编译通过即可
mvn -DskipTests install
```
