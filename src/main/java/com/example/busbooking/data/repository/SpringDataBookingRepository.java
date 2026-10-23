package com.example.busbooking.data.repository;

import com.example.busbooking.data.entity.BookingEntity;
import com.example.busbooking.domain.enums.BookingStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface SpringDataBookingRepository extends JpaRepository<BookingEntity, Long> {
    Optional<BookingEntity> findByIdAndUserId(Long id, Long userId);
    List<BookingEntity> findAllByUserIdOrderByBookedAtDesc(Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM BookingEntity b WHERE b.id = :id")
    Optional<BookingEntity> findByIdForUpdate(@Param("id") Long id);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE BookingEntity b SET b.status = :status WHERE b.id = :id")
    int updateStatus(@Param("id") Long id, @Param("status") BookingStatus status);
}
