package com.thinklab.application.usecase;

import com.thinklab.application.dto.request.CreateSiteRequest;
import com.thinklab.application.dto.response.SiteResponse;
import com.thinklab.application.mapper.SiteMapper;
import com.thinklab.domain.port.HashServicePort;
import com.thinklab.domain.repository.SiteRepository;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

/**
 * Orchestrates the business flow for Site creation (BIAN Behavior Qualifier: {@code initiate}).
 */
@Singleton
public class InitiateSiteUseCase {

    private static final Logger log = LoggerFactory.getLogger(InitiateSiteUseCase.class);

    private final HashServicePort hashServicePort;
    private final SiteRepository siteRepository;

    public InitiateSiteUseCase(HashServicePort hashServicePort, SiteRepository siteRepository) {
        this.hashServicePort = hashServicePort;
        this.siteRepository = siteRepository;
    }

    public Mono<SiteResponse> execute(CreateSiteRequest request) {
        log.info("[USE CASE] Initiating site creation for organisation: {}", request.organisationId());

        return hashServicePort.generateSovereignId("site-creation")
                .map(sovereignId -> SiteMapper.toDomain(request, sovereignId))
                .flatMap(siteRepository::create)
                .map(saved -> {
                    log.info("[USE CASE] Site successfully created with ID: {}", saved.getId());
                    return SiteMapper.toResponse(saved);
                });
    }
}
