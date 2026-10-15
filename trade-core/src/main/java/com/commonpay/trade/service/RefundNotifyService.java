package com.commonpay.trade.service;

import com.commonpay.pay.api.dto.PayNotifyCommand;
import com.commonpay.pay.api.dto.RefundResult;
import com.commonpay.trade.common.ErrorCode;
import com.commonpay.trade.exception.BusinessException;
import com.commonpay.trade.service.impl.RefundStateService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RefundNotifyService {
    private final PaymentChannelFactory channelFactory;
    private final RefundStateService stateService;

    public void handle(PayNotifyCommand command) {
        if (command == null || command.getChannel() == null) throw new BusinessException(ErrorCode.BUSINESS_ERROR, "退款通知缺少渠道");
        RefundResult result = channelFactory.resolve(command.getChannel()).parseRefundNotify(command);
        if (result == null || result.getRefundNo() == null) throw new BusinessException(ErrorCode.BUSINESS_ERROR, "退款通知不完整");
        stateService.apply(Long.valueOf(result.getRefundNo()), command.getChannel(), null, result);
    }
}
