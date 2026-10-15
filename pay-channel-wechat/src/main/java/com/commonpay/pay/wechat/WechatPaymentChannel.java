package com.commonpay.pay.wechat;

import com.commonpay.pay.api.PaymentChannel;
import com.commonpay.pay.api.dto.ClosePayRequest;
import com.commonpay.pay.api.dto.ClosePayResult;
import com.commonpay.pay.api.dto.CreatePayRequest;
import com.commonpay.pay.api.dto.CreatePayResponse;
import com.commonpay.pay.api.dto.PayNotifyCommand;
import com.commonpay.pay.api.dto.PayNotifyResult;
import com.commonpay.pay.api.dto.PayQueryRequest;
import com.commonpay.pay.api.dto.PayQueryResult;
import com.commonpay.pay.api.enums.ClosePayStatus;
import com.commonpay.pay.api.enums.PayQueryStatus;
import com.commonpay.pay.api.dto.RefundRequest;
import com.commonpay.pay.api.dto.RefundResult;
import com.commonpay.pay.api.enums.RefundQueryStatus;
import com.wechat.pay.java.service.refund.RefundService;
import com.wechat.pay.java.service.refund.model.CreateRequest;
import com.wechat.pay.java.service.refund.model.AmountReq;
import com.wechat.pay.java.service.refund.model.QueryByOutRefundNoRequest;
import com.wechat.pay.java.service.refund.model.RefundNotification;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import com.wechat.pay.java.core.exception.ServiceException;
import com.wechat.pay.java.core.notification.NotificationParser;
import com.wechat.pay.java.core.notification.RequestParam;
import com.wechat.pay.java.service.payments.model.Transaction;
import com.wechat.pay.java.service.payments.nativepay.NativePayService;
import com.wechat.pay.java.service.payments.nativepay.model.Amount;
import com.wechat.pay.java.service.payments.nativepay.model.CloseOrderRequest;
import com.wechat.pay.java.service.payments.nativepay.model.PrepayRequest;
import com.wechat.pay.java.service.payments.nativepay.model.PrepayResponse;
import com.wechat.pay.java.service.payments.nativepay.model.QueryOrderByOutTradeNoRequest;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/** WeChat Pay Native QR strategy. */
public class WechatPaymentChannel implements PaymentChannel {

    /** 微信 time_expire 要 RFC3339，如 2018-06-08T10:34:56+08:00 */
    private static final DateTimeFormatter RFC3339 =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");

    private final WechatPayProperties properties;
    private final NativePayService service;
    private final NotificationParser parser;
    private final RefundService refundService;

    public WechatPaymentChannel(WechatPayProperties properties, NativePayService service,
                               NotificationParser parser, RefundService refundService) {
        this.properties = properties;
        this.service = service;
        this.parser = parser;
        this.refundService = refundService;
    }

    @Override
    public String channelCode() { return "WECHAT"; }

    @Override
    public CreatePayResponse createPay(CreatePayRequest command) {
        if (command == null || command.getPaymentId() == null || command.getAmount() == null
                || command.getExpireTime() == null || !command.getExpireTime().isAfter(LocalDateTime.now())) {
            throw new IllegalArgumentException("微信支付参数不完整");
        }
        int cents = command.getAmount().movePointRight(2).intValueExact();
        if (cents <= 0) {
            throw new IllegalArgumentException("微信支付金额必须大于零");
        }
        Amount amount = new Amount();
        amount.setTotal(cents);
        PrepayRequest request = new PrepayRequest();
        request.setAppid(properties.getAppId());
        request.setMchid(properties.getMerchantId());
        request.setAmount(amount);
        request.setDescription(command.getSubject());
        request.setNotifyUrl(properties.getNotifyUrl());
        request.setOutTradeNo(command.getPaymentId().toString());
        // 渠道和本地使用同一截止时间；跨系统仍须先确认渠道关闭，再取消本地。
        if (command.getExpireTime() != null) {
            request.setTimeExpire(command.getExpireTime()
                    .atZone(ZoneId.of("Asia/Shanghai")).format(RFC3339));
        }
        PrepayResponse response = service.prepay(request);
        if (response == null || response.getCodeUrl() == null || response.getCodeUrl().isBlank()) {
            throw new IllegalStateException("微信支付未返回二维码链接");
        }
        return CreatePayResponse.builder().paymentId(command.getPaymentId())
                .channel(channelCode()).payPayload(response.getCodeUrl()).build();
    }

    @Override
    public PayNotifyResult parseNotify(PayNotifyCommand command) {
        if (command == null || command.getRawBody() == null || command.getHeaders() == null) {
            throw new IllegalArgumentException("微信支付回调缺少原始报文或签名头");
        }
        Map<String, String> headers = command.getHeaders();
        RequestParam request = new RequestParam.Builder()
                .serialNumber(headers.get("Wechatpay-Serial"))
                .nonce(headers.get("Wechatpay-Nonce"))
                .signature(headers.get("Wechatpay-Signature"))
                .timestamp(headers.get("Wechatpay-Timestamp"))
                .body(command.getRawBody()).build();
        Transaction transaction = parser.parse(request, Transaction.class);
        if (transaction == null || transaction.getOutTradeNo() == null
                || !properties.getMerchantId().equals(transaction.getMchid())
                || !properties.getAppId().equals(transaction.getAppid())
                || transaction.getAmount() == null || transaction.getAmount().getTotal() == null
                || transaction.getTransactionId() == null) {
            throw new IllegalArgumentException("微信支付通知商户、应用或交易信息不匹配");
        }
        PayNotifyResult result = new PayNotifyResult();
        result.setChannel(channelCode());
        result.setPaymentId(Long.valueOf(transaction.getOutTradeNo()));
        result.setChannelTradeNo(transaction.getTransactionId());
        result.setNotifyId(transaction.getTransactionId());
        result.setAmount(BigDecimal.valueOf(transaction.getAmount().getTotal(), 2));
        result.setSuccess(transaction.getTradeState() == Transaction.TradeStateEnum.SUCCESS);
        return result;
    }

    @Override
    public ClosePayResult closePay(ClosePayRequest command) {
        if (command == null || command.getPaymentId() == null) {
            throw new IllegalArgumentException("微信关单参数不完整");
        }
        CloseOrderRequest request = new CloseOrderRequest();
        request.setMchid(properties.getMerchantId());
        request.setOutTradeNo(command.getPaymentId().toString());
        try {
            service.closeOrder(request);
            return ClosePayResult.of(ClosePayStatus.CLOSED);
        } catch (ServiceException e) {
            String code = e.getErrorCode();
            if ("ORDERPAID".equals(code)) {
                return ClosePayResult.of(ClosePayStatus.ALREADY_PAID);
            }
            if ("ORDERNOTEXIST".equals(code) || "ORDER_NOT_EXIST".equals(code)) {
                return ClosePayResult.of(ClosePayStatus.NOT_FOUND);
            }
            if ("ORDER_CLOSED".equals(code)) return ClosePayResult.of(ClosePayStatus.CLOSED);
            throw new IllegalStateException("微信关单失败: " + code + " " + e.getErrorMessage(), e);
        }
    }

    @Override
    public PayQueryResult queryPay(PayQueryRequest command) {
        if (command == null || command.getPaymentId() == null) {
            throw new IllegalArgumentException("微信查单参数不完整");
        }
        QueryOrderByOutTradeNoRequest request = new QueryOrderByOutTradeNoRequest();
        request.setMchid(properties.getMerchantId());
        request.setOutTradeNo(command.getPaymentId().toString());

        Transaction transaction;
        try {
            transaction = service.queryOrderByOutTradeNo(request);
        } catch (ServiceException e) {
            if ("ORDERNOTEXIST".equals(e.getErrorCode()) || "ORDER_NOT_EXIST".equals(e.getErrorCode())) {
                return PayQueryResult.of(PayQueryStatus.NOT_FOUND);
            }
            throw new IllegalStateException("微信查单失败: " + e.getErrorCode() + " " + e.getErrorMessage(), e);
        }
        if (transaction == null || transaction.getTradeState() == null
                || !properties.getMerchantId().equals(transaction.getMchid())
                || !properties.getAppId().equals(transaction.getAppid())
                || !command.getPaymentId().toString().equals(transaction.getOutTradeNo())) {
            throw new IllegalStateException("微信查单无有效响应或商户/订单不匹配");
        }
        if (transaction.getTradeState() == Transaction.TradeStateEnum.REFUND) {
            // 已退款不能再当 PAID 入账；映射为 CLOSED，由关单/对账走冲突分支而非抛未分类异常
            return PayQueryResult.of(PayQueryStatus.CLOSED);
        }
        if (transaction.getTradeState() != Transaction.TradeStateEnum.SUCCESS) {
            if (transaction.getTradeState() == Transaction.TradeStateEnum.CLOSED
                    || transaction.getTradeState() == Transaction.TradeStateEnum.REVOKED) {
                return PayQueryResult.of(PayQueryStatus.CLOSED);
            }
            return PayQueryResult.of(PayQueryStatus.NOT_PAID);
        }
        if (transaction.getAmount() == null || transaction.getAmount().getTotal() == null) {
            throw new IllegalStateException("微信查单已支付但缺少金额");
        }
        PayQueryResult result = PayQueryResult.of(PayQueryStatus.PAID);
        result.setChannelTradeNo(transaction.getTransactionId());
        result.setAmount(BigDecimal.valueOf(transaction.getAmount().getTotal(), 2));
        return result;
    }

    @Override
    public RefundResult refund(RefundRequest command) {
        CreateRequest request = new CreateRequest();
        request.setOutTradeNo(command.getPaymentId().toString());
        request.setTransactionId(command.getChannelTradeNo());
        request.setOutRefundNo(command.getRefundNo());
        request.setReason(command.getReason());
        if (properties.getRefundNotifyUrl() != null && !properties.getRefundNotifyUrl().isBlank()) {
            request.setNotifyUrl(properties.getRefundNotifyUrl());
        }
        AmountReq amount = new AmountReq();
        amount.setTotal(command.getTotalAmount().movePointRight(2).longValueExact());
        amount.setRefund(command.getRefundAmount().movePointRight(2).longValueExact());
        amount.setCurrency("CNY");
        request.setAmount(amount);
        return refundResult(refundService.create(request));
    }

    @Override
    public RefundResult queryRefund(RefundRequest command) {
        QueryByOutRefundNoRequest request = new QueryByOutRefundNoRequest();
        request.setOutRefundNo(command.getRefundNo());
        try {
            return refundResult(refundService.queryByOutRefundNo(request));
        } catch (ServiceException e) {
            if ("RESOURCE_NOT_EXISTS".equals(e.getErrorCode())) return RefundResult.notFound();
            throw e; // 超时、权限和未知错误都不是“未退款”
        }
    }

    @Override
    public RefundResult parseRefundNotify(PayNotifyCommand command) {
        if (command == null || command.getRawBody() == null || command.getHeaders() == null) {
            throw new IllegalArgumentException("微信退款回调缺少原始报文或签名头");
        }
        Map<String, String> headers = command.getHeaders();
        RequestParam request = new RequestParam.Builder()
                .serialNumber(headers.get("Wechatpay-Serial")).nonce(headers.get("Wechatpay-Nonce"))
                .signature(headers.get("Wechatpay-Signature")).timestamp(headers.get("Wechatpay-Timestamp"))
                .body(command.getRawBody()).build();
        RefundNotification notification = parser.parse(request, RefundNotification.class);
        if (notification == null || notification.getRefundStatus() == null) {
            throw new IllegalArgumentException("微信退款通知缺少状态");
        }
        com.wechat.pay.java.service.refund.model.Refund refund = new com.wechat.pay.java.service.refund.model.Refund();
        refund.setOutRefundNo(notification.getOutRefundNo());
        refund.setOutTradeNo(notification.getOutTradeNo());
        refund.setTransactionId(notification.getTransactionId());
        refund.setRefundId(notification.getRefundId());
        refund.setStatus(notification.getRefundStatus());
        refund.setAmount(notification.getAmount());
        refund.setSuccessTime(notification.getSuccessTime());
        return refundResult(refund);
    }

    private RefundResult refundResult(com.wechat.pay.java.service.refund.model.Refund refund) {
        if (refund == null || refund.getStatus() == null || refund.getAmount() == null
                || refund.getAmount().getRefund() == null || refund.getOutTradeNo() == null
                || !"CNY".equals(refund.getAmount().getCurrency())) {
            throw new IllegalStateException("微信退款响应不完整或币种不匹配");
        }
        RefundResult result = new RefundResult();
        result.setStatus(RefundQueryStatus.valueOf(refund.getStatus().name()));
        result.setRefundNo(refund.getOutRefundNo());
        result.setPaymentId(Long.valueOf(refund.getOutTradeNo()));
        result.setChannelTradeNo(refund.getTransactionId());
        result.setChannelRefundNo(refund.getRefundId());
        result.setAmount(BigDecimal.valueOf(refund.getAmount().getRefund(), 2));
        if (refund.getSuccessTime() != null) {
            result.setRefundedAt(OffsetDateTime.parse(refund.getSuccessTime())
                    .atZoneSameInstant(ZoneId.of("Asia/Shanghai")).toLocalDateTime());
        }
        return result;
    }
}
