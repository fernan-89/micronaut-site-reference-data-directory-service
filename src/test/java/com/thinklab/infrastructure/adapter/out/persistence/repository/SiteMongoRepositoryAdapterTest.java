package com.thinklab.infrastructure.adapter.out.persistence.repository;

import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.result.InsertOneResult;
import com.mongodb.client.result.UpdateResult;
import com.mongodb.reactivestreams.client.FindPublisher;
import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoCollection;
import com.mongodb.reactivestreams.client.MongoDatabase;
import com.thinklab.domain.exception.SiteNotFoundException;
import com.thinklab.domain.model.Site;
import com.thinklab.domain.model.Site.Building;
import com.thinklab.domain.model.Site.Rack;
import com.thinklab.domain.model.Site.RackStatus;
import com.thinklab.domain.model.Site.Room;
import com.thinklab.domain.model.Site.SiteStatus;
import com.thinklab.infrastructure.adapter.out.persistence.entity.SiteDocument;
import com.thinklab.infrastructure.adapter.out.persistence.entity.SiteDocument.SitePersistenceMapper;
import org.bson.BsonObjectId;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("unchecked")
class SiteMongoRepositoryAdapterTest {

    @Mock private MongoClient mongoClient;
    @Mock private MongoDatabase mongoDatabase;
    @Mock private MongoCollection<SiteDocument> mongoCollection;

    private SiteMongoRepositoryAdapter adapter;
    private UUID siteId;
    private UUID organisationId;
    private Site site;

    @BeforeEach
    void setUp() {
        lenient().when(mongoClient.getDatabase("thinklab_site_db")).thenReturn(mongoDatabase);
        lenient().when(mongoDatabase.getCollection("sites", SiteDocument.class)).thenReturn(mongoCollection);
        lenient().when(mongoCollection.withCodecRegistry(any())).thenReturn(mongoCollection);
        adapter = new SiteMongoRepositoryAdapter(mongoClient, "mongodb://localhost:27017/thinklab_site_db");

        siteId = UUID.randomUUID();
        organisationId = UUID.randomUUID();
        site = Site.createNew(siteId, organisationId, "HQ", "addr", "city", "country", "zip", "tz", 1.0, 2.0);
    }

    @Test
    @DisplayName("the database falls back to the default name when the URI has none")
    void databaseFallback() {
        lenient().when(mongoClient.getDatabase("thinklab_site_db")).thenReturn(mongoDatabase);
        SiteMongoRepositoryAdapter fallbackAdapter = new SiteMongoRepositoryAdapter(mongoClient, "mongodb://localhost:27017");
        when(mongoCollection.insertOne(any(SiteDocument.class)))
                .thenReturn(Mono.just(InsertOneResult.acknowledged(new BsonObjectId(new ObjectId()))));

        StepVerifier.create(fallbackAdapter.create(site)).expectNext(site).verifyComplete();
    }

    @Test
    @DisplayName("create inserts the whole aggregate as one document")
    void create() {
        when(mongoCollection.insertOne(any(SiteDocument.class)))
                .thenReturn(Mono.just(InsertOneResult.acknowledged(new BsonObjectId(new ObjectId()))));

        StepVerifier.create(adapter.create(site)).expectNext(site).verifyComplete();
    }

    @Test
    @DisplayName("findById maps the found document back to the domain aggregate")
    void findById() {
        FindPublisher<SiteDocument> publisher = mock(FindPublisher.class);
        when(mongoCollection.find(any(Bson.class))).thenReturn(publisher);
        when(publisher.first()).thenReturn(publisher);
        doAnswer(invocation -> {
            org.reactivestreams.Subscriber<SiteDocument> subscriber = invocation.getArgument(0);
            Flux.just(SitePersistenceMapper.toDocument(site)).subscribe(subscriber);
            return null;
        }).when(publisher).subscribe(any());

        StepVerifier.create(adapter.findById(siteId))
                .assertNext(found -> org.junit.jupiter.api.Assertions.assertEquals(siteId, found.getId()))
                .verifyComplete();
    }

    @Test
    @DisplayName("findAllByOrganisationId applies the optional status filter")
    void findAllByOrganisationId() {
        FindPublisher<SiteDocument> publisher = mock(FindPublisher.class);
        when(mongoCollection.find(any(Bson.class))).thenReturn(publisher);
        doAnswer(invocation -> {
            org.reactivestreams.Subscriber<SiteDocument> subscriber = invocation.getArgument(0);
            Flux.just(SitePersistenceMapper.toDocument(site)).subscribe(subscriber);
            return null;
        }).when(publisher).subscribe(any());

        StepVerifier.create(adapter.findAllByOrganisationId(organisationId, SiteStatus.ACTIVE)).expectNextCount(1).verifyComplete();
        StepVerifier.create(adapter.findAllByOrganisationId(organisationId, null)).expectNextCount(1).verifyComplete();
    }

    @Test
    @DisplayName("updateBasicInfo issues a granular set of every field")
    void updateBasicInfo() {
        when(mongoCollection.updateOne(any(Bson.class), any(Bson.class), any(UpdateOptions.class)))
                .thenReturn(Mono.just(UpdateResult.acknowledged(1, 1L, null)));

        StepVerifier.create(adapter.updateBasicInfo(siteId, "HQ2", "addr2", "city2", "country2", "zip2", "tz2", 3.0, 4.0))
                .verifyComplete();
    }

    @Test
    @DisplayName("updateBasicInfo clears the location when both latitude and longitude are null")
    void updateBasicInfoNoLocation() {
        when(mongoCollection.updateOne(any(Bson.class), any(Bson.class), any(UpdateOptions.class)))
                .thenReturn(Mono.just(UpdateResult.acknowledged(1, 1L, null)));

        StepVerifier.create(adapter.updateBasicInfo(siteId, "HQ2", null, null, null, null, null, null, null))
                .verifyComplete();
    }

    @Test
    @DisplayName("updateBasicInfo also clears the location when only one of latitude/longitude is null")
    void updateBasicInfoPartialCoordinates() {
        when(mongoCollection.updateOne(any(Bson.class), any(Bson.class), any(UpdateOptions.class)))
                .thenReturn(Mono.just(UpdateResult.acknowledged(1, 1L, null)));

        StepVerifier.create(adapter.updateBasicInfo(siteId, "HQ2", null, null, null, null, null, 3.0, null))
                .verifyComplete();
    }

    @Test
    @DisplayName("updateStatus issues a granular status set")
    void updateStatus() {
        when(mongoCollection.updateOne(any(Bson.class), any(Bson.class), any(UpdateOptions.class)))
                .thenReturn(Mono.just(UpdateResult.acknowledged(1, 1L, null)));

        StepVerifier.create(adapter.updateStatus(siteId, SiteStatus.INACTIVE)).verifyComplete();
    }

    @Test
    @DisplayName("addBuilding pushes onto the top-level buildings array")
    void addBuilding() {
        when(mongoCollection.updateOne(any(Bson.class), any(Bson.class), any(UpdateOptions.class)))
                .thenReturn(Mono.just(UpdateResult.acknowledged(1, 1L, null)));

        Building building = new Building(UUID.randomUUID(), "B1", null, List.of());
        StepVerifier.create(adapter.addBuilding(siteId, building)).verifyComplete();
    }

    @Test
    @DisplayName("addRoom pushes onto a matched building's rooms array via an array filter")
    void addRoom() {
        when(mongoCollection.updateOne(any(Bson.class), any(Bson.class), any(UpdateOptions.class)))
                .thenReturn(Mono.just(UpdateResult.acknowledged(1, 1L, null)));

        Room room = new Room(UUID.randomUUID(), "R1", com.thinklab.domain.model.Site.RoomType.OFFICE, List.of());
        StepVerifier.create(adapter.addRoom(siteId, UUID.randomUUID(), room)).verifyComplete();
    }

    @Test
    @DisplayName("addRack pushes onto a matched room's racks array via two array filters")
    void addRack() {
        when(mongoCollection.updateOne(any(Bson.class), any(Bson.class), any(UpdateOptions.class)))
                .thenReturn(Mono.just(UpdateResult.acknowledged(1, 1L, null)));

        Rack rack = new Rack(UUID.randomUUID(), "K1", 42, RackStatus.ACTIVE);
        StepVerifier.create(adapter.addRack(siteId, UUID.randomUUID(), UUID.randomUUID(), rack)).verifyComplete();
    }

    @Test
    @DisplayName("updateRackStatus sets a matched rack's status via three array filters")
    void updateRackStatus() {
        when(mongoCollection.updateOne(any(Bson.class), any(Bson.class), any(UpdateOptions.class)))
                .thenReturn(Mono.just(UpdateResult.acknowledged(1, 1L, null)));

        StepVerifier.create(adapter.updateRackStatus(siteId, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), RackStatus.DECOMMISSIONED))
                .verifyComplete();
    }

    @Test
    @DisplayName("a zero-matched update translates into SiteNotFoundException")
    void zeroMatchedUpdate() {
        when(mongoCollection.updateOne(any(Bson.class), any(Bson.class), any(UpdateOptions.class)))
                .thenReturn(Mono.just(UpdateResult.acknowledged(0, 0L, null)));

        StepVerifier.create(adapter.updateStatus(siteId, SiteStatus.INACTIVE))
                .expectError(SiteNotFoundException.class)
                .verify();
    }
}
