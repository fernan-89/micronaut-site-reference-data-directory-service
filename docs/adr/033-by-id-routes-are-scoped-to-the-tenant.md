# ADR-033: The By-Id Routes Are Scoped to the Tenant

## Status
Accepted (2026-11-18). Part of the tenant audit of the platform (thinklab-platform `docs/TENANT-AUDIT-2026-11.md`); same rule as workflow-approval ADR-037, change management ADR-038, asset registry ADR-029, hardware maintenance ADR-036, operation window ADR-022 and discovery ADR-034.

## Context
Every route of a site (and of the buildings, rooms and racks under it) that takes the site id found it by id alone. The cross-tenant probe (platform `scripts/e2e/tenant-probe.sh`) showed a site of tenant X answering 200 to tenant Y on retrieve.

## Decision
- Every route that takes a site id takes `X-Tenant-Id` (required, 400 if blank) and finds the site only in that tenant: another tenant's site is the same as none (404 `SiteNotFoundException`). The repository has no unscoped `findById` any more.
- The writes that go straight to the store by id (`update`, `building/initiate`) first load the site in the caller's tenant and write only if it is there: the organisation of a site never changes, so checking first is enough.

## Consequences
- Positive: a UUID is no longer enough to read or change another tenant's site, building, room or rack.
- Negative: a client that sends no tenant on one of these routes now gets a 400; `update` and `building/initiate` read the site once more before writing.
