package com.example.posapp.service;

import org.springframework.stereotype.Service;

import com.example.posapp.dto.PosAuthenticateResponse;
import com.example.posapp.entity.Employee;
import com.example.posapp.entity.EmployeePinCredential;
import com.example.posapp.entity.EmployeePinStoreScope;
import com.example.posapp.entity.Store;
import com.example.posapp.exception.PosAuthFailedException;
import com.example.posapp.exception.PosAuthRateLimitedException;
import com.example.posapp.repository.EmployeePinCredentialRepository;
import com.example.posapp.repository.EmployeePinStoreScopeRepository;
import com.example.posapp.repository.StoreRepository;

/**
 * Orchestrates POS PIN authentication at a store and hands a verified
 * employee to {@link PosSessionService} for session issuance.
 * <p>
 * Ordering and information discipline are the whole design:
 * <ol>
 *   <li>a locked-out {@code (store, client)} pair is refused with
 *       {@link PosAuthRateLimitedException} <em>before</em> any credential is
 *       touched, so throttling never hints at whether a PIN was right;</li>
 *   <li>the store must exist and be active;</li>
 *   <li>the PIN is fingerprinted once and resolved through
 *       {@code employee_pin_store_scope} by {@code (store_id,
 *       pin_fingerprint)}. That unique index returns at most one employee,
 *       so an attempt costs the same whether the store has three employees
 *       or three hundred - it no longer runs one PBKDF2 derivation per
 *       assigned employee;</li>
 *   <li>the submitted PIN is then verified exactly once against that
 *       employee's stored hash, or once against a decoy hash when no claim
 *       matched, so a PIN nobody at the store holds costs the same as a
 *       wrong PIN and timing cannot be used to probe claims;</li>
 *   <li>every failure - missing store, inactive store, no assignment, no
 *       credential, wrong PIN, inactive employee - collapses to one
 *       {@link PosAuthFailedException}, and the failed attempt is counted.</li>
 * </ol>
 * Nothing here reveals which of those conditions occurred, and the raw PIN is
 * only ever used transiently for verification; it is never logged, stored, or
 * returned. The fingerprint derived from it gets the same treatment: it is
 * compared and discarded, never logged. The service is intentionally
 * non-transactional: repository reads run per call and session issuance opens
 * its own transaction, mirroring the existing non-transactional orchestrator
 * convention.
 * </p>
 * <p>
 * Failures are reported to {@link PosAuthTelemetry} with only the IDs the
 * lookup actually established. When the fingerprint matched a claim, that
 * claim named an employee, so the failure is attributable to them and the
 * telemetry says so; when it matched nothing, the attempt is counted against
 * the store alone. Guessing who was targeted in the second case would turn
 * "somebody is trying PINs" into a false accusation against a named person.
 * </p>
 */
@Service
public class PosAuthenticationService {

    private final StoreRepository storeRepository;
    private final EmployeePinStoreScopeRepository scopeRepository;
    private final EmployeePinCredentialRepository credentialRepository;
    private final PinHasher pinHasher;
    private final PosAuthRateLimiter rateLimiter;
    private final PosAuthTelemetry telemetry;
    private final PosSessionService sessionService;

    /**
     * A PIN that no employee uses, hashed once on first miss so a submission
     * matching no claim still pays exactly one PBKDF2 derivation. The value
     * only ever reaches {@link PinHasher#verify}; it is never compared to a
     * real credential and never leaves this class.
     */
    private static final String COST_EQUALISING_PIN = "0000000000";

    /** Lazily computed decoy hash; volatile so the first miss publishes it. */
    private volatile String decoyHash;

    /**
     * Constructor for PosAuthenticationService.
     * @param storeRepository store lookups
     * @param scopeRepository the indexed (store, fingerprint) PIN claim lookup
     * @param credentialRepository PIN credential reads for the matched employee
     * @param pinHasher the PIN verifier
     * @param rateLimiter the per-store/client attempt limiter
     * @param telemetry the failure counters and lockout line
     * @param sessionService the session issuer
     */
    public PosAuthenticationService(StoreRepository storeRepository,
                                    EmployeePinStoreScopeRepository scopeRepository,
                                    EmployeePinCredentialRepository credentialRepository,
                                    PinHasher pinHasher,
                                    PosAuthRateLimiter rateLimiter,
                                    PosAuthTelemetry telemetry,
                                    PosSessionService sessionService) {
        this.storeRepository = storeRepository;
        this.scopeRepository = scopeRepository;
        this.credentialRepository = credentialRepository;
        this.pinHasher = pinHasher;
        this.rateLimiter = rateLimiter;
        this.telemetry = telemetry;
        this.sessionService = sessionService;
    }

    /**
     * Authenticate a PIN at a store and, on success, issue a session.
     * @param storeId the store being authenticated at
     * @param pin the PIN as submitted, used verbatim (leading zeros preserved)
     * @param clientKey the resolved client identifier for rate limiting
     * @return the issued session with its one-time raw bearer token
     * @throws PosAuthRateLimitedException when the client is in cooldown
     * @throws PosAuthFailedException for every credential-related failure
     */
    public PosAuthenticateResponse authenticate(Long storeId, String pin, String clientKey) {
        if (rateLimiter.isBlocked(storeId, clientKey)) {
            throw new PosAuthRateLimitedException();
        }
        // Read the store eagerly once: the loaded instance carries the active
        // flag used for the gate and the name used for the response, so no
        // uninitialized proxy is ever handed to the session writer.
        Store store = storeRepository.findById(storeId).filter(Store::isActive).orElse(null);
        PinAttempt attempt = store == null ? PinAttempt.NO_CLAIM : matchAssignedEmployee(storeId, pin);
        if (attempt.employee() == null) {
            // A missing/inactive store and every credential mismatch land
            // here, so the failure is indistinguishable to the caller. The
            // telemetry is told what the lookup established and nothing more.
            boolean lockoutEntered = rateLimiter.recordFailure(storeId, clientKey);
            telemetry.failedAttempt(storeId, attempt.identifiedEmployeeId(), clientKey, lockoutEntered);
            throw new PosAuthFailedException();
        }
        rateLimiter.recordSuccess(storeId, clientKey);
        return sessionService.startSession(attempt.employee(), store);
    }

    /**
     * What the credential lookup established: the authenticated employee,
     * plus the employee the submitted PIN belonged to when a claim matched.
     * The second value survives a failed verification - a claim that named an
     * employee tells us whose PIN was tried, even though the attempt failed -
     * and is {@code null} when nothing claimed the PIN, because then nobody
     * has been identified.
     * @param employee the authenticated employee, or {@code null}
     * @param identifiedEmployeeId the employee named by the claim, or
     *        {@code null} when no claim matched
     */
    private record PinAttempt(Employee employee, Long identifiedEmployeeId) {

        /** No claim matched: nothing authenticated and nobody identified. */
        static final PinAttempt NO_CLAIM = new PinAttempt(null, null);
    }

    /**
     * Resolve the employee whose PIN claim at the store matches the
     * submitted PIN, and record which employee the attempt was against even
     * when it fails. The returned employee is initialized (name and active
     * flag), so the caller can build a response without lazy access.
     * <p>
     * Exactly one call to {@link PinHasher#verify} happens on every path,
     * including the miss path where it runs against a decoy hash. That is
     * what keeps "this PIN belongs to nobody here" and "this PIN is wrong"
     * indistinguishable by timing.
     * </p>
     * @param storeId the store being authenticated at
     * @param pin the submitted PIN
     * @return the matching active employee plus the identified employee,
     *         where one or both may be absent
     */
    private PinAttempt matchAssignedEmployee(Long storeId, String pin) {
        String fingerprint = pinHasher.fingerprint(pin);
        EmployeePinStoreScope claim = scopeRepository
                .findByStoreIdAndPinFingerprint(storeId, fingerprint)
                .orElse(null);
        Employee employee = claim == null ? null : claim.getEmployee();
        Long identifiedEmployeeId = employee == null ? null : employee.getId();
        String storedHash = employee == null ? costEqualisingHash() : credentialHash(employee);
        // The submitted PIN is compared with the decoy on a miss, so the
        // decoy's own PIN would "verify" there; the employee check below is
        // what keeps that a failure.
        boolean pinMatches = pinHasher.verify(pin, storedHash);
        if (employee == null || !employee.isActive() || !pinMatches) {
            return new PinAttempt(null, identifiedEmployeeId);
        }
        return new PinAttempt(employee, identifiedEmployeeId);
    }

    /**
     * Read the matched employee's stored hash, falling back to the decoy
     * when the claim exists without a credential. That combination is not
     * reachable through the PIN writer, but falling back keeps the call
     * count fixed instead of failing fast and cheaply.
     * @param employee the employee named by the claim
     * @return the stored PBKDF2 hash, or the decoy hash
     */
    private String credentialHash(Employee employee) {
        return credentialRepository.findByEmployeeId(employee.getId())
                .map(EmployeePinCredential::getPinHash)
                .orElseGet(this::costEqualisingHash);
    }

    /**
     * The decoy hash used to charge a miss the same work as a wrong PIN.
     * Computed once, lazily, because hashing carries the configured PBKDF2
     * cost and startup should not pay it for a server that never
     * authenticates anyone.
     * @return a PBKDF2 hash of {@link #COST_EQUALISING_PIN}
     */
    private String costEqualisingHash() {
        String hash = decoyHash;
        if (hash == null) {
            hash = pinHasher.hash(COST_EQUALISING_PIN);
            decoyHash = hash;
        }
        return hash;
    }
}
