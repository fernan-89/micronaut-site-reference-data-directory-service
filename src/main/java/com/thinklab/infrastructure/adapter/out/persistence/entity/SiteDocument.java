package com.thinklab.infrastructure.adapter.out.persistence.entity;

import com.thinklab.domain.model.Site;
import com.thinklab.domain.model.Site.Building;
import com.thinklab.domain.model.Site.Rack;
import com.thinklab.domain.model.Site.RackStatus;
import com.thinklab.domain.model.Site.Room;
import com.thinklab.domain.model.Site.RoomType;
import com.thinklab.domain.model.Site.SiteStatus;
import com.mongodb.client.model.geojson.Point;
import com.mongodb.client.model.geojson.Position;
import io.micronaut.core.annotation.Introspected;
import org.bson.codecs.pojo.annotations.BsonId;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Infrastructure-specific representation of the Site Aggregate for MongoDB. Ensures the pure
 * Domain Model remains untainted by persistence annotations. Uses native BSON annotations for
 * high-performance mapping without ORM overhead.
 */
@Introspected
public class SiteDocument {

    @BsonId // Native MongoDB driver annotation for Sovereign Identity
    private UUID id;

    private UUID organisationId;
    private String siteName;
    private String address;
    private String city;
    private String country;
    private String zipCode;
    private String timezone;
    private Double latitude;
    private Double longitude;
    // Denormalized from latitude/longitude, GeoJSON-shaped, purely so the 2dsphere index (ADR pending)
    // can be created on this field - the domain model never sees GeoJSON, only plain lat/long.
    private Point location;
    private String status;
    private List<BuildingDocument> buildings = new ArrayList<>();
    private Instant createdAt;
    private Instant updatedAt;

    // Getters and Setters required by framework POJO codec
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getOrganisationId() { return organisationId; }
    public void setOrganisationId(UUID organisationId) { this.organisationId = organisationId; }
    public String getSiteName() { return siteName; }
    public void setSiteName(String siteName) { this.siteName = siteName; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }
    public String getCountry() { return country; }
    public void setCountry(String country) { this.country = country; }
    public String getZipCode() { return zipCode; }
    public void setZipCode(String zipCode) { this.zipCode = zipCode; }
    public String getTimezone() { return timezone; }
    public void setTimezone(String timezone) { this.timezone = timezone; }
    public Double getLatitude() { return latitude; }
    public void setLatitude(Double latitude) { this.latitude = latitude; }
    public Double getLongitude() { return longitude; }
    public void setLongitude(Double longitude) { this.longitude = longitude; }
    public Point getLocation() { return location; }
    public void setLocation(Point location) { this.location = location; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public List<BuildingDocument> getBuildings() { return buildings; }
    public void setBuildings(List<BuildingDocument> buildings) { this.buildings = buildings; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    @Introspected
    public record BuildingDocument(UUID buildingId, String buildingName, Integer floorCount, List<RoomDocument> rooms) {}

    @Introspected
    public record RoomDocument(UUID roomId, String roomName, String roomType, List<RackDocument> racks) {}

    @Introspected
    public record RackDocument(UUID rackId, String rackName, int heightU, String status) {}

    /**
     * Internal Persistence Mapper ensuring strict isolation between Document and Domain.
     */
    public static final class SitePersistenceMapper {

        private SitePersistenceMapper() { throw new UnsupportedOperationException(); }

        public static SiteDocument toDocument(Site site) {
            SiteDocument doc = new SiteDocument();
            doc.setId(site.getId());
            doc.setOrganisationId(site.getOrganisationId());
            doc.setSiteName(site.getSiteName());
            doc.setAddress(site.getAddress());
            doc.setCity(site.getCity());
            doc.setCountry(site.getCountry());
            doc.setZipCode(site.getZipCode());
            doc.setTimezone(site.getTimezone());
            doc.setLatitude(site.getLatitude());
            doc.setLongitude(site.getLongitude());
            if (site.getLatitude() != null && site.getLongitude() != null) {
                doc.setLocation(new Point(new Position(site.getLongitude(), site.getLatitude())));
            }
            doc.setStatus(site.getStatus().name());
            doc.setCreatedAt(site.getCreatedAt());
            doc.setUpdatedAt(site.getUpdatedAt());
            doc.setBuildings(site.getBuildings().stream().map(SitePersistenceMapper::toDocument).collect(Collectors.toList()));
            return doc;
        }

        private static BuildingDocument toDocument(Building b) {
            return new BuildingDocument(b.buildingId(), b.buildingName(), b.floorCount(),
                    b.rooms().stream().map(SitePersistenceMapper::toDocument).collect(Collectors.toList()));
        }

        private static RoomDocument toDocument(Room r) {
            return new RoomDocument(r.roomId(), r.roomName(), r.roomType().name(),
                    r.racks().stream().map(SitePersistenceMapper::toDocument).collect(Collectors.toList()));
        }

        private static RackDocument toDocument(Rack k) {
            return new RackDocument(k.rackId(), k.rackName(), k.heightU(), k.status().name());
        }

        public static Site toDomain(SiteDocument doc) {
            List<Building> domainBuildings = doc.getBuildings() != null
                    ? doc.getBuildings().stream().map(SitePersistenceMapper::toDomain).collect(Collectors.toList())
                    : Collections.emptyList();

            SiteStatus status = doc.getStatus() != null ? SiteStatus.valueOf(doc.getStatus()) : SiteStatus.ACTIVE;

            return Site.reconstitute(
                    doc.getId(),
                    doc.getOrganisationId(),
                    doc.getSiteName(),
                    doc.getAddress(),
                    doc.getCity(),
                    doc.getCountry(),
                    doc.getZipCode(),
                    doc.getTimezone(),
                    doc.getLatitude(),
                    doc.getLongitude(),
                    status,
                    domainBuildings,
                    doc.getCreatedAt(),
                    doc.getUpdatedAt()
            );
        }

        private static Building toDomain(BuildingDocument b) {
            List<Room> rooms = b.rooms() != null
                    ? b.rooms().stream().map(SitePersistenceMapper::toDomain).collect(Collectors.toList())
                    : Collections.emptyList();
            return new Building(b.buildingId(), b.buildingName(), b.floorCount(), rooms);
        }

        private static Room toDomain(RoomDocument r) {
            List<Rack> racks = r.racks() != null
                    ? r.racks().stream().map(SitePersistenceMapper::toDomain).collect(Collectors.toList())
                    : Collections.emptyList();
            RoomType roomType = r.roomType() != null ? RoomType.valueOf(r.roomType()) : RoomType.OTHER;
            return new Room(r.roomId(), r.roomName(), roomType, racks);
        }

        private static Rack toDomain(RackDocument k) {
            RackStatus status = k.status() != null ? RackStatus.valueOf(k.status()) : RackStatus.ACTIVE;
            return new Rack(k.rackId(), k.rackName(), k.heightU(), status);
        }
    }
}
