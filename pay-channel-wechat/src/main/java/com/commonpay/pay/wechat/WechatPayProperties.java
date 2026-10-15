package com.commonpay.pay.wechat;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "common-pay.wechat")
public class WechatPayProperties {
    private boolean enabled;
    private String appId;
    private String merchantId;
    private String merchantSerialNumber;
    private String merchantPrivateKeyPath;
    private String wechatPayPublicKeyPath;
    private String wechatPayPublicKeyId;
    private String apiV3Key;
    private String notifyUrl;
    private String refundNotifyUrl;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getAppId() { return appId; }
    public void setAppId(String appId) { this.appId = appId; }
    public String getMerchantId() { return merchantId; }
    public void setMerchantId(String merchantId) { this.merchantId = merchantId; }
    public String getMerchantSerialNumber() { return merchantSerialNumber; }
    public void setMerchantSerialNumber(String merchantSerialNumber) { this.merchantSerialNumber = merchantSerialNumber; }
    public String getMerchantPrivateKeyPath() { return merchantPrivateKeyPath; }
    public void setMerchantPrivateKeyPath(String merchantPrivateKeyPath) { this.merchantPrivateKeyPath = merchantPrivateKeyPath; }
    public String getWechatPayPublicKeyPath() { return wechatPayPublicKeyPath; }
    public void setWechatPayPublicKeyPath(String wechatPayPublicKeyPath) { this.wechatPayPublicKeyPath = wechatPayPublicKeyPath; }
    public String getWechatPayPublicKeyId() { return wechatPayPublicKeyId; }
    public void setWechatPayPublicKeyId(String wechatPayPublicKeyId) { this.wechatPayPublicKeyId = wechatPayPublicKeyId; }
    public String getApiV3Key() { return apiV3Key; }
    public void setApiV3Key(String apiV3Key) { this.apiV3Key = apiV3Key; }
    public String getNotifyUrl() { return notifyUrl; }
    public void setNotifyUrl(String notifyUrl) { this.notifyUrl = notifyUrl; }
    public String getRefundNotifyUrl() { return refundNotifyUrl; }
    public void setRefundNotifyUrl(String refundNotifyUrl) { this.refundNotifyUrl = refundNotifyUrl; }

    void validate() {
        required("app-id", appId);
        required("merchant-id", merchantId);
        required("merchant-serial-number", merchantSerialNumber);
        required("merchant-private-key-path", merchantPrivateKeyPath);
        required("wechat-pay-public-key-path", wechatPayPublicKeyPath);
        required("wechat-pay-public-key-id", wechatPayPublicKeyId);
        required("api-v3-key", apiV3Key);
        required("notify-url", notifyUrl);
    }

    private void required(String name, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("缺少微信支付配置 common-pay.wechat." + name);
        }
    }
}
