package club.cred.servicediscovery.api;

import club.cred.servicediscovery.registry.RegistryException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public final class RegistryExceptionHandler {

    @ExceptionHandler(RegistryException.class)
    ProblemDetail handleRegistryException(RegistryException exception) {
        HttpStatus status = switch (exception.kind()) {
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case LEASE_MISMATCH, CONCURRENT_MODIFICATION -> HttpStatus.CONFLICT;
            case LEASE_EXPIRED -> HttpStatus.GONE;
            case UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
        };
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, exception.getMessage());
        problem.setTitle("Service registry request failed");
        problem.setProperty("kind", exception.kind());
        return problem;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail handleInvalidArgument(IllegalArgumentException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                exception.getMessage());
        problem.setTitle("Invalid service registry request");
        return problem;
    }
}
