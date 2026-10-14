package com.example.posapp.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.posapp.dto.PosAuthenticateResponse;
import com.example.posapp.entity.Employee;
import com.example.posapp.entity.EmployeePinCredential;
import com.example.posapp.entity.EmployeeStoreAssignment;
import com.example.posapp.entity.Store;
import com.example.posapp.exception.PosAuthFailedException;
import com.example.posapp.exception.PosAuthRateLimitedException;
import com.example.posapp.repository.EmployeePinCredentialRepository;
import com.example.posapp.repository.EmployeeRepository;
import com.example.posapp.repository.EmployeeStoreAssignmentRepository;
import com.example.posapp.repository.StoreRepository;

/**
 * Unit tests for {@link PosAuthenticationService} covering the happy path,
 * every failure mode that must collapse to one generic error, the
 * rate-limit-before-credential ordering, and the rule that a PIN is used
 * verbatim (leading zeros preserved) for verification.
 */
@ExtendWith(MockitoExtension.class)
class PosAuthenticationServiceTest {

    private static final Long STORE_ID = 2L;
    private static final Long EMPLOYEE_ID = 10L;
    private static final String CLIENT = "203.0.113.9";

    @Mock
    private StoreRepository storeRepository;
    @Mock
    private EmployeeRepository employeeRepository;
    @Mock
    private EmployeeStoreAssignmentRepository assignmentRepository;
    @Mock
    private EmployeePinCredentialRepository credentialRepository;
    @Mock
    private PinHasher pinHasher;
    @Mock
    private PosAuthRateLimiter rateLimiter;
    @Mock
    private PosSessionService sessionService;

    @InjectMocks
    private PosAuthenticationService service;

    @Test
    @DisplayName("a valid PIN for an active assigned employee opens a session")
    void authenticatesValidPin() {
        Employee employee = employee(true);
        Store store = store(true);
        stubStore(store);
        stubAssignmentEmployee(employee);
        when(credentialRepository.findByEmployeeId(EMPLOYEE_ID))
                .thenReturn(Optional.of(credential("HASH")));
        when(pinHasher.verify("0421", "HASH")).thenReturn(true);
        PosAuthenticateResponse issued = new PosAuthenticateResponse(
                "tok", EMPLOYEE_ID, "Ada", STORE_ID, "Downtown", java.time.Instant.now());
        when(sessionService.startSession(employee, store)).thenReturn(issued);

        PosAuthenticateResponse result = service.authenticate(STORE_ID, "0421", CLIENT);

        assertThat(result).isSameAs(issued);
        verify(rateLimiter).recordSuccess(STORE_ID, CLIENT);
        verify(rateLimiter, never()).recordFailure(anyLong(), anyString());
    }

    @Test
    @DisplayName("the submitted PIN is verified verbatim, preserving leading zeros")
    void pinUsedVerbatim() {
        Employee employee = employee(true);
        Store store = store(true);
        stubStore(store);
        stubAssignmentEmployee(employee);
        when(credentialRepository.findByEmployeeId(EMPLOYEE_ID))
                .thenReturn(Optional.of(credential("HASH")));
        when(pinHasher.verify(anyString(), anyString())).thenReturn(false);

        assertThatThrownBy(() -> service.authenticate(STORE_ID, "0042", CLIENT))
                .isInstanceOf(PosAuthFailedException.class);

        verify(pinHasher).verify("0042", "HASH");
    }

    @Test
    @DisplayName("a wrong PIN fails generically and counts a failed attempt")
    void wrongPinFails() {
        Employee employee = employee(true);
        stubStore(store(true));
        stubAssignmentEmployee(employee);
        when(credentialRepository.findByEmployeeId(EMPLOYEE_ID))
                .thenReturn(Optional.of(credential("HASH")));
        when(pinHasher.verify("9999", "HASH")).thenReturn(false);

        assertThatThrownBy(() -> service.authenticate(STORE_ID, "9999", CLIENT))
                .isInstanceOf(PosAuthFailedException.class)
                .hasMessage("Invalid store or PIN.");

        verify(rateLimiter).recordFailure(STORE_ID, CLIENT);
        verify(sessionService, never()).startSession(any(), any());
    }

    @Test
    @DisplayName("an assigned employee with no PIN credential fails generically")
    void missingCredentialFails() {
        Employee employee = employee(true);
        stubStore(store(true));
        stubAssignmentEmployee(employee);
        when(credentialRepository.findByEmployeeId(EMPLOYEE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.authenticate(STORE_ID, "0421", CLIENT))
                .isInstanceOf(PosAuthFailedException.class);

        verify(pinHasher, never()).verify(anyString(), anyString());
    }

    @Test
    @DisplayName("an inactive assigned employee cannot authenticate even with the right PIN")
    void inactiveEmployeeFails() {
        Employee employee = employee(false);
        Store store = store(true);
        stubStore(store);
        stubAssignmentEmployee(employee);

        assertThatThrownBy(() -> service.authenticate(STORE_ID, "0421", CLIENT))
                .isInstanceOf(PosAuthFailedException.class);

        verify(credentialRepository, never()).findByEmployeeId(anyLong());
    }

    @Test
    @DisplayName("an employee not assigned to the store fails generically")
    void notAssignedFails() {
        stubStore(store(true));
        when(assignmentRepository.findByStoreId(STORE_ID)).thenReturn(List.of());

        assertThatThrownBy(() -> service.authenticate(STORE_ID, "0421", CLIENT))
                .isInstanceOf(PosAuthFailedException.class);
    }

    @Test
    @DisplayName("a missing store and an inactive store produce the same generic failure")
    void storeStatesFailIdentically() {
        when(rateLimiter.isBlocked(STORE_ID, CLIENT)).thenReturn(false);
        when(storeRepository.findById(STORE_ID)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.authenticate(STORE_ID, "0421", CLIENT))
                .isInstanceOf(PosAuthFailedException.class);

        when(storeRepository.findById(STORE_ID)).thenReturn(Optional.of(store(false)));
        assertThatThrownBy(() -> service.authenticate(STORE_ID, "0421", CLIENT))
                .isInstanceOf(PosAuthFailedException.class)
                // The generic message never names the store.
                .hasMessage("Invalid store or PIN.");
    }

    @Test
    @DisplayName("a locked-out client is refused before any credential is examined")
    void rateLimitShortCircuits() {
        when(rateLimiter.isBlocked(STORE_ID, CLIENT)).thenReturn(true);

        assertThatThrownBy(() -> service.authenticate(STORE_ID, "0421", CLIENT))
                .isInstanceOf(PosAuthRateLimitedException.class);

        verifyNoInteractions(storeRepository, employeeRepository,
                assignmentRepository, credentialRepository, pinHasher, sessionService);
    }

    // --- fixtures ---

    private void stubStore(Store store) {
        when(rateLimiter.isBlocked(STORE_ID, CLIENT)).thenReturn(false);
        when(storeRepository.findById(STORE_ID)).thenReturn(Optional.of(store));
    }

    private void stubAssignmentEmployee(Employee employee) {
        EmployeeStoreAssignment assignment = mock(EmployeeStoreAssignment.class);
        Employee reference = mock(Employee.class);
        when(reference.getId()).thenReturn(EMPLOYEE_ID);
        when(assignment.getEmployee()).thenReturn(reference);
        when(assignmentRepository.findByStoreId(STORE_ID)).thenReturn(List.of(assignment));
        when(employeeRepository.findById(EMPLOYEE_ID)).thenReturn(Optional.of(employee));
    }

    private static Employee employee(boolean active) {
        Employee employee = new Employee("Ada");
        employee.setActive(active);
        setId(employee, EMPLOYEE_ID);
        return employee;
    }

    private static Store store(boolean active) {
        Store store = new Store(null, "Downtown", "US", "CA", "Los Angeles",
                "123 Main St", "90001", "America/Los_Angeles");
        store.setActive(active);
        setId(store, STORE_ID);
        return store;
    }

    private static EmployeePinCredential credential(String pinHash) {
        return new EmployeePinCredential(null, pinHash, "FP");
    }

    private static void setId(Object entity, Long id) {
        try {
            Field field = entity.getClass().getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, id);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
