package com.example.posapp.service;

import java.time.Instant;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

/**
 * Refuses to start a database whose PIN fingerprints were written under a
 * different {@code pos.pin.hmac-secret} than the one configured now.
 * <p>
 * The fingerprint is keyed, so a claim row means nothing under any other
 * secret, and the PINs are not recoverable - only salted PBKDF2 hashes are
 * stored. A changed secret therefore cannot be repaired by re-deriving
 * anything: every claim has to be written again from a PIN somebody types.
 * Left undetected the failure is silent and total - the application starts,
 * every login fails, and the cause looks exactly like a wrong PIN (and, now
 * that failures are counted, like an attack in progress). One row recording
 * which secret the database was anchored with turns that into a refusal to
 * boot, with a message that says what happened and what to do.
 * </p>
 * <p>
 * <b>Rotation is re-enrollment.</b> There is no transparent rotation,
 * because recomputing a fingerprint needs the PIN. The supported procedure
 * is to arm {@code pos.pin.fingerprint-reset}, start once, and have every
 * employee set their PIN again: the claims are dropped, so nobody can
 * authenticate until they do. Arming the flag is the operator saying the
 * old secret is compromised and the outage is acceptable; it is never
 * implicit.
 * </p>
 * <p>
 * The check runs at context refresh ({@link PostConstruct}), before the web
 * server accepts traffic, and costs one PBKDF2 derivation at startup. On
 * the first startup after the anchor table exists the configured secret is
 * adopted as the anchor: it is the only secret the deployment has ever been
 * given, so the existing claims are by definition the ones it produced.
 * </p>
 * <p>
 * The anchoring write assumes one instance reaches an empty anchor table.
 * Two replicas booting for the first time at the same instant collide on the
 * fixed primary key, and the loser fails to start - loudly, and without
 * touching data, which is the right failure for a race this rare. Giving the
 * anchor an advisory-lock-protected insert is M7 work, alongside the shared
 * rate limiter D10 already calls for.
 * </p>
 */
@Component
public class PinSecretGuard {

    private static final Logger LOG = LoggerFactory.getLogger(PinSecretGuard.class);

    private final PinProperties pinProperties;
    private final PinHasher pinHasher;
    private final PinSecretAnchorWriter anchorWriter;

    /**
     * Constructor for PinSecretGuard.
     * @param pinProperties the configured PIN policy, including the secret
     * @param pinHasher the hasher used to hash and verify the secret itself
     * @param anchorWriter transactional access to the anchor and the claims
     */
    public PinSecretGuard(PinProperties pinProperties, PinHasher pinHasher,
                          PinSecretAnchorWriter anchorWriter) {
        this.pinProperties = pinProperties;
        this.pinHasher = pinHasher;
        this.anchorWriter = anchorWriter;
    }

    /**
     * Verify the configured secret against the anchor, before the
     * application is allowed to serve requests.
     * @throws IllegalStateException when the secret changed and rotation
     *         was not armed
     */
    @PostConstruct
    void verifyBoundSecret() {
        verifyOrAnchor();
    }

    /**
     * Compare the configured secret with the anchored one and act on the
     * result: adopt it on a database that has no anchor yet, accept it
     * quietly when it matches, reset the claims when rotation is armed, and
     * otherwise fail. Public so a test can drive every branch without
     * rebooting a context; nothing in the application calls it except the
     * startup hook above.
     * @throws IllegalStateException when the secret changed and rotation
     *         was not armed
     */
    public void verifyOrAnchor() {
        String secret = pinProperties.hmacSecret();
        Optional<String> anchored = anchorWriter.findSecretHash();
        if (anchored.isEmpty()) {
            anchorWriter.anchor(pinHasher.hash(secret), Instant.now());
            LOG.info("Anchored the POS PIN fingerprint secret for this database");
            return;
        }
        if (pinHasher.verify(secret, anchored.get())) {
            if (pinProperties.fingerprintReset()) {
                LOG.warn("pos.pin.fingerprint-reset is armed but the configured secret already"
                        + " matches the anchor; nothing was reset. Unset it to stop this warning.");
            }
            return;
        }
        if (!pinProperties.fingerprintReset()) {
            throw new IllegalStateException(describeMismatch());
        }
        long droppedClaims = anchorWriter.reset(pinHasher.hash(secret), Instant.now());
        LOG.warn("POS PIN fingerprint secret rotated: droppedClaims={} dropped; every employee must"
                + " set their PIN again before they can authenticate. Unset pos.pin.fingerprint-reset.",
                droppedClaims);
    }

    /**
     * Explain a mismatch without quoting the secret, the anchored hash or a
     * fingerprint. The message has to be actionable on a server console at
     * three in the morning, so it says what the change means and the exact
     * way to make it deliberate.
     * @return the failure message
     */
    private static String describeMismatch() {
        return "pos.pin.hmac-secret does not match the secret this database was fingerprinted with."
                + " Every store-scoped PIN claim was derived under the previous secret, so no"
                + " submitted PIN could match one and each employee's login would fail exactly like"
                + " a wrong PIN. Refusing to start."
                + " To rotate deliberately, start once with pos.pin.fingerprint-reset=true: the"
                + " claims are dropped and every employee must set their PIN again before they can"
                + " log in.";
    }
}
