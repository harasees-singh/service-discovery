package club.cred.servicediscovery.registry;

import tools.jackson.databind.ObjectMapper;

import org.springframework.stereotype.Component;

@Component
final class RegistryCodec {

    private final ObjectMapper objectMapper;

    RegistryCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    byte[] serialize(RegisteredServiceInstance instance) {
        try {
            return objectMapper.writeValueAsBytes(instance);
        } catch (Exception exception) {
            throw new RegistryException(
                    RegistryException.Kind.UNAVAILABLE,
                    "Could not serialize service registration",
                    exception);
        }
    }

    RegisteredServiceInstance deserialize(byte[] data) {
        try {
            return objectMapper.readValue(data, RegisteredServiceInstance.class);
        } catch (Exception exception) {
            throw new RegistryException(
                    RegistryException.Kind.UNAVAILABLE,
                    "Could not deserialize service registration",
                    exception);
        }
    }
}
