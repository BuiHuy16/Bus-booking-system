package com.example.busbooking.data.adapter;

import com.example.busbooking.business.port.repository.RoleRepository;
import com.example.busbooking.data.mapper.RolePersistenceMapper;
import com.example.busbooking.data.repository.SpringDataRoleRepository;
import com.example.busbooking.domain.enums.RoleName;
import com.example.busbooking.domain.model.Role;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.util.Optional;

@Component
@Transactional(readOnly = true)
public class RoleRepositoryAdapter implements RoleRepository {
    private final SpringDataRoleRepository repository;
    private final RolePersistenceMapper mapper;

    public RoleRepositoryAdapter(SpringDataRoleRepository repository, RolePersistenceMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    public Optional<Role> findByName(RoleName name) {
        return repository.findByName(name).map(mapper::toDomain);
    }
}
