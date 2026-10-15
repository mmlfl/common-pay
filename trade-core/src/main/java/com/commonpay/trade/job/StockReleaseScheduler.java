package com.commonpay.trade.job;
import com.commonpay.trade.entity.StockRelease;
import com.commonpay.trade.mapper.StockReleaseMapper;
import com.commonpay.trade.redis.RedisUtil;
import com.commonpay.trade.service.InventoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;
@Component
@RequiredArgsConstructor
@Slf4j
public class StockReleaseScheduler {
    private final StockReleaseMapper mapper;
    private final RedisUtil redis;
    private final InventoryService inventory;
    @Scheduled(fixedDelayString = "${trade.stock-release-interval-seconds:15}",timeUnit = TimeUnit.SECONDS)
    public void release() {
        for (StockRelease release:mapper.due(LocalDateTime.now())) {
            LocalDateTime lease=LocalDateTime.now().plusMinutes(2).withNano(0);
            if(mapper.claim(release.getOrderId(),LocalDateTime.now(),lease)!=1) continue;
            try {
                if (!inventory.warmUpStock(release.getProductId())) throw new IllegalStateException("库存记录不存在");
                redis.releaseStockOnce(release.getOrderId(),release.getProductId(),release.getReleaseId(),release.getQuantity());
                mapper.done(release.getOrderId(),lease);
            } catch(Exception e) {
                String message=e.getMessage()==null?e.getClass().getSimpleName():e.getMessage();
                mapper.retry(release.getOrderId(),lease,LocalDateTime.now().plusMinutes(1),
                        message.substring(0,Math.min(message.length(),1000)));
                log.warn("Redis 回库失败，保留回库记录 orderId={}",release.getOrderId(),e);
            }
        }
    }
}
