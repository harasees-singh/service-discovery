package club.cred.servicediscovery.registry;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record RegisteredServiceInstance(
        String serviceName,
        String instanceId,
        String address,
        int port,
        Map<String, String> metadata,
        UUID leaseId,
        Instant registeredAt,
        Instant leaseExpiresAt) {

    public RegisteredServiceInstance {
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    public boolean isLive(Clock clock) {
        return leaseExpiresAt.isAfter(clock.instant());
    }

    public RegisteredServiceInstance renew(Instant newExpiry) {
        return new RegisteredServiceInstance(
                serviceName,
                instanceId,
                address,
                port,
                metadata,
                leaseId,
                registeredAt,
                newExpiry);
    }
}
