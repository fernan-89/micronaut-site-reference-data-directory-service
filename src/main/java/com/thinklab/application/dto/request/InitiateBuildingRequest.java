package com.thinklab.application.dto.request;

import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.NotBlank;

/**
 * DTO for adding a new Building to an existing Site (BIAN Behavior Qualifier: {@code building/initiate}).
 */
@Serdeable
public record InitiateBuildingRequest(
        @NotBlank(message = "Building Name is required")
        String buildingName,

        Integer floorCount
) {}
