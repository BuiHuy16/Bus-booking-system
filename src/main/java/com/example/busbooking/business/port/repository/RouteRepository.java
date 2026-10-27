package com.example.busbooking.business.port.repository;

import com.example.busbooking.domain.model.Route;
import com.example.busbooking.domain.enums.RouteStatus;
import java.util.List;
import java.util.Optional;

public interface RouteRepository {
    List<Route> search(Long originId, Long destinationId, RouteStatus status);
    Optional<Route> findById(Long id);
    Optional<Route> findByIdForUpdate(Long id);
    boolean existsByEndpoints(Long originId, Long destinationId, Long excludedId);
    boolean hasAnyTrips(Long id);
    boolean hasOperationalTrips(Long id);
    Route save(Route route);
}
