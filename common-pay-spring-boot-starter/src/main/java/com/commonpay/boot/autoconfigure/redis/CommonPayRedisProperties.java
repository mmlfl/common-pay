package com.commonpay.boot.autoconfigure.redis;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Redis 配置。Redis 为强制依赖，宿主必须配置 {@code spring.data.redis.*}。
 */
@ConfigurationProperties(prefix = "common-pay.redis")
public class CommonPayRedisProperties {

    /** 全局 key 前缀，如 {@code ticket:}；空则不加前缀。 */
    private String keyPrefix = "";

    public String getKeyPrefix() {
        return keyPrefix;
    }

    public void setKeyPrefix(String keyPrefix) {
        this.keyPrefix = keyPrefix;
    }
}
