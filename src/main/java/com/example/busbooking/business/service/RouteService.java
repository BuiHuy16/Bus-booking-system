package com.example.busbooking.business.service;

import com.example.busbooking.business.command.SaveRouteCommand;
import com.example.busbooking.business.port.repository.LocationRepository;
import com.example.busbooking.business.port.repository.RouteRepository;
import com.example.busbooking.domain.enums.RouteStatus;
import com.example.busbooking.domain.exception.*;
import com.example.busbooking.domain.model.Location;
import com.example.busbooking.domain.model.Route;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
public class RouteService {
    private final RouteRepository routes;
    private final LocationRepository locations;

    public RouteService(RouteRepository routes, LocationRepository locations) {
        this.routes = routes;
        this.locations = locations;
    }

    @Transactional(readOnly = true)
    public List<Route> search(Long originId, Long destinationId, RouteStatus status) {
        requireOptionalPositive(originId, "originId");
        requireOptionalPositive(destinationId, "destinationId");
        return routes.search(originId, destinationId, status);
    }

    @Transactional
    public Route create(SaveRouteCommand command) {
        Route route = validated(command, null, RouteStatus.ACTIVE);
        requireUnique(route, null);
        return routes.save(route);
    }

    @Transactional
    public Route update(Long routeId, SaveRouteCommand command) {
        if (routeId == null || routeId <= 0) {
            throw new ResourceNotFoundException("Route not found with id: " + routeId);
        }
        Route current = routes.findByIdForUpdate(routeId)
                .orElseThrow(() -> new ResourceNotFoundException("Route not found with id: " + routeId));
        if (command == null || command.status() == null) {
            throw new BusinessException("status is required");
        }
        Route replacement = validated(command, routeId, command.status());
        boolean endpointsChanged = !current.origin().id().equals(replacement.origin().id())
                || !current.destination().id().equals(replacement.destination().id());
        if (endpointsChanged && routes.hasAnyTrips(routeId)) {
            throw new RouteEndpointsLockedException();
        }
        requireUnique(replacement, routeId);
        if (current.status() == RouteStatus.ACTIVE && replacement.status() == RouteStatus.INACTIVE
                && routes.hasOperationalTrips(routeId)) {
            throw new RouteInUseException();
        }
        return routes.save(replacement);
    }

    private Route validated(SaveRouteCommand command, Long id, RouteStatus status) {
        if (command == null) throw new BusinessException("Route data is required");
        if (command.originId() == null || command.originId() <= 0) {
            throw new BusinessException("originId must be a positive number");
        }
        if (command.destinationId() == null || command.destinationId() <= 0) {
            throw new BusinessException("destinationId must be a positive number");
        }
        if (command.originId().equals(command.destinationId())) {
            throw new BusinessException("originId and destinationId must be different");
        }
        if (command.estimatedDurationMinutes() == null || command.estimatedDurationMinutes() <= 0) {
            throw new BusinessException("estimatedDurationMinutes must be positive");
        }
        Location origin = location(command.originId());
        Location destination = location(command.destinationId());
        return new Route(id, origin, destination, command.estimatedDurationMinutes(), status);
    }

    private Location location(Long id) {
        return locations.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Location not found with id: " + id));
    }

    private void requireUnique(Route route, Long excludedId) {
        if (routes.existsByEndpoints(route.origin().id(), route.destination().id(), excludedId)) {
            throw new DuplicateRouteException();
        }
    }

    private void requireOptionalPositive(Long id, String field) {
        if (id != null && id <= 0) throw new BusinessException(field + " must be a positive number");
    }
}
