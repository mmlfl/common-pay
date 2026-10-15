package com.commonpay.boot.autoconfigure.web;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Web 适配开关。业务方用 {@code common-pay.web.*} 配置。
 */
@ConfigurationProperties(prefix = "common-pay.web")
public class CommonPayWebProperties {

    /**
     * 是否注册统一异常处理（返回引擎 {@code R}，HTTP 200 + 业务码）。
     * 宿主若自带全局 Handler 且不想用本组件的，设为 false。
     */
    private boolean exceptionHandler = true;

    public boolean isExceptionHandler() {
        return exceptionHandler;
    }

    public void setExceptionHandler(boolean exceptionHandler) {
        this.exceptionHandler = exceptionHandler;
    }
}
