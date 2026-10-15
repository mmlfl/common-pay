package com.commonpay.trade.redis;

import com.commonpay.trade.entity.Product;
import com.commonpay.trade.service.ProductService;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RedissonClient;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
public class ProductBloomGate implements CommandLineRunner {
    private static final int PAGE_SIZE = 500;
    private final ProductService productService;
    private final RBloomFilter<String> bloom;
    private volatile boolean ready;

    public ProductBloomGate(RedissonClient client,
                            RedisUtil redisUtil,
                            ProductService productService) {
        this.productService = productService;
        this.bloom = client.getBloomFilter(redisUtil.fullKey(RedisKeys.productBloom()));
    }

    @Override
    public void run(String... args) throws Exception {
        try{
            long expected = Math.max(100_100L,productService.count() * 3L);
            bloom.tryInit(expected,0.01);
            long lastId = 0L;
            while(true){
                //按主键游标分页,只取 ID;
                List<Product> page = productService.lambdaQuery()
                        .select(Product::getProductId)
                        .gt(lastId != 0L, Product::getProductId, lastId)
                        .orderByAsc(Product::getProductId)
                        .last("LIMIT " + PAGE_SIZE)
                        .list();
                if(page.isEmpty()){
                    break;
                }
                for (Product product : page) {
                    bloom.add(product.getProductId().toString());
                }
                lastId  = page.get(page.size() -1).getProductId();
            }
            ready = true; //全量完成之后通知
            log.info("商品布隆过滤器就绪,最后加载的 productId = {}",lastId);
        }catch (Exception e){
            ready = false;
            log.error("商品布隆过滤器加载失败,本实例退回原下单链路",e);
        }

    }

    /** true 才能提前拒绝；false 表示继续走原有校验。 */
    public boolean definitelyAbsent(Long productId) {
        if (!ready || productId == null) {
            return false;
        }
        try {
            return !bloom.contains(productId.toString());
        } catch (Exception e) {
            log.warn("商品布隆过滤器查询失败，放行 productId={}", productId, e);
            return false;
        }
    }

    /** 未来新增商品时，在它变为可售之前调用。 */
    public void add(Long productId) {
        if (productId == null) {
            throw new IllegalArgumentException("productId 不能为空");
        }
        bloom.add(productId.toString());
    }
}
