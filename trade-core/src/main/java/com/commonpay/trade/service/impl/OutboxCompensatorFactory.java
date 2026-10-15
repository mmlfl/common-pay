package com.commonpay.trade.service.impl;

import com.commonpay.trade.common.ErrorCode;
import com.commonpay.trade.exception.BaseException;
import com.commonpay.trade.service.OutboxCompensator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class OutboxCompensatorFactory {

    private final List<OutboxCompensator> compensators;

    public OutboxCompensator get(String topic) {
        return compensators.stream()
                .filter(each -> each.topic().equals(topic))
                .findFirst()
                .orElseThrow(() -> new BaseException(ErrorCode.SYSTEM_ERROR, "没有该 topic 的补偿处理器: " + topic));
    }
}
