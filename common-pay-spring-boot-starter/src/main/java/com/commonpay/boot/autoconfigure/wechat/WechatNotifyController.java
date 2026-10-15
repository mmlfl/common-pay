package com.commonpay.boot.autoconfigure.wechat;

import com.commonpay.pay.api.dto.PayNotifyCommand;
import com.commonpay.trade.exception.BusinessException;
import com.commonpay.trade.service.PayAppService;
import com.commonpay.trade.service.RefundNotifyService;
import com.wechat.pay.java.core.exception.ValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Public WeChat Pay callback. Keep the body unchanged for signature verification. */
@RestController
public class WechatNotifyController {
    private static final Logger log = LoggerFactory.getLogger(WechatNotifyController.class);
    private final PayAppService payAppService;
    private final RefundNotifyService refundNotifyService;

    public WechatNotifyController(PayAppService payAppService, RefundNotifyService refundNotifyService) {
        this.payAppService = payAppService;
        this.refundNotifyService = refundNotifyService;
    }

    @PostMapping("/common-pay/notify/wechat")
    public ResponseEntity<String> notifyPaid(
            @RequestBody String rawBody,
            @RequestHeader("Wechatpay-Serial") String serial,
            @RequestHeader("Wechatpay-Nonce") String nonce,
            @RequestHeader("Wechatpay-Signature") String signature,
            @RequestHeader("Wechatpay-Timestamp") String timestamp) {
        PayNotifyCommand command = new PayNotifyCommand();
        command.setChannel("WECHAT");
        command.setRawBody(rawBody);
        command.setHeaders(Map.of("Wechatpay-Serial", serial, "Wechatpay-Nonce", nonce,
                "Wechatpay-Signature", signature, "Wechatpay-Timestamp", timestamp));
        try {
            payAppService.handlePaySuccess(command);
            return ResponseEntity.ok("{\"code\":\"SUCCESS\",\"message\":\"成功\"}");
        } catch (ValidationException | IllegalArgumentException e) {
            log.warn("微信支付通知校验失败", e);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("{\"code\":\"FAIL\"}");
        } catch (BusinessException e) {
            if (!e.isRetryable()) {
                // 金额/流水/状态等永久拒绝：ACK 避免渠道无限重试，需人工核对
                log.error("微信支付通知永久业务拒绝，已 ACK payment 需人工核对", e);
                return ResponseEntity.ok("{\"code\":\"SUCCESS\",\"message\":\"成功\"}");
            }
            log.error("微信支付通知可重试业务失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("{\"code\":\"FAIL\"}");
        } catch (RuntimeException e) {
            log.error("微信支付通知处理失败，请核对交易状态", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("{\"code\":\"FAIL\"}");
        }
    }

    @PostMapping("/common-pay/notify/wechat/refund")
    public ResponseEntity<String> notifyRefund(@RequestBody String rawBody,
            @RequestHeader("Wechatpay-Serial") String serial, @RequestHeader("Wechatpay-Nonce") String nonce,
            @RequestHeader("Wechatpay-Signature") String signature,
            @RequestHeader("Wechatpay-Timestamp") String timestamp) {
        PayNotifyCommand command = new PayNotifyCommand();
        command.setChannel("WECHAT");
        command.setRawBody(rawBody);
        command.setHeaders(Map.of("Wechatpay-Serial",serial,"Wechatpay-Nonce",nonce,
                "Wechatpay-Signature",signature,"Wechatpay-Timestamp",timestamp));
        try {
            refundNotifyService.handle(command);
            return ResponseEntity.ok("{\"code\":\"SUCCESS\"}");
        } catch (ValidationException | IllegalArgumentException e) {
            log.warn("微信退款通知校验失败",e);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("{\"code\":\"FAIL\"}");
        } catch (RuntimeException e) {
            log.error("微信退款通知处理失败，等待渠道重试或主动查询",e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("{\"code\":\"FAIL\"}");
        }
    }
}
