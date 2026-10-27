package com.example.busbooking.business.service;

import com.example.busbooking.business.command.SaveLocationCommand;
import com.example.busbooking.business.port.repository.LocationRepository;
import com.example.busbooking.domain.exception.BusinessException;
import com.example.busbooking.domain.exception.DuplicateLocationException;
import com.example.busbooking.domain.exception.ResourceNotFoundException;
import com.example.busbooking.domain.model.Location;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
public class LocationService {
    private final LocationRepository locations;

    public LocationService(LocationRepository locations) {
        this.locations = locations;
    }

    @Transactional(readOnly = true)
    public List<Location> search(String query) {
        String normalizedQuery = normalizeQuery(query);
        return locations.search(normalizedQuery);
    }

    @Transactional
    public Location create(SaveLocationCommand command) {
        Location location = normalize(command, null);
        requireUnique(location, null);
        return locations.save(location);
    }

    @Transactional
    public Location update(Long locationId, SaveLocationCommand command) {
        if (locationId == null || locationId <= 0) {
            throw new ResourceNotFoundException("Location not found with id: " + locationId);
        }
        locations.findById(locationId)
                .orElseThrow(() -> new ResourceNotFoundException("Location not found with id: " + locationId));
        Location location = normalize(command, locationId);
        requireUnique(location, locationId);
        return locations.save(location);
    }

    private void requireUnique(Location location, Long excludedId) {
        if (locations.existsByIdentity(location.name(), location.province(), location.address(), excludedId)) {
            throw new DuplicateLocationException();
        }
    }

    private Location normalize(SaveLocationCommand command, Long id) {
        if (command == null) {
            throw new BusinessException("Location data is required");
        }
        String name = normalizeRequired(command.name(), "name", 100);
        String province = normalizeRequired(command.province(), "province", 100);
        String address = normalizeRequired(command.address(), "address", 255);
        return new Location(id, name, province, address);
    }

    private String normalizeQuery(String query) {
        if (query == null || query.isBlank()) return "";
        String normalized = normalizeWhitespace(query);
        if (normalized.length() > 100) {
            throw new BusinessException("Location search query must not exceed 100 characters");
        }
        return normalized;
    }

    private String normalizeRequired(String value, String field, int maxLength) {
        if (value == null) throw new BusinessException(field + " is required");
        String normalized = normalizeWhitespace(value);
        if (normalized.isEmpty() || normalized.length() > maxLength) {
            throw new BusinessException(field + " must contain between 1 and " + maxLength + " characters");
        }
        return normalized;
    }

    private String normalizeWhitespace(String value) {
        return value.strip().replaceAll("\\s+", " ");
    }
}
