package com.example.busbooking.data.repository;

import com.example.busbooking.data.entity.TripEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface SpringDataTripRepository
        extends JpaRepository<TripEntity, Long> {

    @Query("""
        SELECT t
        FROM TripEntity t
        WHERE (:originId IS NULL OR t.routeId = :originId)
          AND (:destinationId IS NULL OR t.routeId = :destinationId)
          AND (
              :departureDate IS NULL
              OR (
                  t.departureTime >= :departureDate
                  AND t.departureTime < :departureDateEnd
              )
          )
        ORDER BY t.departureTime ASC
        """)
    List<TripEntity> search(
            @Param("originId") Long originId,
            @Param("destinationId") Long destinationId,
            @Param("departureDate") LocalDateTime departureDate,
            @Param("departureDateEnd") LocalDateTime departureDateEnd
    );

    @org.springframework.data.jpa.repository.Lock(
            jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM TripEntity t WHERE t.id = :tripId")
    java.util.Optional<TripEntity> findByIdForUpdate(@Param("tripId") Long tripId);

    @org.springframework.data.jpa.repository.Modifying
    @Query("""
        UPDATE TripEntity t
        SET t.availableSeats = t.availableSeats + :seatDelta
        WHERE t.id = :tripId
          AND t.availableSeats + :seatDelta >= 0
        """)
    int updateAvailableSeats(
            @Param("tripId") Long tripId,
            @Param("seatDelta") int seatDelta);
}
