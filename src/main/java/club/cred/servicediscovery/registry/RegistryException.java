package club.cred.servicediscovery.registry;

public final class RegistryException extends RuntimeException {

    public enum Kind {
        NOT_FOUND,
        LEASE_MISMATCH,
        LEASE_EXPIRED,
        CONCURRENT_MODIFICATION,
        UNAVAILABLE
    }

    private final Kind kind;

    public RegistryException(Kind kind, String message) {
        super(message);
        this.kind = kind;
    }

    public RegistryException(Kind kind, String message, Throwable cause) {
        super(message, cause);
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }
}
