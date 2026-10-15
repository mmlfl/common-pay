package com.commonpay.pay.api.dto;

import com.commonpay.pay.api.enums.RefundQueryStatus;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class RefundResult {
    private RefundQueryStatus status;
    private String refundNo;
    private Long paymentId;
    private String channelTradeNo;
    private String channelRefundNo;
    private BigDecimal amount;
    private LocalDateTime refundedAt;

    public static RefundResult notFound() {
        RefundResult result = new RefundResult();
        result.setStatus(RefundQueryStatus.NOT_FOUND);
        return result;
    }
}
