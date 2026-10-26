package com.example.posapp.service;

import java.util.List;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.posapp.entity.Employee;
import com.example.posapp.entity.EmployeeGroup;
import com.example.posapp.entity.EmployeeGroupMembership;
import com.example.posapp.exception.EmployeeGroupNotFoundException;
import com.example.posapp.exception.EmployeeNotFoundException;
import com.example.posapp.repository.EmployeeGroupMembershipRepository;
import com.example.posapp.repository.EmployeeGroupRepository;
import com.example.posapp.repository.EmployeeRepository;

/**
 * Service layer for the Employee ↔ EmployeeGroup membership lifecycle.
 * <p>
 * The membership is intentionally separated from {@link EmployeeService}
 * and {@link EmployeeGroupService} so the existing aggregates stay
 * untouched, per the project's assignment-layer convention. Every write
 * must reference an employee and a group that both belong to the caller's
 * brand: the two lookups are scoped to the brand, so an aggregate in
 * another brand is reported as 404 - the same answer a genuinely missing
 * one gets - rather than as a cross-organization 400 that would confirm the
 * row exists. The database still enforces the same-organization rule as a
 * defence in depth through the composite foreign keys added by the V14
 * migration.
 * </p>
 * <p>
 * Both mutating operations are idempotent: adding an existing membership
 * is a no-op and removing a missing membership is a no-op, so clients can
 * safely retry PUT/DELETE without a prior GET. The add path also survives
 * a concurrent write: the initial existence check, the insert, and the
 * recovery read each run in their own short transaction (the method is
 * deliberately not wrapped in an outer {@code @Transactional}), so if
 * another caller commits the same pair between our check and our insert
 * the {@code uk_employee_group_membership} violation is caught, only
 * rethrown when it is not the pair-uniqueness constraint, and translated
 * into a re-read that returns the winner's row. The database uniqueness
 * constraint therefore remains the final safeguard rather than being
 * bypassed. Listing the groups of an employee or the members of a group
 * requires the aggregate to exist and returns the peers resolved through
 * the join table.
 * </p>
 */
@Service
public class EmployeeGroupMembershipService {

    private final EmployeeRepository employeeRepo;
    private final EmployeeGroupRepository employeeGroupRepo;
    private final EmployeeGroupMembershipRepository membershipRepo;

    /**
     * Constructor for EmployeeGroupMembershipService.
     * @param employeeRepo the repository for employees
     * @param employeeGroupRepo the repository for employee groups
     * @param membershipRepo the repository for employee ↔ employee group memberships
     */
    public EmployeeGroupMembershipService(EmployeeRepository employeeRepo,
                                          EmployeeGroupRepository employeeGroupRepo,
                                          EmployeeGroupMembershipRepository membershipRepo) {
        this.employeeRepo = employeeRepo;
        this.employeeGroupRepo = employeeGroupRepo;
        this.membershipRepo = membershipRepo;
    }

    /**
     * Attach an employee to an employee group. Re-attaching an existing
     * membership is a no-op so the endpoint is idempotent, including when
     * a concurrent caller commits the same pair between our existence
     * check and our insert. The employee and the group must both belong to
     * the caller's brand.
     * <p>
     * No outer {@code @Transactional} wraps this method: the two reads
     * and the insert each run in Spring Data's per-call transaction, so
     * a duplicate-key failure rolls back only the losing insert and the
     * recovery re-read still sees the concurrently committed row. Any
     * {@link DataIntegrityViolationException} that is not the pair
     * uniqueness constraint is rethrown unchanged so unrelated
     * integrity errors keep surfacing as 400 "Conflicting data".
     * </p>
     * @param employeeId the employee ID
     * @param employeeGroupId the employee group ID
     * @param brandId the organization the caller may act within
     * @return the existing or newly saved membership
     * @throws EmployeeNotFoundException if the employee does not exist in
     *         the brand
     * @throws EmployeeGroupNotFoundException if the group does not exist in
     *         the brand
     */
    public EmployeeGroupMembership addMembership(Long employeeId, Long employeeGroupId, Long brandId) {
        Employee employee = employeeRepo.findByIdAndOrganizationId(employeeId, brandId)
                .orElseThrow(() -> new EmployeeNotFoundException(employeeId));
        EmployeeGroup group = employeeGroupRepo.findByIdAndOrganizationId(employeeGroupId, brandId)
                .orElseThrow(() -> new EmployeeGroupNotFoundException(employeeGroupId));
        return membershipRepo.findByEmployeeIdAndEmployeeGroupId(employeeId, employeeGroupId)
                .orElseGet(() -> insertOrRecoverConcurrentDuplicate(employee, group,
                        employeeId, employeeGroupId));
    }

    /**
     * Attempt the insert and, if a concurrent request committed the same
     * (employee, group) pair in the meantime, fall back to reading the
     * winner's row back so the caller still sees success. Only
     * {@code uk_employee_group_membership} violations are swallowed;
     * anything else (for example a tampered {@code organization_id}
     * triggering {@code fk_egm_employee}) is rethrown so the global
     * handler can still surface it as a client conflict.
     * @param employee the resolved employee
     * @param group the resolved employee group
     * @param employeeId the employee ID used for the recovery read
     * @param employeeGroupId the employee group ID used for the recovery read
     * @return the newly saved membership or the concurrently created one
     */
    private EmployeeGroupMembership insertOrRecoverConcurrentDuplicate(Employee employee,
                                                                       EmployeeGroup group,
                                                                       Long employeeId,
                                                                       Long employeeGroupId) {
        try {
            return membershipRepo.saveAndFlush(new EmployeeGroupMembership(employee, group));
        } catch (DataIntegrityViolationException ex) {
            if (!isDuplicatePairViolation(ex)) {
                throw ex;
            }
            return membershipRepo.findByEmployeeIdAndEmployeeGroupId(employeeId, employeeGroupId)
                    .orElseThrow(() -> ex);
        }
    }

    /**
     * Detect whether the given exception was raised by the pair-uniqueness
     * constraint rather than by any other integrity rule on the same
     * table. PostgreSQL's duplicate-key error text includes the
     * constraint name somewhere in the wrapped cause chain (leaf
     * {@code SQLException}, a Hibernate {@code ConstraintViolationException}
     * in the middle, and Spring's translated
     * {@code DataIntegrityViolationException} on top). Walking every
     * link, rather than only {@code getMostSpecificCause()}, keeps the
     * recovery reliable when any one layer changes its message format
     * while the others still name the constraint. Because the
     * {@code uk_} prefix is unique to the pair-uniqueness constraint on
     * this table, a false positive against a different integrity failure
     * (for example {@code fk_egm_employee}) is not possible without that
     * exact substring appearing in an unrelated message, and any such
     * message still resolves correctly via the follow-up recovery read.
     * @param ex the caught exception
     * @return {@code true} when any element of the chain reports the pair
     *         uniqueness constraint
     */
    private static boolean isDuplicatePairViolation(DataIntegrityViolationException ex) {
        for (Throwable cursor = ex; cursor != null; cursor = cursor.getCause()) {
            String message = cursor.getMessage();
            if (message != null && message.contains("uk_employee_group_membership")) {
                return true;
            }
        }
        return false;
    }

    /**
     * Detach an employee from an employee group. Removing a missing
     * membership is a no-op so the endpoint is idempotent. Neither the
     * employee nor the group is deleted, and both must belong to the
     * caller's brand.
     * @param employeeId the employee ID
     * @param employeeGroupId the employee group ID
     * @param brandId the organization the caller may act within
     * @throws EmployeeNotFoundException if the employee does not exist in
     *         the brand
     * @throws EmployeeGroupNotFoundException if the group does not exist in
     *         the brand
     */
    @Transactional
    public void removeMembership(Long employeeId, Long employeeGroupId, Long brandId) {
        if (employeeRepo.findByIdAndOrganizationId(employeeId, brandId).isEmpty()) {
            throw new EmployeeNotFoundException(employeeId);
        }
        if (employeeGroupRepo.findByIdAndOrganizationId(employeeGroupId, brandId).isEmpty()) {
            throw new EmployeeGroupNotFoundException(employeeGroupId);
        }
        membershipRepo.findByEmployeeIdAndEmployeeGroupId(employeeId, employeeGroupId)
                .ifPresent(membershipRepo::delete);
    }

    /**
     * List the groups an employee belongs to, within the caller's brand.
     * @param employeeId the employee ID
     * @param brandId the organization the caller may act within
     * @return the groups the employee is attached to (empty when none)
     * @throws EmployeeNotFoundException if the employee does not exist in
     *         the brand
     */
    public List<EmployeeGroup> listGroupsForEmployee(Long employeeId, Long brandId) {
        if (employeeRepo.findByIdAndOrganizationId(employeeId, brandId).isEmpty()) {
            throw new EmployeeNotFoundException(employeeId);
        }
        return membershipRepo.findByEmployeeId(employeeId).stream()
                .map(EmployeeGroupMembership::getEmployeeGroup)
                .toList();
    }

    /**
     * List the employees belonging to a group, within the caller's brand.
     * @param employeeGroupId the employee group ID
     * @param brandId the organization the caller may act within
     * @return the employees attached to the group (empty when none)
     * @throws EmployeeGroupNotFoundException if the group does not exist in
     *         the brand
     */
    public List<Employee> listEmployeesForGroup(Long employeeGroupId, Long brandId) {
        if (employeeGroupRepo.findByIdAndOrganizationId(employeeGroupId, brandId).isEmpty()) {
            throw new EmployeeGroupNotFoundException(employeeGroupId);
        }
        return membershipRepo.findByEmployeeGroupId(employeeGroupId).stream()
                .map(EmployeeGroupMembership::getEmployee)
                .toList();
    }
}
