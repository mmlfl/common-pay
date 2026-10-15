package com.commonpay.boot.autoconfigure;

import com.commonpay.boot.autoconfigure.alipay.AlipayNotifyController;
import com.commonpay.boot.autoconfigure.wechat.WechatNotifyController;
import com.commonpay.trade.service.PayAppService;
import com.commonpay.trade.service.RefundNotifyService;
import org.flywaydb.core.Flyway;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationInitializer;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import javax.sql.DataSource;

/**
 * 引入 starter 后自动：扫交易/渠道 Bean、Mapper，并按需用独立 Flyway 建表。
 */
@AutoConfiguration(after = DataSourceAutoConfiguration.class)
@EnableConfigurationProperties(CommonPayProperties.class)
@ConditionalOnProperty(prefix = "common-pay", name = "enabled", havingValue = "true", matchIfMissing = true)
@ComponentScan(basePackages = {"com.commonpay.trade", "com.commonpay.pay"})
@MapperScan("com.commonpay.trade.mapper")
@EnableScheduling
public class CommonPayAutoConfiguration {

    @Bean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnProperty(prefix = "common-pay.wechat", name = "enabled", havingValue = "true")
    WechatNotifyController commonPayWechatNotifyController(PayAppService payAppService, RefundNotifyService refunds) {
        return new WechatNotifyController(payAppService,refunds);
    }

    @Bean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnProperty(prefix = "common-pay.alipay", name = "enabled", havingValue = "true")
    AlipayNotifyController commonPayAlipayNotifyController(PayAppService payAppService) {
        return new AlipayNotifyController(payAppService);
    }

    /**
     * 独立 Flyway：脚本在 {@code classpath:db/common-pay/migration}，
     * history 表 {@code common_pay_flyway_history}，不占用业务方自己的 Flyway。
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(Flyway.class)
    @ConditionalOnBean(DataSource.class)
    @ConditionalOnProperty(prefix = "common-pay", name = "auto-migrate", havingValue = "true", matchIfMissing = true)
    static class CommonPayFlywayConfiguration {

        @Bean(name = "commonPayFlyway")
        Flyway commonPayFlyway(DataSource dataSource) {
            return Flyway.configure()
                    .dataSource(dataSource)
                    .locations("classpath:db/common-pay/migration")
                    .table("common_pay_flyway_history")
                    .baselineOnMigrate(false)
                    .load();
        }

        @Bean
        FlywayMigrationInitializer commonPayFlywayInitializer(
                @Qualifier("commonPayFlyway") Flyway commonPayFlyway) {
            return new FlywayMigrationInitializer(commonPayFlyway);
        }
    }
}
