package club.cred.servicediscovery.api;

import java.util.Map;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record RegisterInstanceRequest(
        @NotBlank String address,
        @Min(1) @Max(65_535) int port,
        Map<String, String> metadata) {
}
