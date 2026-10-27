package com.example.busbooking.data.mapper;

import com.example.busbooking.data.entity.LocationEntity;
import com.example.busbooking.domain.model.Location;
import org.springframework.stereotype.Component;

@Component
public class LocationPersistenceMapper {
    public Location toDomain(LocationEntity entity) {
        return new Location(entity.getId(), entity.getName(), entity.getProvince(), entity.getAddress());
    }
}
