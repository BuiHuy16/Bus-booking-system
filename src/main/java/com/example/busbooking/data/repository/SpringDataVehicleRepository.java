package com.example.busbooking.data.repository;

import com.example.busbooking.data.entity.VehicleEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface SpringDataVehicleRepository extends JpaRepository<VehicleEntity, Long> {
    @Query("""
            select vehicle from VehicleEntity vehicle
            join fetch vehicle.operator operator
            where (:query = '' or lower(vehicle.licensePlate) like lower(concat('%', replace(:query, ' ', ''), '%'))
                    or lower(vehicle.vehicleType) like lower(concat('%', :query, '%')))
              and (:operatorId is null or vehicle.operatorId = :operatorId)
              and (:status is null or vehicle.status = :status)
            order by lower(operator.name), lower(vehicle.licensePlate), vehicle.id
            """)
    List<VehicleEntity> search(@Param("query") String query,
                               @Param("operatorId") Long operatorId,
                               @Param("status") String status);

    @Query("""
            select vehicle from VehicleEntity vehicle
            join fetch vehicle.operator
            where vehicle.id = :id
            """)
    Optional<VehicleEntity> findDetailedById(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select vehicle from VehicleEntity vehicle where vehicle.id = :id")
    Optional<VehicleEntity> findByIdForUpdate(@Param("id") Long id);

    boolean existsByLicensePlate(String licensePlate);

    @Query("select (count(vehicle) > 0) from VehicleEntity vehicle where vehicle.licensePlate = :licensePlate and vehicle.id <> :excludedId")
    boolean existsLicensePlateExcluding(@Param("licensePlate") String licensePlate,
                                        @Param("excludedId") Long excludedId);

    @Query(value = "SELECT EXISTS (SELECT 1 FROM trips WHERE vehicle_id = :vehicleId)", nativeQuery = true)
    boolean hasAnyTrips(@Param("vehicleId") Long vehicleId);

    @Query(value = """
            SELECT EXISTS (
                SELECT 1 FROM trips
                WHERE vehicle_id = :vehicleId AND status IN ('SCHEDULED', 'IN_PROGRESS')
            )
            """, nativeQuery = true)
    boolean hasOperationalTrips(@Param("vehicleId") Long vehicleId);
}
