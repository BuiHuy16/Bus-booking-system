package com.example.busbooking.data.adapter;

import com.example.busbooking.business.port.repository.RouteRepository;
import com.example.busbooking.data.entity.RouteEntity;
import com.example.busbooking.data.mapper.RoutePersistenceMapper;
import com.example.busbooking.data.repository.SpringDataRouteRepository;
import com.example.busbooking.domain.enums.RouteStatus;
import com.example.busbooking.domain.exception.DuplicateRouteException;
import com.example.busbooking.domain.exception.ResourceNotFoundException;
import com.example.busbooking.domain.model.Route;
import jakarta.persistence.EntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.Optional;

@Component
public class RouteRepositoryAdapter implements RouteRepository {
    private final SpringDataRouteRepository repository;
    private final RoutePersistenceMapper mapper;
    private final EntityManager entityManager;

    public RouteRepositoryAdapter(SpringDataRouteRepository repository, RoutePersistenceMapper mapper,
                                  EntityManager entityManager) {
        this.repository = repository;
        this.mapper = mapper;
        this.entityManager = entityManager;
    }

    @Override
    public List<Route> search(Long originId, Long destinationId, RouteStatus status) {
        return repository.search(originId, destinationId, status == null ? null : status.name()).stream()
                .map(mapper::toDomain).toList();
    }

    @Override
    public Optional<Route> findById(Long id) {
        return repository.findDetailedById(id).map(mapper::toDomain);
    }

    @Override
    public Optional<Route> findByIdForUpdate(Long id) {
        return repository.findByIdForUpdate(id).map(entity -> repository.findDetailedById(id)
                .map(mapper::toDomain).orElseThrow());
    }

    @Override
    public boolean existsByEndpoints(Long originId, Long destinationId, Long excludedId) {
        return excludedId == null
                ? repository.existsByOriginIdAndDestinationId(originId, destinationId)
                : repository.existsEndpointsExcluding(originId, destinationId, excludedId);
    }

    @Override
    public boolean hasAnyTrips(Long id) {
        return repository.hasAnyTrips(id);
    }

    @Override
    public boolean hasOperationalTrips(Long id) {
        return repository.hasOperationalTrips(id);
    }

    @Override
    public Route save(Route route) {
        RouteEntity entity = route.id() == null
                ? new RouteEntity(route.origin().id(), route.destination().id(),
                        route.estimatedDurationMinutes(), route.status().name())
                : repository.findById(route.id()).orElseThrow(() ->
                        new ResourceNotFoundException("Route not found with id: " + route.id()));
        entity.updateDetails(route.origin().id(), route.destination().id(),
                route.estimatedDurationMinutes(), route.status().name());
        try {
            repository.saveAndFlush(entity);
            // Scalar foreign keys own the relationship columns. Refresh so an endpoint update does
            // not return already-initialized origin/destination associations from before the change.
            entityManager.refresh(entity);
            return repository.findDetailedById(entity.getId()).map(mapper::toDomain).orElseThrow();
        } catch (DataIntegrityViolationException error) {
            if (hasConstraint(error, "uk_routes_origin_destination")) {
                throw new DuplicateRouteException();
            }
            throw error;
        }
    }

    private boolean hasConstraint(Throwable error, String constraint) {
        for (Throwable current = error; current != null; current = current.getCause()) {
            if (current.getMessage() != null && current.getMessage().contains(constraint)) return true;
        }
        return false;
    }
}
