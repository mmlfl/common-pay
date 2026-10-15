package com.commonpay.boot.autoconfigure.web;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Web 场景下注册统一异常处理，返回引擎 {@link com.commonpay.trade.common.R}。
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(RestControllerAdvice.class)
@EnableConfigurationProperties(CommonPayWebProperties.class)
@ConditionalOnProperty(prefix = "common-pay.web", name = "exception-handler", havingValue = "true", matchIfMissing = true)
public class CommonPayWebAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(CommonPayExceptionHandler.class)
    public CommonPayExceptionHandler commonPayExceptionHandler() {
        return new CommonPayExceptionHandler();
    }
}
