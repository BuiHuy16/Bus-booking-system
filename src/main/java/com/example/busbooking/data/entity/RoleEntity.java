package com.example.busbooking.data.entity;

import com.example.busbooking.domain.enums.RoleName;
import jakarta.persistence.*;

@Entity
@Table(name = "roles")
public class RoleEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, unique = true, length = 30)
    private RoleName name;

    protected RoleEntity() { }

    public RoleEntity(Long id, RoleName name) {
        this.id = id;
        this.name = name;
    }

    public Long getId() { return id; }
    public RoleName getName() { return name; }
}
