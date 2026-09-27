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
import com.thinklab.domain.exception.SiteNotFoundException;
import com.thinklab.domain.model.Site.SiteStatus;
import io.micronaut.http.HttpStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SiteControllerTest {

    private static final String EXECUTOR = "ops-admin";

    @Mock private InitiateSiteUseCase initiateSiteUseCase;
    @Mock private RetrieveSiteUseCase retrieveSiteUseCase;
    @Mock private RetrieveSitesUseCase retrieveSitesUseCase;
    @Mock private UpdateSiteUseCase updateSiteUseCase;
    @Mock private ControlSiteUseCase controlSiteUseCase;
    @Mock private InitiateBuildingUseCase initiateBuildingUseCase;
    @Mock private InitiateRoomUseCase initiateRoomUseCase;
    @Mock private InitiateRackUseCase initiateRackUseCase;
    @Mock private ControlRackUseCase controlRackUseCase;

    @InjectMocks
    private SiteController controller;

    private UUID siteId;
    private UUID organisationId;
    private UUID buildingId;
    private UUID roomId;
    private UUID rackId;
    private SiteResponse sample;

    @BeforeEach
    void setUp() {
        siteId = UUID.randomUUID();
        organisationId = UUID.randomUUID();
        buildingId = UUID.randomUUID();
        roomId = UUID.randomUUID();
        rackId = UUID.randomUUID();
        sample = new SiteResponse(siteId, organisationId, "HQ", "addr", "city", "country", "zip", "tz", 1.0, 2.0,
                "ACTIVE", List.of(), Instant.now(), Instant.now());
    }

    @Test
    @DisplayName("initiate returns 201 Created")
    void initiate() {
        CreateSiteRequest request = new CreateSiteRequest(organisationId, "HQ", "addr", "city", "country", "zip", "tz", 1.0, 2.0);
        when(initiateSiteUseCase.execute(request)).thenReturn(Mono.just(sample));

        StepVerifier.create(controller.initiate(EXECUTOR, request))
                .assertNext(response -> assertEquals(HttpStatus.CREATED, response.getStatus()))
                .verifyComplete();
    }

    @Test
    @DisplayName("retrieveById returns 200 OK")
    void retrieveById() {
        when(retrieveSiteUseCase.execute(siteId)).thenReturn(Mono.just(sample));

        StepVerifier.create(controller.retrieveById(siteId))
                .assertNext(response -> assertEquals(HttpStatus.OK, response.getStatus()))
                .verifyComplete();
    }

    @Test
    @DisplayName("retrieveById propagates a not-found error")
    void retrieveByIdNotFound() {
        when(retrieveSiteUseCase.execute(siteId)).thenReturn(Mono.error(new SiteNotFoundException(siteId)));

        StepVerifier.create(controller.retrieveById(siteId))
                .expectError(SiteNotFoundException.class)
                .verify();
    }

    @Test
    @DisplayName("retrieveAll scopes by tenant and forwards the status filter")
    void retrieveAll() {
        when(retrieveSitesUseCase.execute(organisationId, SiteStatus.ACTIVE)).thenReturn(Flux.just(sample));

        StepVerifier.create(controller.retrieveAll(organisationId.toString(), SiteStatus.ACTIVE))
                .assertNext(list -> assertEquals(1, list.size()))
                .verifyComplete();
    }

    @Test
    @DisplayName("retrieveAll rejects a malformed tenant header")
    void retrieveAllMalformedTenant() {
        StepVerifier.create(controller.retrieveAll("not-a-uuid", null))
                .expectError(IllegalArgumentException.class)
                .verify();
    }

    @Test
    @DisplayName("update returns 204 No Content")
    void update() {
        UpdateSiteRequest request = new UpdateSiteRequest("HQ 2", "addr", "city", "country", "zip", "tz", 1.0, 2.0);
        when(updateSiteUseCase.execute(siteId, request)).thenReturn(Mono.empty());

        StepVerifier.create(controller.update(siteId, EXECUTOR, request))
                .assertNext(response -> assertEquals(HttpStatus.NO_CONTENT, response.getStatus()))
                .verifyComplete();
    }

    @Test
    @DisplayName("control/activate and control/deactivate dispatch the matching action")
    void control() {
        when(controlSiteUseCase.execute(siteId, ControlSiteUseCase.Action.ACTIVATE)).thenReturn(Mono.empty());
        when(controlSiteUseCase.execute(siteId, ControlSiteUseCase.Action.DEACTIVATE)).thenReturn(Mono.empty());

        StepVerifier.create(controller.controlActivate(siteId, EXECUTOR)).expectNextCount(1).verifyComplete();
        StepVerifier.create(controller.controlDeactivate(siteId, EXECUTOR)).expectNextCount(1).verifyComplete();
        verify(controlSiteUseCase).execute(siteId, ControlSiteUseCase.Action.ACTIVATE);
        verify(controlSiteUseCase).execute(siteId, ControlSiteUseCase.Action.DEACTIVATE);
    }

    @Test
    @DisplayName("building/initiate returns 201 Created")
    void initiateBuilding() {
        InitiateBuildingRequest request = new InitiateBuildingRequest("B1", 2);
        when(initiateBuildingUseCase.execute(siteId, request)).thenReturn(Mono.empty());

        StepVerifier.create(controller.initiateBuilding(siteId, EXECUTOR, request))
                .assertNext(response -> assertEquals(HttpStatus.CREATED, response.getStatus()))
                .verifyComplete();
    }

    @Test
    @DisplayName("building/retrieve returns the aggregate's buildings")
    void retrieveBuildings() {
        when(retrieveSiteUseCase.execute(siteId)).thenReturn(Mono.just(sample));

        StepVerifier.create(controller.retrieveBuildings(siteId))
                .assertNext(response -> assertEquals(HttpStatus.OK, response.getStatus()))
                .verifyComplete();
    }

    @Test
    @DisplayName("room/initiate returns 201 Created")
    void initiateRoom() {
        InitiateRoomRequest request = new InitiateRoomRequest("R1", "OFFICE");
        when(initiateRoomUseCase.execute(siteId, buildingId, request)).thenReturn(Mono.empty());

        StepVerifier.create(controller.initiateRoom(siteId, buildingId, EXECUTOR, request))
                .assertNext(response -> assertEquals(HttpStatus.CREATED, response.getStatus()))
                .verifyComplete();
    }

    @Test
    @DisplayName("rack/initiate returns 201 Created")
    void initiateRack() {
        InitiateRackRequest request = new InitiateRackRequest("K1", 42);
        when(initiateRackUseCase.execute(siteId, buildingId, roomId, request)).thenReturn(Mono.empty());

        StepVerifier.create(controller.initiateRack(siteId, buildingId, roomId, EXECUTOR, request))
                .assertNext(response -> assertEquals(HttpStatus.CREATED, response.getStatus()))
                .verifyComplete();
    }

    @Test
    @DisplayName("rack/control/activate and .../decommission dispatch the matching action")
    void controlRack() {
        when(controlRackUseCase.execute(siteId, buildingId, roomId, rackId, ControlRackUseCase.Action.ACTIVATE)).thenReturn(Mono.empty());
        when(controlRackUseCase.execute(siteId, buildingId, roomId, rackId, ControlRackUseCase.Action.DECOMMISSION)).thenReturn(Mono.empty());

        StepVerifier.create(controller.controlRackActivate(siteId, buildingId, roomId, rackId, EXECUTOR)).expectNextCount(1).verifyComplete();
        StepVerifier.create(controller.controlRackDecommission(siteId, buildingId, roomId, rackId, EXECUTOR)).expectNextCount(1).verifyComplete();
    }
}
