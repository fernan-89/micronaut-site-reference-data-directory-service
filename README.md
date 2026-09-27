# Thinklab Site Reference Data Directory Service

**Version:** v1.0.0-BIAN

**Status:** Reference implementation (ThinkLab portfolio project)

## Overview

The Thinklab Site Reference Data Directory Service is the authoritative model of an organisation's
physical footprint — sites, buildings, rooms and racks — the base an Asset's `locationId` will
eventually point at, and the base a rack canvas will eventually render. It implements the
BIAN-aligned `site-reference-data-directory` Service Domain (ADR-013): the `Site` is the Control
Record and every route follows the `/{control-record-id}/{behavior-qualifier}` convention.

Building, Room and Rack are individually addressable Behavior Qualifier Instance Records nested
three levels deep under a Site, but persisted as one embedded document per Site — not as separate
top-level collections (ADR-030). Every Site is scoped to an Organisation from the Party Reference
Data Directory (`X-Tenant-Id`) and receives its sovereign UUID from the Hash Token Registry.

Built with Java 21 and Micronaut 4.4.2 on a strict Hexagonal Architecture and a fully reactive
stack (Project Reactor, reactive MongoDB driver).

## Technology Stack

* **Runtime:** Java 21 LTS
* **Framework:** Micronaut 4.4.2 (AOT optimized, reflection-free DI and Serde)
* **Reactive Engine:** Project Reactor (Mono / Flux)
* **Persistence:** Reactive MongoDB (`thinklab_site_db`, collection `sites`), BSON UUID standard representation, a sparse `2dsphere` index on the derived GeoJSON location and a compound `(organisationId, status)` index (ADR-031)
* **Observability:** W3C Trace Context, SLF4J/Logback, Reactor MDC bridge
* **Containerization:** Google Distroless (nonroot), read-only root filesystem
* **Testing:** JUnit 5, Mockito, Reactor Test (aggregate/FSM matrix, use cases, controller, adapter, index initializer)
* **Documentation:** OpenAPI 3.0 / Swagger generated at compile time

## Domain Model

```text
Site {
  id, organisationId, siteName, address, city, country, zipCode, timezone,
  latitude?, longitude?, status, buildings[], createdAt, updatedAt
}
Building { buildingId, buildingName, floorCount?, rooms[] }
Room     { roomId, roomName, roomType, racks[] }
Rack     { rackId, rackName, heightU, status }

roomType: DATA_CENTER | OFFICE | STORAGE | OTHER
```

### Lifecycles

```text
SiteStatus: ACTIVE <-> INACTIVE      (both directions legal, self-transition is a 409 idempotency violation)
RackStatus: ACTIVE -> DECOMMISSIONED (terminal, no exit, no DELETE)
```

A Site's own status and each nested Rack's status are independent state machines (ADR-019).
Building and Room have no status of their own — they exist for as long as their parent does.

## BIAN Behavior Qualifier Contract (`/site-reference-data-directory/v1`)

`X-Tenant-Id` (Organisation UUID) is mandatory on the collection `retrieve`; `X-Executor` is
mandatory on every mutation. There is no `DELETE`.

| Behavior Qualifier | Method & Path |
|---|---|
| initiate | `POST /site-reference-data-directory/v1/initiate` |
| retrieve (single) | `GET /site-reference-data-directory/v1/{id}/retrieve` |
| retrieve (collection, filter `status`) | `GET /site-reference-data-directory/v1/retrieve` |
| update | `PUT /site-reference-data-directory/v1/{id}/update` |
| control/activate, deactivate | `PUT /site-reference-data-directory/v1/{id}/control/{action}` |
| building/initiate | `POST /site-reference-data-directory/v1/{id}/building/initiate` |
| building/retrieve (collection) | `GET /site-reference-data-directory/v1/{id}/building/retrieve` |
| building/room/initiate | `POST /site-reference-data-directory/v1/{id}/building/{buildingId}/room/initiate` |
| building/room/rack/initiate | `POST /site-reference-data-directory/v1/{id}/building/{buildingId}/room/{roomId}/rack/initiate` |
| rack/control/activate, decommission | `PUT /site-reference-data-directory/v1/{id}/building/{buildingId}/room/{roomId}/rack/{rackId}/control/{action}` |

### Error catalog (RFC 7807, `error_code` field)

| error_code | HTTP | Meaning |
|---|---|---|
| `ERR-SITE-00404` | 404 | Site, Building, Room or Rack not found |
| `ERR-SITE-00409` | 409 | Illegal or idempotent lifecycle transition (Site or Rack, ADR-019) |
| `ERR-VALIDATION-00400` | 400 | Payload/header/identifier validation failure |
| `ERR-INTERNAL-00500` | 500 | Unexpected technical failure |

Example:

```bash
curl -X POST http://localhost:8087/site-reference-data-directory/v1/initiate \
  -H "Content-Type: application/json" \
  -H "X-Executor: admin-user-01" \
  -d '{"organisationId":"6f1c7a52-3d0b-4a44-9c3e-0a7d1f6e2b10","siteName":"HQ","address":"1 Main St","city":"Lisbon","country":"PT","zipCode":"1000-001","timezone":"Europe/Lisbon","latitude":38.72,"longitude":-9.14}'
```

## Operational Procedures

```bash
# Build, run AOT optimizations and test
./gradlew clean build

# Start the service (default port 8087)
./gradlew run

# Container image
docker build -t thinklab-site-reference-data-directory-service:latest .
```

* **Health:** `http://localhost:8087/health`
* **Swagger UI:** `http://localhost:8087/swagger-ui`
* **Postman suite:** `docs/postman/` (lifecycle + nested Building/Room/Rack + negative/409 scenarios)

### Configuration

| Variable | Default | Purpose |
|---|---|---|
| `MICRONAUT_SERVER_PORT` | `8087` | HTTP port |
| `MONGODB_URI` | `mongodb://localhost:27017/thinklab_site_db` | MongoDB connection |
| `HASH_SERVICE_URL` | `http://localhost:8080` | Hash Token Registry base URL |

## Architecture Decision Records

`docs/adr/`: 001 hexagonal reactive stack · 005 UUID identity sovereignty · 013 BIAN service domain
conventions · 019 HTTP 409 for state conflicts · 030 embedded Building/Room/Rack hierarchy · 031
geospatial index and local index initializer.

### Automated Tests

```bash
./gradlew test                          # unit suite + 100% line/branch coverage gate (no Docker needed)
./gradlew integrationTest               # Testcontainers suite against a real MongoDB (needs Docker)
./gradlew check                         # both, as CI runs it
```

## License

Licensed under the [PolyForm Strict License 1.0.0](LICENSE): you may read and use this software for noncommercial purposes only. Modifying it, creating derivative works, redistributing it and any commercial use are not permitted without a separate written license. This software is not open source.
