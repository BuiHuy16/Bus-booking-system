package com.example.busbooking.business.port.repository;

import com.example.busbooking.domain.enums.OperatorStatus;
import com.example.busbooking.domain.model.Operator;
import java.util.List;
import java.util.Optional;

public interface OperatorRepository {
    List<Operator> search(String query, OperatorStatus status);
    Optional<Operator> findByIdForUpdate(Long id);
    boolean existsByName(String name, Long excludedId);
    boolean existsByEmail(String email, Long excludedId);
    boolean hasOperationalDependencies(Long id);
    Operator save(Operator operator);
}
