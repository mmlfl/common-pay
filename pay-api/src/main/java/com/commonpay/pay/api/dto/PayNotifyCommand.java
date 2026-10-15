package com.commonpay.pay.api.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 渠道回调原始入参（由 Controller 组装后交给渠道解析）。
 */
@Data
public class PayNotifyCommand {

    private String channel;
    private Long paymentId;
    /** 渠道通知号，用于幂等 */
    private String notifyId;
    private String channelTradeNo;
    /** 渠道实际收款金额，交易内核须与支付单金额核对。 */
    private BigDecimal amount;
    /** 是否支付成功（Mock 直接传；真渠道从报文解析） */
    private boolean success;
    /** 原始报文，真渠道验签用 */
    private String rawBody;
    /** Original HTTP headers, needed for WeChat signature verification. */
    private Map<String, String> headers;
    /** Original form fields, needed for Alipay signature verification. */
    private Map<String, String> formFields;
}
