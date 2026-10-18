package club.cred.servicediscovery.registry;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("service-discovery.registration")
public record ServiceRegistrationProperties(
        String basePath,
        String serviceName,
        String instanceId,
        String address,
        int port) {
}
