package com.thinklab.application.dto.request;

import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * DTO for Site Creation Request (BIAN Behavior Qualifier: {@code initiate}).
 * Acts as a protective barrier to the Domain Layer.
 */
@Serdeable
public record CreateSiteRequest(
        @NotNull(message = "Organisation ID is required")
        java.util.UUID organisationId,

        @NotBlank(message = "Site Name is required")
        String siteName,

        String address,
        String city,
        String country,
        String zipCode,
        String timezone,
        Double latitude,
        Double longitude
) {}
