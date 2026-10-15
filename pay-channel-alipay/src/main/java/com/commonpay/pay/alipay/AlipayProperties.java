package com.commonpay.pay.alipay;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "common-pay.alipay")
public class AlipayProperties {
    private boolean enabled;
    private String appId;
    private String merchantPrivateKey;
    private String alipayPublicKey;
    private String sellerId;
    private String notifyUrl;
    private String gatewayUrl = "https://openapi.alipay.com/gateway.do";

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getAppId() { return appId; }
    public void setAppId(String appId) { this.appId = appId; }
    public String getMerchantPrivateKey() { return merchantPrivateKey; }
    public void setMerchantPrivateKey(String merchantPrivateKey) { this.merchantPrivateKey = merchantPrivateKey; }
    public String getAlipayPublicKey() { return alipayPublicKey; }
    public void setAlipayPublicKey(String alipayPublicKey) { this.alipayPublicKey = alipayPublicKey; }
    public String getSellerId() { return sellerId; }
    public void setSellerId(String sellerId) { this.sellerId = sellerId; }
    public String getNotifyUrl() { return notifyUrl; }
    public void setNotifyUrl(String notifyUrl) { this.notifyUrl = notifyUrl; }
    public String getGatewayUrl() { return gatewayUrl; }
    public void setGatewayUrl(String gatewayUrl) { this.gatewayUrl = gatewayUrl; }

    void validate() {
        required("app-id", appId);
        required("merchant-private-key", merchantPrivateKey);
        required("alipay-public-key", alipayPublicKey);
        required("seller-id", sellerId);
        required("notify-url", notifyUrl);
        required("gateway-url", gatewayUrl);
    }

    private void required(String name, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("缺少支付宝配置 common-pay.alipay." + name);
        }
    }
}
