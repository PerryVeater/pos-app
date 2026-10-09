package com.example.posapp.service;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;

import com.example.posapp.entity.Employee;
import com.example.posapp.entity.EmployeeGroup;
import com.example.posapp.entity.EmployeeGroupMembership;
import com.example.posapp.entity.Organization;
import com.example.posapp.exception.EmployeeGroupNotFoundException;
import com.example.posapp.exception.EmployeeGroupValidationException;
import com.example.posapp.exception.EmployeeNotFoundException;
import com.example.posapp.repository.EmployeeGroupMembershipRepository;
import com.example.posapp.repository.EmployeeGroupRepository;
import com.example.posapp.repository.EmployeeRepository;

/**
 * Unit tests for the {@link EmployeeGroupMembershipService} business
 * rules.
 * <p>
 * Repositories are mocked, so these tests exercise the service in
 * isolation: the employee and the group must exist on every operation,
 * both sides must share an organization, adding an existing pair is a
 * no-op, removing a missing pair is a no-op, and the two listing methods
 * require the aggregate to exist. Because entities cannot be assigned IDs
 * directly, tests that need distinct organization IDs stub Mockito mocks
 * for {@link Organization} rather than persisting rows. No Spring context
 * or database is required.
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class EmployeeGroupMembershipServiceTest {

    @Mock
    private EmployeeRepository employeeRepo;

    @Mock
    private EmployeeGroupRepository employeeGroupRepo;

    @Mock
    private EmployeeGroupMembershipRepository membershipRepo;

    @InjectMocks
    private EmployeeGroupMembershipService membershipService;

    /**
     * Build a mock employee whose organization ID equals the supplied
     * value. Using a Mockito mock avoids the ID assignment limitation of
     * the JPA entities and keeps the tests focused on the service rules.
     * @param organizationId the owning organization ID to expose
     * @return the mocked employee
     */
    private static Employee employeeInOrg(long organizationId) {
        Organization organization = mock(Organization.class);
        when(organization.getId()).thenReturn(organizationId);
        Employee employee = mock(Employee.class);
        when(employee.getOrganization()).thenReturn(organization);
        return employee;
    }

    /**
     * Build a mock employee group whose organization ID equals the
     * supplied value.
     * @param organizationId the owning organization ID to expose
     * @return the mocked group
     */
    private static EmployeeGroup groupInOrg(long organizationId) {
        Organization organization = mock(Organization.class);
        when(organization.getId()).thenReturn(organizationId);
        EmployeeGroup group = mock(EmployeeGroup.class);
        when(group.getOrganization()).thenReturn(organization);
        return group;
    }

    // --- addMembership ---

    @Test
    @DisplayName("addMembership: saves a new membership when the pair is not already attached")
    void addMembershipSavesNewPair() {
        Employee employee = employeeInOrg(1L);
        EmployeeGroup group = groupInOrg(1L);
        when(employeeRepo.findById(10L)).thenReturn(Optional.of(employee));
        when(employeeGroupRepo.findById(20L)).thenReturn(Optional.of(group));
        when(membershipRepo.findByEmployeeIdAndEmployeeGroupId(10L, 20L))
                .thenReturn(Optional.empty());
        when(membershipRepo.saveAndFlush(any(EmployeeGroupMembership.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        EmployeeGroupMembership saved = membershipService.addMembership(10L, 20L);

        assertThat(saved.getEmployee()).isSameAs(employee);
        assertThat(saved.getEmployeeGroup()).isSameAs(group);
        assertThat(saved.getOrganizationId()).isEqualTo(1L);
        verify(membershipRepo).saveAndFlush(any(EmployeeGroupMembership.class));
    }

    @Test
    @DisplayName("addMembership: concurrent duplicate insert is caught and the winner's row is returned")
    void addMembershipReturnsWinnerOnConcurrentDuplicateInsert() {
        Employee employee = employeeInOrg(1L);
        EmployeeGroup group = groupInOrg(1L);
        EmployeeGroupMembership winner = mock(EmployeeGroupMembership.class);
        // Discriminating exception chain: the deepest SQLException leaf
        // carries a generic PG batch message with no constraint name,
        // the middle Hibernate wrapper is the only link that names
        // uk_employee_group_membership, and Spring's DuplicateKeyException
        // on top is generic. The old detector (getMostSpecificCause()
        // only) would return the leaf, miss the constraint name, and
        // rethrow; the current walking detector finds the substring on
        // the middle link and takes the recovery path. If production
        // regresses to a leaf-only check, this test fails.
        SQLException leaf = new SQLException("Batch entry 0 was executed");
        RuntimeException hibernateWrapper = new RuntimeException(
                "could not execute statement [ERROR: duplicate key value violates unique"
                        + " constraint \"uk_employee_group_membership\"  Detail: Key"
                        + " (employee_id, employee_group_id)=(10, 20) already exists.]"
                        + " [insert into employee_group_membership (...)]",
                leaf);
        DuplicateKeyException duplicateViolation = new DuplicateKeyException(
                "JPA saveAndFlush failed", hibernateWrapper);
        when(employeeRepo.findById(10L)).thenReturn(Optional.of(employee));
        when(employeeGroupRepo.findById(20L)).thenReturn(Optional.of(group));
        when(membershipRepo.findByEmployeeIdAndEmployeeGroupId(10L, 20L))
                .thenReturn(Optional.empty(), Optional.of(winner));
        when(membershipRepo.saveAndFlush(any(EmployeeGroupMembership.class)))
                .thenThrow(duplicateViolation);

        EmployeeGroupMembership result = membershipService.addMembership(10L, 20L);

        assertThat(result).isSameAs(winner);
        verify(membershipRepo).saveAndFlush(any(EmployeeGroupMembership.class));
    }

    @Test
    @DisplayName("addMembership: duplicate-key violation whose recovery read finds nothing rethrows the original exception")
    void addMembershipRethrowsWhenRecoveryReadFindsNoRow() {
        Employee employee = employeeInOrg(1L);
        EmployeeGroup group = groupInOrg(1L);
        SQLException leaf = new SQLException(
                "ERROR: duplicate key value violates unique constraint"
                        + " \"uk_employee_group_membership\"");
        DuplicateKeyException duplicateViolation = new DuplicateKeyException(
                "Batch entry rejected", leaf);
        when(employeeRepo.findById(10L)).thenReturn(Optional.of(employee));
        when(employeeGroupRepo.findById(20L)).thenReturn(Optional.of(group));
        // Both the pre-check and the recovery read return empty: the
        // concurrent winner was deleted before our re-read could see
        // it, so the recovery is not possible and the original
        // exception must be rethrown rather than reported as success.
        when(membershipRepo.findByEmployeeIdAndEmployeeGroupId(10L, 20L))
                .thenReturn(Optional.empty(), Optional.empty());
        when(membershipRepo.saveAndFlush(any(EmployeeGroupMembership.class)))
                .thenThrow(duplicateViolation);

        assertThatThrownBy(() -> membershipService.addMembership(10L, 20L))
                .isSameAs(duplicateViolation);
    }

    @Test
    @DisplayName("addMembership: unrelated integrity violations are rethrown so the client still sees 400")
    void addMembershipRethrowsUnrelatedIntegrityViolation() {
        Employee employee = employeeInOrg(1L);
        EmployeeGroup group = groupInOrg(1L);
        DataIntegrityViolationException fkViolation = new DataIntegrityViolationException(
                "update or delete on table \"employee\" violates foreign key constraint"
                        + " \"fk_egm_employee\" on table \"employee_group_membership\"");
        when(employeeRepo.findById(10L)).thenReturn(Optional.of(employee));
        when(employeeGroupRepo.findById(20L)).thenReturn(Optional.of(group));
        when(membershipRepo.findByEmployeeIdAndEmployeeGroupId(10L, 20L))
                .thenReturn(Optional.empty());
        when(membershipRepo.saveAndFlush(any(EmployeeGroupMembership.class)))
                .thenThrow(fkViolation);

        assertThatThrownBy(() -> membershipService.addMembership(10L, 20L))
                .isSameAs(fkViolation);
    }

    @Test
    @DisplayName("addMembership: existing membership is returned unchanged so PUT is idempotent")
    void addMembershipIsIdempotentForExistingPair() {
        Employee employee = employeeInOrg(1L);
        EmployeeGroup group = groupInOrg(1L);
        EmployeeGroupMembership existing = mock(EmployeeGroupMembership.class);
        when(employeeRepo.findById(10L)).thenReturn(Optional.of(employee));
        when(employeeGroupRepo.findById(20L)).thenReturn(Optional.of(group));
        when(membershipRepo.findByEmployeeIdAndEmployeeGroupId(10L, 20L))
                .thenReturn(Optional.of(existing));

        EmployeeGroupMembership result = membershipService.addMembership(10L, 20L);

        assertThat(result).isSameAs(existing);
        verify(membershipRepo, never()).save(any(EmployeeGroupMembership.class));
        verify(membershipRepo, never()).saveAndFlush(any(EmployeeGroupMembership.class));
    }

    @Test
    @DisplayName("addMembership: missing employee throws EmployeeNotFoundException and saves nothing")
    void addMembershipMissingEmployeeThrows() {
        when(employeeRepo.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> membershipService.addMembership(99L, 20L))
                .isInstanceOf(EmployeeNotFoundException.class)
                .hasMessageContaining("99");

        verify(membershipRepo, never()).saveAndFlush(any(EmployeeGroupMembership.class));
    }

    @Test
    @DisplayName("addMembership: missing group throws EmployeeGroupNotFoundException and saves nothing")
    void addMembershipMissingGroupThrows() {
        Employee employee = mock(Employee.class);
        when(employeeRepo.findById(10L)).thenReturn(Optional.of(employee));
        when(employeeGroupRepo.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> membershipService.addMembership(10L, 99L))
                .isInstanceOf(EmployeeGroupNotFoundException.class)
                .hasMessageContaining("99");

        verify(membershipRepo, never()).saveAndFlush(any(EmployeeGroupMembership.class));
    }

    @Test
    @DisplayName("addMembership: cross-organization pair is rejected before anything is saved")
    void addMembershipCrossOrganizationThrows() {
        Employee employee = employeeInOrg(1L);
        EmployeeGroup group = groupInOrg(2L);
        when(employeeRepo.findById(10L)).thenReturn(Optional.of(employee));
        when(employeeGroupRepo.findById(20L)).thenReturn(Optional.of(group));

        assertThatThrownBy(() -> membershipService.addMembership(10L, 20L))
                .isInstanceOf(EmployeeGroupValidationException.class)
                .hasMessageContaining("same organization");

        verify(membershipRepo, never()).saveAndFlush(any(EmployeeGroupMembership.class));
    }

    // --- removeMembership ---

    @Test
    @DisplayName("removeMembership: deletes the existing membership row")
    void removeMembershipDeletesExisting() {
        Employee employee = employeeInOrg(1L);
        EmployeeGroup group = groupInOrg(1L);
        EmployeeGroupMembership existing = mock(EmployeeGroupMembership.class);
        when(employeeRepo.findById(10L)).thenReturn(Optional.of(employee));
        when(employeeGroupRepo.findById(20L)).thenReturn(Optional.of(group));
        when(membershipRepo.findByEmployeeIdAndEmployeeGroupId(10L, 20L))
                .thenReturn(Optional.of(existing));

        membershipService.removeMembership(10L, 20L);

        verify(membershipRepo).delete(existing);
    }

    @Test
    @DisplayName("removeMembership: missing pair is a no-op so DELETE is idempotent")
    void removeMembershipIsIdempotentForMissingPair() {
        Employee employee = employeeInOrg(1L);
        EmployeeGroup group = groupInOrg(1L);
        when(employeeRepo.findById(10L)).thenReturn(Optional.of(employee));
        when(employeeGroupRepo.findById(20L)).thenReturn(Optional.of(group));
        when(membershipRepo.findByEmployeeIdAndEmployeeGroupId(10L, 20L))
                .thenReturn(Optional.empty());

        membershipService.removeMembership(10L, 20L);

        verify(membershipRepo, never()).delete(any(EmployeeGroupMembership.class));
    }

    @Test
    @DisplayName("removeMembership: missing employee throws EmployeeNotFoundException")
    void removeMembershipMissingEmployeeThrows() {
        when(employeeRepo.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> membershipService.removeMembership(99L, 20L))
                .isInstanceOf(EmployeeNotFoundException.class)
                .hasMessageContaining("99");

        verify(membershipRepo, never()).delete(any(EmployeeGroupMembership.class));
    }

    @Test
    @DisplayName("removeMembership: missing group throws EmployeeGroupNotFoundException")
    void removeMembershipMissingGroupThrows() {
        Employee employee = mock(Employee.class);
        when(employeeRepo.findById(10L)).thenReturn(Optional.of(employee));
        when(employeeGroupRepo.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> membershipService.removeMembership(10L, 99L))
                .isInstanceOf(EmployeeGroupNotFoundException.class)
                .hasMessageContaining("99");

        verify(membershipRepo, never()).delete(any(EmployeeGroupMembership.class));
    }

    @Test
    @DisplayName("removeMembership: cross-organization pair is rejected before anything is deleted")
    void removeMembershipCrossOrganizationThrows() {
        Employee employee = employeeInOrg(1L);
        EmployeeGroup group = groupInOrg(2L);
        when(employeeRepo.findById(10L)).thenReturn(Optional.of(employee));
        when(employeeGroupRepo.findById(20L)).thenReturn(Optional.of(group));

        assertThatThrownBy(() -> membershipService.removeMembership(10L, 20L))
                .isInstanceOf(EmployeeGroupValidationException.class)
                .hasMessageContaining("same organization");

        verify(membershipRepo, never()).delete(any(EmployeeGroupMembership.class));
    }

    // --- listGroupsForEmployee ---

    @Test
    @DisplayName("listGroupsForEmployee: returns the groups referenced by the memberships")
    void listGroupsForEmployeeReturnsAttachedGroups() {
        when(employeeRepo.existsById(10L)).thenReturn(true);
        EmployeeGroup firstGroup = new EmployeeGroup("Front of House");
        EmployeeGroup secondGroup = new EmployeeGroup("Baristas");
        EmployeeGroupMembership first = mock(EmployeeGroupMembership.class);
        EmployeeGroupMembership second = mock(EmployeeGroupMembership.class);
        when(first.getEmployeeGroup()).thenReturn(firstGroup);
        when(second.getEmployeeGroup()).thenReturn(secondGroup);
        when(membershipRepo.findByEmployeeId(10L)).thenReturn(List.of(first, second));

        List<EmployeeGroup> groups = membershipService.listGroupsForEmployee(10L);

        assertThat(groups).containsExactly(firstGroup, secondGroup);
    }

    @Test
    @DisplayName("listGroupsForEmployee: returns an empty list when the employee has no memberships")
    void listGroupsForEmployeeEmptyReturnsEmptyList() {
        when(employeeRepo.existsById(10L)).thenReturn(true);
        when(membershipRepo.findByEmployeeId(10L)).thenReturn(List.of());

        assertThat(membershipService.listGroupsForEmployee(10L)).isEmpty();
    }

    @Test
    @DisplayName("listGroupsForEmployee: missing employee throws EmployeeNotFoundException")
    void listGroupsForEmployeeMissingThrows() {
        when(employeeRepo.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> membershipService.listGroupsForEmployee(99L))
                .isInstanceOf(EmployeeNotFoundException.class)
                .hasMessageContaining("99");

        verify(membershipRepo, never()).findByEmployeeId(any());
    }

    // --- listEmployeesForGroup ---

    @Test
    @DisplayName("listEmployeesForGroup: returns the employees referenced by the memberships")
    void listEmployeesForGroupReturnsAttachedEmployees() {
        when(employeeGroupRepo.existsById(20L)).thenReturn(true);
        Employee first = new Employee("Ada");
        Employee second = new Employee("Grace");
        EmployeeGroupMembership firstMembership = mock(EmployeeGroupMembership.class);
        EmployeeGroupMembership secondMembership = mock(EmployeeGroupMembership.class);
        when(firstMembership.getEmployee()).thenReturn(first);
        when(secondMembership.getEmployee()).thenReturn(second);
        when(membershipRepo.findByEmployeeGroupId(20L))
                .thenReturn(List.of(firstMembership, secondMembership));

        List<Employee> employees = membershipService.listEmployeesForGroup(20L);

        assertThat(employees).containsExactly(first, second);
    }

    @Test
    @DisplayName("listEmployeesForGroup: returns an empty list when the group has no members")
    void listEmployeesForGroupEmptyReturnsEmptyList() {
        when(employeeGroupRepo.existsById(20L)).thenReturn(true);
        when(membershipRepo.findByEmployeeGroupId(20L)).thenReturn(List.of());

        assertThat(membershipService.listEmployeesForGroup(20L)).isEmpty();
    }

    @Test
    @DisplayName("listEmployeesForGroup: missing group throws EmployeeGroupNotFoundException")
    void listEmployeesForGroupMissingThrows() {
        when(employeeGroupRepo.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> membershipService.listEmployeesForGroup(99L))
                .isInstanceOf(EmployeeGroupNotFoundException.class)
                .hasMessageContaining("99");

        verify(membershipRepo, never()).findByEmployeeGroupId(any());
    }
}
