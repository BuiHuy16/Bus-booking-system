package com.example.busbooking.data.mapper;

import com.example.busbooking.data.entity.RouteEntity;
import com.example.busbooking.domain.enums.RouteStatus;
import com.example.busbooking.domain.model.Location;
import com.example.busbooking.domain.model.Route;
import org.springframework.stereotype.Component;

@Component
public class RoutePersistenceMapper {
    public Route toDomain(RouteEntity entity) {
        return new Route(entity.getId(),
                new Location(entity.getOrigin().getId(), entity.getOrigin().getName(),
                        entity.getOrigin().getProvince(), entity.getOrigin().getAddress()),
                new Location(entity.getDestination().getId(), entity.getDestination().getName(),
                        entity.getDestination().getProvince(), entity.getDestination().getAddress()),
                entity.getEstimatedDurationMinutes(), RouteStatus.valueOf(entity.getStatus()));
    }
}
