package com.thinklab.application.usecase;

import com.thinklab.domain.exception.SiteNotFoundException;
import com.thinklab.domain.model.Site.RackStatus;
import com.thinklab.domain.repository.SiteRepository;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Use Case governing an individual Rack's lifecycle (BIAN Behavior Qualifier:
 * {@code rack/control}): {@link Action#ACTIVATE} and {@link Action#DECOMMISSION}. Loads the Site
 * aggregate first so the FSM transition and the buildingId/roomId/rackId existence are both
 * validated by the domain model before the granular write - same reasoning as
 * {@link ControlSiteUseCase} and {@link InitiateRoomUseCase}.
 */
@Singleton
public class ControlRackUseCase {

    private static final Logger log = LoggerFactory.getLogger(ControlRackUseCase.class);

    private final SiteRepository siteRepository;

    public ControlRackUseCase(SiteRepository siteRepository) {
        this.siteRepository = siteRepository;
    }

    public Mono<Void> execute(UUID siteId, UUID buildingId, UUID roomId, UUID rackId, Action action) {
        log.info("[USE CASE] Controlling rack lifecycle: {} for rack {} of site ID: {}", action, rackId, siteId);

        return siteRepository.findById(siteId)
                .switchIfEmpty(Mono.error(new SiteNotFoundException(siteId)))
                .flatMap(site -> {
                    site.changeRackStatus(buildingId, roomId, rackId, action.targetStatus());
                    return siteRepository.updateRackStatus(siteId, buildingId, roomId, rackId, action.targetStatus());
                });
    }

    public enum Action {
        ACTIVATE(RackStatus.ACTIVE),
        DECOMMISSION(RackStatus.DECOMMISSIONED);

        private final RackStatus targetStatus;

        Action(RackStatus targetStatus) {
            this.targetStatus = targetStatus;
        }

        public RackStatus targetStatus() {
            return targetStatus;
        }
    }
}
