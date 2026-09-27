package com.thinklab.application.usecase;

import com.thinklab.application.dto.request.InitiateBuildingRequest;
import com.thinklab.application.dto.request.InitiateRackRequest;
import com.thinklab.application.dto.request.InitiateRoomRequest;
import com.thinklab.domain.exception.SiteNotFoundException;
import com.thinklab.domain.model.Site;
import com.thinklab.domain.model.Site.Building;
import com.thinklab.domain.model.Site.Rack;
import com.thinklab.domain.model.Site.RackStatus;
import com.thinklab.domain.model.Site.Room;
import com.thinklab.domain.model.Site.RoomType;
import com.thinklab.domain.port.HashServicePort;
import com.thinklab.domain.repository.SiteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NestedResourceUseCaseTest {

    @Mock private SiteRepository siteRepository;
    @Mock private HashServicePort hashServicePort;

    private UUID siteId;
    private UUID buildingId;
    private UUID roomId;
    private Site site;

    @BeforeEach
    void setUp() {
        siteId = UUID.randomUUID();
        buildingId = UUID.randomUUID();
        roomId = UUID.randomUUID();
        site = Site.createNew(siteId, UUID.randomUUID(), "HQ", null, null, null, null, null, null, null);
        site.addBuilding(new Building(buildingId, "B1", null, List.of()));
        site.addRoom(buildingId, new Room(roomId, "R1", RoomType.DATA_CENTER, List.of()));
    }

    @Test
    @DisplayName("InitiateBuildingUseCase generates an id and appends via the repository, without pre-loading")
    void initiateBuilding() {
        UUID newBuildingId = UUID.randomUUID();
        when(hashServicePort.generateSovereignId("building-creation")).thenReturn(Mono.just(newBuildingId));
        when(siteRepository.addBuilding(org.mockito.ArgumentMatchers.eq(siteId), any(Building.class))).thenReturn(Mono.empty());

        StepVerifier.create(new InitiateBuildingUseCase(hashServicePort, siteRepository)
                        .execute(siteId, new InitiateBuildingRequest("B2", 1)))
                .verifyComplete();
    }

    @Test
    @DisplayName("InitiateRoomUseCase loads the Site, validates the buildingId via the domain, then writes")
    void initiateRoom() {
        UUID newRoomId = UUID.randomUUID();
        when(siteRepository.findById(siteId)).thenReturn(Mono.just(site));
        when(hashServicePort.generateSovereignId("room-creation")).thenReturn(Mono.just(newRoomId));
        when(siteRepository.addRoom(org.mockito.ArgumentMatchers.eq(siteId), org.mockito.ArgumentMatchers.eq(buildingId), any(Room.class)))
                .thenReturn(Mono.empty());

        StepVerifier.create(new InitiateRoomUseCase(hashServicePort, siteRepository)
                        .execute(siteId, buildingId, new InitiateRoomRequest("R2", "OFFICE")))
                .verifyComplete();
    }

    @Test
    @DisplayName("InitiateRoomUseCase 404s when the Site itself does not exist")
    void initiateRoomSiteNotFound() {
        when(siteRepository.findById(siteId)).thenReturn(Mono.empty());

        StepVerifier.create(new InitiateRoomUseCase(hashServicePort, siteRepository)
                        .execute(siteId, buildingId, new InitiateRoomRequest("R2", "OFFICE")))
                .expectError(SiteNotFoundException.class)
                .verify();
    }

    @Test
    @DisplayName("InitiateRoomUseCase propagates the domain's not-found when the buildingId is unknown")
    void initiateRoomBuildingNotFound() {
        when(siteRepository.findById(siteId)).thenReturn(Mono.just(site));

        StepVerifier.create(new InitiateRoomUseCase(hashServicePort, siteRepository)
                        .execute(siteId, UUID.randomUUID(), new InitiateRoomRequest("R2", "OFFICE")))
                .expectError(SiteNotFoundException.class)
                .verify();
    }

    @Test
    @DisplayName("InitiateRackUseCase loads the Site, validates buildingId/roomId via the domain, then writes")
    void initiateRack() {
        UUID newRackId = UUID.randomUUID();
        when(siteRepository.findById(siteId)).thenReturn(Mono.just(site));
        when(hashServicePort.generateSovereignId("rack-creation")).thenReturn(Mono.just(newRackId));
        when(siteRepository.addRack(org.mockito.ArgumentMatchers.eq(siteId), org.mockito.ArgumentMatchers.eq(buildingId),
                org.mockito.ArgumentMatchers.eq(roomId), any(Rack.class))).thenReturn(Mono.empty());

        StepVerifier.create(new InitiateRackUseCase(hashServicePort, siteRepository)
                        .execute(siteId, buildingId, roomId, new InitiateRackRequest("K1", 42)))
                .verifyComplete();
    }

    @Test
    @DisplayName("InitiateRackUseCase propagates the domain's not-found when the roomId is unknown")
    void initiateRackRoomNotFound() {
        when(siteRepository.findById(siteId)).thenReturn(Mono.just(site));

        StepVerifier.create(new InitiateRackUseCase(hashServicePort, siteRepository)
                        .execute(siteId, buildingId, UUID.randomUUID(), new InitiateRackRequest("K1", 42)))
                .expectError(SiteNotFoundException.class)
                .verify();
    }

    @Test
    @DisplayName("InitiateRackUseCase 404s when the Site itself does not exist")
    void initiateRackSiteNotFound() {
        when(siteRepository.findById(siteId)).thenReturn(Mono.empty());

        StepVerifier.create(new InitiateRackUseCase(hashServicePort, siteRepository)
                        .execute(siteId, buildingId, roomId, new InitiateRackRequest("K1", 42)))
                .expectError(SiteNotFoundException.class)
                .verify();
    }

    @Test
    @DisplayName("ControlRackUseCase loads the Site, validates the FSM transition, then writes")
    void controlRack() {
        UUID rackId = UUID.randomUUID();
        site.addRack(buildingId, roomId, new Rack(rackId, "K1", 42, RackStatus.ACTIVE));
        when(siteRepository.findById(siteId)).thenReturn(Mono.just(site));
        when(siteRepository.updateRackStatus(siteId, buildingId, roomId, rackId, RackStatus.DECOMMISSIONED)).thenReturn(Mono.empty());

        StepVerifier.create(new ControlRackUseCase(siteRepository)
                        .execute(siteId, buildingId, roomId, rackId, ControlRackUseCase.Action.DECOMMISSION))
                .verifyComplete();
    }

    @Test
    @DisplayName("ControlRackUseCase 404s when the Site itself does not exist")
    void controlRackSiteNotFound() {
        when(siteRepository.findById(siteId)).thenReturn(Mono.empty());

        StepVerifier.create(new ControlRackUseCase(siteRepository)
                        .execute(siteId, buildingId, roomId, UUID.randomUUID(), ControlRackUseCase.Action.ACTIVATE))
                .expectError(SiteNotFoundException.class)
                .verify();
    }

    @Test
    @DisplayName("ControlRackUseCase.Action exposes its target status")
    void actionTargets() {
        org.junit.jupiter.api.Assertions.assertEquals(RackStatus.ACTIVE, ControlRackUseCase.Action.ACTIVATE.targetStatus());
        org.junit.jupiter.api.Assertions.assertEquals(RackStatus.DECOMMISSIONED, ControlRackUseCase.Action.DECOMMISSION.targetStatus());
    }
}
