package com.example.busbooking.data.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "vehicles")
public class VehicleEntity {
    @Id
    private Long id;
    @Column(name = "operator_id", nullable = false)
    private Long operatorId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "operator_id", insertable = false, updatable = false)
    private OperatorEntity operator;
    @Column(name = "vehicle_type", nullable = false, length = 50)
    private String vehicleType;
    @Column(name = "seat_capacity", nullable = false)
    private int seatCapacity;
    @Column(nullable = false, length = 20)
    private String status;
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    protected VehicleEntity() {}
    public Long getId() { return id; }
    public String getStatus() { return status; }
    public String getVehicleType() { return vehicleType; }
    public int getSeatCapacity() { return seatCapacity; }
    public OperatorEntity getOperator() { return operator; }
}
