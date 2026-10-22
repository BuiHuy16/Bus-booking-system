package com.example.busbooking.data.repository;

import com.example.busbooking.data.entity.UserEntity;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.example.busbooking.domain.enums.RoleName;
import com.example.busbooking.domain.enums.UserStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface SpringDataUserRepository extends JpaRepository<UserEntity, Long> {
    @Override
    @EntityGraph(attributePaths = "role")
    Optional<UserEntity> findById(Long id);

    @EntityGraph(attributePaths = "role")
    Optional<UserEntity> findByEmail(String email);

    boolean existsByEmail(String email);

    @EntityGraph(attributePaths = "role")
    Optional<UserEntity> findByRole_Name(RoleName name);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update UserEntity u set u.fullName = :name, u.phone = :phone "
            + "where u.id = :id and u.status = :status and u.emailVerified = true")
    int updateProfile(@Param("id") Long id, @Param("name") String fullName,
                      @Param("phone") String phone, @Param("status") UserStatus status);
}
