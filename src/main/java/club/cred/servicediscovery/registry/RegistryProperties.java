package club.cred.servicediscovery.registry;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("service-discovery.registry")
public record RegistryProperties(
        String basePath,
        Duration leaseDuration,
        Duration reapInterval,
        Duration cacheInitializationTimeout) {
}
