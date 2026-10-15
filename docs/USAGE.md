# common-pay 使用文档

本文说明如何在业务项目中引入、配置并使用 common-pay。

## 1. 引入依赖

业务方**只引入 starter**：

```xml
<dependency>
    <groupId>com.commonpay</groupId>
    <artifactId>common-pay-spring-boot-starter</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

本地尚未发布到私服时，先在本仓库执行：

```bash
mvn -DskipTests install
```

宿主应用还需具备：

- `spring-boot-starter-web`（回调 Controller、异常处理）
- 可用的 `DataSource`（MySQL）
- `spring.data.redis.*`（单机 Redis）
- RocketMQ NameServer 与 Producer 配置

## 2. 基础设施

### 2.1 数据库

事先创建业务库（脚本不建库、不 DROP）：

```sql
CREATE DATABASE your_db DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

`common-pay.auto-migrate=true`（默认）时，启动会执行：

`classpath:db/common-pay/migration/V1__init.sql`

使用独立 history 表：`common_pay_flyway_history`。

若不希望自动建表：

```yaml
common-pay:
  auto-migrate: false
```

此时可手工执行 starter 中的 `V1__init.sql`。

### 2.2 Redis

强制依赖。宿主必须配置 `spring.data.redis.*`。

当前 Redisson 仅支持**单机**（host/port）。若使用 URL / Sentinel / Cluster / SSL Bundle，需自行扩展配置，否则启动失败。

可选 key 前缀，避免与业务 key 冲突：

```yaml
common-pay:
  redis:
    key-prefix: "ticket:"   # 空字符串表示不加前缀
```

### 2.3 RocketMQ

异步下单（Redis 预扣 + Outbox）与延迟关单依赖 RocketMQ。请配置：

```yaml
rocketmq:
  name-server: 127.0.0.1:9876
  producer:
    group: your-app-producer
```

Topic / Consumer 由内核内注解绑定，部署前请保证 NameServer 可达。

## 3. 配置项一览

前缀均为 `common-pay`。

### 3.1 总开关

| 配置项 | 默认 | 说明 |
|--------|------|------|
| `common-pay.enabled` | `true` | `false` 时不装配交易 Bean、不跑迁移 |
| `common-pay.auto-migrate` | `true` | 是否执行独立 Flyway 建表 |

```yaml
common-pay:
  enabled: true
  auto-migrate: true
```

### 3.2 Web 适配

| 配置项 | 默认 | 说明 |
|--------|------|------|
| `common-pay.web.exception-handler` | `true` | 注册统一异常处理（HTTP 200 + 业务码 `R`）。宿主已有全局 Handler 时可关闭 |

```yaml
common-pay:
  web:
    exception-handler: true
```

### 3.3 Redis 前缀

| 配置项 | 默认 | 说明 |
|--------|------|------|
| `common-pay.redis.key-prefix` | `""` | 全局 key 前缀 |

### 3.4 微信支付

默认关闭。`enabled=true` 时启动会校验必填项。

```yaml
common-pay:
  wechat:
    enabled: false
    app-id: ${WECHAT_APP_ID:}
    merchant-id: ${WECHAT_MERCHANT_ID:}
    merchant-serial-number: ${WECHAT_MERCHANT_SERIAL_NUMBER:}
    merchant-private-key-path: ${WECHAT_MERCHANT_PRIVATE_KEY_PATH:}
    wechat-pay-public-key-path: ${WECHAT_PAY_PUBLIC_KEY_PATH:}
    wechat-pay-public-key-id: ${WECHAT_PAY_PUBLIC_KEY_ID:}
    api-v3-key: ${WECHAT_API_V3_KEY:}
    notify-url: https://your-domain.example/common-pay/notify/wechat
    refund-notify-url: https://your-domain.example/common-pay/notify/wechat/refund
```

| 配置项 | 必填（启用时） | 说明 |
|--------|----------------|------|
| `enabled` | — | 是否启用微信渠道 |
| `app-id` | 是 | 应用 AppId |
| `merchant-id` | 是 | 商户号 |
| `merchant-serial-number` | 是 | 商户证书序列号 |
| `merchant-private-key-path` | 是 | 商户 API 私钥路径 |
| `wechat-pay-public-key-path` | 是 | 微信支付公钥路径 |
| `wechat-pay-public-key-id` | 是 | 微信支付公钥 ID |
| `api-v3-key` | 是 | APIv3 密钥 |
| `notify-url` | 是 | 支付结果回调（公网 HTTPS） |
| `refund-notify-url` | 否 | 退款结果回调 |

回调路径（启用后自动注册）：

- `POST /common-pay/notify/wechat`
- `POST /common-pay/notify/wechat/refund`

### 3.5 支付宝

默认关闭。示例为 **RSA2 公钥模式**（密钥正文，不是文件路径）。

```yaml
common-pay:
  alipay:
    enabled: false
    app-id: ${ALIPAY_APP_ID:}
    merchant-private-key: ${ALIPAY_MERCHANT_PRIVATE_KEY:}
    alipay-public-key: ${ALIPAY_PUBLIC_KEY:}
    seller-id: ${ALIPAY_SELLER_ID:}
    gateway-url: https://openapi.alipay.com/gateway.do
    notify-url: https://your-domain.example/common-pay/notify/alipay
```

| 配置项 | 必填（启用时） | 说明 |
|--------|----------------|------|
| `enabled` | — | 是否启用支付宝渠道 |
| `app-id` | 是 | 应用 AppId |
| `merchant-private-key` | 是 | 商户私钥正文 |
| `alipay-public-key` | 是 | 支付宝公钥正文 |
| `seller-id` | 是 | 卖家/商户 PID |
| `gateway-url` | 是 | 网关，默认正式环境 |
| `notify-url` | 是 | 异步通知地址（公网 HTTPS） |

回调路径：

- `POST /common-pay/notify/alipay`

## 4. 完整配置示例

```yaml
spring:
  datasource:
    url: jdbc:mysql://127.0.0.1:3306/your_db?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai
    username: root
    password: ******
    driver-class-name: com.mysql.cj.jdbc.Driver
  data:
    redis:
      host: 127.0.0.1
      port: 6379
      database: 0
      # password:

rocketmq:
  name-server: 127.0.0.1:9876
  producer:
    group: your-producer-group

common-pay:
  enabled: true
  auto-migrate: true
  redis:
    key-prefix: "your-app:"
  web:
    exception-handler: true
  wechat:
    enabled: false
  alipay:
    enabled: false
```

本地密钥建议放在 `application-local.yml`（已在 `.gitignore` 中忽略），或通过环境变量注入。

### 4.1 交易内核调度（可选）

| 配置项 | 默认 | 说明 |
|--------|------|------|
| `trade.reservation-debt.enabled` | `true` | 崩溃后「有预扣、无 Outbox」义务对账 |
| `trade.reservation-debt.age-seconds` | `900` | 义务至少存活秒数才回补；需大于半消息发送最坏耗时 |
| `trade.reservation-debt.interval-seconds` | `1800` | 对账调度间隔 |
| `trade.order-close.slice-minutes` | `1` | 关单时间片宽度（分钟） |

```yaml
trade:
  reservation-debt:
    enabled: true
    age-seconds: 900
    interval-seconds: 1800
  order-close:
    slice-minutes: 1
```

## 5. 核心用法

注入内核服务即可（starter 已 `@ComponentScan`）：

| 服务 | 用途 |
|------|------|
| `OrderService` | 下单、查单、关单、异步进度 |
| `PayAppService` | 创建支付、处理支付成功回调、按订单查支付单 |
| `ProductService` / `InventoryService` | 商品与库存 |
| `RefundService` / `RefundNotifyService` | 退款与退款回调 |

### 5.1 异步下单（推荐）

```java
CreateOrderRequest req = new CreateOrderRequest();
req.setUserId("u1");
req.setProductId(1001L);
req.setQuantity(1);
req.setRequestId(UUID.randomUUID().toString()); // 业务侧生成，幂等键

CreateOrderResult accepted = orderService.createOrderRedis(req);
// 轮询进度
OrderProgress progress = orderService.getCreateProgress(req.getRequestId());
```

流程概要：Redis 预扣库存 → 写 Outbox → 发事务/普通消息 → 消费端落库订单。

### 5.2 发起支付

```java
// channel: MOCK | WECHAT | ALIPAY
CreatePayResponse resp = payAppService.pay(orderId, "MOCK");
String payload = resp.getPayPayload(); // MOCK 自定义 / 微信 code_url / 支付宝 qr_code
```

前端用 `payPayload` 生成二维码或跳转；**支付成功只能由服务端验签回调（或查单任务）确认**，前端不可直接改状态。

### 5.3 查询

```java
Order order = orderService.getOrder(orderId);
Payment payment = payAppService.getByOrderId(orderId);
```

## 6. 表结构（V1__init.sql）

| 表名 | 说明 |
|------|------|
| `trade_product` | 可售商品 |
| `trade_inventory` | 商品库存 |
| `trade_order` | 业务订单 |
| `trade_payment` | 支付单（与订单 1:1） |
| `trade_user` | 交易用户（不绑宿主账号体系） |
| `trade_stock_return` | Redis 预扣失败后的归还欠账 |
| `trade_outbox` | 本地消息表 |
| `trade_refund` | 迟到支付的全额退款义务 |
| `trade_stock_release` | 取消订单后的 Redis 回库义务 |

脚本末尾含 Demo 种子：商品 `1001`、库存 `10`、用户 `u1`（已存在则跳过）。

完整 DDL 见：

`common-pay-spring-boot-starter/src/main/resources/db/common-pay/migration/V1__init.sql`

## 7. 运行时行为摘要

- **异步下单**：Redis 预扣 + 事务消息写 Outbox。仅确认本地 `ROLLBACK` 且无 Outbox 时才归还预扣；`UNKNOW`/发送异常保留预扣并返回 `ORDER_RESULT_UNKNOWN`，由回查与债务任务收敛。
- **关单**：支付超时后先关渠道，再在本地事务取消订单并登记 Redis 回库义务。定时任务按时间片扫描，并额外兜底「滑窗外仍过期」的漏单；`pay_deadline <= now` 与认领条件一致。
- **查单入账**：回调丢失时主动查渠道；`PAID` 必须带金额与流水号，合成 `notifyId=QUERY:{channelTradeNo}` 后走与回调相同的结算。
- **支付回调**：验签失败返回失败；不可重试的 `BusinessException`（金额/流水/状态冲突等）会 ACK 成功以免渠道无限重试，需依赖日志人工核对。
- **迟到支付**：订单已取消仍收到真实付款时，保留支付事实并写入退款任务。
- **异常响应**：默认 `common-pay.web.exception-handler=true`，返回引擎统一 `R`（HTTP 200 + 业务码）。

## 8. 注意事项

1. 本组件面向**干净库首次接入**；`V1__init.sql` 为最终态单文件，不要与历史分版本迁移混用。
2. 微信 / 支付宝 `notify-url` 必须是支付平台可访问的公网 HTTPS。
3. 不要把商户私钥、APIv3 密钥、证书、真实商户号提交进 Git；示例配置一律用环境变量占位。
4. Redisson 当前仅单机；集群场景需自行改造后再启用。
5. 雪花 ID 超出 JS 安全整数，序列化已转字符串；前端请按字符串处理 `orderId` / `paymentId`。
6. 仓库当前**无自动化单元/集成测试**；接入前请在宿主项目用 MOCK 渠道做冒烟（下单 → 支付 → 回调/查单 → 关单/退款）。

## 9. 已知限制（未在本版本完全消除）

| 风险 | 说明 |
|------|------|
| 预扣债务竞态 | 若受理卡住超过 `age-seconds` 且仍无 Outbox，债务任务可能先回补；随后受理成功会造成超卖。请保证 age 覆盖最坏发送耗时。 |
| 长期 `SENT` Outbox | 半消息若被异常丢弃，缺少把超时 `SENT` 自动失败并回补的专用对账；需运维关注积压。 |
| 本地 SUCCESS + 渠道已退款 | 关单会报冲突并退避重试，需人工核对，不会自动把订单关成取消。 |
| 布隆过滤器 | 运行期新增商品需自行调用 `ProductBloomGate.add`，否则可能被误判为不存在。 |
| 永久拒绝的回调 ACK | 金额不符等永久错误会向渠道返回成功以免狂重试；必须监控错误日志，避免漏处理资金差异。 |
