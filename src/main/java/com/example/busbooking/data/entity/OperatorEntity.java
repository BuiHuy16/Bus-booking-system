package com.example.busbooking.data.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "bus_operators")
public class OperatorEntity {
    @Id
    private Long id;
    @Column(nullable = false, length = 150)
    private String name;
    @Column(nullable = false, length = 255)
    private String address;
    @Column(nullable = false, length = 20)
    private String phone;
    @Column(nullable = false, length = 100)
    private String email;
    @Column(nullable = false, length = 20)
    private String status;
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    protected OperatorEntity() {}
    public Long getId() { return id; }
    public String getName() { return name; }
}
