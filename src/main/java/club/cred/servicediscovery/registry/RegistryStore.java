package club.cred.servicediscovery.registry;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.curator.framework.CuratorFramework;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.data.Stat;
import org.springframework.stereotype.Component;

@Component
public final class RegistryStore {

    private static final int MAX_VERSION_RETRIES = 3;

    private final CuratorFramework client;
    private final RegistryCodec codec;
    private final RegistryProperties properties;
    private final Clock clock;

    RegistryStore(
            CuratorFramework client,
            RegistryCodec codec,
            RegistryProperties properties,
            Clock clock) {
        this.client = client;
        this.codec = codec;
        this.properties = properties;
        this.clock = clock;
    }

    public RegisteredServiceInstance register(
            String serviceName,
            String instanceId,
            String address,
            int port,
            Map<String, String> metadata) {
        validateEndpoint(address, port);
        String path = RegistryPaths.instancePath(properties.basePath(), serviceName, instanceId);
        Instant now = clock.instant();
        RegisteredServiceInstance instance = new RegisteredServiceInstance(
                serviceName,
                instanceId,
                address,
                port,
                metadata,
                UUID.randomUUID(),
                now,
                now.plus(properties.leaseDuration()));

        try {
            client.create()
                    .orSetData()
                    .creatingParentContainersIfNeeded()
                    .withMode(CreateMode.PERSISTENT)
                    .forPath(path, codec.serialize(instance));
            return instance;
        } catch (Exception exception) {
            throw unavailable("Could not register " + serviceName + "/" + instanceId, exception);
        }
    }

    public RegisteredServiceInstance heartbeat(
            String serviceName,
            String instanceId,
            UUID leaseId) {
        String path = RegistryPaths.instancePath(properties.basePath(), serviceName, instanceId);

        for (int attempt = 0; attempt < MAX_VERSION_RETRIES; attempt++) {
            VersionedInstance versioned = read(path, serviceName, instanceId);
            verifyLease(versioned.instance(), leaseId);
            if (!versioned.instance().isLive(clock)) {
                throw new RegistryException(
                        RegistryException.Kind.LEASE_EXPIRED,
                        "The registration lease has expired");
            }

            RegisteredServiceInstance renewed = versioned.instance()
                    .renew(clock.instant().plus(properties.leaseDuration()));
            try {
                client.setData()
                        .withVersion(versioned.version())
                        .forPath(path, codec.serialize(renewed));
                return renewed;
            } catch (KeeperException.BadVersionException ignored) {
                // Another heartbeat won the race. Re-read and retry with its version.
            } catch (KeeperException.NoNodeException exception) {
                throw notFound(serviceName, instanceId);
            } catch (Exception exception) {
                throw unavailable("Could not renew the registration lease", exception);
            }
        }

        throw new RegistryException(
                RegistryException.Kind.CONCURRENT_MODIFICATION,
                "The registration changed too many times; retry the heartbeat");
    }

    public void unregister(String serviceName, String instanceId, UUID leaseId) {
        String path = RegistryPaths.instancePath(properties.basePath(), serviceName, instanceId);
        VersionedInstance versioned = read(path, serviceName, instanceId);
        verifyLease(versioned.instance(), leaseId);

        try {
            client.delete().withVersion(versioned.version()).forPath(path);
        } catch (KeeperException.NoNodeException exception) {
            throw notFound(serviceName, instanceId);
        } catch (KeeperException.BadVersionException exception) {
            throw new RegistryException(
                    RegistryException.Kind.CONCURRENT_MODIFICATION,
                    "The registration changed while it was being removed");
        } catch (Exception exception) {
            throw unavailable("Could not remove the registration", exception);
        }
    }

    public void removeExpired() {
        for (String serviceName : children(properties.basePath())) {
            String servicePath = RegistryPaths.servicePath(properties.basePath(), serviceName);
            for (String instanceId : children(servicePath)) {
                removeIfExpired(serviceName, instanceId);
            }
        }
    }

    private void removeIfExpired(String serviceName, String instanceId) {
        String path = RegistryPaths.instancePath(properties.basePath(), serviceName, instanceId);
        try {
            Stat stat = new Stat();
            RegisteredServiceInstance instance = codec.deserialize(
                    client.getData().storingStatIn(stat).forPath(path));
            if (!instance.isLive(clock)) {
                client.delete().withVersion(stat.getVersion()).forPath(path);
            }
        } catch (KeeperException.NoNodeException | KeeperException.BadVersionException ignored) {
            // Another registry replica or a heartbeat changed the same registration.
        } catch (Exception exception) {
            throw unavailable("Could not remove an expired registration", exception);
        }
    }

    private VersionedInstance read(String path, String serviceName, String instanceId) {
        try {
            Stat stat = new Stat();
            RegisteredServiceInstance instance = codec.deserialize(
                    client.getData().storingStatIn(stat).forPath(path));
            return new VersionedInstance(instance, stat.getVersion());
        } catch (KeeperException.NoNodeException exception) {
            throw notFound(serviceName, instanceId);
        } catch (Exception exception) {
            throw unavailable("Could not read the registration", exception);
        }
    }

    private List<String> children(String path) {
        try {
            return client.getChildren().forPath(path);
        } catch (KeeperException.NoNodeException ignored) {
            return List.of();
        } catch (Exception exception) {
            throw unavailable("Could not list registry entries", exception);
        }
    }

    private static void verifyLease(RegisteredServiceInstance instance, UUID leaseId) {
        if (!instance.leaseId().equals(leaseId)) {
            throw new RegistryException(
                    RegistryException.Kind.LEASE_MISMATCH,
                    "The lease ID does not own this registration");
        }
    }

    private static void validateEndpoint(String address, int port) {
        if (address == null || address.isBlank()) {
            throw new IllegalArgumentException("address must not be blank");
        }
        if (port < 1 || port > 65_535) {
            throw new IllegalArgumentException("port must be between 1 and 65535");
        }
    }

    private static RegistryException notFound(String serviceName, String instanceId) {
        return new RegistryException(
                RegistryException.Kind.NOT_FOUND,
                "No registration exists for " + serviceName + "/" + instanceId);
    }

    private static RegistryException unavailable(String message, Exception cause) {
        return new RegistryException(RegistryException.Kind.UNAVAILABLE, message, cause);
    }

    private record VersionedInstance(RegisteredServiceInstance instance, int version) {
    }
}
