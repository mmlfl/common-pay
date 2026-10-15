package com.commonpay.boot.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * common-pay 接入开关。业务方在 application.yml 用 {@code common-pay.*} 配置。
 */
@ConfigurationProperties(prefix = "common-pay")
public class CommonPayProperties {

    /** 总开关；false 时不装配交易 Bean、不跑迁移 */
    private boolean enabled = true;

    /**
     * 是否自动执行本库 Flyway 脚本（建 trade_* 表）。
     */
    private boolean autoMigrate = true;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isAutoMigrate() {
        return autoMigrate;
    }

    public void setAutoMigrate(boolean autoMigrate) {
        this.autoMigrate = autoMigrate;
    }
}
