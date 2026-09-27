package com.thinklab.domain.repository;

import com.thinklab.domain.model.Site;
import com.thinklab.domain.model.Site.Building;
import com.thinklab.domain.model.Site.Rack;
import com.thinklab.domain.model.Site.RackStatus;
import com.thinklab.domain.model.Site.Room;
import com.thinklab.domain.model.Site.SiteStatus;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Outbound Port for Site persistence operations (Site Reference Data Directory Service Domain).
 *
 * <p>ARCHITECTURAL RULE: Partial State Mutations (ADR-002, same rule {@code OrganisationRepository}
 * and {@code AssetRepository} already follow). {@link #create(Site)} is the only whole-document
 * write; every other mutation - including three-level-deep nested Building/Room/Rack changes - is a
 * granular, positionally-targeted update, never a full aggregate re-save. There is no
 * {@code deleteById}: {@link #updateStatus} moves a Site to {@link SiteStatus#INACTIVE} instead of a
 * physical delete, matching every other Service Domain in this platform.
 */
public interface SiteRepository {

    Mono<Site> create(Site site);

    Mono<Site> findById(UUID id);

    /**
     * Tenant-scoped listing of Sites belonging to a given Organisation.
     *
     * @param organisationId the tenant boundary
     * @param status         optional status filter ({@code null} = any)
     */
    Flux<Site> findAllByOrganisationId(UUID organisationId, SiteStatus status);

    Mono<Void> updateBasicInfo(UUID id, String siteName, String address, String city, String country,
                                String zipCode, String timezone, Double latitude, Double longitude);

    Mono<Void> updateStatus(UUID id, SiteStatus status);

    Mono<Void> addBuilding(UUID id, Building building);

    Mono<Void> addRoom(UUID id, UUID buildingId, Room room);

    Mono<Void> addRack(UUID id, UUID buildingId, UUID roomId, Rack rack);

    Mono<Void> updateRackStatus(UUID id, UUID buildingId, UUID roomId, UUID rackId, RackStatus status);
}
