package com.example.busbooking.business.port.repository;

import com.example.busbooking.domain.enums.RoleName;
import com.example.busbooking.domain.model.Role;
import java.util.Optional;

public interface RoleRepository {
    Optional<Role> findByName(RoleName name);
}
