package com.commonpay.trade.service;

import com.commonpay.trade.entity.Outbox;

/**
 * 消息最终失败后的收尾。按 topic 注册；本地消息表本身不关心业务。
 * 失败请抛异常，任务会把状态退回 FAILED 下轮重试。
 */
public interface OutboxCompensator {

    String topic();

    void compensate(Outbox record);
}
