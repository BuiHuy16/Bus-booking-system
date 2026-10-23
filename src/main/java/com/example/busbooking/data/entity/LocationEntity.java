package com.example.busbooking.data.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "locations")
public class LocationEntity {
    @Id
    private Long id;
    @Column(nullable = false, length = 100)
    private String name;
    @Column(nullable = false, length = 100)
    private String province;
    @Column(nullable = false, length = 255)
    private String address;
    protected LocationEntity() {}
    public Long getId() { return id; }
    public String getName() { return name; }
}
