package com.commonpay.pay.api.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 向渠道发起支付的入参。
 */
@Data
public class CreatePayRequest {

    private Long paymentId;
    private Long orderId;
    private BigDecimal amount;
    private String subject;
    /** 业务侧支付成功后渠道回调地址（Mock 可忽略） */
    private String notifyUrl;

    /**
     * 凭证过期时间，必须传给渠道（微信 time_expire / 支付宝 timeout_express）。
     * <p>本地和渠道指向同一个时刻，关渠道失败时渠道自己也会同时失效，窗口才不会放大。
     */
    private LocalDateTime expireTime;
}
