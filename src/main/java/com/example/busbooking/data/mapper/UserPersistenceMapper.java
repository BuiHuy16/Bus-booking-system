package com.example.busbooking.data.mapper;

import com.example.busbooking.data.entity.UserEntity;
import com.example.busbooking.domain.model.User;
import org.springframework.stereotype.Component;
import java.util.Locale;

@Component
public class UserPersistenceMapper {
    private final RolePersistenceMapper roleMapper;

    public UserPersistenceMapper(RolePersistenceMapper roleMapper) {
        this.roleMapper = roleMapper;
    }

    public User toDomain(UserEntity entity) {
        return new User(entity.getId(), entity.getFullName(), entity.getEmail(),
                entity.getPasswordHash(), entity.getPhone(), roleMapper.toDomain(entity.getRole()),
                entity.getStatus(), entity.isEmailVerified(), entity.getCreatedAt(), entity.getUpdatedAt());
    }

    public UserEntity toEntity(User user) {
        return new UserEntity(user.getId(), user.getFullName(),
                user.getEmail().strip().toLowerCase(Locale.ROOT), user.getPasswordHash(),
                user.getPhone(), roleMapper.toEntity(user.getRole()), user.getStatus(), user.isEmailVerified());
    }
}
