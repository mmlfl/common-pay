package com.commonpay.pay.alipay;

import com.alipay.api.AlipayClient;
import com.alipay.api.DefaultAlipayClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "common-pay.alipay", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(AlipayProperties.class)
public class AlipayConfiguration {

    @Bean
    public AlipayPaymentChannel alipayPaymentChannel(AlipayProperties properties) {
        properties.validate();
        AlipayClient client = new DefaultAlipayClient(properties.getGatewayUrl(), properties.getAppId(),
                properties.getMerchantPrivateKey(), "json", "UTF-8", properties.getAlipayPublicKey(), "RSA2");
        return new AlipayPaymentChannel(properties, client);
    }
}
