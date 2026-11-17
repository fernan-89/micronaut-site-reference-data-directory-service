package com.thinklab.infrastructure.adapter.out.persistence;

import com.thinklab.application.usecase.ControlSiteUseCase;
import com.thinklab.domain.exception.InvalidSiteStatusException;
import com.thinklab.domain.model.Site;
import com.thinklab.domain.model.Site.SiteStatus;
import com.thinklab.domain.repository.SiteRepository;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.micronaut.test.support.TestPropertyProvider;
import jakarta.inject.Inject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The site status race against a real MongoDB (platform race audit, ACTION-PLAN 0.5): ten deactivations of one site at once have exactly one winner. */
@MicronautTest(packages = "com.thinklab", transactional = false)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TransitionRaceIT implements TestPropertyProvider {

    private static final String DATABASE = "it_site_directory_race_it";

    @Override
    public Map<String, String> getProperties() {
        return Map.of("mongodb.uri", MongoContainer.uri(DATABASE));
    }

    @Inject SiteRepository sites;
    @Inject ControlSiteUseCase control;

    @Test
    @DisplayName("ten deactivations of one site at once: exactly one wins, the others are refused with a conflict")
    void tenDeactivationsAtOnce() {
        Site site = sites.create(Site.createNew(UUID.randomUUID(), UUID.randomUUID(), "HQ", "1 Main St", "Lisbon", "PT", "1000-001", "Europe/Lisbon", 38.72, -9.14)).block();

        List<String> results = Flux.range(0, 10)
                .flatMap(i -> control.execute(site.getId(), site.getOrganisationId(), ControlSiteUseCase.Action.DEACTIVATE).thenReturn("OK")
                        .onErrorResume(e -> Mono.just(e.getClass().getSimpleName())), 10)
                .collectList().block(Duration.ofSeconds(30));

        assertEquals(1, results.stream().filter("OK"::equals).count(), results.toString());
        assertEquals(9, results.stream().filter(InvalidSiteStatusException.class.getSimpleName()::equals).count(), results.toString());
        assertEquals(SiteStatus.INACTIVE, sites.findById(site.getId(), site.getOrganisationId()).block().getStatus());
    }
}
