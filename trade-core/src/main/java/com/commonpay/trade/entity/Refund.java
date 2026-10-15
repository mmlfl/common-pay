package com.commonpay.trade.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("trade_refund")
public class Refund extends BaseEntity {
    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long refundId;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long paymentId;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long orderId;
    private String channel;
    private String channelTradeNo;
    private String channelRefundNo;
    private BigDecimal amount;
    private String reason;
    private Integer status;
    private Integer retryCount;
    private LocalDateTime nextRetryAt;
    private String claimToken;
    private LocalDateTime claimUntil;
    private String lastError;
    private LocalDateTime refundedAt;
}
