package com.example.posapp.service;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.posapp.entity.Employee;
import com.example.posapp.entity.Organization;
import com.example.posapp.exception.EmployeeNotFoundException;
import com.example.posapp.exception.EmployeeValidationException;
import com.example.posapp.exception.OrganizationNotFoundException;
import com.example.posapp.repository.EmployeeGroupMembershipRepository;
import com.example.posapp.repository.EmployeeStoreAssignmentRepository;
import com.example.posapp.repository.EmployeeRepository;
import com.example.posapp.repository.OrganizationRepository;

/**
 * Unit tests for the {@link EmployeeService} business rules.
 * <p>
 * The repositories are mocked, so these tests exercise the service in
 * isolation: name validation, organization resolution on create and
 * update, email normalization, email uniqueness within the organization,
 * and the delete semantics. Because every employee belongs to exactly one
 * brand, these tests also pin the scope rules: an organization, employee,
 * or list request outside the caller's brand is answered exactly like one
 * that does not exist, and the service never asks the repository for a
 * foreign row in the first place. No Spring context or database is
 * required.
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class EmployeeServiceTest {

    /** The caller's brand, and the only organization these tests may name. */
    private static final long BRAND = 1L;

    /** A second brand the caller must never reach. */
    private static final long OTHER_BRAND = 2L;

    @Mock
    private EmployeeRepository employeeRepo;

    @Mock
    private OrganizationRepository organizationRepo;

    @Mock
    private EmployeeGroupMembershipRepository membershipRepo;

    @Mock
    private EmployeeStoreAssignmentRepository assignmentRepo;

    @Mock
    private PosSessionRevoker sessionRevoker;

    @InjectMocks
    private EmployeeService employeeService;

    private static Organization org(String name) {
        return new Organization(name);
    }

    private static Employee employee(String name) {
        return new Employee(name);
    }

    private static Employee employee(String name, String email) {
        Employee employee = new Employee(name);
        employee.setEmail(email);
        return employee;
    }

    // --- createEmployee ---

    @Test
    @DisplayName("createEmployee: valid employee is saved with the resolved organization")
    void createEmployeeValidIsSaved() {
        Organization organization = org("Acme Restaurants");
        Employee input = employee("Ada Lovelace");
        when(organizationRepo.findById(BRAND)).thenReturn(Optional.of(organization));
        when(employeeRepo.save(any(Employee.class))).thenAnswer(inv -> inv.getArgument(0));

        Employee saved = employeeService.createEmployee(input, BRAND, BRAND);

        assertThat(saved.getName()).isEqualTo("Ada Lovelace");
        assertThat(saved.isActive()).isTrue();
        assertThat(saved.getOrganization()).isSameAs(organization);
        verify(employeeRepo).save(input);
    }

    @Test
    @DisplayName("createEmployee: an employee without an email skips the uniqueness check")
    void createEmployeeWithoutEmailSkipsUniquenessCheck() {
        when(organizationRepo.findById(BRAND)).thenReturn(Optional.of(org("Acme")));
        when(employeeRepo.save(any(Employee.class))).thenAnswer(inv -> inv.getArgument(0));

        Employee saved = employeeService.createEmployee(employee("Ada Lovelace"), BRAND, BRAND);

        assertThat(saved.getEmail()).isNull();
        verify(employeeRepo, never()).existsByOrganizationIdAndEmail(any(), any());
    }

    @Test
    @DisplayName("createEmployee: a whitespace-only email is normalized to null")
    void createEmployeeBlankEmailNormalizedToNull() {
        when(organizationRepo.findById(BRAND)).thenReturn(Optional.of(org("Acme")));
        when(employeeRepo.save(any(Employee.class))).thenAnswer(inv -> inv.getArgument(0));

        Employee saved = employeeService.createEmployee(employee("Ada", "   "), BRAND, BRAND);

        assertThat(saved.getEmail()).isNull();
        verify(employeeRepo, never()).existsByOrganizationIdAndEmail(any(), any());
    }

    @Test
    @DisplayName("createEmployee: the email is trimmed before the uniqueness check runs")
    void createEmployeeTrimsEmailBeforeUniquenessCheck() {
        when(organizationRepo.findById(BRAND)).thenReturn(Optional.of(org("Acme")));
        when(employeeRepo.existsByOrganizationIdAndEmail(BRAND, "ada@example.com")).thenReturn(false);
        when(employeeRepo.save(any(Employee.class))).thenAnswer(inv -> inv.getArgument(0));

        Employee saved = employeeService.createEmployee(
                employee("Ada", "  ada@example.com  "), BRAND, BRAND);

        assertThat(saved.getEmail()).isEqualTo("ada@example.com");
        verify(employeeRepo).existsByOrganizationIdAndEmail(BRAND, "ada@example.com");
    }

    @Test
    @DisplayName("createEmployee: an email already used in the organization is rejected")
    void createEmployeeDuplicateEmailThrows() {
        when(organizationRepo.findById(BRAND)).thenReturn(Optional.of(org("Acme")));
        when(employeeRepo.existsByOrganizationIdAndEmail(BRAND, "ada@example.com")).thenReturn(true);

        assertThatThrownBy(() -> employeeService.createEmployee(
                employee("Ada", "ada@example.com"), BRAND, BRAND))
                .isInstanceOf(EmployeeValidationException.class)
                .hasMessageContaining("Email already exists in organization")
                .hasMessageContaining("organizationId=1")
                .hasMessageContaining("ada@example.com");

        verify(employeeRepo, never()).save(any());
    }

    @Test
    @DisplayName("createEmployee: blank name is rejected")
    void createEmployeeBlankNameThrows() {
        when(organizationRepo.findById(BRAND)).thenReturn(Optional.of(org("Acme")));

        assertThatThrownBy(() -> employeeService.createEmployee(employee("   "), BRAND, BRAND))
                .isInstanceOf(EmployeeValidationException.class)
                .hasMessage("Name must be provided");

        verify(employeeRepo, never()).save(any());
    }

    @Test
    @DisplayName("createEmployee: missing name is rejected")
    void createEmployeeNullNameThrows() {
        when(organizationRepo.findById(BRAND)).thenReturn(Optional.of(org("Acme")));

        assertThatThrownBy(() -> employeeService.createEmployee(employee(null), BRAND, BRAND))
                .isInstanceOf(EmployeeValidationException.class)
                .hasMessage("Name must be provided");

        verify(employeeRepo, never()).save(any());
    }

    @Test
    @DisplayName("createEmployee: a null organization ID is treated as not found without hitting the repository")
    void createEmployeeNullOrganizationIdThrows() {
        assertThatThrownBy(() -> employeeService.createEmployee(employee("Ada"), null, BRAND))
                .isInstanceOf(OrganizationNotFoundException.class);

        verify(organizationRepo, never()).findById(any());
        verify(employeeRepo, never()).save(any());
    }

    @Test
    @DisplayName("createEmployee: a missing organization is rejected")
    void createEmployeeMissingOrganizationThrows() {
        when(organizationRepo.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> employeeService.createEmployee(employee("Ada"), 99L, 99L))
                .isInstanceOf(OrganizationNotFoundException.class)
                .hasMessageContaining("99");

        verify(employeeRepo, never()).save(any());
    }

    @Test
    @DisplayName("createEmployee: naming another brand's organization is answered as not found")
    void createEmployeeOutsideBrandIsNotFound() {
        // OTHER_BRAND exists as far as the database is concerned, but this
        // caller may not name it, so the repository is never asked.
        assertThatThrownBy(() -> employeeService.createEmployee(employee("Ada"), OTHER_BRAND, BRAND))
                .isInstanceOf(OrganizationNotFoundException.class)
                .hasMessageContaining("2");

        verify(organizationRepo, never()).findById(any());
        verify(employeeRepo, never()).save(any());
    }

    @Test
    @DisplayName("createEmployee: the organization is resolved before the name is validated")
    void createEmployeeResolvesOrganizationBeforeValidatingName() {
        when(organizationRepo.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> employeeService.createEmployee(employee("   "), 99L, 99L))
                .isInstanceOf(OrganizationNotFoundException.class);

        verify(employeeRepo, never()).save(any());
    }

    // --- updateEmployee ---

    @Test
    @DisplayName("updateEmployee: name, email, and active flag are applied to the existing row")
    void updateEmployeeAppliesChanges() {
        Organization organization = org("Acme");
        Employee existing = employee("Old Name");
        existing.setOrganization(organization);
        Employee replacement = employee("New Name", "new@example.com");
        replacement.setActive(false);
        when(organizationRepo.findById(BRAND)).thenReturn(Optional.of(organization));
        when(employeeRepo.findByIdAndOrganizationId(5L, BRAND)).thenReturn(Optional.of(existing));
        when(employeeRepo.existsByOrganizationIdAndEmailAndIdNot(BRAND, "new@example.com", 5L))
                .thenReturn(false);
        when(employeeRepo.save(any(Employee.class))).thenAnswer(inv -> inv.getArgument(0));

        Employee updated = employeeService.updateEmployee(5L, replacement, BRAND, BRAND);

        assertThat(updated.getName()).isEqualTo("New Name");
        assertThat(updated.getEmail()).isEqualTo("new@example.com");
        assertThat(updated.isActive()).isFalse();
        assertThat(updated.getOrganization()).isSameAs(organization);
        verify(employeeRepo).save(existing);
    }

    @Test
    @DisplayName("updateEmployee: keeping the employee's own email is allowed")
    void updateEmployeeKeepsOwnEmail() {
        Organization organization = org("Acme");
        Employee existing = employee("Ada", "ada@example.com");
        existing.setOrganization(organization);
        when(organizationRepo.findById(BRAND)).thenReturn(Optional.of(organization));
        when(employeeRepo.findByIdAndOrganizationId(5L, BRAND)).thenReturn(Optional.of(existing));
        when(employeeRepo.existsByOrganizationIdAndEmailAndIdNot(BRAND, "ada@example.com", 5L))
                .thenReturn(false);
        when(employeeRepo.save(any(Employee.class))).thenAnswer(inv -> inv.getArgument(0));

        Employee updated = employeeService.updateEmployee(
                5L, employee("Ada Lovelace", "ada@example.com"), BRAND, BRAND);

        assertThat(updated.getEmail()).isEqualTo("ada@example.com");
        verify(employeeRepo).existsByOrganizationIdAndEmailAndIdNot(BRAND, "ada@example.com", 5L);
    }

    @Test
    @DisplayName("updateEmployee: an email used by another employee is rejected")
    void updateEmployeeDuplicateEmailThrows() {
        Employee existing = employee("Ada");
        when(organizationRepo.findById(BRAND)).thenReturn(Optional.of(org("Acme")));
        when(employeeRepo.findByIdAndOrganizationId(5L, BRAND)).thenReturn(Optional.of(existing));
        when(employeeRepo.existsByOrganizationIdAndEmailAndIdNot(BRAND, "taken@example.com", 5L))
                .thenReturn(true);

        assertThatThrownBy(() -> employeeService.updateEmployee(
                5L, employee("Ada", "taken@example.com"), BRAND, BRAND))
                .isInstanceOf(EmployeeValidationException.class)
                .hasMessageContaining("Email already exists in organization")
                .hasMessageContaining("organizationId=1");

        verify(employeeRepo, never()).save(any());
    }

    @Test
    @DisplayName("updateEmployee: a whitespace-only email clears the stored email")
    void updateEmployeeClearsEmailWhenBlank() {
        Employee existing = employee("Ada", "ada@example.com");
        when(organizationRepo.findById(BRAND)).thenReturn(Optional.of(org("Acme")));
        when(employeeRepo.findByIdAndOrganizationId(5L, BRAND)).thenReturn(Optional.of(existing));
        when(employeeRepo.save(any(Employee.class))).thenAnswer(inv -> inv.getArgument(0));

        Employee updated = employeeService.updateEmployee(5L, employee("Ada", "   "), BRAND, BRAND);

        assertThat(updated.getEmail()).isNull();
        verify(employeeRepo, never()).existsByOrganizationIdAndEmailAndIdNot(any(), any(), any());
    }

    @Test
    @DisplayName("updateEmployee: blank name is rejected before the employee is looked up")
    void updateEmployeeBlankNameThrowsBeforeLookup() {
        when(organizationRepo.findById(BRAND)).thenReturn(Optional.of(org("Acme")));

        assertThatThrownBy(() -> employeeService.updateEmployee(5L, employee("   "), BRAND, BRAND))
                .isInstanceOf(EmployeeValidationException.class)
                .hasMessage("Name must be provided");

        verify(employeeRepo, never()).findByIdAndOrganizationId(any(), any());
        verify(employeeRepo, never()).save(any());
    }

    @Test
    @DisplayName("updateEmployee: a null organization ID is treated as not found without hitting the repositories")
    void updateEmployeeNullOrganizationIdThrows() {
        assertThatThrownBy(() -> employeeService.updateEmployee(5L, employee("Ada"), null, BRAND))
                .isInstanceOf(OrganizationNotFoundException.class);

        verify(organizationRepo, never()).findById(any());
        verify(employeeRepo, never()).findByIdAndOrganizationId(any(), any());
    }

    @Test
    @DisplayName("updateEmployee: a missing organization is rejected")
    void updateEmployeeMissingOrganizationThrows() {
        when(organizationRepo.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> employeeService.updateEmployee(5L, employee("Ada"), 99L, 99L))
                .isInstanceOf(OrganizationNotFoundException.class)
                .hasMessageContaining("99");

        verify(employeeRepo, never()).findByIdAndOrganizationId(any(), any());
        verify(employeeRepo, never()).save(any());
    }

    @Test
    @DisplayName("updateEmployee: naming another brand's organization is answered as not found")
    void updateEmployeeOutsideBrandIsNotFound() {
        assertThatThrownBy(() -> employeeService.updateEmployee(5L, employee("Ada"), OTHER_BRAND, BRAND))
                .isInstanceOf(OrganizationNotFoundException.class);

        verify(organizationRepo, never()).findById(any());
        verify(employeeRepo, never()).findByIdAndOrganizationId(any(), any());
    }

    @Test
    @DisplayName("updateEmployee: a missing employee is rejected")
    void updateEmployeeMissingEmployeeThrows() {
        when(organizationRepo.findById(BRAND)).thenReturn(Optional.of(org("Acme")));
        when(employeeRepo.findByIdAndOrganizationId(99L, BRAND)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> employeeService.updateEmployee(99L, employee("Ada"), BRAND, BRAND))
                .isInstanceOf(EmployeeNotFoundException.class)
                .hasMessageContaining("99");

        verify(employeeRepo, never()).existsByOrganizationIdAndEmailAndIdNot(any(), any(), any());
        verify(employeeRepo, never()).save(any());
    }

    @Test
    @DisplayName("updateEmployee: an employee of another brand is answered as missing")
    void updateEmployeeOutsideBrandIsMissing() {
        when(organizationRepo.findById(BRAND)).thenReturn(Optional.of(org("Acme")));

        assertThatThrownBy(() -> employeeService.updateEmployee(5L, employee("Ada"), BRAND, BRAND))
                .isInstanceOf(EmployeeNotFoundException.class)
                .hasMessageContaining("5");

        verify(employeeRepo, never()).save(any());
    }

    @Test
    @DisplayName("updateEmployee: the owning organization is rewritten in place, with no membership or assignment count")
    void updateEmployeeKeepsTheOrganizationItCameWith() {
        // The brand is the employee's current organization, so an update can
        // never move the row. The V14/V15 composite foreign keys are
        // therefore never at risk and no attachment guard is needed.
        Organization current = org("Acme");
        Employee existing = employee("Ada");
        existing.setOrganization(current);
        when(organizationRepo.findById(BRAND)).thenReturn(Optional.of(current));
        when(employeeRepo.findByIdAndOrganizationId(5L, BRAND)).thenReturn(Optional.of(existing));
        when(employeeRepo.save(any(Employee.class))).thenAnswer(inv -> inv.getArgument(0));

        Employee updated = employeeService.updateEmployee(
                5L, employee("Ada Lovelace"), BRAND, BRAND);

        assertThat(updated).isSameAs(existing);
        assertThat(updated.getOrganization()).isSameAs(current);
        verify(membershipRepo, never()).countByEmployeeId(any());
        verify(assignmentRepo, never()).countByEmployeeId(any());
        verify(employeeRepo).save(existing);
    }

    // --- deleteEmployee ---

    @Test
    @DisplayName("deleteEmployee: an existing employee is deleted")
    void deleteEmployeeRemovesExisting() {
        when(employeeRepo.findByIdAndOrganizationId(5L, BRAND))
                .thenReturn(Optional.of(employee("Ada")));

        employeeService.deleteEmployee(5L, BRAND);

        verify(employeeRepo).deleteById(5L);
    }

    @Test
    @DisplayName("deleteEmployee: a missing employee is rejected")
    void deleteEmployeeMissingThrows() {
        when(employeeRepo.findByIdAndOrganizationId(99L, BRAND)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> employeeService.deleteEmployee(99L, BRAND))
                .isInstanceOf(EmployeeNotFoundException.class)
                .hasMessageContaining("99");

        verify(employeeRepo, never()).deleteById(any());
    }

    @Test
    @DisplayName("deleteEmployee: an employee of another brand is answered as missing and nothing is deleted")
    void deleteEmployeeOutsideBrandIsNotFound() {
        assertThatThrownBy(() -> employeeService.deleteEmployee(5L, OTHER_BRAND))
                .isInstanceOf(EmployeeNotFoundException.class);

        verify(employeeRepo).findByIdAndOrganizationId(5L, OTHER_BRAND);
        verify(employeeRepo, never()).deleteById(any());
    }

    @Test
    @DisplayName("deleteEmployee: an employee still belonging to a group is rejected and nothing is deleted")
    void deleteEmployeeWithMembershipsIsRejected() {
        when(employeeRepo.findByIdAndOrganizationId(5L, BRAND))
                .thenReturn(Optional.of(employee("Ada")));
        when(membershipRepo.countByEmployeeId(5L)).thenReturn(2L);

        assertThatThrownBy(() -> employeeService.deleteEmployee(5L, BRAND))
                .isInstanceOf(EmployeeValidationException.class)
                .hasMessageContaining("still belonging to an employee group");

        verify(employeeRepo, never()).deleteById(any());
    }

    @Test
    @DisplayName("deleteEmployee: an employee still assigned to a store is rejected and nothing is deleted")
    void deleteEmployeeWithAssignmentsIsRejected() {
        when(employeeRepo.findByIdAndOrganizationId(5L, BRAND))
                .thenReturn(Optional.of(employee("Ada")));
        when(membershipRepo.countByEmployeeId(5L)).thenReturn(0L);
        when(assignmentRepo.countByEmployeeId(5L)).thenReturn(3L);

        assertThatThrownBy(() -> employeeService.deleteEmployee(5L, BRAND))
                .isInstanceOf(EmployeeValidationException.class)
                .hasMessageContaining("still assigned to a store");

        verify(employeeRepo, never()).deleteById(any());
    }

    // --- getAll / getById ---

    @Test
    @DisplayName("getAllEmployees: asks only for the caller's brand")
    void getAllEmployeesReturnsBrandsEmployees() {
        when(employeeRepo.findByOrganizationId(BRAND))
                .thenReturn(List.of(employee("Ada"), employee("Grace")));

        List<Employee> all = employeeService.getAllEmployees(BRAND);

        assertThat(all).hasSize(2)
                .extracting(Employee::getName)
                .containsExactly("Ada", "Grace");
        verify(employeeRepo).findByOrganizationId(BRAND);
        verify(employeeRepo, never()).findAll();
    }

    @Test
    @DisplayName("getEmployeeById: delegates to the scoped repository when the ID is present")
    void getEmployeeByIdDelegates() {
        Employee existing = employee("Ada");
        when(employeeRepo.findByIdAndOrganizationId(5L, BRAND)).thenReturn(Optional.of(existing));

        assertThat(employeeService.getEmployeeById(5L, BRAND)).contains(existing);
    }

    @Test
    @DisplayName("getEmployeeById: an employee of another brand is not visible")
    void getEmployeeByIdOutsideBrandIsEmpty() {
        assertThat(employeeService.getEmployeeById(5L, OTHER_BRAND)).isEmpty();

        verify(employeeRepo).findByIdAndOrganizationId(5L, OTHER_BRAND);
    }

    @Test
    @DisplayName("getEmployeeById: null ID throws IllegalArgumentException")
    void getEmployeeByIdNullThrows() {
        assertThatThrownBy(() -> employeeService.getEmployeeById(null, BRAND))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("ID cannot be null");
    }
}
