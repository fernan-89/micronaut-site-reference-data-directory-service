package com.thinklab.infrastructure.adapter.out.persistence.repository;

import com.mongodb.ConnectionString;
import com.mongodb.MongoTimeoutException;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.reactivestreams.client.MongoClient;
import io.micronaut.context.annotation.Property;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.event.ApplicationEventListener;
import io.micronaut.context.event.StartupEvent;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Objects;

/**
 * Creates the geospatial {@code 2dsphere} index on {@code location} and the
 * {@code (organisationId, status)} tenant-listing index on {@code sites} at startup.
 *
 * <p>This adapter uses the driver directly (see {@link SiteMongoRepositoryAdapter}'s Javadoc), so
 * the kit's {@code MongoIndexInitializer} (which reads Micronaut Data {@code @Index}/{@code @Indexes}
 * and only ever creates ascending-value indexes, never a {@code 2dsphere} one) does not see it -
 * same reasoning as {@code AssetIndexInitializer} in it-asset-registry-service.
 *
 * <p>Fail-open: {@code createIndex} is idempotent; if it fails the error is logged and the
 * application still starts. Turn it off with {@code thinklab.mongo.create-indexes=false}, as
 * unit-test contexts without MongoDB do.
 */
@Singleton
@Requires(property = "thinklab.mongo.create-indexes", notEquals = "false")
public class SiteIndexInitializer implements ApplicationEventListener<StartupEvent> {

    static final String GEO_INDEX = "location_2dsphere";
    static final String TENANT_STATUS_INDEX = "organisationId_1_status_1";

    private static final Logger log = LoggerFactory.getLogger(SiteIndexInitializer.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    private final MongoClient mongoClient;
    private final String database;
    private final Duration timeout;

    @Inject
    public SiteIndexInitializer(MongoClient mongoClient, @Property(name = "mongodb.uri") String mongoUri) {
        this(mongoClient, mongoUri, TIMEOUT);
    }

    /** Test seam: how long to wait for the server. */
    SiteIndexInitializer(MongoClient mongoClient, String mongoUri, Duration timeout) {
        this.mongoClient = Objects.requireNonNull(mongoClient, "Infrastructure constraint violated: MongoClient cannot be null.");
        String configured = new ConnectionString(Objects.requireNonNull(mongoUri, "mongodb.uri cannot be null.")).getDatabase();
        this.database = configured != null ? configured : SiteMongoRepositoryAdapter.DEFAULT_DATABASE;
        this.timeout = timeout;
    }

    @Override
    public void onApplicationEvent(StartupEvent event) {
        Objects.requireNonNull(event, "Application constraint violated: StartupEvent cannot be null.");
        createIndex(new Document("location", "2dsphere"), new IndexOptions().name(GEO_INDEX).sparse(true), GEO_INDEX);
        createIndex(new Document("organisationId", 1).append("status", 1), new IndexOptions().name(TENANT_STATUS_INDEX), TENANT_STATUS_INDEX);
    }

    private void createIndex(Document keys, IndexOptions options, String name) {
        try {
            Mono.from(mongoClient.getDatabase(database).getCollection(SiteMongoRepositoryAdapter.COLLECTION_NAME)
                    .createIndex(keys, options)).block(timeout);
            log.info("[MONGO_INDEXES] Ensured index [{}] {} on [{}.{}]", name, keys.toJson(), database, SiteMongoRepositoryAdapter.COLLECTION_NAME);
        } catch (MongoTimeoutException e) {
            log.error("[MONGO_INDEXES] MongoDB unreachable; index [{}] was not created. Reason: {}", name, e.getMessage());
        } catch (RuntimeException e) {
            log.error("[MONGO_INDEXES] Could not create index [{}] on [{}.{}]: {}", name, database, SiteMongoRepositoryAdapter.COLLECTION_NAME, e.getMessage());
        }
    }
}
