package com.example.busbooking.data.repository;

import com.example.busbooking.data.entity.OperatorEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface SpringDataBusOperatorRepository extends JpaRepository<OperatorEntity, Long> {
    @Query(value = """
            SELECT * FROM bus_operators
            WHERE (:query = ''
                OR position(lower(:query) in lower(name)) > 0
                OR position(lower(:query) in lower(address)) > 0
                OR position(lower(:query) in lower(phone)) > 0
                OR position(lower(:query) in lower(email)) > 0)
              AND (:status = '' OR status = :status)
            ORDER BY lower(name), id
            """, nativeQuery = true)
    List<OperatorEntity> search(@Param("query") String query, @Param("status") String status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select operator from OperatorEntity operator where operator.id = :id")
    Optional<OperatorEntity> findByIdForUpdate(@Param("id") Long id);

    boolean existsByNameIgnoreCase(String name);
    boolean existsByEmail(String email);

    @Query("select (count(operator) > 0) from OperatorEntity operator where lower(operator.name) = lower(:name) and operator.id <> :excludedId")
    boolean existsNameExcluding(@Param("name") String name, @Param("excludedId") Long excludedId);

    @Query("select (count(operator) > 0) from OperatorEntity operator where operator.email = :email and operator.id <> :excludedId")
    boolean existsEmailExcluding(@Param("email") String email, @Param("excludedId") Long excludedId);

    @Query(value = """
            SELECT EXISTS (
                SELECT 1 FROM vehicles vehicle
                WHERE vehicle.operator_id = :operatorId
                  AND vehicle.status IN ('ACTIVE', 'MAINTENANCE')
            ) OR EXISTS (
                SELECT 1 FROM trips trip
                JOIN vehicles vehicle ON vehicle.id = trip.vehicle_id
                WHERE vehicle.operator_id = :operatorId
                  AND trip.status IN ('SCHEDULED', 'IN_PROGRESS')
            )
            """, nativeQuery = true)
    boolean hasOperationalDependencies(@Param("operatorId") Long operatorId);
}
