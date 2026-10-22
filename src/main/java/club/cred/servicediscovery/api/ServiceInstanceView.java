package club.cred.servicediscovery.api;

import java.time.Instant;
import java.util.Map;

import club.cred.servicediscovery.registry.RegisteredServiceInstance;

public record ServiceInstanceView(
        String serviceName,
        String instanceId,
        String address,
        int port,
        Map<String, String> metadata,
        Instant registeredAt,
        Instant leaseExpiresAt) {

    static ServiceInstanceView from(RegisteredServiceInstance instance) {
        return new ServiceInstanceView(
                instance.serviceName(),
                instance.instanceId(),
                instance.address(),
                instance.port(),
                instance.metadata(),
                instance.registeredAt(),
                instance.leaseExpiresAt());
    }
}
