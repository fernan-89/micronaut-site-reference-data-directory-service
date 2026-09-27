package com.thinklab.infrastructure.adapter.out.persistence.repository;

import com.mongodb.MongoTimeoutException;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoCollection;
import com.mongodb.reactivestreams.client.MongoDatabase;
import io.micronaut.context.event.StartupEvent;
import org.bson.Document;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SiteIndexInitializerTest {

    private final StartupEvent startup = mock(StartupEvent.class);

    @SuppressWarnings("unchecked")
    private MongoCollection<Document> collectionIn(MongoClient client, String database) {
        MongoDatabase mongoDatabase = mock(MongoDatabase.class);
        MongoCollection<Document> collection = mock(MongoCollection.class);
        when(client.getDatabase(database)).thenReturn(mongoDatabase);
        when(mongoDatabase.getCollection("sites")).thenReturn(collection);
        return collection;
    }

    @Test
    @DisplayName("startup creates both the geospatial and the tenant-status index, in the database named by mongodb.uri")
    void createsBothIndexes() {
        MongoClient client = mock(MongoClient.class);
        MongoCollection<Document> collection = collectionIn(client, "tenant_sites");
        when(collection.createIndex(any(), any(IndexOptions.class))).thenReturn(Mono.just("ok"));

        new SiteIndexInitializer(client, "mongodb://mongo:27017/tenant_sites").onApplicationEvent(startup);

        ArgumentCaptor<Document> keys = ArgumentCaptor.forClass(Document.class);
        ArgumentCaptor<IndexOptions> options = ArgumentCaptor.forClass(IndexOptions.class);
        verify(collection, times(2)).createIndex(keys.capture(), options.capture());

        List<Document> capturedKeys = keys.getAllValues();
        List<IndexOptions> capturedOptions = options.getAllValues();
        assertEquals(new Document("location", "2dsphere"), capturedKeys.get(0));
        assertTrue(capturedOptions.get(0).isSparse());
        assertEquals(SiteIndexInitializer.GEO_INDEX, capturedOptions.get(0).getName());
        assertEquals(new Document("organisationId", 1).append("status", 1), capturedKeys.get(1));
        assertEquals(SiteIndexInitializer.TENANT_STATUS_INDEX, capturedOptions.get(1).getName());
    }

    @Test
    @DisplayName("a URI without a database uses the service default")
    void defaultDatabase() {
        MongoClient client = mock(MongoClient.class);
        MongoCollection<Document> collection = collectionIn(client, SiteMongoRepositoryAdapter.DEFAULT_DATABASE);
        when(collection.createIndex(any(), any(IndexOptions.class))).thenReturn(Mono.just("ok"));

        new SiteIndexInitializer(client, "mongodb://mongo:27017").onApplicationEvent(startup);

        verify(collection, times(2)).createIndex(any(), any(IndexOptions.class));
    }

    @Test
    @DisplayName("fail-open: an unreachable server or a rejected index is logged, never propagated")
    void failOpen() {
        MongoClient client = mock(MongoClient.class);
        MongoCollection<Document> collection = collectionIn(client, "sites_db");
        when(collection.createIndex(any(), any(IndexOptions.class)))
                .thenReturn(Mono.error(new MongoTimeoutException("no server")))
                .thenReturn(Mono.error(new IllegalStateException("rejected")));
        SiteIndexInitializer initializer = new SiteIndexInitializer(client, "mongodb://mongo:27017/sites_db", Duration.ofSeconds(1));

        assertDoesNotThrow(() -> initializer.onApplicationEvent(startup));
    }

    @Test
    @DisplayName("collaborators, mongodb.uri and the startup event are null-checked")
    void guards() {
        MongoClient client = mock(MongoClient.class);
        assertThrows(NullPointerException.class, () -> new SiteIndexInitializer(null, "mongodb://mongo:27017/a"));
        assertThrows(NullPointerException.class, () -> new SiteIndexInitializer(client, null));
        assertThrows(NullPointerException.class, () -> new SiteIndexInitializer(client, "mongodb://mongo:27017/a").onApplicationEvent(null));
    }
}
