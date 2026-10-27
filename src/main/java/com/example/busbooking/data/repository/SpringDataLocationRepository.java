package com.example.busbooking.data.repository;

import com.example.busbooking.data.entity.LocationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface SpringDataLocationRepository extends JpaRepository<LocationEntity, Long> {
    @Query(value = """
            SELECT * FROM locations
            WHERE :query = ''
               OR position(lower(:query) in lower(name)) > 0
               OR position(lower(:query) in lower(province)) > 0
               OR position(lower(:query) in lower(address)) > 0
            ORDER BY lower(province), lower(name), id
            """, nativeQuery = true)
    List<LocationEntity> search(@Param("query") String query);

    boolean existsByNameIgnoreCaseAndProvinceIgnoreCaseAndAddressIgnoreCase(
            String name, String province, String address);

    @Query("""
            select (count(location) > 0) from LocationEntity location
            where lower(location.name) = lower(:name)
              and lower(location.province) = lower(:province)
              and lower(location.address) = lower(:address)
              and location.id <> :excludedId
            """)
    boolean existsDuplicateExcluding(@Param("name") String name,
                                     @Param("province") String province,
                                     @Param("address") String address,
                                     @Param("excludedId") Long excludedId);
}
