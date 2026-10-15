package com.commonpay.trade.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.commonpay.trade.entity.Outbox;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface OutboxMapper extends BaseMapper<Outbox> {
}
