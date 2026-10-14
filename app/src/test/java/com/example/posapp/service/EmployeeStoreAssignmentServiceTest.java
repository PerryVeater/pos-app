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
import com.example.posapp.entity.EmployeeStoreAssignment;
import com.example.posapp.entity.Organization;
import com.example.posapp.entity.Store;
import com.example.posapp.exception.EmployeeNotFoundException;
import com.example.posapp.exception.EmployeeStoreValidationException;
import com.example.posapp.exception.StoreNotFoundException;
import com.example.posapp.repository.EmployeeStoreAssignmentRepository;
import com.example.posapp.repository.EmployeeRepository;
import com.example.posapp.repository.StoreRepository;

/**
 * Unit tests for the {@link EmployeeStoreAssignmentService} business
 * rules.
 * <p>
 * Repositories are mocked, so these tests exercise the service in
 * isolation: the employee and the store must exist on every operation,
 * both sides must share an organization, adding an existing pair is a
 * no-op, removing a missing pair is a no-op, and the two listing methods
 * require the aggregate to exist. Because entities cannot be assigned IDs
 * directly, tests that need distinct organization IDs stub Mockito mocks
 * for {@link Organization} rather than persisting rows. No Spring context
 * or database is required.
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class EmployeeStoreAssignmentServiceTest {

    @Mock
    private EmployeeRepository employeeRepo;

    @Mock
    private StoreRepository storeRepo;

    @Mock
    private EmployeeStoreAssignmentRepository assignmentRepo;

    @InjectMocks
    private EmployeeStoreAssignmentService assignmentService;

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
     * Build a mock store whose organization ID equals the supplied value.
     * @param organizationId the owning organization ID to expose
     * @return the mocked store
     */
    private static Store storeInOrg(long organizationId) {
        Organization organization = mock(Organization.class);
        when(organization.getId()).thenReturn(organizationId);
        Store store = mock(Store.class);
        when(store.getOrganization()).thenReturn(organization);
        return store;
    }

    // --- assignEmployeeToStore ---

    @Test
    @DisplayName("assignEmployeeToStore: saves a new assignment when the pair is not already attached")
    void assignSavesNewPair() {
        Employee employee = employeeInOrg(1L);
        Store store = storeInOrg(1L);
        when(employeeRepo.findById(10L)).thenReturn(Optional.of(employee));
        when(storeRepo.findById(20L)).thenReturn(Optional.of(store));
        when(assignmentRepo.findByEmployeeIdAndStoreId(10L, 20L))
                .thenReturn(Optional.empty());
        when(assignmentRepo.saveAndFlush(any(EmployeeStoreAssignment.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        EmployeeStoreAssignment saved = assignmentService.assignEmployeeToStore(10L, 20L);

        assertThat(saved.getEmployee()).isSameAs(employee);
        assertThat(saved.getStore()).isSameAs(store);
        assertThat(saved.getOrganizationId()).isEqualTo(1L);
        verify(assignmentRepo).saveAndFlush(any(EmployeeStoreAssignment.class));
    }

    @Test
    @DisplayName("assignEmployeeToStore: concurrent duplicate insert is caught and the winner's row is returned")
    void assignReturnsWinnerOnConcurrentDuplicateInsert() {
        Employee employee = employeeInOrg(1L);
        Store store = storeInOrg(1L);
        EmployeeStoreAssignment winner = mock(EmployeeStoreAssignment.class);
        // Discriminating exception chain: the deepest SQLException leaf
        // carries a generic PG batch message with no constraint name, the
        // middle Hibernate wrapper is the only link that names
        // uk_employee_store_assignment, and Spring's DuplicateKeyException
        // on top is generic. The chain-walking detector finds the
        // substring on the middle link and takes the recovery path; a
        // regression to a leaf-only check would fail this test.
        SQLException leaf = new SQLException("Batch entry 0 was executed");
        RuntimeException hibernateWrapper = new RuntimeException(
                "could not execute statement [ERROR: duplicate key value violates unique"
                        + " constraint \"uk_employee_store_assignment\"  Detail: Key"
                        + " (employee_id, store_id)=(10, 20) already exists.]"
                        + " [insert into employee_store_assignment (...)]",
                leaf);
        DuplicateKeyException duplicateViolation = new DuplicateKeyException(
                "JPA saveAndFlush failed", hibernateWrapper);
        when(employeeRepo.findById(10L)).thenReturn(Optional.of(employee));
        when(storeRepo.findById(20L)).thenReturn(Optional.of(store));
        when(assignmentRepo.findByEmployeeIdAndStoreId(10L, 20L))
                .thenReturn(Optional.empty(), Optional.of(winner));
        when(assignmentRepo.saveAndFlush(any(EmployeeStoreAssignment.class)))
                .thenThrow(duplicateViolation);

        EmployeeStoreAssignment result = assignmentService.assignEmployeeToStore(10L, 20L);

        assertThat(result).isSameAs(winner);
        verify(assignmentRepo).saveAndFlush(any(EmployeeStoreAssignment.class));
    }

    @Test
    @DisplayName("assignEmployeeToStore: duplicate-key violation whose recovery read finds nothing rethrows the original exception")
    void assignRethrowsWhenRecoveryReadFindsNoRow() {
        Employee employee = employeeInOrg(1L);
        Store store = storeInOrg(1L);
        SQLException leaf = new SQLException(
                "ERROR: duplicate key value violates unique constraint"
                        + " \"uk_employee_store_assignment\"");
        DuplicateKeyException duplicateViolation = new DuplicateKeyException(
                "Batch entry rejected", leaf);
        when(employeeRepo.findById(10L)).thenReturn(Optional.of(employee));
        when(storeRepo.findById(20L)).thenReturn(Optional.of(store));
        // Both the pre-check and the recovery read return empty: the
        // concurrent winner was deleted before our re-read could see it,
        // so recovery is impossible and the original exception is rethrown.
        when(assignmentRepo.findByEmployeeIdAndStoreId(10L, 20L))
                .thenReturn(Optional.empty(), Optional.empty());
        when(assignmentRepo.saveAndFlush(any(EmployeeStoreAssignment.class)))
                .thenThrow(duplicateViolation);

        assertThatThrownBy(() -> assignmentService.assignEmployeeToStore(10L, 20L))
                .isSameAs(duplicateViolation);
    }

    @Test
    @DisplayName("assignEmployeeToStore: unrelated integrity violations are rethrown so the client still sees 400")
    void assignRethrowsUnrelatedIntegrityViolation() {
        Employee employee = employeeInOrg(1L);
        Store store = storeInOrg(1L);
        DataIntegrityViolationException fkViolation = new DataIntegrityViolationException(
                "insert on table \"employee_store_assignment\" violates foreign key constraint"
                        + " \"fk_esa_store\"");
        when(employeeRepo.findById(10L)).thenReturn(Optional.of(employee));
        when(storeRepo.findById(20L)).thenReturn(Optional.of(store));
        when(assignmentRepo.findByEmployeeIdAndStoreId(10L, 20L))
                .thenReturn(Optional.empty());
        when(assignmentRepo.saveAndFlush(any(EmployeeStoreAssignment.class)))
                .thenThrow(fkViolation);

        assertThatThrownBy(() -> assignmentService.assignEmployeeToStore(10L, 20L))
                .isSameAs(fkViolation);
    }

    @Test
    @DisplayName("assignEmployeeToStore: existing assignment is returned unchanged so PUT is idempotent")
    void assignIsIdempotentForExistingPair() {
        Employee employee = employeeInOrg(1L);
        Store store = storeInOrg(1L);
        EmployeeStoreAssignment existing = mock(EmployeeStoreAssignment.class);
        when(employeeRepo.findById(10L)).thenReturn(Optional.of(employee));
        when(storeRepo.findById(20L)).thenReturn(Optional.of(store));
        when(assignmentRepo.findByEmployeeIdAndStoreId(10L, 20L))
                .thenReturn(Optional.of(existing));

        EmployeeStoreAssignment result = assignmentService.assignEmployeeToStore(10L, 20L);

        assertThat(result).isSameAs(existing);
        verify(assignmentRepo, never()).save(any(EmployeeStoreAssignment.class));
        verify(assignmentRepo, never()).saveAndFlush(any(EmployeeStoreAssignment.class));
    }

    @Test
    @DisplayName("assignEmployeeToStore: missing employee throws EmployeeNotFoundException and saves nothing")
    void assignMissingEmployeeThrows() {
        when(employeeRepo.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> assignmentService.assignEmployeeToStore(99L, 20L))
                .isInstanceOf(EmployeeNotFoundException.class)
                .hasMessageContaining("99");

        verify(assignmentRepo, never()).saveAndFlush(any(EmployeeStoreAssignment.class));
    }

    @Test
    @DisplayName("assignEmployeeToStore: missing store throws StoreNotFoundException and saves nothing")
    void assignMissingStoreThrows() {
        Employee employee = mock(Employee.class);
        when(employeeRepo.findById(10L)).thenReturn(Optional.of(employee));
        when(storeRepo.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> assignmentService.assignEmployeeToStore(10L, 99L))
                .isInstanceOf(StoreNotFoundException.class)
                .hasMessageContaining("99");

        verify(assignmentRepo, never()).saveAndFlush(any(EmployeeStoreAssignment.class));
    }

    @Test
    @DisplayName("assignEmployeeToStore: cross-organization pair is rejected before anything is saved")
    void assignCrossOrganizationThrows() {
        Employee employee = employeeInOrg(1L);
        Store store = storeInOrg(2L);
        when(employeeRepo.findById(10L)).thenReturn(Optional.of(employee));
        when(storeRepo.findById(20L)).thenReturn(Optional.of(store));

        assertThatThrownBy(() -> assignmentService.assignEmployeeToStore(10L, 20L))
                .isInstanceOf(EmployeeStoreValidationException.class)
                .hasMessageContaining("same organization");

        verify(assignmentRepo, never()).saveAndFlush(any(EmployeeStoreAssignment.class));
    }

    // --- removeEmployeeFromStore ---

    @Test
    @DisplayName("removeEmployeeFromStore: deletes the existing assignment row")
    void removeDeletesExisting() {
        Employee employee = employeeInOrg(1L);
        Store store = storeInOrg(1L);
        EmployeeStoreAssignment existing = mock(EmployeeStoreAssignment.class);
        when(employeeRepo.findById(10L)).thenReturn(Optional.of(employee));
        when(storeRepo.findById(20L)).thenReturn(Optional.of(store));
        when(assignmentRepo.findByEmployeeIdAndStoreId(10L, 20L))
                .thenReturn(Optional.of(existing));

        assignmentService.removeEmployeeFromStore(10L, 20L);

        verify(assignmentRepo).delete(existing);
    }

    @Test
    @DisplayName("removeEmployeeFromStore: missing pair is a no-op so DELETE is idempotent")
    void removeIsIdempotentForMissingPair() {
        Employee employee = employeeInOrg(1L);
        Store store = storeInOrg(1L);
        when(employeeRepo.findById(10L)).thenReturn(Optional.of(employee));
        when(storeRepo.findById(20L)).thenReturn(Optional.of(store));
        when(assignmentRepo.findByEmployeeIdAndStoreId(10L, 20L))
                .thenReturn(Optional.empty());

        assignmentService.removeEmployeeFromStore(10L, 20L);

        verify(assignmentRepo, never()).delete(any(EmployeeStoreAssignment.class));
    }

    @Test
    @DisplayName("removeEmployeeFromStore: missing employee throws EmployeeNotFoundException")
    void removeMissingEmployeeThrows() {
        when(employeeRepo.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> assignmentService.removeEmployeeFromStore(99L, 20L))
                .isInstanceOf(EmployeeNotFoundException.class)
                .hasMessageContaining("99");

        verify(assignmentRepo, never()).delete(any(EmployeeStoreAssignment.class));
    }

    @Test
    @DisplayName("removeEmployeeFromStore: missing store throws StoreNotFoundException")
    void removeMissingStoreThrows() {
        Employee employee = mock(Employee.class);
        when(employeeRepo.findById(10L)).thenReturn(Optional.of(employee));
        when(storeRepo.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> assignmentService.removeEmployeeFromStore(10L, 99L))
                .isInstanceOf(StoreNotFoundException.class)
                .hasMessageContaining("99");

        verify(assignmentRepo, never()).delete(any(EmployeeStoreAssignment.class));
    }

    @Test
    @DisplayName("removeEmployeeFromStore: cross-organization pair is rejected before anything is deleted")
    void removeCrossOrganizationThrows() {
        Employee employee = employeeInOrg(1L);
        Store store = storeInOrg(2L);
        when(employeeRepo.findById(10L)).thenReturn(Optional.of(employee));
        when(storeRepo.findById(20L)).thenReturn(Optional.of(store));

        assertThatThrownBy(() -> assignmentService.removeEmployeeFromStore(10L, 20L))
                .isInstanceOf(EmployeeStoreValidationException.class)
                .hasMessageContaining("same organization");

        verify(assignmentRepo, never()).delete(any(EmployeeStoreAssignment.class));
    }

    // --- listStoresForEmployee ---

    @Test
    @DisplayName("listStoresForEmployee: returns the stores referenced by the assignments")
    void listStoresForEmployeeReturnsAssignedStores() {
        when(employeeRepo.existsById(10L)).thenReturn(true);
        Store firstStore = mock(Store.class);
        Store secondStore = mock(Store.class);
        EmployeeStoreAssignment first = mock(EmployeeStoreAssignment.class);
        EmployeeStoreAssignment second = mock(EmployeeStoreAssignment.class);
        when(first.getStore()).thenReturn(firstStore);
        when(second.getStore()).thenReturn(secondStore);
        when(assignmentRepo.findByEmployeeId(10L)).thenReturn(List.of(first, second));

        List<Store> stores = assignmentService.listStoresForEmployee(10L);

        assertThat(stores).containsExactly(firstStore, secondStore);
    }

    @Test
    @DisplayName("listStoresForEmployee: returns an empty list when the employee has no assignments")
    void listStoresForEmployeeEmptyReturnsEmptyList() {
        when(employeeRepo.existsById(10L)).thenReturn(true);
        when(assignmentRepo.findByEmployeeId(10L)).thenReturn(List.of());

        assertThat(assignmentService.listStoresForEmployee(10L)).isEmpty();
    }

    @Test
    @DisplayName("listStoresForEmployee: missing employee throws EmployeeNotFoundException")
    void listStoresForEmployeeMissingThrows() {
        when(employeeRepo.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> assignmentService.listStoresForEmployee(99L))
                .isInstanceOf(EmployeeNotFoundException.class)
                .hasMessageContaining("99");

        verify(assignmentRepo, never()).findByEmployeeId(any());
    }

    // --- listEmployeesForStore ---

    @Test
    @DisplayName("listEmployeesForStore: returns the employees referenced by the assignments")
    void listEmployeesForStoreReturnsAssignedEmployees() {
        when(storeRepo.existsById(20L)).thenReturn(true);
        Employee first = mock(Employee.class);
        Employee second = mock(Employee.class);
        EmployeeStoreAssignment firstAssignment = mock(EmployeeStoreAssignment.class);
        EmployeeStoreAssignment secondAssignment = mock(EmployeeStoreAssignment.class);
        when(firstAssignment.getEmployee()).thenReturn(first);
        when(secondAssignment.getEmployee()).thenReturn(second);
        when(assignmentRepo.findByStoreId(20L))
                .thenReturn(List.of(firstAssignment, secondAssignment));

        List<Employee> employees = assignmentService.listEmployeesForStore(20L);

        assertThat(employees).containsExactly(first, second);
    }

    @Test
    @DisplayName("listEmployeesForStore: returns an empty list when the store has no assignments")
    void listEmployeesForStoreEmptyReturnsEmptyList() {
        when(storeRepo.existsById(20L)).thenReturn(true);
        when(assignmentRepo.findByStoreId(20L)).thenReturn(List.of());

        assertThat(assignmentService.listEmployeesForStore(20L)).isEmpty();
    }

    @Test
    @DisplayName("listEmployeesForStore: missing store throws StoreNotFoundException")
    void listEmployeesForStoreMissingThrows() {
        when(storeRepo.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> assignmentService.listEmployeesForStore(99L))
                .isInstanceOf(StoreNotFoundException.class)
                .hasMessageContaining("99");

        verify(assignmentRepo, never()).findByStoreId(any());
    }
}
