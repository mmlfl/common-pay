package com.commonpay.pay.wechat;

import com.wechat.pay.java.core.RSAPublicKeyConfig;
import com.wechat.pay.java.core.notification.NotificationParser;
import com.wechat.pay.java.service.payments.nativepay.NativePayService;
import com.wechat.pay.java.service.refund.RefundService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "common-pay.wechat", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(WechatPayProperties.class)
public class WechatPayConfiguration {

    @Bean
    public WechatPaymentChannel wechatPaymentChannel(WechatPayProperties properties) {
        properties.validate();
        RSAPublicKeyConfig config = new RSAPublicKeyConfig.Builder()
                .merchantId(properties.getMerchantId())
                .privateKeyFromPath(properties.getMerchantPrivateKeyPath())
                .publicKeyFromPath(properties.getWechatPayPublicKeyPath())
                .publicKeyId(properties.getWechatPayPublicKeyId())
                .merchantSerialNumber(properties.getMerchantSerialNumber())
                .apiV3Key(properties.getApiV3Key())
                .build();
        return new WechatPaymentChannel(properties,
                new NativePayService.Builder().config(config).build(), new NotificationParser(config),
                new RefundService.Builder().config(config).build());
    }
}
