package com.example.posapp.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
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
import com.example.posapp.entity.EmployeePinStoreScope;
import com.example.posapp.entity.Store;
import com.example.posapp.exception.PosAuthFailedException;
import com.example.posapp.exception.PosAuthRateLimitedException;
import com.example.posapp.repository.EmployeePinCredentialRepository;
import com.example.posapp.repository.EmployeePinStoreScopeRepository;
import com.example.posapp.repository.StoreRepository;

/**
 * Unit tests for {@link PosAuthenticationService} covering the happy path,
 * every failure mode that must collapse to one generic error, the
 * rate-limit-before-credential ordering, the rule that a PIN is used
 * verbatim (leading zeros preserved), and the constant-work property: an
 * attempt costs exactly one PIN verification whether or not anything at the
 * store claims the submitted PIN.
 */
@ExtendWith(MockitoExtension.class)
class PosAuthenticationServiceTest {

    private static final Long STORE_ID = 2L;
    private static final Long EMPLOYEE_ID = 10L;
    private static final String CLIENT = "203.0.113.9";
    private static final String DECOY_HASH = "pbkdf2:decoy";

    @Mock
    private StoreRepository storeRepository;
    @Mock
    private EmployeePinStoreScopeRepository scopeRepository;
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
    @DisplayName("a valid PIN for an active claimed employee opens a session after one verification")
    void authenticatesValidPin() {
        Employee employee = employee(true);
        Store store = store(true);
        stubStore(store);
        stubClaim(employee, "0421");
        when(credentialRepository.findByEmployeeId(EMPLOYEE_ID))
                .thenReturn(Optional.of(credential("HASH")));
        when(pinHasher.verify("0421", "HASH")).thenReturn(true);
        PosAuthenticateResponse issued = new PosAuthenticateResponse(
                "tok", EMPLOYEE_ID, "Ada", STORE_ID, "Downtown", java.time.Instant.now());
        when(sessionService.startSession(employee, store)).thenReturn(issued);

        PosAuthenticateResponse result = service.authenticate(STORE_ID, "0421", CLIENT);

        assertThat(result).isSameAs(issued);
        verify(pinHasher, times(1)).verify("0421", "HASH");
        verify(rateLimiter).recordSuccess(STORE_ID, CLIENT);
        verify(rateLimiter, never()).recordFailure(anyLong(), anyString());
    }

    @Test
    @DisplayName("the store and fingerprint are resolved by the submitted PIN, verbatim")
    void pinUsedVerbatim() {
        Employee employee = employee(true);
        stubStore(store(true));
        when(pinHasher.fingerprint("0042")).thenReturn("fp:0042");
        when(scopeRepository.findByStoreIdAndPinFingerprint(STORE_ID, "fp:0042"))
                .thenReturn(Optional.of(claim(employee, "fp:0042")));
        when(credentialRepository.findByEmployeeId(EMPLOYEE_ID))
                .thenReturn(Optional.of(credential("HASH")));
        when(pinHasher.verify(anyString(), anyString())).thenReturn(false);

        assertThatThrownBy(() -> service.authenticate(STORE_ID, "0042", CLIENT))
                .isInstanceOf(PosAuthFailedException.class);

        verify(scopeRepository).findByStoreIdAndPinFingerprint(STORE_ID, "fp:0042");
        verify(pinHasher).verify("0042", "HASH");
    }

    @Test
    @DisplayName("a wrong PIN fails generically and counts a failed attempt")
    void wrongPinFails() {
        stubStore(store(true));
        stubClaim(employee(true), "9999");
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
    @DisplayName("a claimed employee with no credential still costs one verification, then fails")
    void missingCredentialFails() {
        stubStore(store(true));
        stubClaim(employee(true), "0421");
        when(credentialRepository.findByEmployeeId(EMPLOYEE_ID)).thenReturn(Optional.empty());
        when(pinHasher.hash(anyString())).thenReturn(DECOY_HASH);

        assertThatThrownBy(() -> service.authenticate(STORE_ID, "0421", CLIENT))
                .isInstanceOf(PosAuthFailedException.class);

        verify(pinHasher, times(1)).verify("0421", DECOY_HASH);
        verify(rateLimiter).recordFailure(STORE_ID, CLIENT);
    }

    @Test
    @DisplayName("an inactive claimed employee cannot authenticate even with the right PIN")
    void inactiveEmployeeFails() {
        stubStore(store(true));
        stubClaim(employee(false), "0421");
        when(credentialRepository.findByEmployeeId(EMPLOYEE_ID))
                .thenReturn(Optional.of(credential("HASH")));
        when(pinHasher.verify("0421", "HASH")).thenReturn(true);

        assertThatThrownBy(() -> service.authenticate(STORE_ID, "0421", CLIENT))
                .isInstanceOf(PosAuthFailedException.class);

        // The PIN was right; the account state is what failed it. A cheaper
        // "check active first" path would make the state observable.
        verify(pinHasher, times(1)).verify("0421", "HASH");
        verify(sessionService, never()).startSession(any(), any());
    }

    @Test
    @DisplayName("a PIN no employee at the store claims costs one verification and reads no credential")
    void unclaimedPinCostsTheSameOneVerification() {
        stubStore(store(true));
        when(pinHasher.fingerprint("0421")).thenReturn("fp:nobody");
        when(scopeRepository.findByStoreIdAndPinFingerprint(STORE_ID, "fp:nobody"))
                .thenReturn(Optional.empty());
        when(pinHasher.hash(anyString())).thenReturn(DECOY_HASH);

        assertThatThrownBy(() -> service.authenticate(STORE_ID, "0421", CLIENT))
                .isInstanceOf(PosAuthFailedException.class)
                .hasMessage("Invalid store or PIN.");

        // The cost of a miss is one PBKDF2 against the decoy, not a fast
        // path that would let timing reveal which PINs are in use here.
        verify(pinHasher, times(1)).verify("0421", DECOY_HASH);
        verifyNoInteractions(credentialRepository);
        verify(rateLimiter).recordFailure(STORE_ID, CLIENT);
    }

    @Test
    @DisplayName("a missing store and an inactive store produce the same generic failure")
    void storeStatesFailIdentically() {
        when(storeRepository.findById(STORE_ID)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.authenticate(STORE_ID, "0421", CLIENT))
                .isInstanceOf(PosAuthFailedException.class);

        when(storeRepository.findById(STORE_ID)).thenReturn(Optional.of(store(false)));
        assertThatThrownBy(() -> service.authenticate(STORE_ID, "0421", CLIENT))
                .isInstanceOf(PosAuthFailedException.class)
                // The generic message never names the store.
                .hasMessage("Invalid store or PIN.");

        verifyNoInteractions(scopeRepository);
    }

    @Test
    @DisplayName("a locked-out client is refused before any credential is examined")
    void rateLimitShortCircuits() {
        when(rateLimiter.isBlocked(STORE_ID, CLIENT)).thenReturn(true);

        assertThatThrownBy(() -> service.authenticate(STORE_ID, "0421", CLIENT))
                .isInstanceOf(PosAuthRateLimitedException.class);

        verifyNoInteractions(storeRepository, scopeRepository,
                credentialRepository, pinHasher, sessionService);
    }

    // --- fixtures ---

    private void stubStore(Store store) {
        when(storeRepository.findById(STORE_ID)).thenReturn(Optional.of(store));
    }

    private void stubClaim(Employee employee, String pin) {
        String fingerprint = "fp:" + pin;
        when(pinHasher.fingerprint(pin)).thenReturn(fingerprint);
        when(scopeRepository.findByStoreIdAndPinFingerprint(STORE_ID, fingerprint))
                .thenReturn(Optional.of(claim(employee, fingerprint)));
    }

    private static EmployeePinStoreScope claim(Employee employee, String fingerprint) {
        return new EmployeePinStoreScope(employee, store(true), fingerprint);
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
