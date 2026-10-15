package com.commonpay.pay.alipay;

import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.domain.AlipayTradeCloseModel;
import com.alipay.api.domain.AlipayTradePrecreateModel;
import com.alipay.api.domain.AlipayTradeQueryModel;
import com.alipay.api.internal.util.AlipaySignature;
import com.alipay.api.request.AlipayTradeCloseRequest;
import com.alipay.api.request.AlipayTradePrecreateRequest;
import com.alipay.api.request.AlipayTradeQueryRequest;
import com.alipay.api.response.AlipayTradeCloseResponse;
import com.alipay.api.response.AlipayTradePrecreateResponse;
import com.alipay.api.response.AlipayTradeQueryResponse;
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
import com.alipay.api.domain.AlipayTradeRefundModel;
import com.alipay.api.domain.AlipayTradeFastpayRefundQueryModel;
import com.alipay.api.request.AlipayTradeRefundRequest;
import com.alipay.api.request.AlipayTradeFastpayRefundQueryRequest;
import com.alipay.api.response.AlipayTradeRefundResponse;
import com.alipay.api.response.AlipayTradeFastpayRefundQueryResponse;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/** Alipay trade.precreate QR strategy, using RSA2 public-key signing. */
public class AlipayPaymentChannel implements PaymentChannel {
    private final AlipayProperties properties;
    private final AlipayClient client;

    public AlipayPaymentChannel(AlipayProperties properties, AlipayClient client) {
        this.properties = properties;
        this.client = client;
    }

    @Override
    public String channelCode() { return "ALIPAY"; }

    @Override
    public CreatePayResponse createPay(CreatePayRequest command) {
        if (command == null || command.getPaymentId() == null || command.getAmount() == null
                || command.getAmount().signum() <= 0 || command.getExpireTime() == null) {
            throw new IllegalArgumentException("支付宝下单参数不完整");
        }
        AlipayTradePrecreateModel model = new AlipayTradePrecreateModel();
        model.setOutTradeNo(command.getPaymentId().toString());
        model.setTotalAmount(command.getAmount().setScale(2).toPlainString());
        model.setSubject(command.getSubject());
        // 绝对截止时间：重试预创建不能重新给二维码增加一段相对有效期。
        if (command.getExpireTime() != null) {
            if (!command.getExpireTime().isAfter(LocalDateTime.now())) {
                throw new IllegalArgumentException("支付凭证已过期");
            }
            model.setTimeExpire(command.getExpireTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        }
        AlipayTradePrecreateRequest request = new AlipayTradePrecreateRequest();
        request.setBizModel(model);
        request.setNotifyUrl(properties.getNotifyUrl());
        try {
            AlipayTradePrecreateResponse response = client.execute(request);
            if (response == null || !response.isSuccess() || response.getQrCode() == null
                    || response.getQrCode().isBlank()) {
                throw new IllegalStateException("支付宝预下单失败: "
                        + (response == null ? "无响应" : response.getSubMsg()));
            }
            return CreatePayResponse.builder().paymentId(command.getPaymentId())
                    .channel(channelCode()).payPayload(response.getQrCode()).build();
        } catch (AlipayApiException e) {
            throw new IllegalStateException("支付宝预下单请求失败", e);
        }
    }

    @Override
    public PayNotifyResult parseNotify(PayNotifyCommand command) {
        Map<String, String> fields = command == null ? null : command.getFormFields();
        if (fields == null || fields.isEmpty()) {
            throw new IllegalArgumentException("支付宝回调缺少表单字段");
        }
        try {
            if (!AlipaySignature.rsaCheckV1(fields, properties.getAlipayPublicKey(), "UTF-8", "RSA2")) {
                throw new IllegalArgumentException("支付宝回调验签失败");
            }
        } catch (AlipayApiException e) {
            throw new IllegalArgumentException("支付宝回调验签失败", e);
        }
        if (!properties.getAppId().equals(fields.get("app_id"))
                || !properties.getSellerId().equals(fields.get("seller_id"))) {
            throw new IllegalArgumentException("支付宝回调应用或收款方不匹配");
        }
        String tradeStatus = fields.get("trade_status");
        if (!"TRADE_SUCCESS".equals(tradeStatus) && !"TRADE_FINISHED".equals(tradeStatus)) {
            throw new IllegalArgumentException("支付宝交易尚未支付成功");
        }
        PayNotifyResult result = new PayNotifyResult();
        result.setChannel(channelCode());
        result.setPaymentId(Long.valueOf(required(fields, "out_trade_no")));
        result.setNotifyId(required(fields, "notify_id"));
        result.setChannelTradeNo(required(fields, "trade_no"));
        result.setAmount(new BigDecimal(required(fields, "total_amount")));
        result.setSuccess(true);
        return result;
    }

    @Override
    public ClosePayResult closePay(ClosePayRequest command) {
        if (command == null || command.getPaymentId() == null) {
            throw new IllegalArgumentException("支付宝关单参数不完整");
        }
        AlipayTradeCloseModel model = new AlipayTradeCloseModel();
        model.setOutTradeNo(command.getPaymentId().toString());
        AlipayTradeCloseRequest request = new AlipayTradeCloseRequest();
        request.setBizModel(model);
        try {
            AlipayTradeCloseResponse response = client.execute(request);
            if (response != null && response.isSuccess()) {
                return ClosePayResult.of(ClosePayStatus.CLOSED);
            }
            String subCode = response == null ? "" : String.valueOf(response.getSubCode());
            if ("ACQ.TRADE_NOT_EXIST".equals(subCode) || "TRADE_NOT_EXIST".equals(subCode)) {
                return ClosePayResult.of(ClosePayStatus.NOT_FOUND);
            }
            if ("ACQ.TRADE_HAS_SUCCESS".equals(subCode)) {
                return ClosePayResult.of(ClosePayStatus.ALREADY_PAID);
            }
            if ("ACQ.TRADE_STATUS_ERROR".equals(subCode)) {
                // 状态错误不一定是已支付；反查到明确 CLOSED/已支付才作决定。
                AlipayTradeQueryModel query = new AlipayTradeQueryModel();
                query.setOutTradeNo(command.getPaymentId().toString());
                AlipayTradeQueryRequest queryRequest = new AlipayTradeQueryRequest();
                queryRequest.setBizModel(query);
                AlipayTradeQueryResponse queried = client.execute(queryRequest);
                if (queried != null && queried.isSuccess()
                        && command.getPaymentId().toString().equals(queried.getOutTradeNo())) {
                    if ("TRADE_CLOSED".equals(queried.getTradeStatus())) {
                        return ClosePayResult.of(ClosePayStatus.CLOSED);
                    }
                    if ("TRADE_SUCCESS".equals(queried.getTradeStatus())
                            || "TRADE_FINISHED".equals(queried.getTradeStatus())) {
                        return ClosePayResult.of(ClosePayStatus.ALREADY_PAID);
                    }
                }
            }
            throw new IllegalStateException("支付宝关单失败: " + subCode + " "
                    + (response == null ? "无响应" : response.getSubMsg()));
        } catch (AlipayApiException e) {
            throw new IllegalStateException("支付宝关单请求失败", e);
        }
    }

    @Override
    public PayQueryResult queryPay(PayQueryRequest command) {
        if (command == null || command.getPaymentId() == null) {
            throw new IllegalArgumentException("支付宝查单参数不完整");
        }
        AlipayTradeQueryModel model = new AlipayTradeQueryModel();
        model.setOutTradeNo(command.getPaymentId().toString());
        AlipayTradeQueryRequest request = new AlipayTradeQueryRequest();
        request.setBizModel(model);
        try {
            AlipayTradeQueryResponse response = client.execute(request);
            if (response != null && response.isSuccess()) {
                String tradeStatus = response.getTradeStatus();
                if (tradeStatus == null || tradeStatus.isBlank()
                        || !command.getPaymentId().toString().equals(response.getOutTradeNo())) {
                    throw new IllegalStateException("支付宝查单缺少状态或订单不匹配");
                }
                if (!"TRADE_SUCCESS".equals(tradeStatus) && !"TRADE_FINISHED".equals(tradeStatus)) {
                    if (!"WAIT_BUYER_PAY".equals(tradeStatus) && !"TRADE_CLOSED".equals(tradeStatus)) {
                        throw new IllegalStateException("支付宝查单返回未知状态: " + tradeStatus);
                    }
                    return PayQueryResult.of("TRADE_CLOSED".equals(tradeStatus)
                            ? PayQueryStatus.CLOSED : PayQueryStatus.NOT_PAID);
                }
                if (response.getTotalAmount() == null || response.getTotalAmount().isBlank()) {
                    throw new IllegalStateException("支付宝查单已支付但缺少金额");
                }
                PayQueryResult result = PayQueryResult.of(PayQueryStatus.PAID);
                result.setChannelTradeNo(response.getTradeNo());
                result.setAmount(new BigDecimal(response.getTotalAmount()));
                return result;
            }
            String subCode = response == null ? "" : String.valueOf(response.getSubCode());
            if ("ACQ.TRADE_NOT_EXIST".equals(subCode) || "TRADE_NOT_EXIST".equals(subCode)) {
                return PayQueryResult.of(PayQueryStatus.NOT_FOUND);
            }
            throw new IllegalStateException("支付宝查单失败: " + subCode + " "
                    + (response == null ? "无响应" : response.getSubMsg()));
        } catch (AlipayApiException e) {
            throw new IllegalStateException("支付宝查单请求失败", e);
        }
    }

    @Override
    public RefundResult refund(RefundRequest command) {
        AlipayTradeRefundModel model = new AlipayTradeRefundModel();
        model.setOutTradeNo(command.getPaymentId().toString());
        model.setTradeNo(command.getChannelTradeNo());
        model.setOutRequestNo(command.getRefundNo());
        model.setRefundAmount(command.getRefundAmount().setScale(2).toPlainString());
        model.setRefundReason(command.getReason());
        AlipayTradeRefundRequest request = new AlipayTradeRefundRequest();
        request.setBizModel(model);
        try {
            AlipayTradeRefundResponse response = client.execute(request);
            if (response == null || !response.isSuccess()) {
                throw new IllegalStateException("支付宝退款未确认: "
                        + (response == null ? "无响应" : response.getSubCode() + " " + response.getSubMsg()));
            }
            if (!"Y".equals(response.getFundChange())) {
                // 幂等重放未发生新资金变动，反查同一 out_request_no 确认之前的结果。
                RefundResult result = queryRefund(command);
                if (result.getStatus() == RefundQueryStatus.NOT_FOUND) {
                    throw new IllegalStateException("支付宝未确认这次退款，请查原退款号");
                }
                return result;
            }
            RefundResult result = refundResult(command.getRefundNo(), response.getOutTradeNo(),
                    response.getTradeNo(), response.getRefundFee(), RefundQueryStatus.SUCCESS);
            if (response.getGmtRefundPay() != null) result.setRefundedAt(response.getGmtRefundPay()
                    .toInstant().atZone(ZoneId.of("Asia/Shanghai")).toLocalDateTime());
            return result;
        } catch (AlipayApiException e) {
            throw new IllegalStateException("支付宝退款调用结果未知", e);
        }
    }

    @Override
    public RefundResult queryRefund(RefundRequest command) {
        AlipayTradeFastpayRefundQueryModel model = new AlipayTradeFastpayRefundQueryModel();
        model.setOutTradeNo(command.getPaymentId().toString());
        model.setTradeNo(command.getChannelTradeNo());
        model.setOutRequestNo(command.getRefundNo());
        AlipayTradeFastpayRefundQueryRequest request = new AlipayTradeFastpayRefundQueryRequest();
        request.setBizModel(model);
        try {
            AlipayTradeFastpayRefundQueryResponse response = client.execute(request);
            if (response == null) throw new IllegalStateException("支付宝退款查询无响应");
            if (!response.isSuccess()) {
                String code = response.getSubCode();
                if ("TRADE_NOT_EXIST".equals(code) || "ACQ.TRADE_NOT_EXIST".equals(code)) {
                    return RefundResult.notFound();
                }
                throw new IllegalStateException("支付宝退款查询失败: " + code + " " + response.getSubMsg());
            }
            // 10000 只代表查询成功。没有退款数据不能标记已退款。
            if (response.getRefundAmount() == null || response.getRefundAmount().isBlank()) {
                return RefundResult.notFound();
            }
            String state = response.getRefundStatus();
            RefundQueryStatus status;
            if (state == null || state.isBlank() || "REFUND_SUCCESS".equals(state)) {
                status = RefundQueryStatus.SUCCESS; // 旧接口通过完整的退款数据表示成功
            } else {
                throw new IllegalStateException("支付宝退款查询返回未知状态: " + state);
            }
            RefundResult result = refundResult(response.getOutRequestNo(), response.getOutTradeNo(),
                    response.getTradeNo(), response.getRefundAmount(), status);
            if (response.getGmtRefundPay() != null) result.setRefundedAt(response.getGmtRefundPay()
                    .toInstant().atZone(ZoneId.of("Asia/Shanghai")).toLocalDateTime());
            return result;
        } catch (AlipayApiException e) {
            throw new IllegalStateException("支付宝退款查询调用失败", e);
        }
    }

    private RefundResult refundResult(String refundNo, String paymentId, String tradeNo,
                                      String amount, RefundQueryStatus status) {
        if (refundNo == null || paymentId == null || tradeNo == null || amount == null) {
            throw new IllegalStateException("支付宝退款结果缺少退款号、支付单号、交易或金额");
        }
        RefundResult result = new RefundResult();
        result.setStatus(status);
        result.setRefundNo(refundNo);
        result.setPaymentId(Long.valueOf(paymentId));
        result.setChannelTradeNo(tradeNo);
        // 支付宝此接口没有独立的退款流水，使用其返回/确认的 out_request_no。
        result.setChannelRefundNo(refundNo);
        result.setAmount(new BigDecimal(amount));
        return result;
    }

    private String required(Map<String, String> fields, String name) {
        String value = fields.get(name);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("支付宝回调缺少 " + name);
        }
        return value;
    }
}
