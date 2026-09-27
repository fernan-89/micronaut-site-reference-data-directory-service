# ADR-031: 2dsphere Geospatial Index via a Local SiteIndexInitializer

## Status
Accepted

## Context
A Site carries an optional `latitude`/`longitude` pair, kept as plain `Double` fields on the domain
model to stay framework-free (hexagonal purity — the domain has no notion of GeoJSON). Proximity
queries ("sites near this point") need a Mongo `2dsphere` index over a GeoJSON-shaped field, which the
domain model does not have; the persistence entity derives a `location` field
(`com.mongodb.client.model.geojson.Point`) only when both coordinates are present, at the mapping
boundary (`SitePersistenceMapper.toDocument`).

This service also follows the established raw-reactive-streams-driver adapter pattern (`Organisation`,
`Asset`), not Micronaut Data. The kit's generic `MongoIndexInitializer` (introduced in kit 0.5.0) only
reads `@MappedEntity`/`@Index` annotations and only creates ascending-value indexes — it does not see
raw-driver adapters at all, and cannot express a `2dsphere` index type even if it did.

## Decision
- `SiteIndexInitializer` is a local, service-specific `ApplicationEventListener<StartupEvent>`
  (mirroring the `AssetIndexInitializer` precedent), not a contribution to the kit — a `2dsphere` index
  is a one-off need, not a generalizable pattern the kit should absorb yet.
- It creates two indexes on startup: a sparse `2dsphere` index (`location_2dsphere`) so sites without
  coordinates are simply excluded rather than indexed as null, and a compound
  `(organisationId, status)` index (`organisationId_1_status_1`) for the tenant-scoped collection
  `retrieve` query.
- Fail-open: an unreachable Mongo server or a rejected index at startup is logged, never propagated —
  the service still starts and serves non-geospatial routes. This mirrors `RevocationPoller`'s
  fail-open posture, not `MongoWarmupObserver`'s fail-fast one, because a missing secondary index is a
  degraded-query problem, not a correctness problem.
- Gated by `thinklab.mongo.create-indexes` (default true), so it can be disabled in an environment that
  manages indexes out-of-band (e.g. a shared production cluster with its own migration tooling).

## Consequences
- Positive: proximity queries have the right index type without waiting for the kit to grow
  geospatial support it may never need elsewhere; the pattern is a straight copy of the already-proven
  `AssetIndexInitializer`, no new risk surface.
- Negative: a second raw-driver service now duplicates the same "local index initializer" boilerplate
  instead of sharing it. Acceptable for now (two occurrences); a third would be the trigger to
  reconsider generalizing this into the kit.
