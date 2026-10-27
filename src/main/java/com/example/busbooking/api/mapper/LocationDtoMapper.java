package com.example.busbooking.api.mapper;

import com.example.busbooking.api.dto.location.LocationResponse;
import com.example.busbooking.domain.model.Location;
import org.springframework.stereotype.Component;

@Component
public class LocationDtoMapper {
    public LocationResponse toResponse(Location location) {
        return new LocationResponse(location.id(), location.name(), location.province(), location.address());
    }
}
