package com.example.busbooking.data.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;

@Entity
@Table(name = "vehicles")
public class VehicleEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "operator_id", nullable = false)
    private Long operatorId;
    @Column(name = "license_plate", nullable = false, length = 20)
    private String licensePlate;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "operator_id", insertable = false, updatable = false)
    private OperatorEntity operator;
    @Column(name = "vehicle_type", nullable = false, length = 50)
    private String vehicleType;
    @Column(name = "seat_capacity", nullable = false)
    private int seatCapacity;
    @Column(nullable = false, length = 20)
    private String status;
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    protected VehicleEntity() {}
    public VehicleEntity(Long operatorId, String licensePlate, String vehicleType,
                         int seatCapacity, String status) {
        updateDetails(operatorId, licensePlate, vehicleType, seatCapacity, status);
    }
    public Long getId() { return id; }
    public Long getOperatorId() { return operatorId; }
    public String getLicensePlate() { return licensePlate; }
    public String getStatus() { return status; }
    public String getVehicleType() { return vehicleType; }
    public int getSeatCapacity() { return seatCapacity; }
    public OperatorEntity getOperator() { return operator; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void updateDetails(Long operatorId, String licensePlate, String vehicleType,
                              int seatCapacity, String status) {
        this.operatorId = operatorId;
        this.licensePlate = licensePlate;
        this.vehicleType = vehicleType;
        this.seatCapacity = seatCapacity;
        this.status = status;
    }
}
