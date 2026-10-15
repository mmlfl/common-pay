package com.commonpay.pay.api.dto;

import lombok.Data;
import java.math.BigDecimal;

/** 同一次退款重试必须保持 refundNo、交易和金额不变。 */
@Data
public class RefundRequest {
    private String refundNo;
    private Long paymentId;
    private String channelTradeNo;
    private BigDecimal totalAmount;
    private BigDecimal refundAmount;
    private String reason;
}
