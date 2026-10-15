package com.commonpay.trade.entity;
import lombok.Data;
import java.time.LocalDateTime;
@Data
public class StockRelease {
    private Long releaseId;
    private Long orderId;
    private Long productId;
    private Integer quantity;
    private Integer retryCount;
    private LocalDateTime nextRetryAt;
}
