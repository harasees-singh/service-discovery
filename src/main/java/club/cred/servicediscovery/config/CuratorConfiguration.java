package club.cred.servicediscovery.config;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.framework.state.ConnectionState;
import org.apache.curator.ensemble.fixed.FixedEnsembleProvider;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ZooKeeperProperties.class)
public class CuratorConfiguration {

    private static final Logger log = LoggerFactory.getLogger(CuratorConfiguration.class);

    @Bean(destroyMethod = "close")
    CuratorFramework curatorFramework(ZooKeeperProperties properties) {
        CuratorFramework client = CuratorFrameworkFactory.builder()
                .ensembleProvider(new FixedEnsembleProvider(properties.connectString(), false))
                .ensembleTracker(false)
                .sessionTimeoutMs(Math.toIntExact(properties.sessionTimeout().toMillis()))
                .connectionTimeoutMs(Math.toIntExact(properties.connectionTimeout().toMillis()))
                .retryPolicy(new ExponentialBackoffRetry(250, 5))
                .build();

        client.getConnectionStateListenable().addListener((ignored, state) ->
                logConnectionState(state));
        client.start();
        return client;
    }

    private static void logConnectionState(ConnectionState state) {
        if (state == ConnectionState.LOST) {
            log.warn("ZooKeeper session lost; registry operations and cache freshness are unavailable");
        } else {
            log.info("ZooKeeper connection state changed to {}", state);
        }
    }
}
