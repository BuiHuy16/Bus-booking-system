package com.example.busbooking.data.adapter;

import com.example.busbooking.business.port.repository.LocationRepository;
import com.example.busbooking.data.entity.LocationEntity;
import com.example.busbooking.data.mapper.LocationPersistenceMapper;
import com.example.busbooking.data.repository.SpringDataLocationRepository;
import com.example.busbooking.domain.exception.DuplicateLocationException;
import com.example.busbooking.domain.exception.ResourceNotFoundException;
import com.example.busbooking.domain.model.Location;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.Optional;

@Component
public class LocationRepositoryAdapter implements LocationRepository {
    private final SpringDataLocationRepository repository;
    private final LocationPersistenceMapper mapper;

    public LocationRepositoryAdapter(SpringDataLocationRepository repository, LocationPersistenceMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    public List<Location> search(String query) {
        return repository.search(query).stream().map(mapper::toDomain).toList();
    }

    @Override
    public Optional<Location> findById(Long id) {
        return repository.findById(id).map(mapper::toDomain);
    }

    @Override
    public boolean existsByIdentity(String name, String province, String address, Long excludedId) {
        if (excludedId == null) {
            return repository.existsByNameIgnoreCaseAndProvinceIgnoreCaseAndAddressIgnoreCase(name, province, address);
        }
        return repository.existsDuplicateExcluding(name, province, address, excludedId);
    }

    @Override
    public Location save(Location location) {
        LocationEntity entity = location.id() == null
                ? new LocationEntity(location.name(), location.province(), location.address())
                : repository.findById(location.id()).orElseThrow(() ->
                        new ResourceNotFoundException("Location not found with id: " + location.id()));
        entity.updateDetails(location.name(), location.province(), location.address());
        try {
            return mapper.toDomain(repository.saveAndFlush(entity));
        } catch (DataIntegrityViolationException error) {
            throw new DuplicateLocationException();
        }
    }
}
