package com.example.busbooking.business.port.repository;

import com.example.busbooking.domain.enums.RoleName;
import com.example.busbooking.domain.model.User;

public interface RoleManagementRepository {
    User changeRole(Long actorId, Long targetId, RoleName role);
}
