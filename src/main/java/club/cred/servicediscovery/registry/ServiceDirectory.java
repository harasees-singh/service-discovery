package club.cred.servicediscovery.registry;

import java.time.Clock;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.recipes.cache.ChildData;
import org.apache.curator.framework.recipes.cache.CuratorCache;
import org.apache.curator.framework.recipes.cache.CuratorCacheListener;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

@Component
public final class ServiceDirectory implements SmartLifecycle {

    private final CuratorFramework client;
    private final RegistryCodec codec;
    private final RegistryProperties properties;
    private final Clock clock;

    private volatile boolean running;
    private volatile CuratorCache cache;

    ServiceDirectory(
            CuratorFramework client,
            RegistryCodec codec,
            RegistryProperties properties,
            Clock clock) {
        this.client = client;
        this.codec = codec;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public synchronized void start() {
        if (running) {
            return;
        }

        try {
            ensureBasePath();
            CountDownLatch initialized = new CountDownLatch(1);
            CuratorCache newCache = CuratorCache.build(client, properties.basePath());
            newCache.listenable().addListener(CuratorCacheListener.builder()
                    .forInitialized(initialized::countDown)
                    .build());
            newCache.start();

            boolean ready = initialized.await(
                    properties.cacheInitializationTimeout().toMillis(),
                    TimeUnit.MILLISECONDS);
            if (!ready) {
                newCache.close();
                throw new IllegalStateException("Timed out while initializing the service directory cache");
            }

            cache = newCache;
            running = true;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while initializing the service directory", exception);
        } catch (Exception exception) {
            throw new IllegalStateException("Could not start the service directory", exception);
        }
    }

    public List<RegisteredServiceInstance> instances(String serviceName) {
        String servicePath = RegistryPaths.servicePath(properties.basePath(), serviceName);
        String instancePrefix = servicePath + "/";
        CuratorCache currentCache = cache;
        if (currentCache == null) {
            return List.of();
        }

        return currentCache.stream()
                .filter(data -> isDirectChild(data, instancePrefix))
                .map(ChildData::getData)
                .map(codec::deserialize)
                .filter(instance -> instance.isLive(clock))
                .sorted((left, right) -> left.instanceId().compareTo(right.instanceId()))
                .toList();
    }

    @Override
    public synchronized void stop() {
        CuratorCache currentCache = cache;
        cache = null;
        running = false;
        if (currentCache != null) {
            currentCache.close();
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    private void ensureBasePath() throws Exception {
        try {
            client.create()
                    .creatingParentContainersIfNeeded()
                    .withMode(CreateMode.PERSISTENT)
                    .forPath(properties.basePath());
        } catch (KeeperException.NodeExistsException ignored) {
            // The base path is shared by all registry replicas.
        }
    }

    private static boolean isDirectChild(ChildData data, String instancePrefix) {
        if (data.getData() == null || !data.getPath().startsWith(instancePrefix)) {
            return false;
        }
        String remainder = data.getPath().substring(instancePrefix.length());
        return !remainder.isEmpty() && !remainder.contains("/");
    }
}
