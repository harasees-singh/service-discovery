package club.cred.servicediscovery.registry;

import java.util.List;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.state.ConnectionState;
import org.apache.curator.x.discovery.ServiceCache;
import org.apache.curator.x.discovery.ServiceDiscovery;
import org.apache.curator.x.discovery.ServiceDiscoveryBuilder;
import org.apache.curator.x.discovery.ServiceInstance;
import org.apache.curator.x.discovery.ServiceType;
import org.apache.curator.x.discovery.details.ServiceCacheListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

@Component
@EnableConfigurationProperties(ServiceRegistrationProperties.class)
public final class ServiceRegistry implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(ServiceRegistry.class);

    private final CuratorFramework client;
    private final ServiceRegistrationProperties properties;

    private volatile boolean running;
    private ServiceDiscovery<Void> discovery;
    private ServiceCache<Void> cache;

    public ServiceRegistry(
            CuratorFramework client,
            ServiceRegistrationProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    @Override
    public synchronized void start() {
        if (running) {
            return;
        }

        try {
            ServiceInstance<Void> thisInstance = ServiceInstance.<Void>builder()
                    .name(properties.serviceName())
                    .id(properties.instanceId())
                    .address(properties.address())
                    .port(properties.port())
                    .serviceType(ServiceType.DYNAMIC)
                    .build();

            discovery = ServiceDiscoveryBuilder.builder(Void.class)
                    .client(client)
                    .basePath(properties.basePath())
                    .thisInstance(thisInstance)
                    .build();
            discovery.start();

            cache = discovery.serviceCacheBuilder()
                    .name(properties.serviceName())
                    .build();
            cache.addListener(new RegistryCacheListener());
            cache.start();

            running = true;
            log.info("Registered service instance {} at {}", properties.instanceId(), registrationPath());
        } catch (Exception exception) {
            closeResources();
            throw new IllegalStateException("Could not start the service registry", exception);
        }
    }

    public List<ServiceInstance<Void>> currentInstances() {
        ServiceCache<Void> currentCache = cache;
        return currentCache == null ? List.of() : List.copyOf(currentCache.getInstances());
    }

    @Override
    public synchronized void stop() {
        closeResources();
        running = false;
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    private String registrationPath() {
        return "%s/%s/%s".formatted(
                properties.basePath(),
                properties.serviceName(),
                properties.instanceId());
    }

    private void closeResources() {
        close(cache, "service cache");
        close(discovery, "service discovery");
        cache = null;
        discovery = null;
    }

    private static void close(AutoCloseable resource, String name) {
        if (resource == null) {
            return;
        }
        try {
            resource.close();
        } catch (Exception exception) {
            log.warn("Could not close {}", name, exception);
        }
    }

    private final class RegistryCacheListener implements ServiceCacheListener {

        @Override
        public void cacheChanged() {
            log.info("Known {} instances: {}", properties.serviceName(), currentInstances().size());
        }

        @Override
        public void stateChanged(CuratorFramework ignored, ConnectionState newState) {
            log.info("Registry cache connection state changed to {}", newState);
        }
    }
}
