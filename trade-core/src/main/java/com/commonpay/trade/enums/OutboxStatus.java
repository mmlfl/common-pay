package com.commonpay.trade.enums;

/**
 * 本地消息表状态，对应 trade_outbox.status。
 * <p>名字描述【消息的结局】，不带业务语义 —— 失败后做什么由 OutboxCompensator 按 topic 分发。
 * <p>FAILED 是唯一的「确定失败」状态，只能由消费者/死信消费者显式写入，
 * 不能由定时任务按时间推断 —— 那样会分不清「消费失败」和「消费还没做完」。
 */
public enum OutboxStatus {

    /** 生产端已落库，还没发出去 */
    PENDING(0, "待发送"),

    /** 已投递到 MQ，等消费者 */
    SENT(1, "已发送"),

    /** 消费者报告处理成功 */
    CONSUMED(2, "已消费"),

    /** 消费者/死信消费者报告处理失败（终态，不会再被消费） */
    FAILED(3, "消费失败"),

    /** 失败后的收尾动作已完成 */
    COMPENSATED(4, "已补偿");

    private final int code;
    private final String desc;

    OutboxStatus(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public int getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }
}
