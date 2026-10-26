package com.example.busbooking.business.port.repository;

import com.example.busbooking.domain.model.Location;
import java.util.List;
import java.util.Optional;

public interface LocationRepository {
    List<Location> search(String query);
    Optional<Location> findById(Long id);
    boolean existsByIdentity(String name, String province, String address, Long excludedId);
    Location save(Location location);
}
