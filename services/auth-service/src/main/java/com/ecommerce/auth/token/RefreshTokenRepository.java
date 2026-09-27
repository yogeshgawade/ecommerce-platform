package com.ecommerce.auth.token;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

@Repository
public class RefreshTokenRepository {

    private final JdbcClient jdbcClient;

    public RefreshTokenRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public RefreshToken save(RefreshToken token) {
        jdbcClient.sql("""
                        INSERT INTO refresh_tokens (
                            id, user_id, token_hash, expires_at, revoked,
                            created_at, replaced_by
                        )
                        VALUES (
                            :id, :userId, :tokenHash, :expiresAt, :revoked,
                            :createdAt, :replacedBy
                        )
                        """)
                .param("id", token.id())
                .param("userId", token.userId())
                .param("tokenHash", token.tokenHash())
                .param("expiresAt", token.expiresAt().atOffset(ZoneOffset.UTC))
                .param("revoked", token.revoked())
                .param("createdAt", token.createdAt().atOffset(ZoneOffset.UTC))
                .param("replacedBy", token.replacedBy())
                .update();

        return token;
    }

    public Optional<RefreshToken> findActiveByHash(String tokenHash) {
        return jdbcClient.sql("""
                        SELECT id, user_id, token_hash, expires_at, revoked,
                               created_at, replaced_by
                        FROM refresh_tokens
                        WHERE token_hash = :tokenHash
                          AND revoked = FALSE
                        """)
                .param("tokenHash", tokenHash)
                .query((rs, rowNum) -> new RefreshToken(
                        rs.getObject("id", UUID.class),
                        rs.getObject("user_id", UUID.class),
                        rs.getString("token_hash"),
                        rs.getTimestamp("expires_at").toInstant(),
                        rs.getBoolean("revoked"),
                        rs.getTimestamp("created_at").toInstant(),
                        rs.getObject("replaced_by", UUID.class)
                ))
                .optional();
    }

    public void revoke(UUID id, UUID replacedBy) {
        jdbcClient.sql("""
                        UPDATE refresh_tokens
                        SET revoked = TRUE, replaced_by = :replacedBy
                        WHERE id = :id
                        """)
                .param("id", id)
                .param("replacedBy", replacedBy)
                .update();
    }

    public void revokeByHash(String tokenHash) {
        jdbcClient.sql("""
                        UPDATE refresh_tokens
                        SET revoked = TRUE
                        WHERE token_hash = :tokenHash
                        """)
                .param("tokenHash", tokenHash)
                .update();
    }
}
