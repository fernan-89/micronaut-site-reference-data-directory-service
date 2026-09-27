# ADR-030: Building/Room/Rack Persisted as an Embedded Hierarchy, Not Top-Level Collections

## Status
Accepted

## Context
A Site's physical model is a three-level tree — Building contains Room, Room contains Rack — needed
as the base for a future rack canvas and for an Asset's `locationId` to eventually point at a rack.
Each level (`InitiateBuildingRequest`, `InitiateRoomRequest`, `InitiateRackRequest`) is individually
addressable under the Site's URL
(`/{id}/building/{buildingId}/room/{roomId}/rack/{rackId}/...`, BIAN Behavior Qualifier Instance
Records per ADR-013), which could suggest three more Mongo collections with foreign keys back to the
Site. The platform already answered this question for the Party Reference Data Directory's
Organisation/OrganisationUnit/Contact nesting: individually addressable does not mean individually
persisted.

## Decision
Building, Room and Rack are nested Java records embedded inside the single `Site` aggregate and
persisted as one Mongo document in the `sites` collection — never as their own top-level collections.
- Every mutation (`addBuilding`, `addRoom`, `addRack`, `changeRackStatus`) loads or targets the whole
  `Site` document; there is no cross-collection join or a Room/Rack repository.
- The Mongo adapter still performs *granular* writes into the nested arrays via `arrayFilters`
  (`buildings.$[b].rooms.$[r].racks.$[rk].status`, `UpdateOptions().arrayFilters(...)`) rather than
  rewriting the whole document on every change, so concurrent updates to different racks in the same
  site don't clobber each other.
- Existence of a parent (`findBuilding`, `findRoom`) is always validated against the in-memory
  aggregate *before* issuing a write. A naive `arrayFilters` update on a missing nested id would
  otherwise silently no-op: the top-level `_id` still matches, so `matchedCount` alone cannot detect a
  missing nested parent — this was caught and fixed during implementation, not assumed from the start.

## Consequences
- Positive: one document read gives the whole physical model (a rack canvas needs the full tree
  anyway); no join, no orphaned Room/Rack rows, no distributed transaction across collections; matches
  the platform's established "Partial State Mutations" pattern.
- Negative: a Site with an unusually large number of buildings/rooms/racks grows one document instead
  of scaling across rows — acceptable for a physical inventory (dozens to low hundreds of racks per
  site, not millions), and Mongo's 16 MB document limit is not a practical concern at this scale.
