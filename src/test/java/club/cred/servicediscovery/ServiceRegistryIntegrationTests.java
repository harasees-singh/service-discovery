package club.cred.servicediscovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import club.cred.servicediscovery.api.RegisterInstanceRequest;
import club.cred.servicediscovery.api.RegistrationLeaseResponse;
import club.cred.servicediscovery.api.ServiceInstanceView;
import club.cred.servicediscovery.api.ServiceRegistryController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.web.client.RestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ServiceRegistryIntegrationTests {

    @Container
    static final GenericContainer<?> ZOOKEEPER = new GenericContainer<>(
            DockerImageName.parse("zookeeper:3.9.6"))
            .withExposedPorts(2181)
            .waitingFor(Wait.forListeningPort());

    @DynamicPropertySource
    static void registryProperties(DynamicPropertyRegistry registry) {
        registry.add(
                "service-discovery.zookeeper.connect-string",
                () -> ZOOKEEPER.getHost() + ":" + ZOOKEEPER.getMappedPort(2181));
        registry.add("service-discovery.registry.base-path", () -> "/integration-test/services");
        registry.add("service-discovery.registry.lease-duration", () -> "2s");
        registry.add("service-discovery.registry.reap-interval", () -> "200ms");
    }

    @LocalServerPort
    int port;

    private RestClient client;

    @BeforeEach
    void createClient() {
        client = RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .build();
    }

    @Test
    void registrationCanBeDiscoveredRenewedAndRemoved() {
        String serviceName = uniqueServiceName();
        String instanceId = "payments-1";

        RegistrationLeaseResponse registered = register(serviceName, instanceId);
        assertThat(registered.leaseId()).isNotNull();
        assertThat(registered.instance().address()).isEqualTo("10.0.0.21");

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                assertThat(instances(serviceName))
                        .extracting(ServiceInstanceView::instanceId)
                        .containsExactly(instanceId));

        RegistrationLeaseResponse renewed = heartbeat(serviceName, instanceId, registered.leaseId());
        assertThat(renewed.leaseId()).isEqualTo(registered.leaseId());
        assertThat(renewed.leaseExpiresAt()).isAfter(registered.leaseExpiresAt());

        unregister(serviceName, instanceId, registered.leaseId());
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                assertThat(instances(serviceName)).isEmpty());
    }

    @Test
    void expiredRegistrationDisappearsWithoutAnExplicitDelete() {
        String serviceName = uniqueServiceName();
        register(serviceName, "catalog-1");

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                assertThat(instances(serviceName)).hasSize(1));
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                assertThat(instances(serviceName)).isEmpty());
    }

    @Test
    void replacingARegistrationFencesOutItsPreviousLease() {
        String serviceName = uniqueServiceName();
        String instanceId = "orders-1";
        RegistrationLeaseResponse first = register(serviceName, instanceId);
        RegistrationLeaseResponse replacement = register(serviceName, instanceId);

        assertThat(replacement.leaseId()).isNotEqualTo(first.leaseId());

        HttpStatus heartbeatStatus = client.post()
                .uri(instancePath(serviceName, instanceId) + "/heartbeat")
                .header(ServiceRegistryController.LEASE_ID_HEADER, first.leaseId().toString())
                .exchange((request, response) -> HttpStatus.valueOf(response.getStatusCode().value()));

        assertThat(heartbeatStatus).isEqualTo(HttpStatus.CONFLICT);
    }

    private RegistrationLeaseResponse register(String serviceName, String instanceId) {
        RegisterInstanceRequest request = new RegisterInstanceRequest(
                "10.0.0.21",
                8080,
                Map.of("zone", "ap-south-1a"));
        ResponseEntity<RegistrationLeaseResponse> response = client.post()
                .uri(instancePath(serviceName, instanceId))
                .body(request)
                .retrieve()
                .toEntity(RegistrationLeaseResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return Objects.requireNonNull(response.getBody());
    }

    private RegistrationLeaseResponse heartbeat(String serviceName, String instanceId, UUID leaseId) {
        return Objects.requireNonNull(client.post()
                .uri(instancePath(serviceName, instanceId) + "/heartbeat")
                .header(ServiceRegistryController.LEASE_ID_HEADER, leaseId.toString())
                .retrieve()
                .body(RegistrationLeaseResponse.class));
    }

    private void unregister(String serviceName, String instanceId, UUID leaseId) {
        ResponseEntity<Void> response = client.delete()
                .uri(instancePath(serviceName, instanceId))
                .header(ServiceRegistryController.LEASE_ID_HEADER, leaseId.toString())
                .retrieve()
                .toBodilessEntity();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    private java.util.List<ServiceInstanceView> instances(String serviceName) {
        ServiceInstanceView[] body = client.get()
                .uri("/api/v1/services/{serviceName}/instances", serviceName)
                .retrieve()
                .body(ServiceInstanceView[].class);
        return Arrays.asList(Objects.requireNonNull(body));
    }

    private static String instancePath(String serviceName, String instanceId) {
        return "/api/v1/services/" + serviceName + "/instances/" + instanceId;
    }

    private static String uniqueServiceName() {
        return "service-" + UUID.randomUUID();
    }
}
