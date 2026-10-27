package com.example.busbooking.business.service;

import com.example.busbooking.business.command.SaveOperatorCommand;
import com.example.busbooking.business.port.repository.OperatorRepository;
import com.example.busbooking.domain.enums.OperatorStatus;
import com.example.busbooking.domain.exception.*;
import com.example.busbooking.domain.model.Operator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
public class OperatorService {
    private final OperatorRepository operators;

    public OperatorService(OperatorRepository operators) {
        this.operators = operators;
    }

    @Transactional(readOnly = true)
    public List<Operator> search(String query, OperatorStatus status) {
        return operators.search(OperatorInputPolicy.query(query), status);
    }

    @Transactional
    public Operator create(SaveOperatorCommand command) {
        Operator operator = normalize(command, null, OperatorStatus.ACTIVE, null);
        requireUnique(operator, null);
        return operators.save(operator);
    }

    @Transactional
    public Operator update(Long operatorId, SaveOperatorCommand command) {
        if (operatorId == null || operatorId <= 0) {
            throw new ResourceNotFoundException("Operator not found with id: " + operatorId);
        }
        Operator current = operators.findByIdForUpdate(operatorId)
                .orElseThrow(() -> new ResourceNotFoundException("Operator not found with id: " + operatorId));
        if (command == null || command.status() == null) {
            throw new InvalidOperatorInputException("status", "status is required");
        }
        Operator replacement = normalize(command, operatorId, command.status(), current.createdAt());
        requireUnique(replacement, operatorId);
        if (current.status() == OperatorStatus.ACTIVE && replacement.status() == OperatorStatus.INACTIVE
                && operators.hasOperationalDependencies(operatorId)) {
            throw new OperatorInUseException();
        }
        return operators.save(replacement);
    }

    private void requireUnique(Operator operator, Long excludedId) {
        if (operators.existsByName(operator.name(), excludedId)
                || operators.existsByEmail(operator.email(), excludedId)) {
            throw new DuplicateOperatorException();
        }
    }

    private Operator normalize(SaveOperatorCommand command, Long id, OperatorStatus status,
                               java.time.LocalDateTime createdAt) {
        if (command == null) {
            throw new InvalidOperatorInputException("request", "Operator data is required");
        }
        return new Operator(id,
                OperatorInputPolicy.text(command.name(), "name", 150),
                OperatorInputPolicy.text(command.address(), "address", 255),
                OperatorInputPolicy.phone(command.phone()),
                OperatorInputPolicy.email(command.email()),
                status,
                createdAt);
    }
}
