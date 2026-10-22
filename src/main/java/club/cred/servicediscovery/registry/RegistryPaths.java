package club.cred.servicediscovery.registry;

import java.util.regex.Pattern;

final class RegistryPaths {

    private static final Pattern SEGMENT = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,127}");

    private RegistryPaths() {
    }

    static String servicePath(String basePath, String serviceName) {
        validateSegment("service name", serviceName);
        return basePath + "/" + serviceName;
    }

    static String instancePath(String basePath, String serviceName, String instanceId) {
        validateSegment("instance ID", instanceId);
        return servicePath(basePath, serviceName) + "/" + instanceId;
    }

    static void validateSegment(String description, String value) {
        if (value == null || !SEGMENT.matcher(value).matches()) {
            throw new IllegalArgumentException(
                    description + " must match " + SEGMENT.pattern());
        }
    }
}
