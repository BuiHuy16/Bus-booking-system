package com.example.busbooking.data.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "locations")
public class LocationEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 100)
    private String name;
    @Column(nullable = false, length = 100)
    private String province;
    @Column(nullable = false, length = 255)
    private String address;
    protected LocationEntity() {}
    public LocationEntity(String name, String province, String address) {
        updateDetails(name, province, address);
    }
    public Long getId() { return id; }
    public String getName() { return name; }
    public String getProvince() { return province; }
    public String getAddress() { return address; }
    public void updateDetails(String name, String province, String address) {
        this.name = name;
        this.province = province;
        this.address = address;
    }
}
