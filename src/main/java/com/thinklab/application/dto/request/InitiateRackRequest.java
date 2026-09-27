package com.thinklab.application.dto.request;

import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

/**
 * DTO for adding a new Rack to an existing Room (BIAN Behavior Qualifier: {@code rack/initiate}).
 */
@Serdeable
public record InitiateRackRequest(
        @NotBlank(message = "Rack Name is required")
        String rackName,

        @Positive(message = "Rack height in U must be positive")
        int heightU
) {}
