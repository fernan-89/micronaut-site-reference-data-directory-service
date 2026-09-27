package com.thinklab.infrastructure.adapter.out.persistence.entity;

import com.thinklab.domain.model.Site;
import com.thinklab.domain.model.Site.Building;
import com.thinklab.domain.model.Site.Rack;
import com.thinklab.domain.model.Site.RackStatus;
import com.thinklab.domain.model.Site.Room;
import com.thinklab.domain.model.Site.RoomType;
import com.thinklab.domain.model.Site.SiteStatus;
import com.thinklab.infrastructure.adapter.out.persistence.entity.SiteDocument.SitePersistenceMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SiteDocumentTest {

    @Test
    @DisplayName("toDocument / toDomain round-trips the whole aggregate, including three levels of nesting and the derived GeoJSON location")
    void roundTrip() {
        UUID id = UUID.randomUUID();
        UUID organisationId = UUID.randomUUID();
        Rack rack = new Rack(UUID.randomUUID(), "K1", 42, RackStatus.DECOMMISSIONED);
        Room room = new Room(UUID.randomUUID(), "R1", RoomType.DATA_CENTER, List.of(rack));
        Building building = new Building(UUID.randomUUID(), "B1", 3, List.of(room));
        Site site = Site.reconstitute(id, organisationId, "HQ", "addr", "city", "country", "zip", "tz",
                40.5, -74.5, SiteStatus.INACTIVE, List.of(building), Instant.now(), Instant.now());

        SiteDocument doc = SitePersistenceMapper.toDocument(site);
        Site restored = SitePersistenceMapper.toDomain(doc);

        assertEquals(id, doc.getId());
        assertEquals("INACTIVE", doc.getStatus());
        assertEquals(40.5, doc.getLocation().getPosition().getValues().get(1));
        assertEquals(-74.5, doc.getLocation().getPosition().getValues().get(0));
        assertEquals(id, restored.getId());
        assertEquals(organisationId, restored.getOrganisationId());
        assertEquals(SiteStatus.INACTIVE, restored.getStatus());
        assertEquals(1, restored.getBuildings().size());
        assertEquals(1, restored.getBuildings().get(0).rooms().size());
        assertEquals(1, restored.getBuildings().get(0).rooms().get(0).racks().size());
        assertEquals(RackStatus.DECOMMISSIONED, restored.getBuildings().get(0).rooms().get(0).racks().get(0).status());
        assertEquals(RoomType.DATA_CENTER, restored.getBuildings().get(0).rooms().get(0).roomType());
    }

    @Test
    @DisplayName("toDocument leaves location null when both latitude and longitude are missing")
    void noLocationWithoutCoordinates() {
        Site site = Site.createNew(UUID.randomUUID(), UUID.randomUUID(), "HQ", null, null, null, null, null, null, null);

        SiteDocument doc = SitePersistenceMapper.toDocument(site);

        assertNull(doc.getLocation());
    }

    @Test
    @DisplayName("toDocument also leaves location null when only one of latitude/longitude is present")
    void noLocationWithPartialCoordinates() {
        Site site = Site.createNew(UUID.randomUUID(), UUID.randomUUID(), "HQ", null, null, null, null, null, 1.0, null);

        SiteDocument doc = SitePersistenceMapper.toDocument(site);

        assertNull(doc.getLocation());
    }

    @Test
    @DisplayName("toDomain defaults a missing status to ACTIVE and missing nested lists to empty")
    void defaultsWhenFieldsMissing() {
        SiteDocument doc = new SiteDocument();
        doc.setId(UUID.randomUUID());
        doc.setOrganisationId(UUID.randomUUID());
        doc.setSiteName("n");
        doc.setBuildings(null);
        doc.setCreatedAt(Instant.now());
        doc.setUpdatedAt(Instant.now());

        Site restored = SitePersistenceMapper.toDomain(doc);

        assertEquals(SiteStatus.ACTIVE, restored.getStatus());
        assertTrue(restored.getBuildings().isEmpty());
    }

    @Test
    @DisplayName("toDomain defaults a missing roomType to OTHER and missing rooms/racks lists to empty")
    void nestedDefaultsWhenFieldsMissing() {
        SiteDocument.BuildingDocument buildingDoc = new SiteDocument.BuildingDocument(UUID.randomUUID(), "B1", null, null);
        SiteDocument doc = new SiteDocument();
        doc.setId(UUID.randomUUID());
        doc.setOrganisationId(UUID.randomUUID());
        doc.setSiteName("n");
        doc.setBuildings(List.of(buildingDoc));
        doc.setCreatedAt(Instant.now());
        doc.setUpdatedAt(Instant.now());

        Site restored = SitePersistenceMapper.toDomain(doc);

        assertTrue(restored.getBuildings().get(0).rooms().isEmpty());

        SiteDocument.RoomDocument roomDoc = new SiteDocument.RoomDocument(UUID.randomUUID(), "R1", null, null);
        SiteDocument.BuildingDocument buildingWithRoom = new SiteDocument.BuildingDocument(UUID.randomUUID(), "B1", null, List.of(roomDoc));
        doc.setBuildings(List.of(buildingWithRoom));

        Site restored2 = SitePersistenceMapper.toDomain(doc);
        assertEquals(RoomType.OTHER, restored2.getBuildings().get(0).rooms().get(0).roomType());
        assertTrue(restored2.getBuildings().get(0).rooms().get(0).racks().isEmpty());
    }

    @Test
    @DisplayName("toDomain defaults a missing rack status to ACTIVE")
    void rackStatusDefault() {
        SiteDocument.RackDocument rackDoc = new SiteDocument.RackDocument(UUID.randomUUID(), "K1", 10, null);
        SiteDocument.RoomDocument roomDoc = new SiteDocument.RoomDocument(UUID.randomUUID(), "R1", "OFFICE", List.of(rackDoc));
        SiteDocument.BuildingDocument buildingDoc = new SiteDocument.BuildingDocument(UUID.randomUUID(), "B1", null, List.of(roomDoc));
        SiteDocument doc = new SiteDocument();
        doc.setId(UUID.randomUUID());
        doc.setOrganisationId(UUID.randomUUID());
        doc.setSiteName("n");
        doc.setBuildings(List.of(buildingDoc));
        doc.setCreatedAt(Instant.now());
        doc.setUpdatedAt(Instant.now());

        Site restored = SitePersistenceMapper.toDomain(doc);

        assertEquals(RackStatus.ACTIVE, restored.getBuildings().get(0).rooms().get(0).racks().get(0).status());
    }

    @Test
    @DisplayName("the persistence mapper is a non-instantiable utility class")
    void utilityClass() throws Exception {
        Constructor<SitePersistenceMapper> constructor = SitePersistenceMapper.class.getDeclaredConstructor();
        constructor.setAccessible(true);

        InvocationTargetException ex = assertThrows(InvocationTargetException.class, constructor::newInstance);
        assertInstanceOf(UnsupportedOperationException.class, ex.getCause());
    }

    @Test
    @DisplayName("plain accessors expose what was set (POJO codec contract)")
    void accessors() {
        SiteDocument doc = new SiteDocument();
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        doc.setId(id);
        doc.setStatus("ACTIVE");
        doc.setCreatedAt(now);
        doc.setUpdatedAt(now);
        doc.setAddress("addr");
        doc.setCity("city");
        doc.setCountry("country");
        doc.setZipCode("zip");
        doc.setTimezone("tz");
        doc.setLatitude(1.0);
        doc.setLongitude(2.0);

        assertEquals(id, doc.getId());
        assertEquals("ACTIVE", doc.getStatus());
        assertEquals(now, doc.getCreatedAt());
        assertEquals(now, doc.getUpdatedAt());
        assertEquals("addr", doc.getAddress());
        assertEquals("city", doc.getCity());
        assertEquals("country", doc.getCountry());
        assertEquals("zip", doc.getZipCode());
        assertEquals("tz", doc.getTimezone());
        assertEquals(1.0, doc.getLatitude());
        assertEquals(2.0, doc.getLongitude());
    }
}
