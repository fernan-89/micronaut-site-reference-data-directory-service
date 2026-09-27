package com.thinklab.infrastructure.adapter.out.persistence;

import com.mongodb.client.model.Filters;
import com.mongodb.reactivestreams.client.MongoClient;
import com.thinklab.domain.exception.SiteNotFoundException;
import com.thinklab.domain.model.Site;
import com.thinklab.domain.model.Site.Building;
import com.thinklab.domain.model.Site.Rack;
import com.thinklab.domain.model.Site.RackStatus;
import com.thinklab.domain.model.Site.Room;
import com.thinklab.domain.model.Site.RoomType;
import com.thinklab.domain.model.Site.SiteStatus;
import com.thinklab.domain.repository.SiteRepository;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.micronaut.test.support.TestPropertyProvider;
import jakarta.inject.Inject;
import org.bson.Document;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Site aggregate through {@link SiteRepository} against a real MongoDB: three-level nested
 * Building/Room/Rack writes via arrayFilters, the derived GeoJSON location, tenant-scoped filtering,
 * not-found handling, the database taken from {@code mongodb.uri}, and the geospatial/tenant-status
 * indexes created by {@link SiteIndexInitializer} at startup.
 */
@MicronautTest(packages = "com.thinklab", transactional = false)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SitePersistenceIT implements TestPropertyProvider {

    private static final String DATABASE = "site_reference_data_directory_it";

    @Override
    public Map<String, String> getProperties() {
        return Map.of("mongodb.uri", MongoContainer.uri(DATABASE));
    }

    @Inject
    SiteRepository sites;

    @Inject
    MongoClient mongoClient;

    private static Site newSite(UUID organisationId) {
        return Site.createNew(UUID.randomUUID(), organisationId, "HQ", "1 Main St", "Lisbon", "PT", "1000-001",
                "Europe/Lisbon", 38.72, -9.14);
    }

    @Test
    @DisplayName("a created site is read back with its coordinates and ACTIVE status")
    void createAndFind() {
        Site created = sites.create(newSite(UUID.randomUUID())).block();

        Site found = sites.findById(created.getId()).block();

        assertEquals(created.getSiteName(), found.getSiteName());
        assertEquals(SiteStatus.ACTIVE, found.getStatus());
        assertEquals(38.72, found.getLatitude());
        assertEquals(-9.14, found.getLongitude());
        assertTrue(found.getBuildings().isEmpty());
        assertNotNull(found.getCreatedAt());
    }

    @Test
    @DisplayName("writes land in the database named by mongodb.uri, with a derived GeoJSON location")
    void usesTheConfiguredDatabaseWithGeoLocation() {
        Site created = sites.create(newSite(UUID.randomUUID())).block();

        Document stored = Mono.from(mongoClient.getDatabase(DATABASE).getCollection("sites")
                .find(Filters.eq("_id", created.getId())).first()).block();

        assertNotNull(stored, "site not found in " + DATABASE);
        assertEquals("Point", stored.get("location", Document.class).getString("type"));
    }

    @Test
    @DisplayName("updateBasicInfo and updateStatus are persisted as granular sets")
    void basicInfoAndStatusUpdates() {
        Site created = sites.create(newSite(UUID.randomUUID())).block();

        sites.updateBasicInfo(created.getId(), "HQ 2", "2 Main St", "Porto", "PT", "4000-001", "Europe/Lisbon", null, null).block();
        sites.updateStatus(created.getId(), SiteStatus.INACTIVE).block();

        Site found = sites.findById(created.getId()).block();
        assertEquals("HQ 2", found.getSiteName());
        assertEquals("Porto", found.getCity());
        assertNull(found.getLatitude());
        assertEquals(SiteStatus.INACTIVE, found.getStatus());
    }

    @Test
    @DisplayName("Building, Room and Rack are added three levels deep via arrayFilters, and a rack status change lands on the right rack")
    void nestedHierarchyRoundTrip() {
        Site created = sites.create(newSite(UUID.randomUUID())).block();
        Building building = new Building(UUID.randomUUID(), "B1", 3, List.of());
        sites.addBuilding(created.getId(), building).block();
        Room room = new Room(UUID.randomUUID(), "R1", RoomType.DATA_CENTER, List.of());
        sites.addRoom(created.getId(), building.buildingId(), room).block();
        Rack firstRack = new Rack(UUID.randomUUID(), "K1", 42, RackStatus.ACTIVE);
        Rack secondRack = new Rack(UUID.randomUUID(), "K2", 42, RackStatus.ACTIVE);
        sites.addRack(created.getId(), building.buildingId(), room.roomId(), firstRack).block();
        sites.addRack(created.getId(), building.buildingId(), room.roomId(), secondRack).block();

        sites.updateRackStatus(created.getId(), building.buildingId(), room.roomId(), secondRack.rackId(), RackStatus.DECOMMISSIONED).block();

        Site found = sites.findById(created.getId()).block();
        assertEquals(1, found.getBuildings().size());
        List<Rack> racks = found.getBuildings().get(0).rooms().get(0).racks();
        assertEquals(2, racks.size());
        assertEquals(RackStatus.ACTIVE, found.findRack(found.getBuildings().get(0).rooms().get(0), firstRack.rackId()).status());
        assertEquals(RackStatus.DECOMMISSIONED, found.findRack(found.getBuildings().get(0).rooms().get(0), secondRack.rackId()).status());
    }

    @Test
    @DisplayName("listing is tenant-scoped and honours the optional status filter")
    void listingFilters() {
        UUID organisation = UUID.randomUUID();
        Site active = sites.create(newSite(organisation)).block();
        Site toDeactivate = sites.create(newSite(organisation)).block();
        sites.create(newSite(UUID.randomUUID())).block();
        sites.updateStatus(toDeactivate.getId(), SiteStatus.INACTIVE).block();

        assertEquals(Set.of(active.getId(), toDeactivate.getId()),
                ids(sites.findAllByOrganisationId(organisation, null).collectList().block()));
        assertEquals(Set.of(active.getId()),
                ids(sites.findAllByOrganisationId(organisation, SiteStatus.ACTIVE).collectList().block()));
        assertEquals(Set.of(toDeactivate.getId()),
                ids(sites.findAllByOrganisationId(organisation, SiteStatus.INACTIVE).collectList().block()));
    }

    @Test
    @DisplayName("an unknown site is empty on read and SiteNotFoundException on update")
    void notFound() {
        UUID unknown = UUID.randomUUID();

        assertNull(sites.findById(unknown).block());
        assertThrows(SiteNotFoundException.class, () -> sites.updateStatus(unknown, SiteStatus.INACTIVE).block());
    }

    @Test
    @DisplayName("the sparse 2dsphere and compound (organisationId, status) indexes exist")
    void indexesExist() {
        sites.create(newSite(UUID.randomUUID())).block();

        List<Document> indexes = Flux.from(mongoClient.getDatabase(DATABASE).getCollection("sites").listIndexes()).collectList().block();

        assertTrue(indexes.stream().anyMatch(index -> "2dsphere".equals(index.get("key", Document.class).get("location"))
                && Boolean.TRUE.equals(index.getBoolean("sparse"))), () -> "sites: " + indexes);
        assertTrue(indexes.stream().anyMatch(index -> new Document("organisationId", 1).append("status", 1).equals(index.get("key", Document.class))),
                () -> "sites: " + indexes);
    }

    private static Set<UUID> ids(List<Site> list) {
        return list.stream().map(Site::getId).collect(Collectors.toSet());
    }
}
