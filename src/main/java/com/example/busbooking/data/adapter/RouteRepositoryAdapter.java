package com.example.busbooking.data.adapter;

import com.example.busbooking.business.port.repository.RouteRepository;
import com.example.busbooking.data.repository.SpringDataRouteRepository;
import com.example.busbooking.domain.enums.RouteStatus;
import com.example.busbooking.domain.model.Route;
import org.springframework.stereotype.Component;
import java.util.Optional;

@Component
public class RouteRepositoryAdapter implements RouteRepository {
    private final SpringDataRouteRepository repository;

    public RouteRepositoryAdapter(SpringDataRouteRepository repository) {
        this.repository = repository;
    }

    public Optional<Route> findById(Long id) {
        return repository.findById(id).map(entity -> new Route(entity.getId(), RouteStatus.valueOf(entity.getStatus())));
    }
}
