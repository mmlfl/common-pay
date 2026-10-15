package com.commonpay.pay.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 渠道下单返回：前端用来跳转/调起支付。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreatePayResponse {

    private Long paymentId;
    private String channel;
    /** Mock 为模拟支付地址；微信 Native、支付宝预创建交易均为二维码内容。 */
    private String payPayload;
}
