package com.example.busbooking.api.mapper;

import com.example.busbooking.api.dto.route.RouteResponse;
import com.example.busbooking.domain.model.Route;
import org.springframework.stereotype.Component;

@Component
public class RouteDtoMapper {
    private final LocationDtoMapper locations;

    public RouteDtoMapper(LocationDtoMapper locations) {
        this.locations = locations;
    }

    public RouteResponse toResponse(Route route) {
        return new RouteResponse(route.id(), locations.toResponse(route.origin()),
                locations.toResponse(route.destination()), route.estimatedDurationMinutes(), route.status());
    }
}
