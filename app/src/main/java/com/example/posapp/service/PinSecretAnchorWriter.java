package com.example.posapp.service;

import java.time.Instant;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.posapp.entity.PinSecretAnchor;
import com.example.posapp.repository.EmployeePinStoreScopeRepository;
import com.example.posapp.repository.PinSecretAnchorRepository;

/**
 * Transactional access to the {@link PinSecretAnchor} row and the claims it
 * governs.
 * <p>
 * The class exists for the same reason {@link EmployeeStorePinWriter} does:
 * the anchor read, the claim purge and the anchor rewrite have to commit or
 * roll back together, and a self-invocation inside {@link PinSecretGuard}
 * would bypass the proxy and quietly run without a transaction. Keeping the
 * boundary here also means the guard's decisions are pure control flow that
 * a unit test can drive against a mock.
 * </p>
 * <p>
 * The reset is destructive, so it is not exposed as a general-purpose
 * operation: only {@link PinSecretGuard} calls it, and only when
 * {@code pos.pin.fingerprint-reset} is armed.
 * </p>
 */
@Service
public class PinSecretAnchorWriter {

    private final PinSecretAnchorRepository anchorRepo;
    private final EmployeePinStoreScopeRepository scopeRepo;

    /**
     * Constructor for PinSecretAnchorWriter.
     * @param anchorRepo the single-row anchor repository
     * @param scopeRepo the store-scope claims derived from the secret
     */
    public PinSecretAnchorWriter(PinSecretAnchorRepository anchorRepo,
                                 EmployeePinStoreScopeRepository scopeRepo) {
        this.anchorRepo = anchorRepo;
        this.scopeRepo = scopeRepo;
    }

    /**
     * Read the anchored secret hash, if the database has been anchored yet.
     * @return the salted hash of the anchored secret, or empty on a database
     *         that predates the anchor
     */
    @Transactional(readOnly = true)
    public Optional<String> findSecretHash() {
        return anchorRepo.findById(PinSecretAnchor.SINGLETON_ID)
                .map(PinSecretAnchor::getSecretHash);
    }

    /**
     * Anchor a secret for the first time. Called once, on the first startup
     * of a database whose fingerprints were all written under the secret in
     * the configuration.
     * @param secretHash the salted hash of the configured secret
     * @param now the anchoring instant
     */
    @Transactional
    public void anchor(String secretHash, Instant now) {
        anchorRepo.save(new PinSecretAnchor(secretHash, now));
    }

    /**
     * Re-anchor after a deliberate secret rotation, deleting every claim
     * that the old secret produced.
     * <p>
     * The claims cannot be recomputed - the PINs are not recoverable - so
     * they are dropped rather than rewritten. Each employee's claim
     * reappears the next time their PIN is set, which is the only point at
     * which a new fingerprint can be derived. Until then they cannot log
     * in, which is the intended cost of rotating the key.
     * </p>
     * @param secretHash the salted hash of the new secret
     * @param now the re-anchoring instant
     * @return the number of claims dropped
     */
    @Transactional
    public long reset(String secretHash, Instant now) {
        long droppedClaims = scopeRepo.count();
        scopeRepo.deleteAllInBatch();
        PinSecretAnchor anchor = anchorRepo.findById(PinSecretAnchor.SINGLETON_ID)
                .orElseGet(PinSecretAnchor::new);
        anchor.setSecretHash(secretHash);
        anchor.setAnchoredAt(now);
        anchorRepo.save(anchor);
        return droppedClaims;
    }
}
