package com.example.busbooking.data.adapter;

import com.example.busbooking.business.port.repository.OperatorRepository;
import com.example.busbooking.data.entity.OperatorEntity;
import com.example.busbooking.data.mapper.OperatorPersistenceMapper;
import com.example.busbooking.data.repository.SpringDataBusOperatorRepository;
import com.example.busbooking.domain.enums.OperatorStatus;
import com.example.busbooking.domain.exception.*;
import com.example.busbooking.domain.model.Operator;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.Optional;

@Component
public class BusOperatorRepositoryAdapter implements OperatorRepository {
    private final SpringDataBusOperatorRepository repository;
    private final OperatorPersistenceMapper mapper;

    public BusOperatorRepositoryAdapter(SpringDataBusOperatorRepository repository,
                                        OperatorPersistenceMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    public List<Operator> search(String query, OperatorStatus status) {
        return repository.search(query, status == null ? "" : status.name()).stream()
                .map(mapper::toDomain).toList();
    }

    @Override
    public Optional<Operator> findByIdForUpdate(Long id) {
        return repository.findByIdForUpdate(id).map(mapper::toDomain);
    }

    @Override
    public boolean existsByName(String name, Long excludedId) {
        return excludedId == null ? repository.existsByNameIgnoreCase(name)
                : repository.existsNameExcluding(name, excludedId);
    }

    @Override
    public boolean existsByEmail(String email, Long excludedId) {
        return excludedId == null ? repository.existsByEmail(email)
                : repository.existsEmailExcluding(email, excludedId);
    }

    @Override
    public boolean hasOperationalDependencies(Long id) {
        return repository.hasOperationalDependencies(id);
    }

    @Override
    public Operator save(Operator operator) {
        OperatorEntity entity = operator.id() == null
                ? new OperatorEntity(operator.name(), operator.address(), operator.phone(), operator.email(), operator.status())
                : repository.findById(operator.id()).orElseThrow(() ->
                        new ResourceNotFoundException("Operator not found with id: " + operator.id()));
        entity.updateDetails(operator.name(), operator.address(), operator.phone(), operator.email(), operator.status());
        try {
            return mapper.toDomain(repository.saveAndFlush(entity));
        } catch (DataIntegrityViolationException error) {
            throw new DuplicateOperatorException();
        }
    }
}
