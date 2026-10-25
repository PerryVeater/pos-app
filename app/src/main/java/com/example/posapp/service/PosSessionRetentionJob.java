package com.example.posapp.service;

import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Periodically deletes POS session rows that expired long enough ago to be
 * nobody's business any more.
 * <p>
 * The table only ever grows as terminals authenticate, and an expired row is
 * dead weight that still costs a token-hash lookup. Deleting them is also a
 * privacy measure: a row is a record of "this employee was at this store at
 * this hour", and keeping that forever serves nobody.
 * </p>
 * <p>
 * A run is bounded twice over. Each batch is a short transaction
 * ({@link PosSessionPurger}), and a run stops after
 * {@link #MAX_BATCHES_PER_RUN} batches even if more rows qualify, so a large
 * backlog is worked off over successive runs instead of monopolising the
 * database and the scheduler thread. Anything left is picked up by the next
 * run, which recomputes the cutoff - the operation is idempotent, and
 * running it twice in a row deletes nothing the second time.
 * </p>
 * <p>
 * The bean is conditional so tests can switch scheduling off with
 * {@code pos.session.purge-enabled=false} and drive {@link PosSessionPurger}
 * directly, instead of racing a background thread against their own rows.
 * </p>
 */
@Component
@ConditionalOnProperty(name = "pos.session.purge-enabled", havingValue = "true",
        matchIfMissing = true)
public class PosSessionRetentionJob {

    private static final Logger LOG = LoggerFactory.getLogger(PosSessionRetentionJob.class);

    /** Ceiling on batches per run: a backlog drains over runs, not one. */
    private static final int MAX_BATCHES_PER_RUN = 20;

    private final PosSessionPurger purger;
    private final SessionProperties properties;

    /**
     * Constructor for PosSessionRetentionJob.
     * @param purger the transactional batch unit
     * @param properties the retention window and batch size
     */
    public PosSessionRetentionJob(PosSessionPurger purger, SessionProperties properties) {
        this.purger = purger;
        this.properties = properties;
    }

    /**
     * Purge expired sessions, batch by batch, until the backlog is empty or
     * the per-run ceiling is reached. The interval and the startup delay are
     * configuration, not constants: a deployment that authenticates
     * thousands of shifts a day may want to run this more often than one
     * that serves a single counter.
     */
    @Scheduled(fixedDelayString = "${pos.session.purge-interval-ms:3600000}",
            initialDelayString = "${pos.session.purge-initial-delay-ms:60000}")
    public void purgeExpiredSessions() {
        Instant cutoff = Instant.now().minus(properties.retention());
        int batchSize = properties.purgeBatchSize();
        int removed = 0;
        for (int batch = 0; batch < MAX_BATCHES_PER_RUN; batch++) {
            int deleted = purger.purgeBatch(cutoff, batchSize);
            removed += deleted;
            if (deleted < batchSize) {
                // A short batch means the backlog is exhausted.
                break;
            }
        }
        if (removed > 0) {
            // Counts only: no employee, store or token material.
            LOG.info("Purged {} POS sessions that expired before {}", removed, cutoff);
        }
    }
}
