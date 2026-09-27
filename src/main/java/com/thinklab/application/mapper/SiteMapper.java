package com.thinklab.application.mapper;

import com.thinklab.application.dto.request.CreateSiteRequest;
import com.thinklab.application.dto.request.InitiateBuildingRequest;
import com.thinklab.application.dto.request.InitiateRackRequest;
import com.thinklab.application.dto.request.InitiateRoomRequest;
import com.thinklab.application.dto.response.SiteResponse;
import com.thinklab.application.dto.response.SiteResponse.BuildingResponse;
import com.thinklab.application.dto.response.SiteResponse.RackResponse;
import com.thinklab.application.dto.response.SiteResponse.RoomResponse;
import com.thinklab.domain.model.Site;
import com.thinklab.domain.model.Site.Building;
import com.thinklab.domain.model.Site.Rack;
import com.thinklab.domain.model.Site.RackStatus;
import com.thinklab.domain.model.Site.Room;
import com.thinklab.domain.model.Site.RoomType;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Static factory mapper for Site DTOs and Domain Entities. Enforces strict DTO Isolation Pattern.
 */
public final class SiteMapper {

    private SiteMapper() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    public static Site toDomain(CreateSiteRequest request, UUID sovereignId) {
        return Site.createNew(
                sovereignId,
                request.organisationId(),
                request.siteName(),
                request.address(),
                request.city(),
                request.country(),
                request.zipCode(),
                request.timezone(),
                request.latitude(),
                request.longitude()
        );
    }

    public static Building toBuilding(InitiateBuildingRequest request, UUID buildingId) {
        return new Building(buildingId, request.buildingName(), request.floorCount(), List.of());
    }

    public static Room toRoom(InitiateRoomRequest request, UUID roomId) {
        RoomType roomType = request.roomType() != null ? RoomType.valueOf(request.roomType()) : RoomType.OTHER;
        return new Room(roomId, request.roomName(), roomType, List.of());
    }

    public static Rack toRack(InitiateRackRequest request, UUID rackId) {
        return new Rack(rackId, request.rackName(), request.heightU(), RackStatus.ACTIVE);
    }

    public static SiteResponse toResponse(Site site) {
        List<BuildingResponse> buildingResponses = site.getBuildings().stream()
                .map(SiteMapper::toResponse)
                .collect(Collectors.toList());

        return new SiteResponse(
                site.getId(),
                site.getOrganisationId(),
                site.getSiteName(),
                site.getAddress(),
                site.getCity(),
                site.getCountry(),
                site.getZipCode(),
                site.getTimezone(),
                site.getLatitude(),
                site.getLongitude(),
                site.getStatus().name(),
                buildingResponses,
                site.getCreatedAt(),
                site.getUpdatedAt()
        );
    }

    private static BuildingResponse toResponse(Building b) {
        return new BuildingResponse(b.buildingId(), b.buildingName(), b.floorCount(),
                b.rooms().stream().map(SiteMapper::toResponse).collect(Collectors.toList()));
    }

    private static RoomResponse toResponse(Room r) {
        return new RoomResponse(r.roomId(), r.roomName(), r.roomType().name(),
                r.racks().stream().map(SiteMapper::toResponse).collect(Collectors.toList()));
    }

    private static RackResponse toResponse(Rack k) {
        return new RackResponse(k.rackId(), k.rackName(), k.heightU(), k.status().name());
    }
}
