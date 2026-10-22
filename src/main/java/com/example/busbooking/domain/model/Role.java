package com.example.busbooking.domain.model;

import com.example.busbooking.domain.enums.RoleName;

public final class Role {
    private final Long id;
    private final RoleName name;

    public Role(Long id, RoleName name) {
        this.id = id;
        this.name = name;
    }

    public Long getId() { return id; }

    public RoleName getName() { return name; }
}
