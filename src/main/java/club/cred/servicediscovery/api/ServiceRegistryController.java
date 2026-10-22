package club.cred.servicediscovery.api;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import club.cred.servicediscovery.registry.RegisteredServiceInstance;
import club.cred.servicediscovery.registry.RegistryProperties;
import club.cred.servicediscovery.registry.RegistryStore;
import club.cred.servicediscovery.registry.ServiceDirectory;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/services")
public final class ServiceRegistryController {

    public static final String LEASE_ID_HEADER = "X-Lease-Id";

    private final RegistryStore registryStore;
    private final ServiceDirectory serviceDirectory;
    private final long leaseDurationSeconds;

    ServiceRegistryController(
            RegistryStore registryStore,
            ServiceDirectory serviceDirectory,
            RegistryProperties properties) {
        this.registryStore = registryStore;
        this.serviceDirectory = serviceDirectory;
        this.leaseDurationSeconds = properties.leaseDuration().toSeconds();
    }

    @PostMapping("/{serviceName}/instances/{instanceId}")
    ResponseEntity<RegistrationLeaseResponse> register(
            @PathVariable String serviceName,
            @PathVariable String instanceId,
            @Valid @RequestBody RegisterInstanceRequest request) {
        RegisteredServiceInstance instance = registryStore.register(
                serviceName,
                instanceId,
                request.address(),
                request.port(),
                request.metadata());

        URI location = URI.create("/api/v1/services/" + serviceName + "/instances/" + instanceId);
        return ResponseEntity.created(location)
                .body(RegistrationLeaseResponse.from(instance, leaseDurationSeconds));
    }

    @PostMapping("/{serviceName}/instances/{instanceId}/heartbeat")
    RegistrationLeaseResponse heartbeat(
            @PathVariable String serviceName,
            @PathVariable String instanceId,
            @RequestHeader(LEASE_ID_HEADER) UUID leaseId) {
        RegisteredServiceInstance instance = registryStore.heartbeat(serviceName, instanceId, leaseId);
        return RegistrationLeaseResponse.from(instance, leaseDurationSeconds);
    }

    @DeleteMapping("/{serviceName}/instances/{instanceId}")
    ResponseEntity<Void> unregister(
            @PathVariable String serviceName,
            @PathVariable String instanceId,
            @RequestHeader(LEASE_ID_HEADER) UUID leaseId) {
        registryStore.unregister(serviceName, instanceId, leaseId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{serviceName}/instances")
    List<ServiceInstanceView> instances(@PathVariable String serviceName) {
        return serviceDirectory.instances(serviceName).stream()
                .map(ServiceInstanceView::from)
                .toList();
    }
}
