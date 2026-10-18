package club.cred.servicediscovery.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("service-discovery.zookeeper")
public record ZooKeeperProperties(
        String connectString,
        Duration sessionTimeout,
        Duration connectionTimeout) {
}
