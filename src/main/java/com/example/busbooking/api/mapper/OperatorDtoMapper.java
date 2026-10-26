package com.example.busbooking.api.mapper;

import com.example.busbooking.api.dto.operator.OperatorResponse;
import com.example.busbooking.domain.model.Operator;
import org.springframework.stereotype.Component;

@Component
public class OperatorDtoMapper {
    public OperatorResponse toResponse(Operator operator) {
        return new OperatorResponse(operator.id(), operator.name(), operator.address(), operator.phone(),
                operator.email(), operator.status(), operator.createdAt());
    }
}
