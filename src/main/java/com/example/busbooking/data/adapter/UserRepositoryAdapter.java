package com.example.busbooking.data.adapter;

import com.example.busbooking.business.port.repository.UserRepository;
import com.example.busbooking.data.entity.UserEntity;
import com.example.busbooking.data.mapper.UserPersistenceMapper;
import com.example.busbooking.data.repository.SpringDataUserRepository;
import com.example.busbooking.domain.model.User;
import com.example.busbooking.domain.enums.RoleName;
import com.example.busbooking.domain.enums.UserStatus;
import com.example.busbooking.domain.exception.SuperAdminAlreadyExistsException;
import com.example.busbooking.domain.exception.DuplicateEmailException;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import jakarta.persistence.EntityManager;
import java.util.Locale;
import java.util.Optional;

@Component
@Transactional(readOnly = true)
public class UserRepositoryAdapter implements UserRepository {
    private final SpringDataUserRepository repository;
    private final UserPersistenceMapper mapper;
    private final EntityManager entityManager;

    public UserRepositoryAdapter(SpringDataUserRepository repository, UserPersistenceMapper mapper,
                                 EntityManager entityManager) {
        this.repository = repository;
        this.mapper = mapper;
        this.entityManager = entityManager;
    }

    @Override
    public Optional<User> findById(Long id) {
        return repository.findById(id).map(mapper::toDomain);
    }

    @Override
    public Optional<User> findByEmail(String email) {
        return repository.findByEmail(normalizeEmail(email)).map(mapper::toDomain);
    }

    @Override
    public boolean existsByEmail(String email) {
        return repository.existsByEmail(normalizeEmail(email));
    }

    @Override
    public Optional<User> findSuperAdmin() {
        return repository.findByRole_Name(RoleName.SUPER_ADMIN).map(mapper::toDomain);
    }

    @Override
    @Transactional
    public Optional<User> updateProfile(Long userId, String fullName, String phone) {
        if (repository.updateProfile(userId, fullName, phone, UserStatus.ACTIVE) == 0) {
            return Optional.empty();
        }
        return repository.findById(userId).map(mapper::toDomain);
    }

    @Override
    @Transactional
    public User save(User user) {
        try {
            UserEntity saved = repository.saveAndFlush(mapper.toEntity(user));
            // Timestamps are owned by PostgreSQL defaults/trigger. Reload after flush.
            entityManager.refresh(saved);
            return mapper.toDomain(saved);
        } catch (DataIntegrityViolationException exception) {
            for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
                if (cause instanceof ConstraintViolationException constraint
                        && "23505".equals(constraint.getSQLState())
                        && "uk_users_email".equals(constraint.getConstraintName())) {
                    throw new DuplicateEmailException();
                }
                if (cause instanceof ConstraintViolationException constraint
                        && "23505".equals(constraint.getSQLState())
                        && "uk_users_single_super_admin".equals(constraint.getConstraintName())) {
                    throw new SuperAdminAlreadyExistsException();
                }
            }
            throw exception;
        }
    }

    private String normalizeEmail(String email) {
        return email.strip().toLowerCase(Locale.ROOT);
    }
}
