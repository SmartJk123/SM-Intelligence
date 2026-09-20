package io.smartmoney.api;

import io.smartmoney.api.bankintegration.ncba.NcbaProperties;
import io.smartmoney.api.bankintegration.stanbic.StanbicProperties;
import io.smartmoney.api.bankintegration.kcb.KcbProperties;
import io.smartmoney.api.config.PlatformProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
@EnableConfigurationProperties({
        StanbicProperties.class,
        KcbProperties.class,
        NcbaProperties.class,
        PlatformProperties.class
})
public class SmartMoneyApplication {

    public static void main(String[] args) {
        SpringApplication.run(SmartMoneyApplication.class, args);
    }
}
