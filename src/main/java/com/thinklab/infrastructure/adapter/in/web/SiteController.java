package com.thinklab.infrastructure.adapter.in.web;

import com.thinklab.application.dto.request.CreateSiteRequest;
import com.thinklab.application.dto.request.InitiateBuildingRequest;
import com.thinklab.application.dto.request.InitiateRackRequest;
import com.thinklab.application.dto.request.InitiateRoomRequest;
import com.thinklab.application.dto.request.UpdateSiteRequest;
import com.thinklab.application.dto.response.SiteResponse;
import com.thinklab.application.usecase.ControlRackUseCase;
import com.thinklab.application.usecase.ControlSiteUseCase;
import com.thinklab.application.usecase.InitiateBuildingUseCase;
import com.thinklab.application.usecase.InitiateRackUseCase;
import com.thinklab.application.usecase.InitiateRoomUseCase;
import com.thinklab.application.usecase.InitiateSiteUseCase;
import com.thinklab.application.usecase.RetrieveSiteUseCase;
import com.thinklab.application.usecase.RetrieveSitesUseCase;
import com.thinklab.application.usecase.UpdateSiteUseCase;
import com.thinklab.domain.model.Site.SiteStatus;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Header;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Put;
import io.micronaut.http.annotation.QueryValue;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

/**
 * Inbound Web Adapter for the {@code site-reference-data-directory} Service Domain.
 *
 * <p><b>BIAN-Aligned Resource Model (ADR-013):</b> {@link com.thinklab.domain.model.Site} is the
 * Control Record; {@code Building}/{@code Room}/{@code Rack} are subordinate, individually
 * addressable Behavior Qualifier Instance Records nested three levels deep. Every route follows
 * {@code /site-reference-data-directory/v1/{control-record-id}/{behavior-qualifier}}. There is no
 * {@code DELETE}: {@code control/deactivate} is a terminal, soft status transition, never a
 * physical deletion.
 *
 * <p><b>Header-Sourced Forensics:</b> every mutation requires the {@code X-Executor} header; the
 * tenant-scoped collection endpoint requires {@code X-Tenant-Id} (the owning Organisation).
 */
@Controller("/site-reference-data-directory/v1")
public class SiteController {

    private static final Logger log = LoggerFactory.getLogger(SiteController.class);
    private static final String EXECUTOR_HEADER = "X-Executor";
    private static final String TENANT_HEADER = "X-Tenant-Id";

    private final InitiateSiteUseCase initiateSiteUseCase;
    private final RetrieveSiteUseCase retrieveSiteUseCase;
    private final RetrieveSitesUseCase retrieveSitesUseCase;
    private final UpdateSiteUseCase updateSiteUseCase;
    private final ControlSiteUseCase controlSiteUseCase;
    private final InitiateBuildingUseCase initiateBuildingUseCase;
    private final InitiateRoomUseCase initiateRoomUseCase;
    private final InitiateRackUseCase initiateRackUseCase;
    private final ControlRackUseCase controlRackUseCase;

    public SiteController(
            InitiateSiteUseCase initiateSiteUseCase,
            RetrieveSiteUseCase retrieveSiteUseCase,
            RetrieveSitesUseCase retrieveSitesUseCase,
            UpdateSiteUseCase updateSiteUseCase,
            ControlSiteUseCase controlSiteUseCase,
            InitiateBuildingUseCase initiateBuildingUseCase,
            InitiateRoomUseCase initiateRoomUseCase,
            InitiateRackUseCase initiateRackUseCase,
            ControlRackUseCase controlRackUseCase
    ) {
        this.initiateSiteUseCase = initiateSiteUseCase;
        this.retrieveSiteUseCase = retrieveSiteUseCase;
        this.retrieveSitesUseCase = retrieveSitesUseCase;
        this.updateSiteUseCase = updateSiteUseCase;
        this.controlSiteUseCase = controlSiteUseCase;
        this.initiateBuildingUseCase = initiateBuildingUseCase;
        this.initiateRoomUseCase = initiateRoomUseCase;
        this.initiateRackUseCase = initiateRackUseCase;
        this.controlRackUseCase = controlRackUseCase;
    }

    /** Behavior Qualifier: {@code initiate}. Creates a new Site Control Record. */
    @Post("/initiate")
    public Mono<HttpResponse<SiteResponse>> initiate(
            @Header(EXECUTOR_HEADER) @NotBlank String executor,
            @Body @Valid CreateSiteRequest request
    ) {
        log.info("[ACTION: INITIATE_SITE] [EXECUTOR: {}] Received request to create site: {}", executor, request.siteName());

        return initiateSiteUseCase.execute(request).map(HttpResponse::created);
    }

    /** Behavior Qualifier: {@code retrieve}. Fetches a single Site by UUID. */
    @Get("/{id}/retrieve")
    public Mono<HttpResponse<SiteResponse>> retrieveById(@PathVariable UUID id) {
        log.info("[ACTION: RETRIEVE_SITE] Received request to get site by ID: {}", id);

        return retrieveSiteUseCase.execute(id).map(HttpResponse::ok);
    }

    /** Behavior Qualifier: {@code retrieve} (collection). Lists Sites scoped to a tenant. */
    @Get("/retrieve")
    public Mono<List<SiteResponse>> retrieveAll(
            @Header(TENANT_HEADER) @NotBlank String tenantId,
            @QueryValue @Nullable SiteStatus status
    ) {
        log.info("[ACTION: RETRIEVE_SITES] Received request to list sites for organisation: {} status: {}", tenantId, status);

        return Mono.defer(() -> retrieveSitesUseCase.execute(UUID.fromString(tenantId), status).collectList());
    }

    /** Behavior Qualifier: {@code update}. Updates basic Site info. */
    @Put("/{id}/update")
    public Mono<HttpResponse<Void>> update(
            @PathVariable UUID id,
            @Header(EXECUTOR_HEADER) @NotBlank String executor,
            @Body @Valid UpdateSiteRequest request
    ) {
        log.info("[ACTION: UPDATE_SITE] [EXECUTOR: {}] Received request to update site info for ID: {}", executor, id);

        return updateSiteUseCase.execute(id, request).thenReturn(HttpResponse.noContent());
    }

    /** Behavior Qualifier: {@code control/activate}. */
    @Put("/{id}/control/activate")
    public Mono<HttpResponse<Void>> controlActivate(@PathVariable UUID id, @Header(EXECUTOR_HEADER) @NotBlank String executor) {
        log.info("[ACTION: CONTROL_SITE] [EXECUTOR: {}] activate for ID: {}", executor, id);

        return controlSiteUseCase.execute(id, ControlSiteUseCase.Action.ACTIVATE).thenReturn(HttpResponse.noContent());
    }

    /** Behavior Qualifier: {@code control/deactivate}. Terminal, soft — replaces a physical DELETE. */
    @Put("/{id}/control/deactivate")
    public Mono<HttpResponse<Void>> controlDeactivate(@PathVariable UUID id, @Header(EXECUTOR_HEADER) @NotBlank String executor) {
        log.info("[ACTION: CONTROL_SITE] [EXECUTOR: {}] deactivate for ID: {}", executor, id);

        return controlSiteUseCase.execute(id, ControlSiteUseCase.Action.DEACTIVATE).thenReturn(HttpResponse.noContent());
    }

    /** Behavior Qualifier: {@code building/initiate}. */
    @Post("/{id}/building/initiate")
    public Mono<HttpResponse<Void>> initiateBuilding(
            @PathVariable UUID id,
            @Header(EXECUTOR_HEADER) @NotBlank String executor,
            @Body @Valid InitiateBuildingRequest request
    ) {
        log.info("[ACTION: INITIATE_BUILDING] [EXECUTOR: {}] Received request to add building for site ID: {}", executor, id);

        return initiateBuildingUseCase.execute(id, request).thenReturn(HttpResponse.status(HttpStatus.CREATED));
    }

    /** Behavior Qualifier: {@code building/retrieve} (collection). Lists all buildings of a Site. */
    @Get("/{id}/building/retrieve")
    public Mono<HttpResponse<List<SiteResponse.BuildingResponse>>> retrieveBuildings(@PathVariable UUID id) {
        log.info("[ACTION: RETRIEVE_BUILDINGS] Received request to list buildings for site ID: {}", id);

        return retrieveSiteUseCase.execute(id).map(response -> HttpResponse.ok(response.buildings()));
    }

    /** Behavior Qualifier: {@code building/{buildingId}/room/initiate}. */
    @Post("/{id}/building/{buildingId}/room/initiate")
    public Mono<HttpResponse<Void>> initiateRoom(
            @PathVariable UUID id,
            @PathVariable UUID buildingId,
            @Header(EXECUTOR_HEADER) @NotBlank String executor,
            @Body @Valid InitiateRoomRequest request
    ) {
        log.info("[ACTION: INITIATE_ROOM] [EXECUTOR: {}] Received request to add room to building {} of site ID: {}", executor, buildingId, id);

        return initiateRoomUseCase.execute(id, buildingId, request).thenReturn(HttpResponse.status(HttpStatus.CREATED));
    }

    /** Behavior Qualifier: {@code building/{buildingId}/room/{roomId}/rack/initiate}. */
    @Post("/{id}/building/{buildingId}/room/{roomId}/rack/initiate")
    public Mono<HttpResponse<Void>> initiateRack(
            @PathVariable UUID id,
            @PathVariable UUID buildingId,
            @PathVariable UUID roomId,
            @Header(EXECUTOR_HEADER) @NotBlank String executor,
            @Body @Valid InitiateRackRequest request
    ) {
        log.info("[ACTION: INITIATE_RACK] [EXECUTOR: {}] Received request to add rack to room {} of site ID: {}", executor, roomId, id);

        return initiateRackUseCase.execute(id, buildingId, roomId, request).thenReturn(HttpResponse.status(HttpStatus.CREATED));
    }

    /** Behavior Qualifier: {@code .../rack/{rackId}/control/activate}. */
    @Put("/{id}/building/{buildingId}/room/{roomId}/rack/{rackId}/control/activate")
    public Mono<HttpResponse<Void>> controlRackActivate(
            @PathVariable UUID id, @PathVariable UUID buildingId, @PathVariable UUID roomId, @PathVariable UUID rackId,
            @Header(EXECUTOR_HEADER) @NotBlank String executor
    ) {
        log.info("[ACTION: CONTROL_RACK] [EXECUTOR: {}] activate for rack {} of site ID: {}", executor, rackId, id);

        return controlRackUseCase.execute(id, buildingId, roomId, rackId, ControlRackUseCase.Action.ACTIVATE)
                .thenReturn(HttpResponse.noContent());
    }

    /** Behavior Qualifier: {@code .../rack/{rackId}/control/decommission}. */
    @Put("/{id}/building/{buildingId}/room/{roomId}/rack/{rackId}/control/decommission")
    public Mono<HttpResponse<Void>> controlRackDecommission(
            @PathVariable UUID id, @PathVariable UUID buildingId, @PathVariable UUID roomId, @PathVariable UUID rackId,
            @Header(EXECUTOR_HEADER) @NotBlank String executor
    ) {
        log.info("[ACTION: CONTROL_RACK] [EXECUTOR: {}] decommission for rack {} of site ID: {}", executor, rackId, id);

        return controlRackUseCase.execute(id, buildingId, roomId, rackId, ControlRackUseCase.Action.DECOMMISSION)
                .thenReturn(HttpResponse.noContent());
    }
}
