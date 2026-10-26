package com.example.busbooking.data.entity;

import jakarta.persistence.*;
import com.example.busbooking.domain.enums.OperatorStatus;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;

@Entity
@Table(name = "bus_operators")
public class OperatorEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 150)
    private String name;
    @Column(nullable = false, length = 255)
    private String address;
    @Column(nullable = false, length = 20)
    private String phone;
    @Column(nullable = false, length = 100)
    private String email;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OperatorStatus status;
    @CreationTimestamp
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    protected OperatorEntity() {}
    public OperatorEntity(String name, String address, String phone, String email, OperatorStatus status) {
        updateDetails(name, address, phone, email, status);
    }
    public Long getId() { return id; }
    public String getName() { return name; }
    public String getAddress() { return address; }
    public String getPhone() { return phone; }
    public String getEmail() { return email; }
    public OperatorStatus getStatus() { return status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void updateDetails(String name, String address, String phone, String email, OperatorStatus status) {
        this.name = name;
        this.address = address;
        this.phone = phone;
        this.email = email;
        this.status = status;
    }
}
