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
import com.example.posapp.repository.EmployeeRepository;
import com.example.posapp.repository.OrganizationRepository;

/**
 * Unit tests for the {@link EmployeeService} business rules.
 * <p>
 * The repositories are mocked, so these tests exercise the service in
 * isolation: name validation, organization resolution on create and
 * update, email normalization, email uniqueness within the organization
 * (including self-exclusion on update and moving into a target
 * organization), and the delete semantics. No Spring context or database
 * is required.
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class EmployeeServiceTest {

    @Mock
    private EmployeeRepository employeeRepo;

    @Mock
    private OrganizationRepository organizationRepo;

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
        when(organizationRepo.findById(1L)).thenReturn(Optional.of(organization));
        when(employeeRepo.save(any(Employee.class))).thenAnswer(inv -> inv.getArgument(0));

        Employee saved = employeeService.createEmployee(input, 1L);

        assertThat(saved.getName()).isEqualTo("Ada Lovelace");
        assertThat(saved.isActive()).isTrue();
        assertThat(saved.getOrganization()).isSameAs(organization);
        verify(employeeRepo).save(input);
    }

    @Test
    @DisplayName("createEmployee: an employee without an email skips the uniqueness check")
    void createEmployeeWithoutEmailSkipsUniquenessCheck() {
        when(organizationRepo.findById(1L)).thenReturn(Optional.of(org("Acme")));
        when(employeeRepo.save(any(Employee.class))).thenAnswer(inv -> inv.getArgument(0));

        Employee saved = employeeService.createEmployee(employee("Ada Lovelace"), 1L);

        assertThat(saved.getEmail()).isNull();
        verify(employeeRepo, never()).existsByOrganizationIdAndEmail(any(), any());
    }

    @Test
    @DisplayName("createEmployee: a whitespace-only email is normalized to null")
    void createEmployeeBlankEmailNormalizedToNull() {
        when(organizationRepo.findById(1L)).thenReturn(Optional.of(org("Acme")));
        when(employeeRepo.save(any(Employee.class))).thenAnswer(inv -> inv.getArgument(0));

        Employee saved = employeeService.createEmployee(employee("Ada", "   "), 1L);

        assertThat(saved.getEmail()).isNull();
        verify(employeeRepo, never()).existsByOrganizationIdAndEmail(any(), any());
    }

    @Test
    @DisplayName("createEmployee: the email is trimmed before the uniqueness check runs")
    void createEmployeeTrimsEmailBeforeUniquenessCheck() {
        when(organizationRepo.findById(1L)).thenReturn(Optional.of(org("Acme")));
        when(employeeRepo.existsByOrganizationIdAndEmail(1L, "ada@example.com")).thenReturn(false);
        when(employeeRepo.save(any(Employee.class))).thenAnswer(inv -> inv.getArgument(0));

        Employee saved = employeeService.createEmployee(employee("Ada", "  ada@example.com  "), 1L);

        assertThat(saved.getEmail()).isEqualTo("ada@example.com");
        verify(employeeRepo).existsByOrganizationIdAndEmail(1L, "ada@example.com");
    }

    @Test
    @DisplayName("createEmployee: an email already used in the organization is rejected")
    void createEmployeeDuplicateEmailThrows() {
        when(organizationRepo.findById(1L)).thenReturn(Optional.of(org("Acme")));
        when(employeeRepo.existsByOrganizationIdAndEmail(1L, "ada@example.com")).thenReturn(true);

        assertThatThrownBy(() -> employeeService.createEmployee(employee("Ada", "ada@example.com"), 1L))
                .isInstanceOf(EmployeeValidationException.class)
                .hasMessageContaining("Email already exists in organization")
                .hasMessageContaining("organizationId=1")
                .hasMessageContaining("ada@example.com");

        verify(employeeRepo, never()).save(any());
    }

    @Test
    @DisplayName("createEmployee: blank name is rejected")
    void createEmployeeBlankNameThrows() {
        when(organizationRepo.findById(1L)).thenReturn(Optional.of(org("Acme")));

        assertThatThrownBy(() -> employeeService.createEmployee(employee("   "), 1L))
                .isInstanceOf(EmployeeValidationException.class)
                .hasMessage("Name must be provided");

        verify(employeeRepo, never()).save(any());
    }

    @Test
    @DisplayName("createEmployee: missing name is rejected")
    void createEmployeeNullNameThrows() {
        when(organizationRepo.findById(1L)).thenReturn(Optional.of(org("Acme")));

        assertThatThrownBy(() -> employeeService.createEmployee(employee(null), 1L))
                .isInstanceOf(EmployeeValidationException.class)
                .hasMessage("Name must be provided");

        verify(employeeRepo, never()).save(any());
    }

    @Test
    @DisplayName("createEmployee: a null organization ID is treated as not found without hitting the repository")
    void createEmployeeNullOrganizationIdThrows() {
        assertThatThrownBy(() -> employeeService.createEmployee(employee("Ada"), null))
                .isInstanceOf(OrganizationNotFoundException.class);

        verify(organizationRepo, never()).findById(any());
        verify(employeeRepo, never()).save(any());
    }

    @Test
    @DisplayName("createEmployee: a missing organization is rejected")
    void createEmployeeMissingOrganizationThrows() {
        when(organizationRepo.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> employeeService.createEmployee(employee("Ada"), 99L))
                .isInstanceOf(OrganizationNotFoundException.class)
                .hasMessageContaining("99");

        verify(employeeRepo, never()).save(any());
    }

    @Test
    @DisplayName("createEmployee: the organization is resolved before the name is validated")
    void createEmployeeResolvesOrganizationBeforeValidatingName() {
        when(organizationRepo.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> employeeService.createEmployee(employee("   "), 99L))
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
        when(organizationRepo.findById(1L)).thenReturn(Optional.of(organization));
        when(employeeRepo.findById(5L)).thenReturn(Optional.of(existing));
        when(employeeRepo.existsByOrganizationIdAndEmailAndIdNot(1L, "new@example.com", 5L))
                .thenReturn(false);
        when(employeeRepo.save(any(Employee.class))).thenAnswer(inv -> inv.getArgument(0));

        Employee updated = employeeService.updateEmployee(5L, replacement, 1L);

        assertThat(updated.getName()).isEqualTo("New Name");
        assertThat(updated.getEmail()).isEqualTo("new@example.com");
        assertThat(updated.isActive()).isFalse();
        assertThat(updated.getOrganization()).isSameAs(organization);
        verify(employeeRepo).save(existing);
    }

    @Test
    @DisplayName("updateEmployee: the employee can be moved to another organization")
    void updateEmployeeCanMoveToAnotherOrganization() {
        Organization acme = org("Acme");
        Organization globex = org("Globex");
        Employee existing = employee("Ada");
        existing.setOrganization(acme);
        when(organizationRepo.findById(2L)).thenReturn(Optional.of(globex));
        when(employeeRepo.findById(5L)).thenReturn(Optional.of(existing));
        when(employeeRepo.save(any(Employee.class))).thenAnswer(inv -> inv.getArgument(0));

        Employee updated = employeeService.updateEmployee(5L, employee("Ada"), 2L);

        assertThat(updated.getOrganization()).isSameAs(globex);
        verify(employeeRepo).save(existing);
    }

    @Test
    @DisplayName("updateEmployee: keeping the employee's own email is allowed")
    void updateEmployeeKeepsOwnEmail() {
        Organization organization = org("Acme");
        Employee existing = employee("Ada", "ada@example.com");
        existing.setOrganization(organization);
        when(organizationRepo.findById(1L)).thenReturn(Optional.of(organization));
        when(employeeRepo.findById(5L)).thenReturn(Optional.of(existing));
        when(employeeRepo.existsByOrganizationIdAndEmailAndIdNot(1L, "ada@example.com", 5L))
                .thenReturn(false);
        when(employeeRepo.save(any(Employee.class))).thenAnswer(inv -> inv.getArgument(0));

        Employee updated = employeeService.updateEmployee(
                5L, employee("Ada Lovelace", "ada@example.com"), 1L);

        assertThat(updated.getEmail()).isEqualTo("ada@example.com");
        verify(employeeRepo).existsByOrganizationIdAndEmailAndIdNot(1L, "ada@example.com", 5L);
    }

    @Test
    @DisplayName("updateEmployee: an email used by another employee in the target organization is rejected")
    void updateEmployeeDuplicateEmailInTargetOrganizationThrows() {
        Organization organization = org("Globex");
        Employee existing = employee("Ada");
        when(organizationRepo.findById(2L)).thenReturn(Optional.of(organization));
        when(employeeRepo.findById(5L)).thenReturn(Optional.of(existing));
        when(employeeRepo.existsByOrganizationIdAndEmailAndIdNot(2L, "taken@example.com", 5L))
                .thenReturn(true);

        assertThatThrownBy(() -> employeeService.updateEmployee(
                5L, employee("Ada", "taken@example.com"), 2L))
                .isInstanceOf(EmployeeValidationException.class)
                .hasMessageContaining("Email already exists in organization")
                .hasMessageContaining("organizationId=2");

        verify(employeeRepo, never()).save(any());
    }

    @Test
    @DisplayName("updateEmployee: a whitespace-only email clears the stored email")
    void updateEmployeeClearsEmailWhenBlank() {
        Organization organization = org("Acme");
        Employee existing = employee("Ada", "ada@example.com");
        existing.setOrganization(organization);
        when(organizationRepo.findById(1L)).thenReturn(Optional.of(organization));
        when(employeeRepo.findById(5L)).thenReturn(Optional.of(existing));
        when(employeeRepo.save(any(Employee.class))).thenAnswer(inv -> inv.getArgument(0));

        Employee updated = employeeService.updateEmployee(5L, employee("Ada", "   "), 1L);

        assertThat(updated.getEmail()).isNull();
        verify(employeeRepo, never()).existsByOrganizationIdAndEmailAndIdNot(any(), any(), any());
    }

    @Test
    @DisplayName("updateEmployee: blank name is rejected before the employee is looked up")
    void updateEmployeeBlankNameThrowsBeforeLookup() {
        when(organizationRepo.findById(1L)).thenReturn(Optional.of(org("Acme")));

        assertThatThrownBy(() -> employeeService.updateEmployee(5L, employee("   "), 1L))
                .isInstanceOf(EmployeeValidationException.class)
                .hasMessage("Name must be provided");

        verify(employeeRepo, never()).findById(any());
        verify(employeeRepo, never()).save(any());
    }

    @Test
    @DisplayName("updateEmployee: a null organization ID is treated as not found without hitting the repositories")
    void updateEmployeeNullOrganizationIdThrows() {
        assertThatThrownBy(() -> employeeService.updateEmployee(5L, employee("Ada"), null))
                .isInstanceOf(OrganizationNotFoundException.class);

        verify(organizationRepo, never()).findById(any());
        verify(employeeRepo, never()).findById(any());
    }

    @Test
    @DisplayName("updateEmployee: a missing organization is rejected")
    void updateEmployeeMissingOrganizationThrows() {
        when(organizationRepo.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> employeeService.updateEmployee(5L, employee("Ada"), 99L))
                .isInstanceOf(OrganizationNotFoundException.class)
                .hasMessageContaining("99");

        verify(employeeRepo, never()).findById(any());
        verify(employeeRepo, never()).save(any());
    }

    @Test
    @DisplayName("updateEmployee: a missing employee is rejected")
    void updateEmployeeMissingEmployeeThrows() {
        when(organizationRepo.findById(1L)).thenReturn(Optional.of(org("Acme")));
        when(employeeRepo.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> employeeService.updateEmployee(99L, employee("Ada"), 1L))
                .isInstanceOf(EmployeeNotFoundException.class)
                .hasMessageContaining("99");

        verify(employeeRepo, never()).existsByOrganizationIdAndEmailAndIdNot(any(), any(), any());
        verify(employeeRepo, never()).save(any());
    }

    // --- deleteEmployee ---

    @Test
    @DisplayName("deleteEmployee: an existing employee is deleted")
    void deleteEmployeeRemovesExisting() {
        when(employeeRepo.existsById(5L)).thenReturn(true);

        employeeService.deleteEmployee(5L);

        verify(employeeRepo).deleteById(5L);
    }

    @Test
    @DisplayName("deleteEmployee: a missing employee is rejected")
    void deleteEmployeeMissingThrows() {
        when(employeeRepo.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> employeeService.deleteEmployee(99L))
                .isInstanceOf(EmployeeNotFoundException.class)
                .hasMessageContaining("99");

        verify(employeeRepo, never()).deleteById(any());
    }

    // --- getAll / getById ---

    @Test
    @DisplayName("getAllEmployees: returns every employee")
    void getAllEmployeesReturnsAll() {
        when(employeeRepo.findAll()).thenReturn(List.of(employee("Ada"), employee("Grace")));

        List<Employee> all = employeeService.getAllEmployees();

        assertThat(all).hasSize(2)
                .extracting(Employee::getName)
                .containsExactly("Ada", "Grace");
    }

    @Test
    @DisplayName("getEmployeeById: delegates to the repository when the ID is present")
    void getEmployeeByIdDelegates() {
        Employee existing = employee("Ada");
        when(employeeRepo.findById(5L)).thenReturn(Optional.of(existing));

        assertThat(employeeService.getEmployeeById(5L)).contains(existing);
    }

    @Test
    @DisplayName("getEmployeeById: null ID throws IllegalArgumentException")
    void getEmployeeByIdNullThrows() {
        assertThatThrownBy(() -> employeeService.getEmployeeById(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("ID cannot be null");
    }
}
