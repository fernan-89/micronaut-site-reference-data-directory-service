package com.thinklab.application.usecase;

import com.thinklab.application.dto.request.CreateSiteRequest;
import com.thinklab.application.dto.request.UpdateSiteRequest;
import com.thinklab.domain.exception.SiteNotFoundException;
import com.thinklab.domain.model.Site;
import com.thinklab.domain.model.Site.SiteStatus;
import com.thinklab.domain.port.HashServicePort;
import com.thinklab.domain.repository.SiteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SiteUseCaseTest {

    @Mock private SiteRepository siteRepository;
    @Mock private HashServicePort hashServicePort;

    private UUID siteId;
    private UUID organisationId;
    private Site site;

    @BeforeEach
    void setUp() {
        siteId = UUID.randomUUID();
        organisationId = UUID.randomUUID();
        site = Site.createNew(siteId, organisationId, "HQ", "addr", "city", "country", "zip", "tz", 1.0, 2.0);
    }

    @Test
    @DisplayName("InitiateSiteUseCase generates a sovereign ID then creates the aggregate")
    void initiate() {
        when(hashServicePort.generateSovereignId("site-creation")).thenReturn(Mono.just(siteId));
        when(siteRepository.create(any(Site.class))).thenReturn(Mono.just(site));

        InitiateSiteUseCase useCase = new InitiateSiteUseCase(hashServicePort, siteRepository);
        CreateSiteRequest request = new CreateSiteRequest(organisationId, "HQ", "addr", "city", "country", "zip", "tz", 1.0, 2.0);

        StepVerifier.create(useCase.execute(request))
                .assertNext(res -> assertEquals(siteId, res.id()))
                .verifyComplete();
    }

    @Test
    @DisplayName("RetrieveSiteUseCase maps the found aggregate to a response")
    void retrieveFound() {
        when(siteRepository.findById(siteId)).thenReturn(Mono.just(site));

        StepVerifier.create(new RetrieveSiteUseCase(siteRepository).execute(siteId))
                .assertNext(res -> assertEquals("HQ", res.siteName()))
                .verifyComplete();
    }

    @Test
    @DisplayName("RetrieveSiteUseCase 404s when the aggregate does not exist")
    void retrieveNotFound() {
        when(siteRepository.findById(siteId)).thenReturn(Mono.empty());

        StepVerifier.create(new RetrieveSiteUseCase(siteRepository).execute(siteId))
                .expectError(SiteNotFoundException.class)
                .verify();
    }

    @Test
    @DisplayName("RetrieveSitesUseCase forwards the tenant and status filter")
    void retrieveAll() {
        when(siteRepository.findAllByOrganisationId(organisationId, SiteStatus.ACTIVE)).thenReturn(Flux.just(site));

        StepVerifier.create(new RetrieveSitesUseCase(siteRepository).execute(organisationId, SiteStatus.ACTIVE))
                .expectNextCount(1)
                .verifyComplete();
    }

    @Test
    @DisplayName("UpdateSiteUseCase delegates directly to the repository")
    void update() {
        when(siteRepository.updateBasicInfo(siteId, "HQ 2", "addr2", "city2", "country2", "zip2", "tz2", 3.0, 4.0))
                .thenReturn(Mono.empty());

        UpdateSiteRequest request = new UpdateSiteRequest("HQ 2", "addr2", "city2", "country2", "zip2", "tz2", 3.0, 4.0);

        StepVerifier.create(new UpdateSiteUseCase(siteRepository).execute(siteId, request)).verifyComplete();
        verify(siteRepository).updateBasicInfo(siteId, "HQ 2", "addr2", "city2", "country2", "zip2", "tz2", 3.0, 4.0);
    }

    @Test
    @DisplayName("ControlSiteUseCase loads the aggregate, validates the transition, then issues the write")
    void controlActivateDeactivate() {
        when(siteRepository.findById(siteId)).thenReturn(Mono.just(site));
        when(siteRepository.updateStatus(siteId, SiteStatus.INACTIVE)).thenReturn(Mono.empty());

        StepVerifier.create(new ControlSiteUseCase(siteRepository).execute(siteId, ControlSiteUseCase.Action.DEACTIVATE))
                .verifyComplete();
        verify(siteRepository).updateStatus(siteId, SiteStatus.INACTIVE);
    }

    @Test
    @DisplayName("ControlSiteUseCase 404s when the aggregate does not exist")
    void controlNotFound() {
        when(siteRepository.findById(siteId)).thenReturn(Mono.empty());

        StepVerifier.create(new ControlSiteUseCase(siteRepository).execute(siteId, ControlSiteUseCase.Action.ACTIVATE))
                .expectError(SiteNotFoundException.class)
                .verify();
    }

    @Test
    @DisplayName("ControlSiteUseCase rejects an illegal transition before writing anything")
    void controlIllegalTransition() {
        when(siteRepository.findById(siteId)).thenReturn(Mono.just(site));

        StepVerifier.create(new ControlSiteUseCase(siteRepository).execute(siteId, ControlSiteUseCase.Action.ACTIVATE))
                .expectError(com.thinklab.domain.exception.InvalidSiteStatusException.class)
                .verify();
    }

    @Test
    @DisplayName("ControlSiteUseCase.Action exposes its target status")
    void actionTargets() {
        assertEquals(SiteStatus.ACTIVE, ControlSiteUseCase.Action.ACTIVATE.targetStatus());
        assertEquals(SiteStatus.INACTIVE, ControlSiteUseCase.Action.DEACTIVATE.targetStatus());
    }
}
