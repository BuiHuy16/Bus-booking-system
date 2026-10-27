package com.example.busbooking.data.mapper;

import com.example.busbooking.data.entity.OperatorEntity;
import com.example.busbooking.domain.model.Operator;
import org.springframework.stereotype.Component;

@Component
public class OperatorPersistenceMapper {
    public Operator toDomain(OperatorEntity entity) {
        return new Operator(entity.getId(), entity.getName(), entity.getAddress(), entity.getPhone(),
                entity.getEmail(), entity.getStatus(), entity.getCreatedAt());
    }
}
