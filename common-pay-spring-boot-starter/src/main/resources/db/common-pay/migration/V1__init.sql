-- common-pay 初始化脚本（最终态建表）
-- 库需业务方事先创建；本脚本只建表，不 DROP、不建库。
-- Flyway history 表：common_pay_flyway_history

-- -----------------------------
-- 商品
-- -----------------------------
CREATE TABLE IF NOT EXISTS trade_product (
    product_id   BIGINT         NOT NULL AUTO_INCREMENT COMMENT '商品ID',
    title        VARCHAR(128)   NOT NULL COMMENT '商品标题',
    price        DECIMAL(12, 2) NOT NULL COMMENT '单价，单位元，保留两位小数',
    status       TINYINT        NOT NULL DEFAULT 1 COMMENT '1上架 0下架',
    created_at   DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (product_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='可售商品';

-- -----------------------------
-- 库存
-- -----------------------------
CREATE TABLE IF NOT EXISTS trade_inventory (
    id           BIGINT   NOT NULL AUTO_INCREMENT COMMENT '库存主键',
    product_id   BIGINT   NOT NULL COMMENT '商品ID',
    cnt          INT      NOT NULL DEFAULT 0 COMMENT '可售库存',
    created_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_inventory_product (product_id),
    CONSTRAINT fk_inventory_product FOREIGN KEY (product_id) REFERENCES trade_product (product_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品库存';

-- -----------------------------
-- 订单
-- status: 0待支付 1已支付 2已取消
-- -----------------------------
CREATE TABLE IF NOT EXISTS trade_order (
    order_id           BIGINT         NOT NULL COMMENT '订单ID（应用侧雪花）',
    user_id            VARCHAR(64)    NOT NULL COMMENT '外部用户ID',
    product_id         BIGINT         NOT NULL COMMENT '商品ID',
    quantity           INT            NOT NULL DEFAULT 1 COMMENT '购买数量',
    amount             DECIMAL(12, 2) NOT NULL COMMENT '订单金额，单位元',
    status             TINYINT        NOT NULL DEFAULT 0 COMMENT '0待支付 1已支付 2已取消',
    pay_deadline       DATETIME       NOT NULL COMMENT '支付截止时间',
    request_id         VARCHAR(64)    NULL COMMENT '异步下单受理号（MQ 幂等键）',
    next_close_at      DATETIME       NULL COMMENT '下次关单重试时间',
    close_retry_count  INT            NOT NULL DEFAULT 0 COMMENT '关单重试次数',
    last_close_error   VARCHAR(1000)  NULL COMMENT '最近一次关单错误',
    created_at         DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (order_id),
    UNIQUE KEY uk_order_request (request_id),
    KEY idx_order_user (user_id),
    KEY idx_order_status_deadline (status, pay_deadline),
    KEY idx_order_product (product_id),
    KEY idx_order_close_retry (status, next_close_at, pay_deadline),
    KEY idx_order_close_deadline_slice (status, pay_deadline, order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='业务订单';

-- -----------------------------
-- 支付单（与订单 1:1）
-- status: 0已创建 1待支付 2成功 3关闭
-- -----------------------------
CREATE TABLE IF NOT EXISTS trade_payment (
    payment_id           BIGINT         NOT NULL COMMENT '支付单ID（应用侧雪花）',
    order_id             BIGINT         NOT NULL COMMENT '订单ID',
    channel              VARCHAR(32)    NOT NULL DEFAULT 'MOCK' COMMENT '渠道：MOCK/WECHAT/ALIPAY',
    amount               DECIMAL(12, 2) NOT NULL COMMENT '支付金额，单位元',
    status               TINYINT        NOT NULL DEFAULT 0 COMMENT '0已创建 1待支付 2成功 3关闭',
    channel_trade_no     VARCHAR(64)    NULL COMMENT '渠道流水号',
    notify_id            VARCHAR(64)    NULL COMMENT '回调通知号（幂等）',
    paid_at              DATETIME       NULL COMMENT '服务端确认支付成功时间',
    expire_time          DATETIME       NULL COMMENT '支付凭证过期时间，与渠道 time_expire 一致',
    next_reconcile_at    DATETIME       NULL COMMENT '下次查单时间',
    last_reconcile_error VARCHAR(1000)  NULL COMMENT '最近一次查单错误',
    created_at           DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (payment_id),
    UNIQUE KEY uk_payment_order (order_id),
    UNIQUE KEY uk_payment_notify (channel, notify_id),
    UNIQUE KEY uk_payment_channel_trade (channel, channel_trade_no),
    KEY idx_payment_status (status),
    KEY idx_payment_reconcile (status, next_reconcile_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='支付单';

-- -----------------------------
-- 交易用户（user_id 与订单同空间，不建外键）
-- -----------------------------
CREATE TABLE IF NOT EXISTS trade_user (
    user_id      VARCHAR(64)  NOT NULL COMMENT '用户ID，与 trade_order.user_id 同空间',
    nickname     VARCHAR(64)  NULL COMMENT '昵称',
    status       TINYINT      NOT NULL DEFAULT 1 COMMENT '1正常 0停用',
    created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id),
    KEY idx_user_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='交易用户';

-- -----------------------------
-- 库存归还欠账（Redis 预扣后发送失败补偿）
-- status: 0待处理 1已归还 2无需归还
-- -----------------------------
CREATE TABLE IF NOT EXISTS trade_stock_return (
    id         BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    product_id BIGINT      NOT NULL COMMENT '商品ID',
    quantity   INT         NOT NULL COMMENT '应归还数量',
    request_id VARCHAR(64) NULL COMMENT '关联的异步受理号',
    reason     VARCHAR(64) NULL COMMENT '欠账原因，如 MQ_SEND_FAILED',
    status     TINYINT     NOT NULL DEFAULT 0 COMMENT '0待处理 1已归还 2无需归还',
    created_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_stock_return_status (status),
    KEY idx_stock_return_request (request_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='库存归还欠账（补偿任务用）';

-- -----------------------------
-- 本地消息表（Outbox）
-- status: 0待发送 1已发送 2已消费 3消费失败 4已补偿
-- -----------------------------
CREATE TABLE IF NOT EXISTS trade_outbox (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    request_id  VARCHAR(64)  NOT NULL COMMENT '业务受理号',
    topic       VARCHAR(128) NOT NULL COMMENT '目标 Topic',
    payload     TEXT         NOT NULL COMMENT '消息体 JSON',
    status      TINYINT      NOT NULL DEFAULT 0 COMMENT '0待发送 1已发送 2已消费 3消费失败 4已补偿',
    retry_count INT          NOT NULL DEFAULT 0 COMMENT '发送失败次数',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_outbox_request (request_id),
    KEY idx_outbox_status (status, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='本地消息表（消息全生命周期）';

-- -----------------------------
-- 退款（已取消订单收到迟到支付后的全额退款义务）
-- status: 0待处理/未知 1处理中 2成功 3人工处理
-- -----------------------------
CREATE TABLE IF NOT EXISTS trade_refund (
    refund_id         BIGINT         NOT NULL COMMENT '同时作为稳定的商户退款请求号',
    payment_id        BIGINT         NOT NULL,
    order_id          BIGINT         NOT NULL,
    channel           VARCHAR(32)    NOT NULL,
    channel_trade_no  VARCHAR(64)    NOT NULL,
    channel_refund_no VARCHAR(64)    NULL,
    amount            DECIMAL(12, 2) NOT NULL,
    reason            VARCHAR(128)   NOT NULL,
    status            TINYINT        NOT NULL DEFAULT 0 COMMENT '0待处理/未知 1处理中 2成功 3人工处理',
    retry_count       INT            NOT NULL DEFAULT 0,
    next_retry_at     DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    claim_token       VARCHAR(64)    NULL,
    claim_until       DATETIME       NULL,
    last_error        VARCHAR(1000)  NULL,
    refunded_at       DATETIME       NULL,
    created_at        DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (refund_id),
    UNIQUE KEY uk_refund_payment (payment_id),
    KEY idx_refund_due (status, next_retry_at, claim_until),
    KEY idx_refund_order (order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='已取消订单的全额退款义务';

-- -----------------------------
-- 库存回库义务（取消订单后 Redis 回库）
-- status: 0待回库 1已处理
-- -----------------------------
CREATE TABLE IF NOT EXISTS trade_stock_release (
    release_id    BIGINT   NOT NULL AUTO_INCREMENT COMMENT '库存回库顺序',
    order_id      BIGINT   NOT NULL,
    product_id    BIGINT   NOT NULL,
    quantity      INT      NOT NULL,
    status        TINYINT  NOT NULL DEFAULT 0 COMMENT '0待回库 1已处理',
    retry_count   INT      NOT NULL DEFAULT 0,
    next_retry_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_error    VARCHAR(1000) NULL,
    created_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (order_id),
    UNIQUE KEY uk_stock_release_sequence (release_id),
    KEY idx_stock_release_product (product_id, release_id),
    KEY idx_stock_release_due (status, next_retry_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='取消订单后的 Redis 库存回库义务';

-- -----------------------------
-- 演示种子（已存在则跳过）
-- -----------------------------
INSERT INTO trade_product (product_id, title, price, status)
SELECT 1001, 'Demo虚拟票-普通票', 1.00, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM trade_product WHERE product_id = 1001);

INSERT INTO trade_inventory (product_id, cnt)
SELECT 1001, 10
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM trade_inventory WHERE product_id = 1001);

INSERT INTO trade_user (user_id, nickname, status)
SELECT 'u1', 'Demo用户', 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM trade_user WHERE user_id = 'u1');
