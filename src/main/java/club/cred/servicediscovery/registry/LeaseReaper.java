package club.cred.servicediscovery.registry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
final class LeaseReaper {

    private static final Logger log = LoggerFactory.getLogger(LeaseReaper.class);

    private final RegistryStore registryStore;

    LeaseReaper(RegistryStore registryStore) {
        this.registryStore = registryStore;
    }

    @Scheduled(fixedDelayString = "${service-discovery.registry.reap-interval}")
    void removeExpiredRegistrations() {
        try {
            registryStore.removeExpired();
        } catch (RegistryException exception) {
            log.warn("Could not complete expired registration cleanup", exception);
        }
    }
}
