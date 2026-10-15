package com.commonpay.trade.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.commonpay.trade.entity.Outbox;
import com.commonpay.trade.mapper.OutboxMapper;
import com.commonpay.trade.service.OutboxService;
import org.springframework.stereotype.Service;

@Service
public class OutboxServiceImpl extends ServiceImpl<OutboxMapper, Outbox> implements OutboxService {
}
