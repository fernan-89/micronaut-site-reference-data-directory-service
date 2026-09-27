package com.thinklab.application.usecase;

import com.thinklab.application.dto.request.InitiateBuildingRequest;
import com.thinklab.application.mapper.SiteMapper;
import com.thinklab.domain.port.HashServicePort;
import com.thinklab.domain.repository.SiteRepository;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Use Case to add a subordinate Building to a Site aggregate (BIAN Behavior Qualifier:
 * {@code building/initiate}). No pre-load needed: {@code addBuilding} is a single-level append and
 * a missing Site is already caught by the repository's zero-matched-document check.
 */
@Singleton
public class InitiateBuildingUseCase {

    private static final Logger log = LoggerFactory.getLogger(InitiateBuildingUseCase.class);

    private final HashServicePort hashServicePort;
    private final SiteRepository siteRepository;

    public InitiateBuildingUseCase(HashServicePort hashServicePort, SiteRepository siteRepository) {
        this.hashServicePort = hashServicePort;
        this.siteRepository = siteRepository;
    }

    public Mono<Void> execute(UUID siteId, InitiateBuildingRequest request) {
        log.info("[USE CASE] Adding building '{}' to site ID: {}", request.buildingName(), siteId);

        return hashServicePort.generateSovereignId("building-creation")
                .map(buildingId -> SiteMapper.toBuilding(request, buildingId))
                .flatMap(building -> siteRepository.addBuilding(siteId, building));
    }
}
