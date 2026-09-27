package com.thinklab.infrastructure.adapter.out.persistence.repository;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;
import com.mongodb.client.model.geojson.Point;
import com.mongodb.client.model.geojson.Position;
import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoCollection;
import com.thinklab.domain.exception.SiteNotFoundException;
import com.thinklab.domain.model.Site;
import com.thinklab.domain.model.Site.Building;
import com.thinklab.domain.model.Site.Rack;
import com.thinklab.domain.model.Site.RackStatus;
import com.thinklab.domain.model.Site.Room;
import com.thinklab.domain.model.Site.SiteStatus;
import com.thinklab.domain.repository.SiteRepository;
import com.thinklab.infrastructure.adapter.out.persistence.entity.SiteDocument;
import com.thinklab.infrastructure.adapter.out.persistence.entity.SiteDocument.BuildingDocument;
import com.thinklab.infrastructure.adapter.out.persistence.entity.SiteDocument.RackDocument;
import com.thinklab.infrastructure.adapter.out.persistence.entity.SiteDocument.RoomDocument;
import com.thinklab.infrastructure.adapter.out.persistence.entity.SiteDocument.SitePersistenceMapper;
import io.micronaut.context.annotation.Property;
import jakarta.inject.Singleton;
import org.bson.codecs.configuration.CodecRegistries;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.codecs.pojo.PojoCodecProvider;
import org.bson.conversions.Bson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * MongoDB Reactive Repository Adapter. Implements the pure Domain Port using the low-level
 * Reactive Streams MongoDB Driver and strictly enforces Partial State Mutations (ADR-002): every
 * transition, including the three-level-deep Building/Room/Rack nesting, is a single atomic
 * positional update via {@code arrayFilters} - never a whole-aggregate re-save.
 */
@Singleton
public class SiteMongoRepositoryAdapter implements SiteRepository {

    private static final Logger log = LoggerFactory.getLogger(SiteMongoRepositoryAdapter.class);
    /** Used only when {@code mongodb.uri} names no database. */
    static final String DEFAULT_DATABASE = "thinklab_site_db";
    static final String COLLECTION_NAME = "sites";
    private static final String FIELD_ID = "_id";
    private static final String FIELD_UPDATED_AT = "updatedAt";

    /**
     * The MongoDB driver's default codec registry has no codec for arbitrary POJOs such as
     * {@link SiteDocument}. Without a {@link PojoCodecProvider} every read/write fails with
     * {@code CodecConfigurationException} (lesson learned from the Party Reference Data Directory rollout).
     */
    private static final CodecRegistry POJO_CODEC_REGISTRY = CodecRegistries.fromRegistries(
            MongoClientSettings.getDefaultCodecRegistry(),
            CodecRegistries.fromProviders(PojoCodecProvider.builder().automatic(true).build())
    );

    private final MongoClient mongoClient;
    private final String database;

    public SiteMongoRepositoryAdapter(MongoClient mongoClient, @Property(name = "mongodb.uri") String mongoUri) {
        this.mongoClient = mongoClient;
        String configured = new ConnectionString(Objects.requireNonNull(mongoUri, "mongodb.uri cannot be null.")).getDatabase();
        this.database = configured != null ? configured : DEFAULT_DATABASE;
    }

    private MongoCollection<SiteDocument> getCollection() {
        return mongoClient.getDatabase(database)
                .getCollection(COLLECTION_NAME, SiteDocument.class)
                .withCodecRegistry(POJO_CODEC_REGISTRY);
    }

    @Override
    public Mono<Site> create(Site site) {
        log.debug("[PERSISTENCE] Monolithic create for Site Aggregate: {}", site.getId());

        SiteDocument document = SitePersistenceMapper.toDocument(site);

        return Mono.from(getCollection().insertOne(document))
                .doOnSuccess(result -> log.debug("[PERSISTENCE] Aggregate successfully created in MongoDB"))
                .map(result -> site);
    }

    @Override
    public Mono<Site> findById(UUID id) {
        log.debug("[PERSISTENCE] Fetching Site Aggregate by ID: {}", id);

        return Mono.from(getCollection().find(Filters.eq(FIELD_ID, id)).first())
                .map(SitePersistenceMapper::toDomain);
    }

    @Override
    public Flux<Site> findAllByOrganisationId(UUID organisationId, SiteStatus status) {
        log.debug("[PERSISTENCE] Fetching Sites for organisation {} status {}", organisationId, status);

        Bson filter = status != null
                ? Filters.and(Filters.eq("organisationId", organisationId), Filters.eq("status", status.name()))
                : Filters.eq("organisationId", organisationId);

        return Flux.from(getCollection().find(filter))
                .map(SitePersistenceMapper::toDomain);
    }

    @Override
    public Mono<Void> updateBasicInfo(UUID id, String siteName, String address, String city, String country,
                                       String zipCode, String timezone, Double latitude, Double longitude) {
        log.debug("[PERSISTENCE] Partial Mutation: updateBasicInfo for ID: {}", id);

        Bson update = Updates.combine(
                Updates.set("siteName", siteName),
                Updates.set("address", address),
                Updates.set("city", city),
                Updates.set("country", country),
                Updates.set("zipCode", zipCode),
                Updates.set("timezone", timezone),
                Updates.set("latitude", latitude),
                Updates.set("longitude", longitude),
                Updates.set("location", latitude != null && longitude != null ? new Point(new Position(longitude, latitude)) : null),
                Updates.set(FIELD_UPDATED_AT, Instant.now())
        );

        return executeUpdate(id, update);
    }

    @Override
    public Mono<Void> updateStatus(UUID id, SiteStatus status) {
        log.debug("[PERSISTENCE] Partial Mutation: updateStatus for ID: {}", id);

        Bson update = Updates.combine(
                Updates.set("status", status.name()),
                Updates.set(FIELD_UPDATED_AT, Instant.now())
        );

        return executeUpdate(id, update);
    }

    @Override
    public Mono<Void> addBuilding(UUID id, Building building) {
        log.debug("[PERSISTENCE] Partial Mutation: addBuilding {} for Site {}", building.buildingId(), id);

        BuildingDocument document = toDocument(building);
        Bson update = Updates.combine(
                Updates.push("buildings", document),
                Updates.set(FIELD_UPDATED_AT, Instant.now())
        );

        return executeUpdate(id, update);
    }

    @Override
    public Mono<Void> addRoom(UUID id, UUID buildingId, Room room) {
        log.debug("[PERSISTENCE] Partial Mutation: addRoom {} to Building {} of Site {}", room.roomId(), buildingId, id);

        RoomDocument document = toDocument(room);
        Bson update = Updates.combine(
                Updates.push("buildings.$[b].rooms", document),
                Updates.set(FIELD_UPDATED_AT, Instant.now())
        );
        UpdateOptions options = new UpdateOptions().arrayFilters(List.of(Filters.eq("b.buildingId", buildingId)));

        return executeUpdate(id, update, options);
    }

    @Override
    public Mono<Void> addRack(UUID id, UUID buildingId, UUID roomId, Rack rack) {
        log.debug("[PERSISTENCE] Partial Mutation: addRack {} to Room {} of Site {}", rack.rackId(), roomId, id);

        RackDocument document = toDocument(rack);
        Bson update = Updates.combine(
                Updates.push("buildings.$[b].rooms.$[r].racks", document),
                Updates.set(FIELD_UPDATED_AT, Instant.now())
        );
        UpdateOptions options = new UpdateOptions().arrayFilters(List.of(
                Filters.eq("b.buildingId", buildingId), Filters.eq("r.roomId", roomId)));

        return executeUpdate(id, update, options);
    }

    @Override
    public Mono<Void> updateRackStatus(UUID id, UUID buildingId, UUID roomId, UUID rackId, RackStatus status) {
        log.debug("[PERSISTENCE] Partial Mutation: updateRackStatus {} for Site {}", rackId, id);

        Bson update = Updates.combine(
                Updates.set("buildings.$[b].rooms.$[r].racks.$[rk].status", status.name()),
                Updates.set(FIELD_UPDATED_AT, Instant.now())
        );
        UpdateOptions options = new UpdateOptions().arrayFilters(List.of(
                Filters.eq("b.buildingId", buildingId), Filters.eq("r.roomId", roomId), Filters.eq("rk.rackId", rackId)));

        return executeUpdate(id, update, options);
    }

    private static BuildingDocument toDocument(Building b) {
        return new BuildingDocument(b.buildingId(), b.buildingName(), b.floorCount(), List.of());
    }

    private static RoomDocument toDocument(Room r) {
        return new RoomDocument(r.roomId(), r.roomName(), r.roomType().name(), List.of());
    }

    private static RackDocument toDocument(Rack k) {
        return new RackDocument(k.rackId(), k.rackName(), k.heightU(), k.status().name());
    }

    /** Helper that executes a partial update and translates a zero-match result into a domain error. */
    private Mono<Void> executeUpdate(UUID id, Bson update) {
        return executeUpdate(id, update, new UpdateOptions());
    }

    private Mono<Void> executeUpdate(UUID id, Bson update, UpdateOptions options) {
        return Mono.from(getCollection().updateOne(Filters.eq(FIELD_ID, id), update, options))
                .flatMap(result -> {
                    if (result.getMatchedCount() == 0) {
                        return Mono.error(new SiteNotFoundException(id));
                    }
                    return Mono.empty();
                });
    }
}
