package com.commonpay.trade.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单表 trade_order。
 * 主键雪花算法生成（IdType.ASSIGN_ID）。
 * <p>必须保留无参构造：仅 {@code @Builder} 时 Lombok 会生成全参构造且去掉默认无参，
 * MyBatis 会按列顺序走构造器映射，把 UUID 的 request_id 塞进 Integer 字段报 NumberFormatException。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("trade_order")
@Accessors(chain = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Order extends BaseEntity {

    /** 订单主键，雪花 ID。雪花值超出 JS 安全整数范围，序列化成字符串防精度丢失 */
    @TableId(value = "order_id", type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long orderId;

    /** 外部用户 ID（无本系统用户表，由调用方传入） */
    private String userId;

    /** 购买的商品 ID */
    private Long productId;

    /** 购买数量 */
    private Integer quantity;

    /** 订单总金额快照（下单时单价×数量），单位：元，保留两位小数 */
    private BigDecimal amount;

    /**
     * 订单状态。
     * 0=待支付，1=已支付，2=已取消（见 {@link com.commonpay.trade.enums.OrderStatus}）
     */
    private Integer status;

    /** 支付截止时间；超时未付由关单任务改为已取消并归还库存 */
    private LocalDateTime payDeadline;

    /**
     * 异步下单受理号（对应 trade_order.request_id，唯一）。
     * 消费端用它做幂等；同步下单为 null。
     */
    private String requestId;

    private LocalDateTime nextCloseAt;
    private Integer closeRetryCount;
    private String lastCloseError;
}
