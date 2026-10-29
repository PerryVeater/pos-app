package com.example.posapp.service;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

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
 * isolation: the employee and the store must exist in the caller's brand on
 * every operation, adding an existing pair is a no-op, removing a missing
 * pair is a no-op, and the two listing methods require the aggregate to
 * exist. Because the assignment entity derives its organization from the
 * pair, the success paths stub Mockito mocks for {@link Organization} so the
 * constructor can resolve it. No Spring context or database is required.
 * The PIN writer collaborator is mocked and left unstubbed for the legacy
 * scenarios: an unstubbed {@code findCredential} returns an empty
 * {@link Optional}, so every pre-existing assignment expectation still runs
 * down the original no-PIN path unchanged.
 * </p>
 * <p>
 * Every lookup carries the brand, so an employee or store belonging to
 * another brand is indistinguishable from a missing one. The database still
 * enforces the same-organization rule through the composite foreign keys
 * added by the V15 migration.
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class EmployeeStoreAssignmentServiceTest {

    /** The caller's brand. */
    private static final long BRAND = 1L;

    /** A second brand the caller must never reach. */
    private static final long OTHER_BRAND = 2L;

    @Mock
    private EmployeeRepository employeeRepo;

    @Mock
    private StoreRepository storeRepo;

    @Mock
    private EmployeeStoreAssignmentRepository assignmentRepo;

    @Mock
    private EmployeeStorePinWriter pinWriter;

    @Mock
    private PosSessionRevoker sessionRevoker;

    @InjectMocks
    private EmployeeStoreAssignmentService assignmentService;

    /**
     * Build a mock employee whose organization ID equals the supplied
     * value. Using a Mockito mock avoids the ID assignment limitation of
     * the JPA entities and lets the assignment entity resolve the
     * organization it derives from the pair.
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
        Employee employee = employeeInOrg(BRAND);
        Store store = storeInOrg(BRAND);
        when(employeeRepo.findByIdAndOrganizationId(10L, BRAND)).thenReturn(Optional.of(employee));
        when(storeRepo.findByIdAndOrganizationId(20L, BRAND)).thenReturn(Optional.of(store));
        when(assignmentRepo.findByEmployeeIdAndStoreId(10L, 20L))
                .thenReturn(Optional.empty());
        when(assignmentRepo.saveAndFlush(any(EmployeeStoreAssignment.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        EmployeeStoreAssignment saved = assignmentService.assignEmployeeToStore(10L, 20L, BRAND);

        assertThat(saved.getEmployee()).isSameAs(employee);
        assertThat(saved.getStore()).isSameAs(store);
        assertThat(saved.getOrganizationId()).isEqualTo(BRAND);
        verify(assignmentRepo).saveAndFlush(any(EmployeeStoreAssignment.class));
    }

    @Test
    @DisplayName("assignEmployeeToStore: concurrent duplicate insert is caught and the winner's row is returned")
    void assignReturnsWinnerOnConcurrentDuplicateInsert() {
        Employee employee = employeeInOrg(BRAND);
        Store store = storeInOrg(BRAND);
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
        when(employeeRepo.findByIdAndOrganizationId(10L, BRAND)).thenReturn(Optional.of(employee));
        when(storeRepo.findByIdAndOrganizationId(20L, BRAND)).thenReturn(Optional.of(store));
        when(assignmentRepo.findByEmployeeIdAndStoreId(10L, 20L))
                .thenReturn(Optional.empty(), Optional.of(winner));
        when(assignmentRepo.saveAndFlush(any(EmployeeStoreAssignment.class)))
                .thenThrow(duplicateViolation);

        EmployeeStoreAssignment result =
                assignmentService.assignEmployeeToStore(10L, 20L, BRAND);

        assertThat(result).isSameAs(winner);
        verify(assignmentRepo).saveAndFlush(any(EmployeeStoreAssignment.class));
    }

    @Test
    @DisplayName("assignEmployeeToStore: duplicate-key violation whose recovery read finds nothing rethrows the original exception")
    void assignRethrowsWhenRecoveryReadFindsNoRow() {
        Employee employee = employeeInOrg(BRAND);
        Store store = storeInOrg(BRAND);
        SQLException leaf = new SQLException(
                "ERROR: duplicate key value violates unique constraint"
                        + " \"uk_employee_store_assignment\"");
        DuplicateKeyException duplicateViolation = new DuplicateKeyException(
                "Batch entry rejected", leaf);
        when(employeeRepo.findByIdAndOrganizationId(10L, BRAND)).thenReturn(Optional.of(employee));
        when(storeRepo.findByIdAndOrganizationId(20L, BRAND)).thenReturn(Optional.of(store));
        // Both the pre-check and the recovery read return empty: the
        // concurrent winner was deleted before our re-read could see it,
        // so recovery is impossible and the original exception is rethrown.
        when(assignmentRepo.findByEmployeeIdAndStoreId(10L, 20L))
                .thenReturn(Optional.empty(), Optional.empty());
        when(assignmentRepo.saveAndFlush(any(EmployeeStoreAssignment.class)))
                .thenThrow(duplicateViolation);

        assertThatThrownBy(() -> assignmentService.assignEmployeeToStore(10L, 20L, BRAND))
                .isSameAs(duplicateViolation);
    }

    @Test
    @DisplayName("assignEmployeeToStore: unrelated integrity violations are rethrown so the client still sees 400")
    void assignRethrowsUnrelatedIntegrityViolation() {
        Employee employee = employeeInOrg(BRAND);
        Store store = storeInOrg(BRAND);
        DataIntegrityViolationException fkViolation = new DataIntegrityViolationException(
                "insert on table \"employee_store_assignment\" violates foreign key constraint"
                        + " \"fk_esa_store\"");
        when(employeeRepo.findByIdAndOrganizationId(10L, BRAND)).thenReturn(Optional.of(employee));
        when(storeRepo.findByIdAndOrganizationId(20L, BRAND)).thenReturn(Optional.of(store));
        when(assignmentRepo.findByEmployeeIdAndStoreId(10L, 20L))
                .thenReturn(Optional.empty());
        when(assignmentRepo.saveAndFlush(any(EmployeeStoreAssignment.class)))
                .thenThrow(fkViolation);

        assertThatThrownBy(() -> assignmentService.assignEmployeeToStore(10L, 20L, BRAND))
                .isSameAs(fkViolation);
    }

    @Test
    @DisplayName("assignEmployeeToStore: existing assignment is returned unchanged so PUT is idempotent")
    void assignIsIdempotentForExistingPair() {
        // The existing row short-circuits the insert, so the organization is
        // never resolved and the pair needs no owning brand.
        Employee employee = mock(Employee.class);
        Store store = mock(Store.class);
        EmployeeStoreAssignment existing = mock(EmployeeStoreAssignment.class);
        when(employeeRepo.findByIdAndOrganizationId(10L, BRAND)).thenReturn(Optional.of(employee));
        when(storeRepo.findByIdAndOrganizationId(20L, BRAND)).thenReturn(Optional.of(store));
        when(assignmentRepo.findByEmployeeIdAndStoreId(10L, 20L))
                .thenReturn(Optional.of(existing));

        EmployeeStoreAssignment result =
                assignmentService.assignEmployeeToStore(10L, 20L, BRAND);

        assertThat(result).isSameAs(existing);
        verify(assignmentRepo, never()).save(any(EmployeeStoreAssignment.class));
        verify(assignmentRepo, never()).saveAndFlush(any(EmployeeStoreAssignment.class));
    }

    @Test
    @DisplayName("assignEmployeeToStore: missing employee throws EmployeeNotFoundException and saves nothing")
    void assignMissingEmployeeThrows() {
        when(employeeRepo.findByIdAndOrganizationId(99L, BRAND)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> assignmentService.assignEmployeeToStore(99L, 20L, BRAND))
                .isInstanceOf(EmployeeNotFoundException.class)
                .hasMessageContaining("99");

        verify(storeRepo, never()).findByIdAndOrganizationId(any(), any());
        verify(assignmentRepo, never()).saveAndFlush(any(EmployeeStoreAssignment.class));
    }

    @Test
    @DisplayName("assignEmployeeToStore: missing store throws StoreNotFoundException and saves nothing")
    void assignMissingStoreThrows() {
        when(employeeRepo.findByIdAndOrganizationId(10L, BRAND))
                .thenReturn(Optional.of(mock(Employee.class)));
        when(storeRepo.findByIdAndOrganizationId(99L, BRAND)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> assignmentService.assignEmployeeToStore(10L, 99L, BRAND))
                .isInstanceOf(StoreNotFoundException.class)
                .hasMessageContaining("99");

        verify(assignmentRepo, never()).saveAndFlush(any(EmployeeStoreAssignment.class));
    }

    @Test
    @DisplayName("assignEmployeeToStore: an employee of another brand is answered as missing")
    void assignEmployeeOutsideBrandIsMissing() {
        assertThatThrownBy(() -> assignmentService.assignEmployeeToStore(10L, 20L, OTHER_BRAND))
                .isInstanceOf(EmployeeNotFoundException.class)
                .hasMessageContaining("10");

        verify(employeeRepo).findByIdAndOrganizationId(10L, OTHER_BRAND);
        verify(storeRepo, never()).findByIdAndOrganizationId(any(), any());
        verify(assignmentRepo, never()).saveAndFlush(any(EmployeeStoreAssignment.class));
    }

    @Test
    @DisplayName("assignEmployeeToStore: a store of another brand is answered as missing")
    void assignStoreOutsideBrandIsMissing() {
        when(employeeRepo.findByIdAndOrganizationId(10L, BRAND))
                .thenReturn(Optional.of(mock(Employee.class)));

        assertThatThrownBy(() -> assignmentService.assignEmployeeToStore(10L, 20L, BRAND))
                .isInstanceOf(StoreNotFoundException.class)
                .hasMessageContaining("20");

        verify(storeRepo).findByIdAndOrganizationId(20L, BRAND);
        verify(assignmentRepo, never()).saveAndFlush(any(EmployeeStoreAssignment.class));
    }

    // --- removeEmployeeFromStore ---

    @Test
    @DisplayName("removeEmployeeFromStore: deletes the existing assignment row")
    void removeDeletesExisting() {
        EmployeeStoreAssignment existing = mock(EmployeeStoreAssignment.class);
        when(employeeRepo.findByIdAndOrganizationId(10L, BRAND))
                .thenReturn(Optional.of(mock(Employee.class)));
        when(storeRepo.findByIdAndOrganizationId(20L, BRAND))
                .thenReturn(Optional.of(mock(Store.class)));
        when(assignmentRepo.findByEmployeeIdAndStoreId(10L, 20L))
                .thenReturn(Optional.of(existing));

        assignmentService.removeEmployeeFromStore(10L, 20L, BRAND);

        verify(assignmentRepo).delete(existing);
    }

    @Test
    @DisplayName("removeEmployeeFromStore: missing pair is a no-op so DELETE is idempotent")
    void removeIsIdempotentForMissingPair() {
        when(employeeRepo.findByIdAndOrganizationId(10L, BRAND))
                .thenReturn(Optional.of(mock(Employee.class)));
        when(storeRepo.findByIdAndOrganizationId(20L, BRAND))
                .thenReturn(Optional.of(mock(Store.class)));
        when(assignmentRepo.findByEmployeeIdAndStoreId(10L, 20L))
                .thenReturn(Optional.empty());

        assignmentService.removeEmployeeFromStore(10L, 20L, BRAND);

        verify(assignmentRepo, never()).delete(any(EmployeeStoreAssignment.class));
    }

    @Test
    @DisplayName("removeEmployeeFromStore: missing employee throws EmployeeNotFoundException")
    void removeMissingEmployeeThrows() {
        when(employeeRepo.findByIdAndOrganizationId(99L, BRAND)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> assignmentService.removeEmployeeFromStore(99L, 20L, BRAND))
                .isInstanceOf(EmployeeNotFoundException.class)
                .hasMessageContaining("99");

        verify(assignmentRepo, never()).delete(any(EmployeeStoreAssignment.class));
    }

    @Test
    @DisplayName("removeEmployeeFromStore: missing store throws StoreNotFoundException")
    void removeMissingStoreThrows() {
        when(employeeRepo.findByIdAndOrganizationId(10L, BRAND))
                .thenReturn(Optional.of(mock(Employee.class)));
        when(storeRepo.findByIdAndOrganizationId(99L, BRAND)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> assignmentService.removeEmployeeFromStore(10L, 99L, BRAND))
                .isInstanceOf(StoreNotFoundException.class)
                .hasMessageContaining("99");

        verify(assignmentRepo, never()).delete(any(EmployeeStoreAssignment.class));
    }

    @Test
    @DisplayName("removeEmployeeFromStore: an employee of another brand is answered as missing")
    void removeEmployeeOutsideBrandIsMissing() {
        assertThatThrownBy(() -> assignmentService.removeEmployeeFromStore(10L, 20L, OTHER_BRAND))
                .isInstanceOf(EmployeeNotFoundException.class)
                .hasMessageContaining("10");

        verify(assignmentRepo, never()).delete(any(EmployeeStoreAssignment.class));
        verify(sessionRevoker, never()).revokeForEmployeeAtStore(any(), any(), any());
    }

    // --- listStoresForEmployee ---

    @Test
    @DisplayName("listStoresForEmployee: returns the employee's assignments")
    void listStoresForEmployeeReturnsAssignments() {
        when(employeeRepo.findByIdAndOrganizationId(10L, BRAND))
                .thenReturn(Optional.of(mock(Employee.class)));
        EmployeeStoreAssignment first = mock(EmployeeStoreAssignment.class);
        EmployeeStoreAssignment second = mock(EmployeeStoreAssignment.class);
        when(assignmentRepo.findByEmployeeId(eq(10L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(first, second)));

        Page<EmployeeStoreAssignment> assignments = assignmentService
                .listStoresForEmployee(10L, BRAND, PageRequest.of(0, 25));

        assertThat(assignments).containsExactly(first, second);
    }

    @Test
    @DisplayName("listStoresForEmployee: returns an empty list when the employee has no assignments")
    void listStoresForEmployeeEmptyReturnsEmptyList() {
        when(employeeRepo.findByIdAndOrganizationId(10L, BRAND))
                .thenReturn(Optional.of(mock(Employee.class)));
        when(assignmentRepo.findByEmployeeId(eq(10L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        assertThat(assignmentService.listStoresForEmployee(10L, BRAND, PageRequest.of(0, 25)))
                .isEmpty();
    }

    @Test
    @DisplayName("listStoresForEmployee: missing employee throws EmployeeNotFoundException")
    void listStoresForEmployeeMissingThrows() {
        when(employeeRepo.findByIdAndOrganizationId(99L, BRAND)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> assignmentService.listStoresForEmployee(99L, BRAND,
                        PageRequest.of(0, 25)))
                .isInstanceOf(EmployeeNotFoundException.class)
                .hasMessageContaining("99");

        verify(assignmentRepo, never()).findByEmployeeId(any(), any());
    }

    // --- listEmployeesForStore ---

    @Test
    @DisplayName("listEmployeesForStore: returns the store's assignments")
    void listEmployeesForStoreReturnsAssignments() {
        when(storeRepo.findByIdAndOrganizationId(20L, BRAND))
                .thenReturn(Optional.of(mock(Store.class)));
        EmployeeStoreAssignment firstAssignment = mock(EmployeeStoreAssignment.class);
        EmployeeStoreAssignment secondAssignment = mock(EmployeeStoreAssignment.class);
        when(assignmentRepo.findByStoreId(eq(20L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(firstAssignment, secondAssignment)));

        Page<EmployeeStoreAssignment> assignments = assignmentService
                .listEmployeesForStore(20L, BRAND, PageRequest.of(0, 25));

        assertThat(assignments).containsExactly(firstAssignment, secondAssignment);
    }

    @Test
    @DisplayName("listEmployeesForStore: returns an empty list when the store has no assignments")
    void listEmployeesForStoreEmptyReturnsEmptyList() {
        when(storeRepo.findByIdAndOrganizationId(20L, BRAND))
                .thenReturn(Optional.of(mock(Store.class)));
        when(assignmentRepo.findByStoreId(eq(20L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        assertThat(assignmentService.listEmployeesForStore(20L, BRAND, PageRequest.of(0, 25)))
                .isEmpty();
    }

    @Test
    @DisplayName("listEmployeesForStore: missing store throws StoreNotFoundException")
    void listEmployeesForStoreMissingThrows() {
        when(storeRepo.findByIdAndOrganizationId(99L, BRAND)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> assignmentService.listEmployeesForStore(99L, BRAND,
                        PageRequest.of(0, 25)))
                .isInstanceOf(StoreNotFoundException.class)
                .hasMessageContaining("99");

        verify(assignmentRepo, never()).findByStoreId(any(), any());
    }
}
