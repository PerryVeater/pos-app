package com.example.posapp.security;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.posapp.entity.Employee;
import com.example.posapp.repository.EmployeeRepository;

/**
 * Maps a validated token's subject to the employee who acts for it.
 * <p>
 * This is the single translation from identity-provider vocabulary to domain
 * vocabulary. It answers a question that has three possible outcomes, and the
 * distinction between the last two matters: the subject may resolve to an
 * active employee, may resolve to nothing, or may resolve to a deactivated
 * one. Only the first is a principal. The others are reported as
 * {@link Optional#empty()} rather than by throwing.
 * </p>
 * <p>
 * An empty result is not an authentication failure - the token was valid - so
 * it must not become a 401, which would send a correctly signed caller back to
 * the provider forever. It is an authenticated identity with no domain
 * principal, which the authorization rules then refuse as a 403. Throwing from
 * here would be subtler and worse: this code runs inside the bearer-token
 * filter, before the exception-translation filter wraps the chain, so an
 * exception raised here escapes as a server error instead of a clean refusal.
 * </p>
 */
@Service
public class BackOfficeIdentityService {

    private static final Logger LOG = LoggerFactory.getLogger(BackOfficeIdentityService.class);

    private final EmployeeRepository employeeRepository;

    /**
     * Constructor for BackOfficeIdentityService.
     * @param employeeRepository the repository holding the identity link
     */
    public BackOfficeIdentityService(EmployeeRepository employeeRepository) {
        this.employeeRepository = employeeRepository;
    }

    /**
     * Resolve the principal for a token subject.
     * @param subject the validated token's {@code sub} claim
     * @return the linked employee and its organization, or empty when the
     *         subject resolves to no active employee
     */
    @Transactional(readOnly = true)
    public Optional<BackOfficePrincipal> resolve(String subject) {
        if (subject == null || subject.isBlank()) {
            // A token without a subject passed validation, so the provider
            // issued something unusable; that is worth an operator's attention.
            LOG.warn("Refused a validated back-office token that carries no subject.");
            return Optional.empty();
        }
        Optional<Employee> employee = employeeRepository.findByIdpSubject(subject);
        if (employee.isEmpty()) {
            LOG.warn("Refused back-office subject {}: no employee is linked to it.", subject);
            return Optional.empty();
        }
        if (!employee.get().isActive()) {
            LOG.warn("Refused back-office subject {}: employee {} is inactive.",
                    subject, employee.get().getId());
            return Optional.empty();
        }
        return employee.map(linked -> new BackOfficePrincipal(subject, linked.getId(),
                linked.getOrganization().getId(), linked.getName()));
    }
}
