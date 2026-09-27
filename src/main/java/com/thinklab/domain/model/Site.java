package com.thinklab.domain.model;

import com.thinklab.domain.exception.InvalidSiteStatusException;
import com.thinklab.domain.exception.SiteNotFoundException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Aggregate Root: a physical location an Organisation operates from (BIAN Service Domain: Site
 * Reference Data Directory). Nests the physical hierarchy a rack canvas needs -
 * {@link Building} contains {@link Room}, {@link Room} contains {@link Rack} - each a record
 * mutated only through this aggregate, never persisted as its own top-level collection (ADR-002's
 * "Partial State Mutations" rule, same as {@code Organisation}'s OrganisationUnit/Contact).
 *
 * <p>An Asset's {@code locationId} points at a {@link Rack}'s id once this platform's canvas
 * exists; which U slot a given asset occupies inside that rack is deliberately out of scope here
 * (it belongs to the asset-registry side, once a canvas actually renders it) - this aggregate only
 * models the rack's total capacity in U.
 */
public class Site {

    private final UUID id;
    private final UUID organisationId;
    private String siteName;
    private String address;
    private String city;
    private String country;
    private String zipCode;
    private String timezone;
    private Double latitude;
    private Double longitude;
    private SiteStatus status;
    private final List<Building> buildings;
    private final Instant createdAt;
    private Instant updatedAt;

    private Site(UUID id, UUID organisationId, String siteName, String address, String city, String country,
                 String zipCode, String timezone, Double latitude, Double longitude, SiteStatus status,
                 List<Building> buildings, Instant createdAt, Instant updatedAt) {
        this.id = Objects.requireNonNull(id, "Domain constraint violated: id cannot be null.");
        this.organisationId = Objects.requireNonNull(organisationId, "Domain constraint violated: organisationId cannot be null.");
        this.siteName = Objects.requireNonNull(siteName, "Domain constraint violated: siteName cannot be null.");
        this.address = address;
        this.city = city;
        this.country = country;
        this.zipCode = zipCode;
        this.timezone = timezone;
        this.latitude = latitude;
        this.longitude = longitude;
        this.status = Objects.requireNonNull(status, "Domain constraint violated: status cannot be null.");
        this.buildings = new ArrayList<>(buildings);
        this.createdAt = Objects.requireNonNull(createdAt, "Domain constraint violated: createdAt cannot be null.");
        this.updatedAt = Objects.requireNonNull(updatedAt, "Domain constraint violated: updatedAt cannot be null.");
    }

    public static Site createNew(UUID id, UUID organisationId, String siteName, String address, String city,
                                  String country, String zipCode, String timezone, Double latitude, Double longitude) {
        Instant now = Instant.now();
        return new Site(id, organisationId, siteName, address, city, country, zipCode, timezone, latitude, longitude,
                SiteStatus.ACTIVE, List.of(), now, now);
    }

    public static Site reconstitute(UUID id, UUID organisationId, String siteName, String address, String city,
                                     String country, String zipCode, String timezone, Double latitude, Double longitude,
                                     SiteStatus status, List<Building> buildings, Instant createdAt, Instant updatedAt) {
        return new Site(id, organisationId, siteName, address, city, country, zipCode, timezone, latitude, longitude,
                status, buildings, createdAt, updatedAt);
    }

    public void updateBasicInfo(String siteName, String address, String city, String country, String zipCode,
                                 String timezone, Double latitude, Double longitude) {
        this.siteName = Objects.requireNonNull(siteName, "Domain constraint violated: siteName cannot be null.");
        this.address = address;
        this.city = city;
        this.country = country;
        this.zipCode = zipCode;
        this.timezone = timezone;
        this.latitude = latitude;
        this.longitude = longitude;
        this.updatedAt = Instant.now();
    }

    public void changeStatus(SiteStatus newStatus) {
        this.status.validateTransitionTo(newStatus);
        this.status = newStatus;
        this.updatedAt = Instant.now();
    }

    public void activate() {
        changeStatus(SiteStatus.ACTIVE);
    }

    public void deactivate() {
        changeStatus(SiteStatus.INACTIVE);
    }

    public void addBuilding(Building building) {
        Objects.requireNonNull(building, "Domain constraint violated: building cannot be null.");
        this.buildings.add(building);
        this.updatedAt = Instant.now();
    }

    public void addRoom(UUID buildingId, Room room) {
        Objects.requireNonNull(room, "Domain constraint violated: room cannot be null.");
        Building building = findBuilding(buildingId);
        replaceBuilding(building.withRooms(append(building.rooms(), room)));
    }

    public void addRack(UUID buildingId, UUID roomId, Rack rack) {
        Objects.requireNonNull(rack, "Domain constraint violated: rack cannot be null.");
        Building building = findBuilding(buildingId);
        Room room = findRoom(building, roomId);
        Room updatedRoom = room.withRacks(append(room.racks(), rack));
        replaceBuilding(building.withRooms(replace(building.rooms(), room.roomId(), updatedRoom)));
    }

    public void changeRackStatus(UUID buildingId, UUID roomId, UUID rackId, RackStatus newStatus) {
        Building building = findBuilding(buildingId);
        Room room = findRoom(building, roomId);
        Rack rack = findRack(room, rackId);
        rack.status().validateTransitionTo(newStatus);
        Room updatedRoom = room.withRacks(replace(room.racks(), rackId, rack.withStatus(newStatus)));
        replaceBuilding(building.withRooms(replace(building.rooms(), roomId, updatedRoom)));
    }

    public Building findBuilding(UUID buildingId) {
        Objects.requireNonNull(buildingId, "Domain constraint violated: buildingId cannot be null.");
        return buildings.stream()
                .filter(b -> b.buildingId().equals(buildingId))
                .findFirst()
                .orElseThrow(() -> new SiteNotFoundException(
                        String.format("Building with ID [%s] could not be found in Site [%s].", buildingId, id)));
    }

    public Room findRoom(Building building, UUID roomId) {
        Objects.requireNonNull(roomId, "Domain constraint violated: roomId cannot be null.");
        return building.rooms().stream()
                .filter(r -> r.roomId().equals(roomId))
                .findFirst()
                .orElseThrow(() -> new SiteNotFoundException(
                        String.format("Room with ID [%s] could not be found in Building [%s].", roomId, building.buildingId())));
    }

    public Rack findRack(Room room, UUID rackId) {
        Objects.requireNonNull(rackId, "Domain constraint violated: rackId cannot be null.");
        return room.racks().stream()
                .filter(r -> r.rackId().equals(rackId))
                .findFirst()
                .orElseThrow(() -> new SiteNotFoundException(
                        String.format("Rack with ID [%s] could not be found in Room [%s].", rackId, room.roomId())));
    }

    private void replaceBuilding(Building updated) {
        for (int i = 0; i < buildings.size(); i++) {
            if (buildings.get(i).buildingId().equals(updated.buildingId())) {
                buildings.set(i, updated);
            }
        }
        this.updatedAt = Instant.now();
    }

    private static <T> List<T> append(List<T> list, T item) {
        List<T> copy = new ArrayList<>(list);
        copy.add(item);
        return copy;
    }

    // Callers always locate the element via findRoom/findRack (which already throw if missing) before
    // calling this, so the id is guaranteed present - no defensive branch for a case that cannot occur.
    // No early exit once matched either: ids are unique, so scanning the rest of the list is harmless.
    private static <T> List<T> replace(List<T> list, UUID id, T replacement) {
        List<T> copy = new ArrayList<>(list);
        for (int i = 0; i < copy.size(); i++) {
            Object candidate = copy.get(i);
            UUID candidateId = candidate instanceof Room r ? r.roomId() : ((Rack) candidate).rackId();
            if (id.equals(candidateId)) {
                copy.set(i, replacement);
            }
        }
        return copy;
    }

    public UUID getId() { return id; }
    public UUID getOrganisationId() { return organisationId; }
    public String getSiteName() { return siteName; }
    public String getAddress() { return address; }
    public String getCity() { return city; }
    public String getCountry() { return country; }
    public String getZipCode() { return zipCode; }
    public String getTimezone() { return timezone; }
    public Double getLatitude() { return latitude; }
    public Double getLongitude() { return longitude; }
    public SiteStatus getStatus() { return status; }
    public List<Building> getBuildings() { return Collections.unmodifiableList(buildings); }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public enum SiteStatus {
        ACTIVE, INACTIVE;

        public boolean canTransitionTo(SiteStatus target) {
            return switch (this) {
                case ACTIVE -> target == INACTIVE;
                case INACTIVE -> target == ACTIVE;
            };
        }

        // Only the idempotency check applies here: with exactly two states and canTransitionTo mapping
        // every non-self pair to true, a non-idempotent-but-still-illegal transition cannot occur - unlike
        // RackStatus below, which does have one.
        public void validateTransitionTo(SiteStatus target) {
            if (this == target) {
                throw new InvalidSiteStatusException(
                        String.format("Idempotency Violation: Site is already in status [%s].", target));
            }
        }
    }

    public record Building(UUID buildingId, String buildingName, Integer floorCount, List<Room> rooms) {
        public Building {
            Objects.requireNonNull(buildingId, "Domain constraint violated: buildingId cannot be null.");
            Objects.requireNonNull(buildingName, "Domain constraint violated: buildingName cannot be null.");
            rooms = rooms == null ? List.of() : List.copyOf(rooms);
        }

        public Building withRooms(List<Room> newRooms) {
            return new Building(buildingId, buildingName, floorCount, newRooms);
        }
    }

    public enum RoomType { DATA_CENTER, OFFICE, STORAGE, OTHER }

    public record Room(UUID roomId, String roomName, RoomType roomType, List<Rack> racks) {
        public Room {
            Objects.requireNonNull(roomId, "Domain constraint violated: roomId cannot be null.");
            Objects.requireNonNull(roomName, "Domain constraint violated: roomName cannot be null.");
            roomType = roomType == null ? RoomType.OTHER : roomType;
            racks = racks == null ? List.of() : List.copyOf(racks);
        }

        public Room withRacks(List<Rack> newRacks) {
            return new Room(roomId, roomName, roomType, newRacks);
        }
    }

    public enum RackStatus {
        ACTIVE, DECOMMISSIONED;

        public boolean canTransitionTo(RackStatus target) {
            return this == ACTIVE && target == DECOMMISSIONED;
        }

        public void validateTransitionTo(RackStatus target) {
            if (this == target) {
                throw new InvalidSiteStatusException(
                        String.format("Idempotency Violation: Rack is already in status [%s].", target));
            }
            if (!canTransitionTo(target)) {
                throw new InvalidSiteStatusException(
                        String.format("Illegal Rack status transition from [%s] to [%s].", this, target));
            }
        }
    }

    public record Rack(UUID rackId, String rackName, int heightU, RackStatus status) {
        public Rack {
            Objects.requireNonNull(rackId, "Domain constraint violated: rackId cannot be null.");
            Objects.requireNonNull(rackName, "Domain constraint violated: rackName cannot be null.");
            if (heightU <= 0) {
                throw new IllegalArgumentException("Domain constraint violated: heightU must be positive.");
            }
            status = status == null ? RackStatus.ACTIVE : status;
        }

        public Rack withStatus(RackStatus newStatus) {
            return new Rack(rackId, rackName, heightU, newStatus);
        }
    }
}
