package com.example.posapp.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.posapp.entity.EmployeeGroupMembership;

/**
 * Repository interface for managing {@link EmployeeGroupMembership}
 * entities, the join records linking {@link com.example.posapp.entity.Employee}s
 * to {@link com.example.posapp.entity.EmployeeGroup}s.
 * <p>
 * Provides the (employee, group) lookups used by the membership service to
 * detect duplicates and locate an existing pair for removal, plus the two
 * listing queries backing the sub-resource endpoints and the counts used
 * by the Employee and EmployeeGroup delete guards.
 * </p>
 */
public interface EmployeeGroupMembershipRepository
        extends JpaRepository<EmployeeGroupMembership, Long> {

    /**
     * Find the membership linking a specific employee to a specific group.
     * @param employeeId the employee ID
     * @param employeeGroupId the employee group ID
     * @return an Optional containing the membership if it exists
     */
    Optional<EmployeeGroupMembership> findByEmployeeIdAndEmployeeGroupId(
            Long employeeId, Long employeeGroupId);

    /**
     * Check whether the given employee already belongs to the given group.
     * This is a convenience predicate exposed for callers that only need
     * a yes/no answer (currently the repository integration tests). The
     * membership service deliberately does not use this method for
     * duplicate detection because its idempotent PUT must return the
     * existing row, so it reads through
     * {@link #findByEmployeeIdAndEmployeeGroupId(Long, Long)} instead.
     * @param employeeId the employee ID
     * @param employeeGroupId the employee group ID
     * @return {@code true} if a membership already exists
     */
    boolean existsByEmployeeIdAndEmployeeGroupId(Long employeeId, Long employeeGroupId);

    /**
     * List every membership for the given employee, used to expose the
     * groups the employee belongs to.
     * @param employeeId the employee ID
     * @return the memberships (possibly empty)
     */
    List<EmployeeGroupMembership> findByEmployeeId(Long employeeId);

    /**
     * List every membership for the given employee group, used to expose
     * the members of the group.
     * @param employeeGroupId the employee group ID
     * @return the memberships (possibly empty)
     */
    List<EmployeeGroupMembership> findByEmployeeGroupId(Long employeeGroupId);

    /**
     * Count memberships referencing the given employee. Used by the
     * Employee delete guard so an employee still attached to any group is
     * rejected at the service layer instead of surfacing a raw FK
     * violation.
     * @param employeeId the employee ID
     * @return the number of memberships referencing the employee
     */
    long countByEmployeeId(Long employeeId);

    /**
     * Count memberships referencing the given employee group. Used by the
     * EmployeeGroup delete guard so a group that still has members is
     * rejected at the service layer instead of surfacing a raw FK
     * violation.
     * @param employeeGroupId the employee group ID
     * @return the number of memberships referencing the group
     */
    long countByEmployeeGroupId(Long employeeGroupId);
}
