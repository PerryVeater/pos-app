package com.example.posapp.service;

import java.util.regex.Pattern;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import com.example.posapp.entity.Employee;
import com.example.posapp.entity.EmployeePinCredential;
import com.example.posapp.exception.EmployeeNotFoundException;
import com.example.posapp.exception.EmployeePinValidationException;
import com.example.posapp.repository.EmployeeRepository;

/**
 * Orchestrates the employee POS PIN lifecycle: set/change, and removal.
 * <p>
 * The service is deliberately non-transactional. All invariant-critical
 * writes live in {@link EmployeeStorePinWriter}, each call commits (or
 * rolls back) as one transaction, and this layer only:
 * <ol>
 *   <li>validates the PIN format against the configured
 *       {@link PinProperties} window - digits only, exact length, no
 *       trimming or normalization, leading zeros preserved;</li>
 *   <li>enforces the eligibility rule that only active employees may hold
 *       a PIN (removal stays allowed regardless of state);</li>
 *   <li>translates a post-rollback
 *       {@link DataIntegrityViolationException} from
 *       {@code uk_epss_store_fingerprint} into a friendly
 *       {@link EmployeePinValidationException}.</li>
 * </ol>
 * Translation never quotes the raw database message: the wrapped cause
 * contains PIN fingerprints, which are sensitive, so the generic
 * {@code ApiExceptionHandler} integrity fallback must never see these
 * failures - every conflict becomes the same clean 400 a check-time
 * rejection would produce.
 * </p>
 */
@Service
public class EmployeePinService {

    private final EmployeeRepository employeeRepo;
    private final EmployeeStorePinWriter pinWriter;
    private final PinHasher pinHasher;
    private final PinProperties pinProperties;

    /**
     * Constructor for EmployeePinService.
     * @param employeeRepo the employee repository
     * @param pinWriter the transactional PIN writer
     * @param pinHasher the credential hasher
     * @param pinProperties the configured PIN policy
     */
    public EmployeePinService(EmployeeRepository employeeRepo,
                              EmployeeStorePinWriter pinWriter,
                              PinHasher pinHasher,
                              PinProperties pinProperties) {
        this.employeeRepo = employeeRepo;
        this.pinWriter = pinWriter;
        this.pinHasher = pinHasher;
        this.pinProperties = pinProperties;
    }

    /**
     * Set or change an employee's POS PIN. The submitted value is used
     * verbatim (no trimming), must match the configured digit window, and
     * the employee must exist and be active. When the PIN collides with
     * another employee at any shared store - detected here against the
     * current claims or by the database under a concurrent write - the
     * whole change is rejected and the employee keeps the previous PIN.
     * @param employeeId the employee ID
     * @param rawPin the PIN as submitted, digits only
     * @return the persisted credential (secret material is write-only;
     *         callers must not serialize it into a response)
     * @throws EmployeeNotFoundException if the employee does not exist
     * @throws EmployeePinValidationException for a malformed PIN, an
     *         inactive employee, or a store-scoped uniqueness conflict
     */
    public EmployeePinCredential setPin(Long employeeId, String rawPin) {
        String pin = validateFormat(rawPin);
        Employee employee = employeeRepo.findById(employeeId)
                .orElseThrow(() -> new EmployeeNotFoundException(employeeId));
        if (!employee.isActive()) {
            throw new EmployeePinValidationException(
                    "PINs can only be set for active employees: employeeId=" + employeeId);
        }
        String fingerprint = pinHasher.fingerprint(pin);
        String hash = pinHasher.hash(pin);
        try {
            return pinWriter.setPin(employee, hash, fingerprint);
        } catch (DataIntegrityViolationException ex) {
            if (!isFingerprintConflict(ex)) {
                throw ex;
            }
            throw new EmployeePinValidationException(
                    "PIN is already in use by another employee assigned to the same store:"
                            + " employeeId=" + employeeId);
        }
    }

    /**
     * Remove an employee's PIN, freeing it at every assigned store.
     * Idempotent: an employee without a credential succeeds quietly.
     * Inactive employees may be removed as well - the active-only rule
     * guards usage (setting), not cleanup. Missing employees are 404.
     * @param employeeId the employee ID
     * @throws EmployeeNotFoundException if the employee does not exist
     */
    public void removePin(Long employeeId) {
        if (!employeeRepo.existsById(employeeId)) {
            throw new EmployeeNotFoundException(employeeId);
        }
        pinWriter.removePin(employeeId);
    }

    /**
     * Validate the PIN against the configured policy. The value must
     * contain only ASCII digits and be exactly {@code minLength} to
     * {@code maxLength} long; nothing is trimmed, padded, or normalized,
     * so {@code " 1234 "}, {@code "1234567"}, {@code "12-34"}, and {@code
     * "abcd"} are all rejected while {@code "007"} can pass at a
     * configured minimum of 3.
     * @param rawPin the PIN as submitted
     * @return the same value, unchanged, once accepted
     * @throws EmployeePinValidationException when the PIN is malformed
     */
    String validateFormat(String rawPin) {
        if (rawPin == null) {
            throw new EmployeePinValidationException("PIN must not be null");
        }
        Pattern digits = Pattern.compile("^\\d{" + pinProperties.minLength()
                + "," + pinProperties.maxLength() + "}$");
        if (!digits.matcher(rawPin).matches()) {
            throw new EmployeePinValidationException(
                    "PIN must be " + pinProperties.minLength() + " to "
                            + pinProperties.maxLength() + " digits");
        }
        return rawPin;
    }

    /**
     * Detect the store-scoped PIN uniqueness constraint anywhere in the
     * cause chain (leaf {@code SQLException}, Hibernate
     * {@code ConstraintViolationException}, Spring's translated wrapper),
     * mirroring the assignment service's detector style. The constraint
     * name is the only reliable discriminator between a PIN conflict and
     * an unrelated integrity failure, which must keep propagating.
     * @param ex the caught exception
     * @return {@code true} when any element of the chain reports
     *         {@code uk_epss_store_fingerprint}
     */
    static boolean isFingerprintConflict(DataIntegrityViolationException ex) {
        for (Throwable cursor = ex; cursor != null; cursor = cursor.getCause()) {
            String message = cursor.getMessage();
            if (message != null && message.contains("uk_epss_store_fingerprint")) {
                return true;
            }
        }
        return false;
    }
}
