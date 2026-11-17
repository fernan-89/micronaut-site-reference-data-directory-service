# ADR 032: Site status is a guarded write

## Status
Accepted (2026-11-17). Found by the platform race audit (ACTION-PLAN 0.5, `thinklab-platform/docs/race-audit.md`).

## Context
A site status change read the site, validated the transition in the domain and wrote the status by `_id` only. Measured by `TransitionRaceIT` on the old
code: ten deactivations of one site at once all answered OK, where the domain says a deactivation of an inactive site is an illegal (idempotent) transition.

## Decision
`updateStatus` takes the status the caller read and is a **guarded write** on it: no match is `409 ERR-SITE-00409` when the site exists and `404` when it does not.

## Consequences
- **Positive:** a transition is applied only to the state it was validated against.
- **Not changed, on purpose:** this service keeps no audit trail, and a rack status change (`ACTIVE -> DECOMMISSIONED`, terminal) is a write into a nested
  array whose double application leaves the same state; guarding it needs an array-filter on the rack status and a modified-count check for little gain. It
  stays as is and is listed as accepted in the race audit.
