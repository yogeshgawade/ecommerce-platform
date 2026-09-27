package com.ecommerce.auth.user;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

@Repository
public class UserRepository {

    private final JdbcClient jdbcClient;

    public UserRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public boolean existsByEmail(String email) {
        Integer count = jdbcClient.sql(
                        "SELECT COUNT(*) FROM users WHERE email = :email"
                )
                .param("email", email)
                .query(Integer.class)
                .single();

        return count > 0;
    }

    public UserAccount save(UserAccount user) {
        jdbcClient.sql("""
                        INSERT INTO users (
                            id, email, password_hash, role, enabled,
                            created_at, updated_at
                        )
                        VALUES (
                            :id, :email, :passwordHash, :role, :enabled,
                            :createdAt, :updatedAt
                        )
                        """)
                .param("id", user.id())
                .param("email", user.email())
                .param("passwordHash", user.passwordHash())
                .param("role", user.role().name())
                .param("enabled", user.enabled())
                .param("createdAt", user.createdAt().atOffset(ZoneOffset.UTC))
                .param("updatedAt", user.updatedAt().atOffset(ZoneOffset.UTC))
                .update();

        return user;
    }

    public Optional<UserAccount> findByEmail(String email) {
        return jdbcClient.sql("""
                        SELECT id, email, password_hash, role, enabled,
                               created_at, updated_at
                        FROM users
                        WHERE email = :email
                        """)
                .param("email", email)
                .query((rs, rowNum) -> new UserAccount(
                        rs.getObject("id", UUID.class),
                        rs.getString("email"),
                        rs.getString("password_hash"),
                        UserRole.valueOf(rs.getString("role")),
                        rs.getBoolean("enabled"),
                        rs.getTimestamp("created_at").toInstant(),
                        rs.getTimestamp("updated_at").toInstant()
                ))
                .optional();
    }

    public Optional<UserAccount> findById(UUID id) {
        return jdbcClient.sql("""
                        SELECT id, email, password_hash, role, enabled,
                               created_at, updated_at
                        FROM users
                        WHERE id = :id
                        """)
                .param("id", id)
                .query((rs, rowNum) -> new UserAccount(
                        rs.getObject("id", UUID.class),
                        rs.getString("email"),
                        rs.getString("password_hash"),
                        UserRole.valueOf(rs.getString("role")),
                        rs.getBoolean("enabled"),
                        rs.getTimestamp("created_at").toInstant(),
                        rs.getTimestamp("updated_at").toInstant()
                ))
                .optional();
    }
}
