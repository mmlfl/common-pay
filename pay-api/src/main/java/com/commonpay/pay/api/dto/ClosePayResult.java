package com.commonpay.pay.api.dto;

import com.commonpay.pay.api.enums.ClosePayStatus;
import lombok.Data;

/** 关闭渠道支付凭证的结果。 */
@Data
public class ClosePayResult {

    private ClosePayStatus status;

    /** {@link ClosePayStatus#ALREADY_PAID} 时有值 */
    private String channelTradeNo;

    public static ClosePayResult of(ClosePayStatus status) {
        ClosePayResult result = new ClosePayResult();
        result.setStatus(status);
        return result;
    }
}
