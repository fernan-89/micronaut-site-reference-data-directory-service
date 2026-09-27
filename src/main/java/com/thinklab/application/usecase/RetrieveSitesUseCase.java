package com.thinklab.application.usecase;

import com.thinklab.application.dto.response.SiteResponse;
import com.thinklab.application.mapper.SiteMapper;
import com.thinklab.domain.model.Site.SiteStatus;
import com.thinklab.domain.repository.SiteRepository;
import jakarta.inject.Singleton;
import reactor.core.publisher.Flux;

import java.util.UUID;

/** Use Case for retrieving the tenant-scoped Site collection (BIAN Behavior Qualifier: {@code retrieve}). */
@Singleton
public class RetrieveSitesUseCase {

    private final SiteRepository siteRepository;

    public RetrieveSitesUseCase(SiteRepository siteRepository) {
        this.siteRepository = siteRepository;
    }

    public Flux<SiteResponse> execute(UUID organisationId, SiteStatus status) {
        return siteRepository.findAllByOrganisationId(organisationId, status)
                .map(SiteMapper::toResponse);
    }
}
