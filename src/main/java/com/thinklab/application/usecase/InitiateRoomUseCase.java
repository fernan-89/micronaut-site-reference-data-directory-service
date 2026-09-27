package com.thinklab.application.usecase;

import com.thinklab.application.dto.request.InitiateRoomRequest;
import com.thinklab.application.mapper.SiteMapper;
import com.thinklab.domain.exception.SiteNotFoundException;
import com.thinklab.domain.port.HashServicePort;
import com.thinklab.domain.repository.SiteRepository;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Use Case to add a subordinate Room to a Building (BIAN Behavior Qualifier: {@code room/initiate}).
 *
 * <p>Loads the Site aggregate first and calls {@code Site.addRoom}, which validates the
 * {@code buildingId} actually exists before this use case issues the persistence write. Skipping
 * this pre-load would let an unknown {@code buildingId} silently no-op: the repository's
 * {@code arrayFilters}-targeted update still matches the Site document by {@code _id} even when the
 * filter condition matches zero nested elements, so {@code matchedCount} alone cannot tell a missing
 * building apart from a real update (found live during this journey's own design, not from a bug
 * report - see the Mongo adapter's Javadoc).
 */
@Singleton
public class InitiateRoomUseCase {

    private static final Logger log = LoggerFactory.getLogger(InitiateRoomUseCase.class);

    private final HashServicePort hashServicePort;
    private final SiteRepository siteRepository;

    public InitiateRoomUseCase(HashServicePort hashServicePort, SiteRepository siteRepository) {
        this.hashServicePort = hashServicePort;
        this.siteRepository = siteRepository;
    }

    public Mono<Void> execute(UUID siteId, UUID buildingId, InitiateRoomRequest request) {
        log.info("[USE CASE] Adding room '{}' to building {} of site ID: {}", request.roomName(), buildingId, siteId);

        return siteRepository.findById(siteId)
                .switchIfEmpty(Mono.error(new SiteNotFoundException(siteId)))
                .flatMap(site -> {
                    site.findBuilding(buildingId); // fail fast, before spending a hash-service call
                    return hashServicePort.generateSovereignId("room-creation")
                            .map(roomId -> SiteMapper.toRoom(request, roomId))
                            .flatMap(room -> {
                                site.addRoom(buildingId, room);
                                return siteRepository.addRoom(siteId, buildingId, room);
                            });
                });
    }
}
