package com.example.posapp.service;

import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.example.posapp.repository.PosSessionRepository;

/**
 * Removes one batch of expired POS session rows.
 * <p>
 * A separate bean from {@link PosSessionRetentionJob} for the same reason
 * the PIN writer is separate from the PIN service: the job decides how
 * often and how much to purge, and this method is the unit of transaction.
 * Each batch is its own short transaction, so a purge of a large backlog
 * does not hold one transaction open for the whole run, and a batch that
 * fails leaves the already-deleted batches deleted - which is fine, because
 * deleting an expired row is idempotent and the next run picks up what is
 * left.
 * </p>
 */
@Component
public class PosSessionPurger {

    private final PosSessionRepository sessionRepository;

    /**
     * Constructor for PosSessionPurger.
     * @param sessionRepository the session repository carrying the batch
     *        select and delete statements
     */
    public PosSessionPurger(PosSessionRepository sessionRepository) {
        this.sessionRepository = sessionRepository;
    }

    /**
     * Delete at most {@code batchSize} rows whose expiry is older than the
     * cutoff, and report how many were removed.
     * @param cutoff rows expiring before this instant are deleted
     * @param batchSize the maximum number of rows this transaction touches
     * @return the number of rows deleted, {@code 0} when nothing was left
     */
    @Transactional
    public int purgeBatch(Instant cutoff, int batchSize) {
        List<Long> ids = sessionRepository.findPurgeableIds(cutoff, PageRequest.of(0, batchSize));
        if (ids.isEmpty()) {
            return 0;
        }
        return sessionRepository.deleteByIds(ids);
    }
}
