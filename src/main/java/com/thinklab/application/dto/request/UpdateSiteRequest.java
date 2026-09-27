package com.thinklab.application.dto.request;

import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.NotBlank;

/**
 * DTO for Site Basic Info Update (BIAN Behavior Qualifier: {@code update}).
 */
@Serdeable
public record UpdateSiteRequest(
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
