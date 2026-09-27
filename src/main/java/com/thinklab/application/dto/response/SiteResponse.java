package com.thinklab.application.dto.response;

import io.micronaut.serde.annotation.Serdeable;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * DTO for Site Output Payload (Site Reference Data Directory Control Record). Enforces the DTO
 * Isolation Pattern by preventing the pure Domain Model from bleeding out into the HTTP/External
 * boundaries.
 */
@Serdeable
public record SiteResponse(
        UUID id,
        UUID organisationId,
        String siteName,
        String address,
        String city,
        String country,
        String zipCode,
        String timezone,
        Double latitude,
        Double longitude,
        String status,
        List<BuildingResponse> buildings,
        Instant createdAt,
        Instant updatedAt
) {
    @Serdeable
    public record BuildingResponse(
            UUID buildingId,
            String buildingName,
            Integer floorCount,
            List<RoomResponse> rooms
    ) {}

    @Serdeable
    public record RoomResponse(
            UUID roomId,
            String roomName,
            String roomType,
            List<RackResponse> racks
    ) {}

    @Serdeable
    public record RackResponse(
            UUID rackId,
            String rackName,
            int heightU,
            String status
    ) {}
}
