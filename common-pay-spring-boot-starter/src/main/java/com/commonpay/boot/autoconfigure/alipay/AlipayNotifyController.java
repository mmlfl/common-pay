package com.commonpay.boot.autoconfigure.alipay;

import com.commonpay.pay.api.dto.PayNotifyCommand;
import com.commonpay.trade.exception.BusinessException;
import com.commonpay.trade.service.PayAppService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Alipay asynchronous form callback; only an accepted verified payment returns success. */
@RestController
public class AlipayNotifyController {
    private static final Logger log = LoggerFactory.getLogger(AlipayNotifyController.class);
    private final PayAppService payAppService;

    public AlipayNotifyController(PayAppService payAppService) {
        this.payAppService = payAppService;
    }

    @PostMapping(value = "/common-pay/notify/alipay", produces = MediaType.TEXT_PLAIN_VALUE)
    public String notifyPaid(@RequestParam Map<String, String> fields) {
        PayNotifyCommand command = new PayNotifyCommand();
        command.setChannel("ALIPAY");
        command.setFormFields(fields);
        try {
            payAppService.handlePaySuccess(command);
            return "success";
        } catch (BusinessException e) {
            if (!e.isRetryable()) {
                log.error("支付宝通知永久业务拒绝，已 ACK 需人工核对", e);
                return "success";
            }
            log.error("支付宝通知可重试业务失败", e);
            return "failure";
        } catch (RuntimeException e) {
            log.error("支付宝通知处理失败，请核对交易状态", e);
            return "failure";
        }
    }
}
