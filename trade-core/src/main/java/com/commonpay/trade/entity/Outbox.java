package com.commonpay.trade.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.commonpay.trade.enums.OutboxStatus;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * 本地消息表 trade_outbox。一条记录跟完消息的一生：
 * 待发送 → 已发送 → 已消费 / 消费失败 → 已补偿。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("trade_outbox")
@Accessors(chain = true)
public class Outbox extends BaseEntity {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 业务受理号，同时是消息的业务幂等键 */
    private String requestId;

    /** 目标 Topic。补偿时用它找 OutboxCompensator */
    private String topic;

    /** 消息体 JSON。补偿要从这里取值，所以必须存全，不能只存个 ID */
    private String payload;

    /** 见 {@link OutboxStatus} */
    private Integer status;

    /** 发送失败次数（成功那次不计），用于观察重试情况 */
    private Integer retryCount;
}
