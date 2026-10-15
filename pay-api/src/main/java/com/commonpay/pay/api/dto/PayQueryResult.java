package com.commonpay.pay.api.dto;

import com.commonpay.pay.api.enums.PayQueryStatus;
import lombok.Data;

import java.math.BigDecimal;

/** 主动查渠道支付结果。 */
@Data
public class PayQueryResult {

    private PayQueryStatus status;

    /** {@link PayQueryStatus#PAID} 时有值 */
    private String channelTradeNo;

    /** PAID 时必须提供订单总金额，交易内核严格校验；缺失不能入账。 */
    private BigDecimal amount;

    public static PayQueryResult of(PayQueryStatus status) {
        PayQueryResult result = new PayQueryResult();
        result.setStatus(status);
        return result;
    }
}
