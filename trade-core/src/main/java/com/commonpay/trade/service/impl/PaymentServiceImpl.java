package com.commonpay.trade.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.commonpay.trade.entity.Payment;
import com.commonpay.trade.mapper.PaymentMapper;
import com.commonpay.trade.service.PaymentService;
import org.springframework.stereotype.Service;

@Service
public class PaymentServiceImpl extends ServiceImpl<PaymentMapper, Payment> implements PaymentService {

    @Override
    public Payment getByOrderId(Long orderId) {
        return lambdaQuery().eq(Payment::getOrderId, orderId).one();
    }

    @Override
    public Payment getForUpdate(Long paymentId) {
        return baseMapper.selectForUpdate(paymentId);
    }

    @Override
    public boolean updateStatus(Long paymentId, int fromStatus, int toStatus) {
        return baseMapper.updateStatus(paymentId, fromStatus, toStatus) == 1;
    }

    @Override
    public boolean markSuccess(Long paymentId, String channelTradeNo, String notifyId) {
        return baseMapper.markSuccess(paymentId, channelTradeNo, notifyId) == 1;
    }
}
