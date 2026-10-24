package com.example.busbooking.data.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "routes")
public class RouteEntity {
    @Id
    private Long id;
    @Column(name = "origin_id", nullable = false)
    private Long originId;
    @Column(name = "destination_id", nullable = false)
    private Long destinationId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "origin_id", insertable = false, updatable = false)
    private LocationEntity origin;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "destination_id", insertable = false, updatable = false)
    private LocationEntity destination;
    @Column(name = "estimated_duration_minutes", nullable = false)
    private int estimatedDurationMinutes;
    @Column(nullable = false, length = 20)
    private String status;
    protected RouteEntity() {}
    public Long getId() { return id; }
    public String getStatus() { return status; }
    public LocationEntity getOrigin() { return origin; }
    public LocationEntity getDestination() { return destination; }
    public int getEstimatedDurationMinutes() { return estimatedDurationMinutes; }
}
