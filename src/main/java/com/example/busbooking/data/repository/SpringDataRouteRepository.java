package com.example.busbooking.data.repository;

import com.example.busbooking.data.entity.RouteEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface SpringDataRouteRepository extends JpaRepository<RouteEntity, Long> {
    @Query("""
            select route from RouteEntity route
            join fetch route.origin origin
            join fetch route.destination destination
            where (:originId is null or route.originId = :originId)
              and (:destinationId is null or route.destinationId = :destinationId)
              and (:status is null or route.status = :status)
            order by lower(origin.province), lower(origin.name),
                     lower(destination.province), lower(destination.name), route.id
            """)
    List<RouteEntity> search(@Param("originId") Long originId,
                             @Param("destinationId") Long destinationId,
                             @Param("status") String status);

    @Query("""
            select route from RouteEntity route
            join fetch route.origin
            join fetch route.destination
            where route.id = :id
            """)
    Optional<RouteEntity> findDetailedById(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select route from RouteEntity route where route.id = :id")
    Optional<RouteEntity> findByIdForUpdate(@Param("id") Long id);

    boolean existsByOriginIdAndDestinationId(Long originId, Long destinationId);

    @Query("""
            select (count(route) > 0) from RouteEntity route
            where route.originId = :originId and route.destinationId = :destinationId
              and route.id <> :excludedId
            """)
    boolean existsEndpointsExcluding(@Param("originId") Long originId,
                                     @Param("destinationId") Long destinationId,
                                     @Param("excludedId") Long excludedId);

    @Query(value = "SELECT EXISTS (SELECT 1 FROM trips WHERE route_id = :routeId)", nativeQuery = true)
    boolean hasAnyTrips(@Param("routeId") Long routeId);

    @Query(value = """
            SELECT EXISTS (
                SELECT 1 FROM trips
                WHERE route_id = :routeId AND status IN ('SCHEDULED', 'IN_PROGRESS')
            )
            """, nativeQuery = true)
    boolean hasOperationalTrips(@Param("routeId") Long routeId);
}
