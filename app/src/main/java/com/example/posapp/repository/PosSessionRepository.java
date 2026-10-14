package com.example.posapp.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.posapp.entity.PosSession;

/**
 * Repository interface for managing {@link PosSession} entities, the
 * store-scoped bearer sessions issued by POS PIN authentication.
 * <p>
 * The only lookup the authentication path needs is by the SHA-256 token
 * hash, since the raw bearer token is never stored and the hash is the
 * value derived from whatever a client presents. The unique constraint
 * {@code uk_pos_session_token_hash} guarantees at most one row per hash.
 * </p>
 */
public interface PosSessionRepository extends JpaRepository<PosSession, Long> {

    /**
     * Find the session whose stored token hash matches the supplied digest.
     * @param tokenHash the SHA-256 hex digest of a presented bearer token
     * @return an Optional containing the session if one matches
     */
    Optional<PosSession> findByTokenHash(String tokenHash);
}
