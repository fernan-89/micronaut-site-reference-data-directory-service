# ADR-019: HTTP 409 Conflict for Every State Conflict

## Status
Accepted

## Context
RFC 9110 defines two adjacent status codes precisely:

- **409 Conflict** — the request is valid but cannot be applied because of the *current state* of
  the target resource. The client may succeed later if the state changes.
- **422 Unprocessable Content** — the request is well formed but its *content* is semantically
  invalid on its own, whatever the state of the resource.
- **400 Bad Request** — malformed syntax, a missing header or an unparsable identifier.

This service has two independent lifecycle state machines sharing the same aggregate: `SiteStatus`
(`ACTIVE <-> INACTIVE`) on the Site itself, and `RackStatus` (`ACTIVE -> DECOMMISSIONED`, terminal) on
each nested Rack. Both an idempotent self-transition (`ACTIVE -> ACTIVE`) and an illegal transition
(`DECOMMISSIONED -> ACTIVE`) are decided entirely by the aggregate's current state, not by anything
wrong with the request body — so both are 409, matching the platform-wide contract already adopted by
the four sibling Service Domains (see the Asset Registry's own ADR-019).

## Decision
1. Platform contract carried over unchanged: **409 for every state conflict** (FSM violations,
   idempotent self-transitions, duplicates), **400** for malformed input. This service never needed a
   422 case — every Site/Rack transition is decided by state alone, so `ERR-SITE-00422` was never
   introduced in the first place.
2. `InvalidSiteStatusException` carries `ERR-SITE-00409` and maps to 409 for both the Site-level and
   the Rack-level FSM. The `detail` member tells idempotency violations apart from illegal transitions;
   both share `ERR-SITE-00409`, as `ERR-USR-00409` does in the User domain.
3. A `SiteNotFoundException` for a missing nested Building/Room/Rack id is 404, not 409 — the resource
   referenced in the URL genuinely does not exist, which is a different failure than a valid resource
   refusing a transition.

## Consequences
- Positive: one predictable error contract across the platform; clients need no per-service rules, and
  no 422 code path had to be built, tested or later retired.
- Negative: none specific to this service — the FSMs are simple enough (two states each) that this
  decision was free to adopt from day one.
