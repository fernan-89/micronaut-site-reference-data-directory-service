package com.thinklab.application.usecase;

import com.thinklab.application.dto.request.UpdateSiteRequest;
import com.thinklab.domain.repository.SiteRepository;
import com.thinklab.domain.exception.SiteNotFoundException;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.UUID;

/** Use Case to update basic Site information (BIAN Behavior Qualifier: {@code update}). */
@Singleton
public class UpdateSiteUseCase {

    private static final Logger log = LoggerFactory.getLogger(UpdateSiteUseCase.class);

    private final SiteRepository siteRepository;

    public UpdateSiteUseCase(SiteRepository siteRepository) {
        this.siteRepository = siteRepository;
    }

    public Mono<Void> execute(UUID id, UUID organisationId, UpdateSiteRequest request) {
        log.info("[USE CASE] Updating site basic info for ID: {}", id);

        // The site has to be the caller's before anything is written (its organisation never changes, so checking first is enough).
        return siteRepository.findById(id, organisationId)
                .switchIfEmpty(Mono.error(new SiteNotFoundException(id)))
                .then(Mono.defer(() -> siteRepository.updateBasicInfo(id, request.siteName(), request.address(), request.city(),
                request.country(), request.zipCode(), request.timezone(), request.latitude(), request.longitude())));
    }
}
