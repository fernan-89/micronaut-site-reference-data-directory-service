package com.thinklab.domain.model;

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
import static org.junit.jupiter.api.Assertions.assertTrue;

class SiteValueObjectsTest {

    @Test
    @DisplayName("Building rejects a null buildingId or buildingName, and defaults a null rooms list to empty")
    void buildingGuards() {
        assertThrows(NullPointerException.class, () -> new Building(null, "name", null, null));
        assertThrows(NullPointerException.class, () -> new Building(UUID.randomUUID(), null, null, null));

        Building building = new Building(UUID.randomUUID(), "name", 2, null);
        assertTrue(building.rooms().isEmpty());
    }

    @Test
    @DisplayName("Building.withRooms replaces only the rooms list")
    void buildingWithRooms() {
        Building original = new Building(UUID.randomUUID(), "name", 2, List.of());
        Room room = new Room(UUID.randomUUID(), "room", RoomType.OFFICE, List.of());

        Building updated = original.withRooms(List.of(room));

        assertEquals(original.buildingId(), updated.buildingId());
        assertEquals(original.buildingName(), updated.buildingName());
        assertEquals(List.of(room), updated.rooms());
    }

    @Test
    @DisplayName("Room rejects a null roomId or roomName, defaults a null roomType to OTHER and a null racks list to empty")
    void roomGuards() {
        assertThrows(NullPointerException.class, () -> new Room(null, "name", RoomType.OFFICE, null));
        assertThrows(NullPointerException.class, () -> new Room(UUID.randomUUID(), null, RoomType.OFFICE, null));

        Room room = new Room(UUID.randomUUID(), "name", null, null);
        assertEquals(RoomType.OTHER, room.roomType());
        assertTrue(room.racks().isEmpty());
    }

    @Test
    @DisplayName("Room.withRacks replaces only the racks list")
    void roomWithRacks() {
        Room original = new Room(UUID.randomUUID(), "name", RoomType.DATA_CENTER, List.of());
        Rack rack = new Rack(UUID.randomUUID(), "rack", 42, RackStatus.ACTIVE);

        Room updated = original.withRacks(List.of(rack));

        assertEquals(original.roomId(), updated.roomId());
        assertEquals(List.of(rack), updated.racks());
    }

    @Test
    @DisplayName("Rack rejects a null rackId or rackName, a non-positive heightU, and defaults a null status to ACTIVE")
    void rackGuards() {
        assertThrows(NullPointerException.class, () -> new Rack(null, "name", 42, RackStatus.ACTIVE));
        assertThrows(NullPointerException.class, () -> new Rack(UUID.randomUUID(), null, 42, RackStatus.ACTIVE));
        assertThrows(IllegalArgumentException.class, () -> new Rack(UUID.randomUUID(), "name", 0, RackStatus.ACTIVE));
        assertThrows(IllegalArgumentException.class, () -> new Rack(UUID.randomUUID(), "name", -1, RackStatus.ACTIVE));

        Rack rack = new Rack(UUID.randomUUID(), "name", 42, null);
        assertEquals(RackStatus.ACTIVE, rack.status());
    }

    @Test
    @DisplayName("Rack.withStatus replaces only the status")
    void rackWithStatus() {
        Rack original = new Rack(UUID.randomUUID(), "name", 42, RackStatus.ACTIVE);

        Rack updated = original.withStatus(RackStatus.DECOMMISSIONED);

        assertEquals(original.rackId(), updated.rackId());
        assertEquals(original.heightU(), updated.heightU());
        assertEquals(RackStatus.DECOMMISSIONED, updated.status());
    }
}
