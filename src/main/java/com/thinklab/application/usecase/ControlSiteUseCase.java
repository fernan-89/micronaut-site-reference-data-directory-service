package com.thinklab.application.usecase;

import com.thinklab.domain.exception.SiteNotFoundException;
import com.thinklab.domain.model.Site.SiteStatus;
import com.thinklab.domain.repository.SiteRepository;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Use Case governing the Site lifecycle (BIAN Behavior Qualifier: {@code control}): {@link Action#ACTIVATE}
 * and {@link Action#DEACTIVATE}. There is no physical DELETE - a Site is never removed from the
 * system of record, only deactivated, matching every other Service Domain in this platform.
 *
 * <p>Loads the aggregate first and delegates the transition to {@link SiteStatus#validateTransitionTo}
 * before issuing the granular partial update, so an illegal move (e.g. activating an already-ACTIVE
 * Site) is rejected by the domain model rather than silently applied by a direct write.
 */
@Singleton
public class ControlSiteUseCase {

    private static final Logger log = LoggerFactory.getLogger(ControlSiteUseCase.class);

    private final SiteRepository siteRepository;

    public ControlSiteUseCase(SiteRepository siteRepository) {
        this.siteRepository = siteRepository;
    }

    public Mono<Void> execute(UUID id, Action action) {
        log.info("[USE CASE] Controlling site lifecycle: {} for ID: {}", action, id);

        return siteRepository.findById(id)
                .switchIfEmpty(Mono.error(new SiteNotFoundException(id)))
                .flatMap(site -> {
                    site.changeStatus(action.targetStatus());
                    return siteRepository.updateStatus(id, action.targetStatus());
                });
    }

    public enum Action {
        ACTIVATE(SiteStatus.ACTIVE),
        DEACTIVATE(SiteStatus.INACTIVE);

        private final SiteStatus targetStatus;

        Action(SiteStatus targetStatus) {
            this.targetStatus = targetStatus;
        }

        public SiteStatus targetStatus() {
            return targetStatus;
        }
    }
}
