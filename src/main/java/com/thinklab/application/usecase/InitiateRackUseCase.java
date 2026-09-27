package com.thinklab.application.usecase;

import com.thinklab.application.dto.request.InitiateRackRequest;
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
 * Use Case to add a subordinate Rack to a Room (BIAN Behavior Qualifier: {@code rack/initiate}).
 * Loads the Site aggregate first for the same reason {@link InitiateRoomUseCase} does - see its
 * Javadoc.
 */
@Singleton
public class InitiateRackUseCase {

    private static final Logger log = LoggerFactory.getLogger(InitiateRackUseCase.class);

    private final HashServicePort hashServicePort;
    private final SiteRepository siteRepository;

    public InitiateRackUseCase(HashServicePort hashServicePort, SiteRepository siteRepository) {
        this.hashServicePort = hashServicePort;
        this.siteRepository = siteRepository;
    }

    public Mono<Void> execute(UUID siteId, UUID buildingId, UUID roomId, InitiateRackRequest request) {
        log.info("[USE CASE] Adding rack '{}' to room {} of site ID: {}", request.rackName(), roomId, siteId);

        return siteRepository.findById(siteId)
                .switchIfEmpty(Mono.error(new SiteNotFoundException(siteId)))
                .flatMap(site -> {
                    site.findRoom(site.findBuilding(buildingId), roomId); // fail fast, before spending a hash-service call
                    return hashServicePort.generateSovereignId("rack-creation")
                            .map(rackId -> SiteMapper.toRack(request, rackId))
                            .flatMap(rack -> {
                                site.addRack(buildingId, roomId, rack);
                                return siteRepository.addRack(siteId, buildingId, roomId, rack);
                            });
                });
    }
}
