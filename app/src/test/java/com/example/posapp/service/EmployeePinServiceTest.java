package com.example.posapp.service;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import com.example.posapp.entity.Employee;
import com.example.posapp.entity.EmployeePinCredential;
import com.example.posapp.entity.Organization;
import com.example.posapp.exception.EmployeeNotFoundException;
import com.example.posapp.exception.EmployeePinValidationException;
import com.example.posapp.repository.EmployeeRepository;

/**
 * Unit tests for {@link EmployeePinService}, the non-transactional PIN
 * orchestrator.
 * <p>
 * The writer is mocked so these tests cover only this layer's
 * responsibilities: format validation against the configured window,
 * existence and active-state rules, the hash/fingerprint hand-off, and the
 * translation of a fingerprint integrity violation into a friendly
 * {@link EmployeePinValidationException} that leaks no secret material.
 * The hasher and properties are real because the mapping under test is
 * exactly their output; a low iteration count keeps the hashing cheap.
 * Store-scoped uniqueness itself is a database guarantee and is covered by
 * the Testcontainers integration tests.
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class EmployeePinServiceTest {

    @Mock
    private EmployeeRepository employeeRepo;

    @Mock
    private EmployeeStorePinWriter pinWriter;

    private EmployeePinService newService() {
        PinProperties properties = new PinProperties(4, 6, "test-hmac-secret", 1000);
        return new EmployeePinService(employeeRepo, pinWriter, new PinHasher(properties), properties);
    }

    private static Employee activeEmployee() {
        Employee employee = new Employee("Ada");
        employee.setOrganization(new Organization("Acme"));
        employee.setActive(true);
        return employee;
    }

    // --- format validation ---

    @Test
    @DisplayName("malformed PINs are rejected before the writer runs")
    void malformedPinsRejectedBeforeWrite() {
        EmployeePinService service = newService();

        for (String bad : new String[]{"123", "1234567", "abcd", "12-34", "1234 ", " 1234", ""}) {
            assertThatThrownBy(() -> service.setPin(1L, bad))
                    .as("PIN '%s' should be rejected", bad)
                    .isInstanceOf(EmployeePinValidationException.class)
                    .hasMessageContaining("digits");
        }
        verify(pinWriter, never()).setPin(any(), anyString(), anyString());
    }

    @Test
    @DisplayName("a null PIN is rejected without touching the writer")
    void nullPinRejected() {
        EmployeePinService service = newService();

        assertThatThrownBy(() -> service.setPin(1L, null))
                .isInstanceOf(EmployeePinValidationException.class);
        verify(pinWriter, never()).setPin(any(), anyString(), anyString());
    }

    @Test
    @DisplayName("the accepted window honours the configured min and max length")
    void windowIsConfigurable() {
        PinProperties wide = new PinProperties(3, 8, "test-hmac-secret", 1000);
        EmployeePinService service =
                new EmployeePinService(employeeRepo, pinWriter, new PinHasher(wide), wide);
        when(employeeRepo.findById(1L)).thenReturn(Optional.of(activeEmployee()));
        when(pinWriter.setPin(any(), anyString(), anyString()))
                .thenReturn(new EmployeePinCredential());

        // "007" would fail the default 4-digit minimum but passes at min 3.
        service.setPin(1L, "007");

        verify(pinWriter).setPin(any(), anyString(), anyString());
    }

    @Test
    @DisplayName("a leading-zero PIN is passed through verbatim, never trimmed or normalized")
    void leadingZerosPreserved() {
        EmployeePinService service = newService();
        when(employeeRepo.findById(1L)).thenReturn(Optional.of(activeEmployee()));
        when(pinWriter.setPin(any(), anyString(), anyString()))
                .thenReturn(new EmployeePinCredential());

        service.setPin(1L, "0421");

        String expectedFingerprint = new PinHasher(new PinProperties(4, 6, "test-hmac-secret", 1000))
                .fingerprint("0421");
        verify(pinWriter).setPin(any(), anyString(), eq(expectedFingerprint));
    }

    // --- existence and active-state rules ---

    @Test
    @DisplayName("setting a PIN for a missing employee raises EmployeeNotFoundException")
    void missingEmployeeRejected() {
        EmployeePinService service = newService();
        when(employeeRepo.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.setPin(99L, "1234"))
                .isInstanceOf(EmployeeNotFoundException.class);
        verify(pinWriter, never()).setPin(any(), anyString(), anyString());
    }

    @Test
    @DisplayName("setting a PIN for an inactive employee is rejected")
    void inactiveEmployeeRejected() {
        EmployeePinService service = newService();
        Employee inactive = activeEmployee();
        inactive.setActive(false);
        when(employeeRepo.findById(1L)).thenReturn(Optional.of(inactive));

        assertThatThrownBy(() -> service.setPin(1L, "1234"))
                .isInstanceOf(EmployeePinValidationException.class)
                .hasMessageContaining("active");
        verify(pinWriter, never()).setPin(any(), anyString(), anyString());
    }

    @Test
    @DisplayName("a valid PIN for an active employee is hashed and handed to the writer")
    void validPinDelegatesToWriter() {
        EmployeePinService service = newService();
        when(employeeRepo.findById(1L)).thenReturn(Optional.of(activeEmployee()));
        when(pinWriter.setPin(any(), anyString(), anyString()))
                .thenReturn(new EmployeePinCredential());

        service.setPin(1L, "1234");

        verify(pinWriter).setPin(any(), anyString(), anyString());
    }

    // --- conflict translation ---

    @Test
    @DisplayName("a fingerprint integrity violation becomes a friendly 400 with no secret material")
    void fingerprintConflictTranslated() {
        EmployeePinService service = newService();
        when(employeeRepo.findById(1L)).thenReturn(Optional.of(activeEmployee()));
        DataIntegrityViolationException conflict =
                new DataIntegrityViolationException("generic wrapper",
                        new RuntimeException("Violation uk_epss_store_fingerprint on secret value"));
        when(pinWriter.setPin(any(), anyString(), anyString())).thenThrow(conflict);

        assertThatThrownBy(() -> service.setPin(1L, "1234"))
                .isInstanceOf(EmployeePinValidationException.class)
                .hasMessageContaining("already in use")
                // Neither the raw constraint message, the fingerprint, nor
                // the plaintext PIN may leak into the client message.
                .hasMessageNotContaining("uk_epss_store_fingerprint")
                .hasMessageNotContaining("secret value")
                .hasMessageNotContaining("1234");
    }

    @Test
    @DisplayName("an unrelated integrity violation keeps propagating unchanged")
    void unrelatedIntegrityViolationRethrown() {
        EmployeePinService service = newService();
        when(employeeRepo.findById(1L)).thenReturn(Optional.of(activeEmployee()));
        DataIntegrityViolationException unrelated =
                new DataIntegrityViolationException("fk_epc_employee violated");
        when(pinWriter.setPin(any(), anyString(), anyString())).thenThrow(unrelated);

        assertThatThrownBy(() -> service.setPin(1L, "1234"))
                .isSameAs(unrelated);
    }

    // --- removal ---

    @Test
    @DisplayName("removing a PIN for a missing employee raises EmployeeNotFoundException")
    void removeMissingEmployeeRejected() {
        EmployeePinService service = newService();
        when(employeeRepo.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> service.removePin(99L))
                .isInstanceOf(EmployeeNotFoundException.class);
        verify(pinWriter, never()).removePin(any());
    }

    @Test
    @DisplayName("removing a PIN delegates to the writer for an existing employee")
    void removeDelegatesToWriter() {
        EmployeePinService service = newService();
        when(employeeRepo.existsById(1L)).thenReturn(true);

        service.removePin(1L);

        verify(pinWriter).removePin(1L);
    }
}
