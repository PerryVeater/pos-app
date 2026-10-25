package com.example.posapp.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.posapp.entity.PosSession;

/**
 * Repository interface for managing {@link PosSession} entities, the
 * store-scoped bearer sessions issued by POS PIN authentication.
 * <p>
 * The read path only needs the token-hash lookup, since the raw bearer
 * token is never stored and the hash is the value derived from whatever a
 * client presents. The unique constraint {@code uk_pos_session_token_hash}
 * guarantees at most one row per hash.
 * </p>
 * <p>
 * The remaining operations exist for the lifecycle that keeps the table
 * honest: revoking the oldest sessions when an employee exceeds the
 * concurrency cap, revoking every live session when the credential or the
 * access behind it changes, and removing rows long after they expired. The
 * revoke and delete statements are bulk operations, so callers must run
 * them in a transaction and must not expect managed entities to be
 * refreshed - nothing in these flows holds a session in the persistence
 * context afterwards.
 * </p>
 */
public interface PosSessionRepository extends JpaRepository<PosSession, Long> {

    /**
     * Find the session whose stored token hash matches the supplied digest.
     * @param tokenHash the SHA-256 hex digest of a presented bearer token
     * @return an Optional containing the session if one matches
     */
    Optional<PosSession> findByTokenHash(String tokenHash);

    /**
     * List an employee's live sessions oldest first, which is the order the
     * cap revokes in: the terminal that authenticated first is the one a
     * human is least likely to still be standing at.
     * <p>
     * Served by {@code idx_pos_session_employee_live}, the partial index on
     * {@code (employee_id, created_at)} restricted to rows that are not
     * revoked.
     * </p>
     * @param employeeId the employee ID
     * @param now the reference instant; sessions expiring at or before it
     *        are not live and are ignored
     * @return live sessions, oldest first
     */
    @Query("""
            select s from PosSession s
            where s.employee.id = :employeeId
              and s.revokedAt is null
              and s.expiresAt > :now
            order by s.createdAt asc, s.id asc
            """)
    List<PosSession> findLiveByEmployeeId(@Param("employeeId") Long employeeId,
                                          @Param("now") Instant now);

    /**
     * Revoke every live session of an employee. Used when the credential or
     * the entitlement behind the session changes: a rotated or removed PIN,
     * or an employee who is no longer active.
     * @param employeeId the employee ID
     * @param revokedAt the revocation instant to stamp
     * @return the number of sessions revoked
     */
    @Modifying
    @Query("""
            update PosSession s set s.revokedAt = :revokedAt
            where s.employee.id = :employeeId and s.revokedAt is null
            """)
    int revokeLiveForEmployee(@Param("employeeId") Long employeeId,
                              @Param("revokedAt") Instant revokedAt);

    /**
     * Revoke the live sessions an employee holds at one store. Removing a
     * store assignment ends access to that store only, so sessions at other
     * stores must survive.
     * @param employeeId the employee ID
     * @param storeId the store ID
     * @param revokedAt the revocation instant to stamp
     * @return the number of sessions revoked
     */
    @Modifying
    @Query("""
            update PosSession s set s.revokedAt = :revokedAt
            where s.employee.id = :employeeId
              and s.store.id = :storeId
              and s.revokedAt is null
            """)
    int revokeLiveForEmployeeAtStore(@Param("employeeId") Long employeeId,
                                     @Param("storeId") Long storeId,
                                     @Param("revokedAt") Instant revokedAt);

    /**
     * Select the next batch of rows the purge may delete: sessions whose
     * absolute expiry is older than the retention window.
     * <p>
     * Revocated rows need no separate condition. Every row carries an
     * {@code expires_at}, so a session revoked early still expires and is
     * removed by this same predicate; the retention window is what keeps
     * logout history readable for a while.
     * </p>
     * <p>
     * Ordered by ID so repeated runs walk the table in a stable direction
     * while it shrinks, and capped by the caller's {@link Pageable} so one
     * transaction never deletes an unbounded number of rows. Served by
     * {@code idx_pos_session_expires_at}.
     * </p>
     * @param cutoff rows expiring before this instant are purgeable
     * @param pageable the batch bound (page size; offset must stay 0
     *        because deleted rows are not revisited)
     * @return the IDs in the batch, possibly empty
     */
    @Query("""
            select s.id from PosSession s
            where s.expiresAt < :cutoff
            order by s.id asc
            """)
    List<Long> findPurgeableIds(@Param("cutoff") Instant cutoff, Pageable pageable);

    /**
     * Delete a batch of sessions by ID. A bulk statement rather than
     * entity-by-entity removal: sessions have no children, and the purge
     * must not load rows it is about to discard.
     * @param ids the IDs to delete
     * @return the number of rows deleted
     */
    @Modifying
    @Query("delete from PosSession s where s.id in :ids")
    int deleteByIds(@Param("ids") List<Long> ids);
}
