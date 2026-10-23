package com.example.busbooking.data.adapter;

import com.example.busbooking.business.port.repository.*;
import com.example.busbooking.domain.enums.RoleName;
import com.example.busbooking.domain.exception.*;
import com.example.busbooking.domain.model.User;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Component
public class RoleManagementRepositoryAdapter implements RoleManagementRepository {
    private final JdbcTemplate jdbc;
    private final UserRepository users;
    public RoleManagementRepositoryAdapter(JdbcTemplate jdbc, UserRepository users) {
        this.jdbc = jdbc; this.users = users;
    }

    @Override
    @Transactional
    public User changeRole(Long actorId, Long targetId, RoleName role) {
        Account actor = lock(actorId).stream().findFirst().orElseThrow(InvalidCredentialsException::new);
        if (!actor.active() || !actor.verified()) { throw new InvalidCredentialsException(); }
        if (actor.role() != RoleName.SUPER_ADMIN) { throw new ForbiddenOperationException(); }
        if (role != RoleName.USER && role != RoleName.ADMIN) {
            throw new InvalidAuthInputException("role", "Only USER or ADMIN can be assigned");
        }
        Account target = lock(targetId).stream().findFirst().orElseThrow(ResourceNotFoundException::new);
        if (target.role() == RoleName.SUPER_ADMIN) { throw new ForbiddenOperationException(); }
        if (role == RoleName.ADMIN && (!target.active() || !target.verified())) { throw new AccountNotEligibleException(); }
        if (target.role() != role) {
            var roleIds = jdbc.queryForList("SELECT id FROM roles WHERE name = ?", Long.class, role.name());
            if (roleIds.isEmpty()) { throw new IllegalStateException("Required managed role is missing"); }
            jdbc.update("UPDATE users SET role_id = ? WHERE id = ?", roleIds.getFirst(), targetId);
        }
        return users.findById(targetId).orElseThrow(ResourceNotFoundException::new);
    }

    private List<Account> lock(Long id) {
        return jdbc.query("""
                SELECT u.status, u.email_verified, r.name
                FROM users u JOIN roles r ON r.id = u.role_id WHERE u.id = ? FOR UPDATE OF u
                """, (row, index) -> new Account("ACTIVE".equals(row.getString("status")),
                row.getBoolean("email_verified"), RoleName.valueOf(row.getString("name"))), id);
    }
    private record Account(boolean active, boolean verified, RoleName role) { }
}
