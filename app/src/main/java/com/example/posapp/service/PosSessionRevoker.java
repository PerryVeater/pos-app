package com.example.posapp.service;

import java.time.Instant;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.example.posapp.repository.PosSessionRepository;

/**
 * Revokes POS sessions when the thing that entitled them changes.
 * <p>
 * A bearer session is a claim about a moment: this employee proved a PIN at
 * this store, so let them work. Everything that could make that claim false
 * has to end the sessions it invalidates, or the token outlives the reason
 * it exists:
 * <ul>
 *   <li>the PIN is rotated or removed - whoever knew the old PIN may still
 *       be holding a valid token;</li>
 *   <li>the employee is no longer active - the shift ended, and a token is
 *       not a shift;</li>
 *   <li>a store assignment is removed - access to that store ends, and only
 *       that store's sessions should end with it.</li>
 * </ul>
 * Each method is one bulk statement in its own transaction, so a failure to
 * revoke never silently rides along inside the write that triggered it, and
 * the caller decides the instant stamped on the row. Until M4 derives store
 * access from the group hierarchy, this is the interim guard that keeps
 * sessions tied to explicit assignments.
 * </p>
 */
@Component
public class PosSessionRevoker {

    private final PosSessionRepository sessionRepository;

    /**
     * Constructor for PosSessionRevoker.
     * @param sessionRepository the session repository carrying the bulk
     *        revoke statements
     */
    public PosSessionRevoker(PosSessionRepository sessionRepository) {
        this.sessionRepository = sessionRepository;
    }

    /**
     * Revoke every live session of an employee: the credential or the
     * employment state behind them changed. Idempotent - already revoked
     * sessions are left alone, so calling it twice costs one no-op update.
     * @param employeeId the employee ID
     * @param now the revocation instant to stamp
     * @return the number of sessions this call revoked
     */
    @Transactional
    public int revokeAllForEmployee(Long employeeId, Instant now) {
        return sessionRepository.revokeLiveForEmployee(employeeId, now);
    }

    /**
     * Revoke the live sessions an employee holds at one store, leaving
     * their sessions at other stores untouched.
     * @param employeeId the employee ID
     * @param storeId the store the assignment was removed from
     * @param now the revocation instant to stamp
     * @return the number of sessions this call revoked
     */
    @Transactional
    public int revokeForEmployeeAtStore(Long employeeId, Long storeId, Instant now) {
        return sessionRepository.revokeLiveForEmployeeAtStore(employeeId, storeId, now);
    }
}
