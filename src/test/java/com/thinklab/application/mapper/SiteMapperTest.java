package com.thinklab.application.mapper;

import com.thinklab.application.dto.request.CreateSiteRequest;
import com.thinklab.application.dto.request.InitiateBuildingRequest;
import com.thinklab.application.dto.request.InitiateRackRequest;
import com.thinklab.application.dto.request.InitiateRoomRequest;
import com.thinklab.application.dto.response.SiteResponse;
import com.thinklab.domain.model.Site;
import com.thinklab.domain.model.Site.Building;
import com.thinklab.domain.model.Site.Rack;
import com.thinklab.domain.model.Site.RackStatus;
import com.thinklab.domain.model.Site.Room;
import com.thinklab.domain.model.Site.RoomType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SiteMapperTest {

    @Test
    @DisplayName("cannot be instantiated")
    void utilityClass() throws NoSuchMethodException {
        var constructor = SiteMapper.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        var invocationTarget = assertThrows(java.lang.reflect.InvocationTargetException.class, constructor::newInstance);
        assertEquals(UnsupportedOperationException.class, invocationTarget.getCause().getClass());
    }

    @Test
    @DisplayName("toDomain maps a CreateSiteRequest and sovereign ID into a new Site")
    void toDomain() {
        UUID sovereignId = UUID.randomUUID();
        UUID organisationId = UUID.randomUUID();
        CreateSiteRequest request = new CreateSiteRequest(organisationId, "HQ", "addr", "city", "country", "zip", "tz", 1.0, 2.0);

        Site site = SiteMapper.toDomain(request, sovereignId);

        assertEquals(sovereignId, site.getId());
        assertEquals(organisationId, site.getOrganisationId());
        assertEquals("HQ", site.getSiteName());
    }

    @Test
    @DisplayName("toBuilding/toRoom/toRack map requests with their generated ids")
    void toNested() {
        UUID buildingId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();
        UUID rackId = UUID.randomUUID();

        Building building = SiteMapper.toBuilding(new InitiateBuildingRequest("B1", 3), buildingId);
        assertEquals(buildingId, building.buildingId());
        assertEquals("B1", building.buildingName());

        Room roomWithType = SiteMapper.toRoom(new InitiateRoomRequest("R1", "DATA_CENTER"), roomId);
        assertEquals(RoomType.DATA_CENTER, roomWithType.roomType());

        Room roomDefaultType = SiteMapper.toRoom(new InitiateRoomRequest("R1", null), roomId);
        assertEquals(RoomType.OTHER, roomDefaultType.roomType());

        Rack rack = SiteMapper.toRack(new InitiateRackRequest("K1", 42), rackId);
        assertEquals(rackId, rack.rackId());
        assertEquals(42, rack.heightU());
    }

    @Test
    @DisplayName("toResponse maps every nested level, including empty buildings")
    void toResponse() {
        UUID siteId = UUID.randomUUID();
        UUID organisationId = UUID.randomUUID();
        Rack rack = new Rack(UUID.randomUUID(), "K1", 42, RackStatus.ACTIVE);
        Room room = new Room(UUID.randomUUID(), "R1", RoomType.OFFICE, List.of(rack));
        Building building = new Building(UUID.randomUUID(), "B1", 2, List.of(room));
        Site site = Site.reconstitute(siteId, organisationId, "HQ", "addr", "city", "country", "zip", "tz", 1.0, 2.0,
                Site.SiteStatus.ACTIVE, List.of(building), java.time.Instant.now(), java.time.Instant.now());

        SiteResponse response = SiteMapper.toResponse(site);

        assertEquals(siteId, response.id());
        assertEquals(1, response.buildings().size());
        assertEquals(1, response.buildings().get(0).rooms().size());
        assertEquals(1, response.buildings().get(0).rooms().get(0).racks().size());
        assertEquals("ACTIVE", response.buildings().get(0).rooms().get(0).racks().get(0).status());

        Site emptySite = Site.createNew(siteId, organisationId, "HQ", null, null, null, null, null, null, null);
        assertEquals(0, SiteMapper.toResponse(emptySite).buildings().size());
    }
}
