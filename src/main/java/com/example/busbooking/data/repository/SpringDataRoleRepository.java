package com.example.busbooking.data.repository;

import com.example.busbooking.data.entity.RoleEntity;
import com.example.busbooking.domain.enums.RoleName;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface SpringDataRoleRepository extends JpaRepository<RoleEntity, Long> {
    Optional<RoleEntity> findByName(RoleName name);
}
