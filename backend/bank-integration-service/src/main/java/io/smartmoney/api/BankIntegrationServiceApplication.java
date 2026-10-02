package io.smartmoney.api;

import io.smartmoney.api.bankintegration.ncba.NcbaProperties;
import io.smartmoney.api.bankintegration.stanbic.StanbicProperties;
import io.smartmoney.api.bankintegration.kcb.KcbProperties;
import io.smartmoney.api.config.PlatformProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableAsync
@EnableScheduling
@EnableConfigurationProperties({
        StanbicProperties.class,
        KcbProperties.class,
        NcbaProperties.class,
        PlatformProperties.class
})
public class BankIntegrationServiceApplication {

    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.findAndRegisterModules();
        return mapper;
    }

    public static void main(String[] args) {
        SpringApplication.run(BankIntegrationServiceApplication.class, args);
    }
}
