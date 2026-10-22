package com.example.busbooking.data.mapper;

import com.example.busbooking.data.entity.RoleEntity;
import com.example.busbooking.domain.model.Role;
import org.springframework.stereotype.Component;

@Component
public class RolePersistenceMapper {
    public Role toDomain(RoleEntity entity) {
        return new Role(entity.getId(), entity.getName());
    }

    public RoleEntity toEntity(Role role) {
        return new RoleEntity(role.getId(), role.getName());
    }
}
