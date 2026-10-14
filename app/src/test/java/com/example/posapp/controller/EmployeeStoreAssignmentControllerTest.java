package com.example.posapp.controller;

import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.example.posapp.entity.Employee;
import com.example.posapp.entity.Store;
import com.example.posapp.exception.EmployeeNotFoundException;
import com.example.posapp.exception.EmployeeStoreValidationException;
import com.example.posapp.exception.StoreNotFoundException;
import com.example.posapp.service.EmployeeStoreAssignmentService;

/**
 * Web-layer tests for {@link EmployeeStoreAssignmentController} using
 * MockMvc.
 * <p>
 * The {@link EmployeeStoreAssignmentService} is replaced with a Mockito
 * mock, so these tests verify the HTTP contract only: routing, status
 * codes, and the RFC 9457 problem-detail responses returned for client
 * errors. Assignment persistence and the same-organization rule are
 * covered by the service unit tests and the Testcontainers integration
 * tests, not here.
 * </p>
 */
@WebMvcTest(EmployeeStoreAssignmentController.class)
class EmployeeStoreAssignmentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EmployeeStoreAssignmentService assignmentService;

    private static Employee employee(String name) {
        return new Employee(name);
    }

    private static Store store(String name) {
        return new Store(null, name, "US", "CA", "Los Angeles",
                "123 Main St", "90001", "America/Los_Angeles");
    }

    // --- PUT /api/v1/employees/{employeeId}/stores/{storeId} ---

    @Test
    @DisplayName("PUT /api/v1/employees/{id}/stores/{storeId} returns 204 on a fresh assign")
    void assignReturns204() throws Exception {
        mockMvc.perform(put("/api/v1/employees/1/stores/2"))
                .andExpect(status().isNoContent());

        verify(assignmentService).assignEmployeeToStore(1L, 2L);
    }

    @Test
    @DisplayName("PUT /api/v1/employees/{id}/stores/{storeId} returns 204 when the pair is already assigned (idempotent)")
    void assignExistingIsIdempotent() throws Exception {
        mockMvc.perform(put("/api/v1/employees/1/stores/2"))
                .andExpect(status().isNoContent());

        verify(assignmentService).assignEmployeeToStore(1L, 2L);
    }

    @Test
    @DisplayName("PUT /api/v1/employees/{id}/stores/{storeId} returns 404 when the employee is missing")
    void assignMissingEmployeeReturns404() throws Exception {
        when(assignmentService.assignEmployeeToStore(99L, 2L))
                .thenThrow(new EmployeeNotFoundException(99L));

        mockMvc.perform(put("/api/v1/employees/99/stores/2"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Employee not found"))
                .andExpect(jsonPath("$.detail").value(containsString("99")));
    }

    @Test
    @DisplayName("PUT /api/v1/employees/{id}/stores/{storeId} returns 404 when the store is missing")
    void assignMissingStoreReturns404() throws Exception {
        when(assignmentService.assignEmployeeToStore(1L, 99L))
                .thenThrow(new StoreNotFoundException(99L));

        mockMvc.perform(put("/api/v1/employees/1/stores/99"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Store not found"))
                .andExpect(jsonPath("$.detail").value(containsString("99")));
    }

    @Test
    @DisplayName("PUT /api/v1/employees/{id}/stores/{storeId} returns 400 for a cross-organization pair")
    void assignCrossOrganizationReturns400() throws Exception {
        when(assignmentService.assignEmployeeToStore(1L, 2L))
                .thenThrow(new EmployeeStoreValidationException(
                        "Employee and store must belong to the same organization:"
                                + " employeeId=1, storeId=2"));

        mockMvc.perform(put("/api/v1/employees/1/stores/2"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Invalid employee store assignment"))
                .andExpect(jsonPath("$.detail").value(containsString("same organization")));
    }

    @Test
    @DisplayName("PUT /api/v1/employees/{id}/stores/{storeId} rejects non-numeric IDs with 400")
    void assignNonNumericIdReturns400() throws Exception {
        mockMvc.perform(put("/api/v1/employees/abc/stores/2"))
                .andExpect(status().isBadRequest());

        verify(assignmentService, never()).assignEmployeeToStore(anyLong(), anyLong());
    }

    // --- DELETE /api/v1/employees/{employeeId}/stores/{storeId} ---

    @Test
    @DisplayName("DELETE /api/v1/employees/{id}/stores/{storeId} returns 204 on a real unassign")
    void removeReturns204() throws Exception {
        mockMvc.perform(delete("/api/v1/employees/1/stores/2"))
                .andExpect(status().isNoContent());

        verify(assignmentService).removeEmployeeFromStore(1L, 2L);
    }

    @Test
    @DisplayName("DELETE /api/v1/employees/{id}/stores/{storeId} returns 204 when the pair is not assigned (idempotent)")
    void removeMissingIsIdempotent() throws Exception {
        // The service's contract is that a missing pair is a silent no-op,
        // so the controller still returns 204 with nothing mocked to throw.
        mockMvc.perform(delete("/api/v1/employees/1/stores/2"))
                .andExpect(status().isNoContent());

        verify(assignmentService).removeEmployeeFromStore(1L, 2L);
    }

    @Test
    @DisplayName("DELETE /api/v1/employees/{id}/stores/{storeId} returns 404 when the employee is missing")
    void removeMissingEmployeeReturns404() throws Exception {
        doThrow(new EmployeeNotFoundException(99L)).when(assignmentService).removeEmployeeFromStore(99L, 2L);

        mockMvc.perform(delete("/api/v1/employees/99/stores/2"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Employee not found"));
    }

    @Test
    @DisplayName("DELETE /api/v1/employees/{id}/stores/{storeId} returns 404 when the store is missing")
    void removeMissingStoreReturns404() throws Exception {
        doThrow(new StoreNotFoundException(99L)).when(assignmentService).removeEmployeeFromStore(1L, 99L);

        mockMvc.perform(delete("/api/v1/employees/1/stores/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Store not found"));
    }

    @Test
    @DisplayName("DELETE /api/v1/employees/{id}/stores/{storeId} returns 400 for a cross-organization pair")
    void removeCrossOrganizationReturns400() throws Exception {
        doThrow(new EmployeeStoreValidationException(
                "Employee and store must belong to the same organization:"
                        + " employeeId=1, storeId=2"))
                .when(assignmentService).removeEmployeeFromStore(1L, 2L);

        mockMvc.perform(delete("/api/v1/employees/1/stores/2"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid employee store assignment"))
                .andExpect(jsonPath("$.detail").value(containsString("same organization")));
    }

    // --- GET /api/v1/employees/{employeeId}/stores ---

    @Test
    @DisplayName("GET /api/v1/employees/{id}/stores returns the employee's stores")
    void listStoresForEmployeeReturnsList() throws Exception {
        when(assignmentService.listStoresForEmployee(1L))
                .thenReturn(List.of(store("Downtown"), store("Airport")));

        mockMvc.perform(get("/api/v1/employees/1/stores"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].name").value("Downtown"))
                .andExpect(jsonPath("$[1].name").value("Airport"));
    }

    @Test
    @DisplayName("GET /api/v1/employees/{id}/stores returns an empty array when the employee has no assignments")
    void listStoresForEmployeeEmptyReturnsEmptyArray() throws Exception {
        when(assignmentService.listStoresForEmployee(1L)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/employees/1/stores"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @DisplayName("GET /api/v1/employees/{id}/stores returns 404 when the employee is missing")
    void listStoresForEmployeeMissingReturns404() throws Exception {
        when(assignmentService.listStoresForEmployee(99L))
                .thenThrow(new EmployeeNotFoundException(99L));

        mockMvc.perform(get("/api/v1/employees/99/stores"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Employee not found"))
                .andExpect(jsonPath("$.detail").value(containsString("99")));
    }

    // --- GET /api/v1/stores/{storeId}/employees ---

    @Test
    @DisplayName("GET /api/v1/stores/{id}/employees returns the store's employees")
    void listEmployeesForStoreReturnsList() throws Exception {
        when(assignmentService.listEmployeesForStore(2L))
                .thenReturn(List.of(employee("Ada"), employee("Grace")));

        mockMvc.perform(get("/api/v1/stores/2/employees"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].name").value("Ada"))
                .andExpect(jsonPath("$[1].name").value("Grace"));
    }

    @Test
    @DisplayName("GET /api/v1/stores/{id}/employees returns an empty array when the store has no assignments")
    void listEmployeesForStoreEmptyReturnsEmptyArray() throws Exception {
        when(assignmentService.listEmployeesForStore(2L)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/stores/2/employees"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @DisplayName("GET /api/v1/stores/{id}/employees returns 404 when the store is missing")
    void listEmployeesForStoreMissingReturns404() throws Exception {
        when(assignmentService.listEmployeesForStore(99L))
                .thenThrow(new StoreNotFoundException(99L));

        mockMvc.perform(get("/api/v1/stores/99/employees"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Store not found"))
                .andExpect(jsonPath("$.detail").value(containsString("99")));
    }
}
