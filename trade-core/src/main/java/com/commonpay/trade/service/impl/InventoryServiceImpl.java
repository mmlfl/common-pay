package com.commonpay.trade.service.impl;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.commonpay.trade.entity.Inventory;
import com.commonpay.trade.entity.Outbox;
import com.commonpay.trade.enums.OutboxStatus;
import com.commonpay.trade.mapper.InventoryMapper;
import com.commonpay.trade.mapper.StockReleaseMapper;
import com.commonpay.trade.mq.common.CreateOrderMessage;
import com.commonpay.trade.redis.RedisKeys;
import com.commonpay.trade.redis.RedisUtil;
import com.commonpay.trade.service.InventoryService;
import com.commonpay.trade.service.OutboxService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class InventoryServiceImpl extends ServiceImpl<InventoryMapper, Inventory> implements InventoryService {
    private final RedisUtil redis;
    private final StockReleaseMapper releases;
    private final OutboxService outboxService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean warmUpStock(Long productId) {
        if (redis.hasKey(RedisKeys.stock(productId))) return true;
        // 关单回库也持有这条库存行锁。读取 cnt 和回库序号期间，不允许另一笔回库提交。
        Inventory inventory = baseMapper.lockByProductId(productId);
        if (inventory == null || inventory.getCnt() == null) return false;
        // MySQL cnt 尚未扣减在途 Outbox 预扣；冷启动灌满会超卖
        int seed = Math.max(0, inventory.getCnt() - countInFlightReservation(productId));
        redis.seedStockIfAbsent(productId, seed, releases.latestReleaseId(productId));
        return true;
    }

    /** PENDING/SENT：已预扣 Redis、尚未（或未确认）扣 MySQL。 */
    private int countInFlightReservation(Long productId) {
        List<Outbox> rows = outboxService.lambdaQuery()
                .in(Outbox::getStatus, OutboxStatus.PENDING.getCode(), OutboxStatus.SENT.getCode())
                .list();
        int reserved = 0;
        for (Outbox row : rows) {
            if (row.getPayload() == null || row.getPayload().isBlank()) {
                continue;
            }
            CreateOrderMessage message = JSON.parseObject(row.getPayload(), CreateOrderMessage.class);
            if (message != null && Objects.equals(productId, message.getProductId())
                    && message.getQuantity() != null && message.getQuantity() > 0) {
                reserved += message.getQuantity();
            }
        }
        return reserved;
    }

    @Override
    public Inventory getByProductId(Long productId) {
        return lambdaQuery().eq(Inventory::getProductId, productId).one();
    }

    @Override
    public boolean decreaseStock(Long productId, int quantity) {
        return baseMapper.decreaseStock(productId, quantity) == 1;
    }

    @Override
    public boolean increaseStock(Long productId, int quantity) {
        return baseMapper.increaseStock(productId, quantity) == 1;
    }
}
