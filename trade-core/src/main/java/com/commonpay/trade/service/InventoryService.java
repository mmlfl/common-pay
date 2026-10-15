package com.commonpay.trade.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.commonpay.trade.entity.Inventory;

/**
 * 库存表基础能力 + 扣减/归还。
 */
public interface InventoryService extends IService<Inventory> {

    /** 按商品 ID 查询库存；不存在返回 null */
    Inventory getByProductId(Long productId);

    /** 冷路径：锁库存行后，将库存和已包含的回库序号一起预热；不覆盖已有计数器。 */
    boolean warmUpStock(Long productId);

    /** @return 是否扣减成功 */
    boolean decreaseStock(Long productId, int quantity);

    /** @return 是否归还成功 */
    boolean increaseStock(Long productId, int quantity);
}
