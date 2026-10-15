package com.commonpay.trade.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 支付单表 trade_payment。
 * 一笔业务订单对应一笔支付（第一期 1:1）；主键雪花算法生成。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("trade_payment")
public class Payment extends BaseEntity {

    /** 支付单主键，雪花 ID */
    @TableId(value = "payment_id", type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long paymentId;

    /** 关联订单 ID（对应 trade_order.order_id，唯一）。指向雪花主键，同样要转字符串 */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long orderId;

    /** 支付渠道编码：MOCK / WECHAT / ALIPAY */
    private String channel;

    /** 本次支付金额，单位：元，保留两位小数（一般与订单 amount 一致） */
    private BigDecimal amount;

    /**
     * 支付单状态。
     * 0=已创建，1=待支付，2=成功，3=关闭（见 {@link com.commonpay.trade.enums.PaymentStatus}）
     */
    private Integer status;

    /** 渠道侧交易流水号（微信/支付宝返回；Mock 可自定义） */
    private String channelTradeNo;

    /** 渠道回调通知号；同一 notifyId 只入账一次，用于幂等 */
    private String notifyId;

    /** 服务端首次确认支付成功的时间；未支付时为 null。 */
    private LocalDateTime paidAt;

    /**
     * 支付凭证（二维码/支付链接）过期时间，与传给渠道的 time_expire 是同一个时刻。
     * <p>关单【不能】只看订单的 payDeadline —— 订单过期不等于凭证失效。
     */
    private LocalDateTime expireTime;

    /** 同时用作查单任务的短认领期限；防止坏单永远占满第一批。 */
    private LocalDateTime nextReconcileAt;
    private String lastReconcileError;
}
