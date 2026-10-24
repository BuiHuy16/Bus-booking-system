package com.example.busbooking.business.port.repository;

import com.example.busbooking.domain.model.Route;
import java.util.Optional;

public interface RouteRepository {
    Optional<Route> findById(Long id);
}
