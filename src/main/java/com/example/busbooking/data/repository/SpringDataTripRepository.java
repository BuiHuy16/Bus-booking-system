
package com.example.busbooking.data.repository;

import com.example.busbooking.data.entity.TripEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface SpringDataTripRepository
        extends JpaRepository<TripEntity, Long> {

    // Giữ nguyên API mà RepositoryAdapter đang sử dụng
    default List<TripEntity> search(
            Long originId,
            Long destinationId,
            LocalDateTime departureDate,
            LocalDateTime departureDateEnd
    ) {
        if ((departureDate == null) != (departureDateEnd == null)) {
            throw new IllegalArgumentException(
                    "Departure date range must have both bounds"
            );
        }

        return searchFiltered(
                originId,
                destinationId,
                departureDate,
                departureDateEnd,
                originId == null,
                destinationId == null,
                departureDate == null
        );
    }

    @Query("""
        SELECT t
        FROM TripEntity t
        JOIN FETCH t.route r
        JOIN FETCH r.origin
        JOIN FETCH r.destination
        JOIN FETCH t.vehicle v
        JOIN FETCH v.operator
        WHERE (:skipOrigin = true OR r.origin.id = :originId)
          AND (:skipDestination = true OR r.destination.id = :destinationId)
          AND (
              :skipDate = true
              OR (
                  t.departureTime >= :departureDate
                  AND t.departureTime < :departureDateEnd
              )
          )
        ORDER BY t.departureTime ASC
        """)
    List<TripEntity> searchFiltered(
            @Param("originId") Long originId,
            @Param("destinationId") Long destinationId,
            @Param("departureDate") LocalDateTime departureDate,
            @Param("departureDateEnd") LocalDateTime departureDateEnd,
            @Param("skipOrigin") boolean skipOrigin,
            @Param("skipDestination") boolean skipDestination,
            @Param("skipDate") boolean skipDate
    );

    @Query("""
        SELECT t
        FROM TripEntity t
        JOIN FETCH t.route r
        JOIN FETCH r.origin
        JOIN FETCH r.destination
        JOIN FETCH t.vehicle v
        JOIN FETCH v.operator
        WHERE t.id = :tripId
        """)
    Optional<TripEntity> findDetailedById(
            @Param("tripId") Long tripId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT t
        FROM TripEntity t
        JOIN FETCH t.vehicle v
        JOIN FETCH v.operator
        WHERE t.id = :tripId
        """)
    Optional<TripEntity> findByIdForUpdate(
            @Param("tripId") Long tripId
    );

    @Modifying
    @Query("""
        UPDATE TripEntity t
        SET t.availableSeats = t.availableSeats + :seatDelta
        WHERE t.id = :tripId
          AND t.availableSeats + :seatDelta >= 0
        """)
    int updateAvailableSeats(
            @Param("tripId") Long tripId,
            @Param("seatDelta") int seatDelta
    );
}
