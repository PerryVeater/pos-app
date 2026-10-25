package com.example.posapp.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.posapp.entity.PinSecretAnchor;

/**
 * Repository for the single {@link PinSecretAnchor} row that records which
 * fingerprint secret this database was written under.
 * <p>
 * There is nothing to query by: the row's id is fixed, so every access is
 * {@code findById(PinSecretAnchor.SINGLETON_ID)} or a save of that same
 * row. Kept as its own repository rather than folded into an existing one
 * because the anchor is not employee, store or session data - it is a
 * property of the database as a whole.
 * </p>
 */
public interface PinSecretAnchorRepository extends JpaRepository<PinSecretAnchor, Long> {
}
