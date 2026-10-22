package club.cred.servicediscovery.registry;

import java.time.Clock;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@EnableConfigurationProperties(RegistryProperties.class)
class RegistryConfiguration {

    @Bean
    Clock registryClock() {
        return Clock.systemUTC();
    }
}
