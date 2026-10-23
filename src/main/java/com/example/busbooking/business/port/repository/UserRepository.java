package com.example.busbooking.business.port.repository;

import com.example.busbooking.domain.model.User;
import java.util.Optional;

public interface UserRepository {
    Optional<User> findById(Long id);
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
    User save(User user);
    Optional<User> updateProfile(Long userId, String fullName, String phone);
    Optional<User> findSuperAdmin();
}
