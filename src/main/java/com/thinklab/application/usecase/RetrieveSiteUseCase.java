package com.thinklab.application.usecase;

import com.thinklab.application.dto.response.SiteResponse;
import com.thinklab.application.mapper.SiteMapper;
import com.thinklab.domain.exception.SiteNotFoundException;
import com.thinklab.domain.repository.SiteRepository;
import jakarta.inject.Singleton;
import reactor.core.publisher.Mono;

import java.util.UUID;

/** Use Case for retrieving a single Site by its sovereign ID (BIAN Behavior Qualifier: {@code retrieve}). */
@Singleton
public class RetrieveSiteUseCase {

    private final SiteRepository siteRepository;

    public RetrieveSiteUseCase(SiteRepository siteRepository) {
        this.siteRepository = siteRepository;
    }

    public Mono<SiteResponse> execute(UUID id) {
        return siteRepository.findById(id)
                .switchIfEmpty(Mono.error(new SiteNotFoundException(id)))
                .map(SiteMapper::toResponse);
    }
}
