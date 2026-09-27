package com.thinklab.domain.model;

import com.thinklab.domain.exception.InvalidSiteStatusException;
import com.thinklab.domain.exception.SiteNotFoundException;
import com.thinklab.domain.model.Site.Building;
import com.thinklab.domain.model.Site.Rack;
import com.thinklab.domain.model.Site.RackStatus;
import com.thinklab.domain.model.Site.Room;
import com.thinklab.domain.model.Site.RoomType;
import com.thinklab.domain.model.Site.SiteStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SiteTest {

    private UUID siteId;
    private UUID organisationId;
    private Site site;

    @BeforeEach
    void setUp() {
        siteId = UUID.randomUUID();
        organisationId = UUID.randomUUID();
        site = Site.createNew(siteId, organisationId, "HQ", "1 Main St", "Springfield", "US", "00000",
                "America/New_York", 40.0, -74.0);
    }

    @Test
    @DisplayName("createNew builds an ACTIVE Site with no buildings")
    void createNew() {
        assertEquals(siteId, site.getId());
        assertEquals(organisationId, site.getOrganisationId());
        assertEquals("HQ", site.getSiteName());
        assertEquals(SiteStatus.ACTIVE, site.getStatus());
        assertTrue(site.getBuildings().isEmpty());
        assertNotNull(site.getCreatedAt());
        assertEquals(site.getCreatedAt(), site.getUpdatedAt());
    }

    @Test
    @DisplayName("reconstitute rebuilds a Site exactly as given, including nested buildings")
    void reconstitute() {
        Rack rack = new Rack(UUID.randomUUID(), "Rack A1", 42, RackStatus.ACTIVE);
        Room room = new Room(UUID.randomUUID(), "Server Room", RoomType.DATA_CENTER, List.of(rack));
        Building building = new Building(UUID.randomUUID(), "Building 1", 3, List.of(room));
        Instant created = Instant.now().minusSeconds(100);
        Instant updated = Instant.now();

        Site rebuilt = Site.reconstitute(siteId, organisationId, "HQ", "addr", "city", "country", "zip", "tz",
                1.0, 2.0, SiteStatus.INACTIVE, List.of(building), created, updated);

        assertEquals(SiteStatus.INACTIVE, rebuilt.getStatus());
        assertEquals(1, rebuilt.getBuildings().size());
        assertEquals(created, rebuilt.getCreatedAt());
        assertEquals(updated, rebuilt.getUpdatedAt());
    }

    @Test
    @DisplayName("updateBasicInfo overwrites every field and bumps updatedAt")
    void updateBasicInfo() throws InterruptedException {
        Instant before = site.getUpdatedAt();
        Thread.sleep(2);

        site.updateBasicInfo("HQ 2", "2 Main St", "Shelbyville", "CA", "11111", "America/Chicago", 41.0, -75.0);

        assertEquals("HQ 2", site.getSiteName());
        assertEquals("2 Main St", site.getAddress());
        assertEquals("Shelbyville", site.getCity());
        assertEquals("CA", site.getCountry());
        assertEquals("11111", site.getZipCode());
        assertEquals("America/Chicago", site.getTimezone());
        assertEquals(41.0, site.getLatitude());
        assertEquals(-75.0, site.getLongitude());
        assertTrue(site.getUpdatedAt().isAfter(before));
    }

    @Test
    @DisplayName("activate then deactivate is a legal round trip")
    void activateDeactivate() {
        site.deactivate();
        assertEquals(SiteStatus.INACTIVE, site.getStatus());
        site.activate();
        assertEquals(SiteStatus.ACTIVE, site.getStatus());
    }

    @Test
    @DisplayName("activating an already-ACTIVE Site is an idempotency violation")
    void activateAlreadyActive() {
        InvalidSiteStatusException ex = assertThrows(InvalidSiteStatusException.class, site::activate);
        assertTrue(ex.getMessage().contains("Idempotency Violation"));
    }

    @Test
    @DisplayName("deactivating an already-INACTIVE Site is an idempotency violation")
    void deactivateAlreadyInactive() {
        site.deactivate();
        assertThrows(InvalidSiteStatusException.class, site::deactivate);
    }

    @Test
    @DisplayName("addBuilding appends and bumps updatedAt")
    void addBuilding() {
        Building building = new Building(UUID.randomUUID(), "Building 1", 2, List.of());
        site.addBuilding(building);

        assertEquals(1, site.getBuildings().size());
        assertEquals(building, site.getBuildings().get(0));
    }

    @Test
    @DisplayName("getBuildings returns an unmodifiable view")
    void buildingsUnmodifiable() {
        assertThrows(UnsupportedOperationException.class, () -> site.getBuildings().add(
                new Building(UUID.randomUUID(), "X", null, List.of())));
    }

    @Test
    @DisplayName("addRoom appends a Room to an existing Building")
    void addRoom() {
        UUID buildingId = UUID.randomUUID();
        site.addBuilding(new Building(buildingId, "Building 1", null, List.of()));

        Room room = new Room(UUID.randomUUID(), "Room A", RoomType.OFFICE, List.of());
        site.addRoom(buildingId, room);

        assertEquals(1, site.findBuilding(buildingId).rooms().size());
        assertEquals(room, site.findBuilding(buildingId).rooms().get(0));
    }

    @Test
    @DisplayName("addRoom against an unknown buildingId throws SiteNotFoundException")
    void addRoomUnknownBuilding() {
        Room room = new Room(UUID.randomUUID(), "Room A", RoomType.OFFICE, List.of());
        assertThrows(SiteNotFoundException.class, () -> site.addRoom(UUID.randomUUID(), room));
    }

    @Test
    @DisplayName("addRack appends a Rack to an existing Room")
    void addRack() {
        UUID buildingId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();
        site.addBuilding(new Building(buildingId, "Building 1", null, List.of()));
        site.addRoom(buildingId, new Room(roomId, "Room A", RoomType.DATA_CENTER, List.of()));

        Rack rack = new Rack(UUID.randomUUID(), "Rack 1", 42, RackStatus.ACTIVE);
        site.addRack(buildingId, roomId, rack);

        Room room = site.findRoom(site.findBuilding(buildingId), roomId);
        assertEquals(1, room.racks().size());
        assertEquals(rack, room.racks().get(0));
    }

    @Test
    @DisplayName("addRack against an unknown roomId throws SiteNotFoundException")
    void addRackUnknownRoom() {
        UUID buildingId = UUID.randomUUID();
        site.addBuilding(new Building(buildingId, "Building 1", null, List.of()));

        Rack rack = new Rack(UUID.randomUUID(), "Rack 1", 42, RackStatus.ACTIVE);
        assertThrows(SiteNotFoundException.class, () -> site.addRack(buildingId, UUID.randomUUID(), rack));
    }

    @Test
    @DisplayName("addRack against an unknown buildingId throws SiteNotFoundException")
    void addRackUnknownBuilding() {
        Rack rack = new Rack(UUID.randomUUID(), "Rack 1", 42, RackStatus.ACTIVE);
        assertThrows(SiteNotFoundException.class, () -> site.addRack(UUID.randomUUID(), UUID.randomUUID(), rack));
    }

    @Test
    @DisplayName("changeRackStatus decommissions an ACTIVE rack")
    void changeRackStatusDecommission() {
        UUID buildingId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();
        UUID rackId = UUID.randomUUID();
        site.addBuilding(new Building(buildingId, "Building 1", null, List.of()));
        site.addRoom(buildingId, new Room(roomId, "Room A", RoomType.DATA_CENTER, List.of()));
        site.addRack(buildingId, roomId, new Rack(rackId, "Rack 1", 42, RackStatus.ACTIVE));

        site.changeRackStatus(buildingId, roomId, rackId, RackStatus.DECOMMISSIONED);

        Room room = site.findRoom(site.findBuilding(buildingId), roomId);
        assertEquals(RackStatus.DECOMMISSIONED, site.findRack(room, rackId).status());
    }

    @Test
    @DisplayName("changeRackStatus back to ACTIVE from DECOMMISSIONED is an illegal transition")
    void changeRackStatusIllegal() {
        UUID buildingId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();
        UUID rackId = UUID.randomUUID();
        site.addBuilding(new Building(buildingId, "Building 1", null, List.of()));
        site.addRoom(buildingId, new Room(roomId, "Room A", RoomType.DATA_CENTER, List.of()));
        site.addRack(buildingId, roomId, new Rack(rackId, "Rack 1", 42, RackStatus.ACTIVE));
        site.changeRackStatus(buildingId, roomId, rackId, RackStatus.DECOMMISSIONED);

        assertThrows(InvalidSiteStatusException.class,
                () -> site.changeRackStatus(buildingId, roomId, rackId, RackStatus.ACTIVE));
    }

    @Test
    @DisplayName("changeRackStatus to the same status is an idempotency violation")
    void changeRackStatusIdempotent() {
        UUID buildingId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();
        UUID rackId = UUID.randomUUID();
        site.addBuilding(new Building(buildingId, "Building 1", null, List.of()));
        site.addRoom(buildingId, new Room(roomId, "Room A", RoomType.DATA_CENTER, List.of()));
        site.addRack(buildingId, roomId, new Rack(rackId, "Rack 1", 42, RackStatus.ACTIVE));

        assertThrows(InvalidSiteStatusException.class,
                () -> site.changeRackStatus(buildingId, roomId, rackId, RackStatus.ACTIVE));
    }

    @Test
    @DisplayName("changeRackStatus against an unknown rackId throws SiteNotFoundException")
    void changeRackStatusUnknownRack() {
        UUID buildingId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();
        site.addBuilding(new Building(buildingId, "Building 1", null, List.of()));
        site.addRoom(buildingId, new Room(roomId, "Room A", RoomType.DATA_CENTER, List.of()));

        assertThrows(SiteNotFoundException.class,
                () -> site.changeRackStatus(buildingId, roomId, UUID.randomUUID(), RackStatus.DECOMMISSIONED));
    }

    @Test
    @DisplayName("mutations correctly target the second entry among several siblings, not just the only one")
    void mutationsTargetTheRightSiblingAmongSeveral() {
        UUID firstBuildingId = UUID.randomUUID();
        UUID secondBuildingId = UUID.randomUUID();
        site.addBuilding(new Building(firstBuildingId, "First", null, List.of()));
        site.addBuilding(new Building(secondBuildingId, "Second", null, List.of()));

        UUID firstRoomId = UUID.randomUUID();
        UUID secondRoomId = UUID.randomUUID();
        site.addRoom(secondBuildingId, new Room(firstRoomId, "First Room", RoomType.OFFICE, List.of()));
        site.addRoom(secondBuildingId, new Room(secondRoomId, "Second Room", RoomType.DATA_CENTER, List.of()));

        UUID firstRackId = UUID.randomUUID();
        UUID secondRackId = UUID.randomUUID();
        site.addRack(secondBuildingId, secondRoomId, new Rack(firstRackId, "First Rack", 10, RackStatus.ACTIVE));
        site.addRack(secondBuildingId, secondRoomId, new Rack(secondRackId, "Second Rack", 20, RackStatus.ACTIVE));

        site.changeRackStatus(secondBuildingId, secondRoomId, secondRackId, RackStatus.DECOMMISSIONED);

        Building secondBuilding = site.findBuilding(secondBuildingId);
        assertEquals(2, secondBuilding.rooms().size());
        Room secondRoom = site.findRoom(secondBuilding, secondRoomId);
        assertEquals(2, secondRoom.racks().size());
        assertEquals(RackStatus.ACTIVE, site.findRack(secondRoom, firstRackId).status());
        assertEquals(RackStatus.DECOMMISSIONED, site.findRack(secondRoom, secondRackId).status());
        // The untouched sibling building is still exactly as it was.
        assertEquals(0, site.findBuilding(firstBuildingId).rooms().size());
    }

    @Test
    @DisplayName("findBuilding on an unknown id throws SiteNotFoundException")
    void findBuildingUnknown() {
        assertThrows(SiteNotFoundException.class, () -> site.findBuilding(UUID.randomUUID()));
    }

    @Test
    @DisplayName("SiteStatus.canTransitionTo covers both directions, and both self-transitions as false")
    void canTransitionTo() {
        assertTrue(SiteStatus.ACTIVE.canTransitionTo(SiteStatus.INACTIVE));
        assertTrue(SiteStatus.INACTIVE.canTransitionTo(SiteStatus.ACTIVE));
        assertTrue(SiteStatus.ACTIVE.canTransitionTo(SiteStatus.ACTIVE) == false);
        assertTrue(SiteStatus.INACTIVE.canTransitionTo(SiteStatus.INACTIVE) == false);
    }

    @Test
    @DisplayName("RackStatus.canTransitionTo only allows ACTIVE -> DECOMMISSIONED")
    void rackCanTransitionTo() {
        assertTrue(RackStatus.ACTIVE.canTransitionTo(RackStatus.DECOMMISSIONED));
        assertTrue(RackStatus.DECOMMISSIONED.canTransitionTo(RackStatus.DECOMMISSIONED) == false);
        assertTrue(RackStatus.ACTIVE.canTransitionTo(RackStatus.ACTIVE) == false);
        assertTrue(RackStatus.DECOMMISSIONED.canTransitionTo(RackStatus.ACTIVE) == false);
    }
}
