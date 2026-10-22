package club.cred.servicediscovery.api;

import java.time.Instant;
import java.util.UUID;

import club.cred.servicediscovery.registry.RegisteredServiceInstance;

public record RegistrationLeaseResponse(
        ServiceInstanceView instance,
        UUID leaseId,
        Instant leaseExpiresAt,
        long leaseDurationSeconds) {

    static RegistrationLeaseResponse from(
            RegisteredServiceInstance instance,
            long leaseDurationSeconds) {
        return new RegistrationLeaseResponse(
                ServiceInstanceView.from(instance),
                instance.leaseId(),
                instance.leaseExpiresAt(),
                leaseDurationSeconds);
    }
}
