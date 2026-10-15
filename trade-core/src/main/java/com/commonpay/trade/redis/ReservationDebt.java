package com.commonpay.trade.redis;

import lombok.Data;

/**
 * Redis 预扣义务：预扣成功后登记，受理成功或即时回补后销账；
 * 超时仍在且无 Outbox 时由对账任务幂等归还。
 */
@Data
public class ReservationDebt {
    private String requestId;
    private Long productId;
    private Integer quantity;
    private String attemptToken;
    private Long createdAt;
}
