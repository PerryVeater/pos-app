package com.example.posapp.controller;

import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import com.example.posapp.exception.EmployeeNotFoundException;
import com.example.posapp.exception.EmployeeValidationException;
import com.example.posapp.exception.OrganizationNotFoundException;
import com.example.posapp.service.EmployeeService;

/**
 * Web-layer tests for {@link EmployeeController} using MockMvc.
 * <p>
 * The {@link EmployeeService} is replaced with a Mockito mock, so these
 * tests verify the HTTP contract only: routing, status codes, the JSON
 * representation of employees, and the RFC 9457 problem-details responses
 * returned for client errors. Entities are never persisted, so only the
 * non-ID fields of the representation are asserted here.
 * </p>
 */
@WebMvcTest(EmployeeController.class)
class EmployeeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EmployeeService employeeService;

    private static Employee employee(String name) {
        return new Employee(name);
    }

    // --- GET /api/v1/employees ---

    @Test
    @DisplayName("GET /api/v1/employees returns an empty list")
    void listEmployeesEmpty() throws Exception {
        when(employeeService.getAllEmployees()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/employees"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @DisplayName("GET /api/v1/employees returns every employee")
    void listEmployeesReturnsAll() throws Exception {
        Employee ada = employee("Ada Lovelace");
        ada.setEmail("ada@example.com");
        when(employeeService.getAllEmployees()).thenReturn(List.of(ada, employee("Grace Hopper")));

        mockMvc.perform(get("/api/v1/employees"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].name").value("Ada Lovelace"))
                .andExpect(jsonPath("$[0].email").value("ada@example.com"))
                .andExpect(jsonPath("$[1].name").value("Grace Hopper"));
    }

    // --- GET /api/v1/employees/{id} ---

    @Test
    @DisplayName("GET /api/v1/employees/{id} returns the employee")
    void getEmployeeReturns200() throws Exception {
        Employee ada = employee("Ada Lovelace");
        ada.setEmail("ada@example.com");
        when(employeeService.getEmployeeById(1L)).thenReturn(Optional.of(ada));

        mockMvc.perform(get("/api/v1/employees/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Ada Lovelace"))
                .andExpect(jsonPath("$.email").value("ada@example.com"))
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    @DisplayName("GET /api/v1/employees/{id} returns 404 ProblemDetail when missing")
    void getEmployeeMissingReturns404() throws Exception {
        when(employeeService.getEmployeeById(99L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/employees/99"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Employee not found"))
                .andExpect(jsonPath("$.detail").value(containsString("99")));
    }

    @Test
    @DisplayName("GET /api/v1/employees/{id} rejects non-numeric IDs with 400")
    void getEmployeeNonNumericIdReturns400() throws Exception {
        mockMvc.perform(get("/api/v1/employees/abc"))
                .andExpect(status().isBadRequest());
    }

    // --- POST /api/v1/employees ---

    @Test
    @DisplayName("POST /api/v1/employees creates an employee and returns 201")
    void createEmployeeReturns201() throws Exception {
        when(employeeService.createEmployee(any(Employee.class), eq(1L)))
                .thenAnswer(inv -> inv.getArgument(0));

        mockMvc.perform(post("/api/v1/employees")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"organizationId\":1,\"name\":\"Ada Lovelace\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Ada Lovelace"))
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    @DisplayName("POST /api/v1/employees honours email and active=false")
    void createEmployeeHonoursEmailAndInactive() throws Exception {
        when(employeeService.createEmployee(any(Employee.class), eq(1L)))
                .thenAnswer(inv -> inv.getArgument(0));

        mockMvc.perform(post("/api/v1/employees")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"organizationId\":1,\"name\":\"Ada Lovelace\","
                                + "\"email\":\"ada@example.com\",\"active\":false}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Ada Lovelace"))
                .andExpect(jsonPath("$.email").value("ada@example.com"))
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    @DisplayName("POST /api/v1/employees rejects blank name with 400 at the boundary")
    void createEmployeeBlankNameReturns400() throws Exception {
        mockMvc.perform(post("/api/v1/employees")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"organizationId\":1,\"name\":\"  \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.detail").value(containsString("name")));
    }

    @Test
    @DisplayName("POST /api/v1/employees rejects missing organizationId with 400")
    void createEmployeeMissingOrganizationReturns400() throws Exception {
        mockMvc.perform(post("/api/v1/employees")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Ada Lovelace\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.detail").value(containsString("organizationId")));
    }

    @Test
    @DisplayName("POST /api/v1/employees rejects missing name with 400")
    void createEmployeeMissingNameReturns400() throws Exception {
        mockMvc.perform(post("/api/v1/employees")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"organizationId\":1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation failed"));
    }

    @Test
    @DisplayName("POST /api/v1/employees rejects a malformed email with 400")
    void createEmployeeInvalidEmailReturns400() throws Exception {
        mockMvc.perform(post("/api/v1/employees")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"organizationId\":1,\"name\":\"Ada\",\"email\":\"not-an-email\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.detail").value(containsString("email")));
    }

    @Test
    @DisplayName("POST /api/v1/employees surfaces an unknown organization as 404 ProblemDetail")
    void createEmployeeUnknownOrganizationReturns404() throws Exception {
        when(employeeService.createEmployee(any(Employee.class), eq(99L)))
                .thenThrow(new OrganizationNotFoundException(99L));

        mockMvc.perform(post("/api/v1/employees")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"organizationId\":99,\"name\":\"Ada\"}"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Organization not found"))
                .andExpect(jsonPath("$.detail").value(containsString("99")));
    }

    @Test
    @DisplayName("POST /api/v1/employees surfaces a duplicate email as 400 ProblemDetail")
    void createEmployeeDuplicateEmailReturns400() throws Exception {
        when(employeeService.createEmployee(any(Employee.class), eq(1L)))
                .thenThrow(new EmployeeValidationException(
                        "Email already exists in organization: organizationId=1,"
                                + " email=ada@example.com"));

        mockMvc.perform(post("/api/v1/employees")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"organizationId\":1,\"name\":\"Ada\",\"email\":\"ada@example.com\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Invalid employee"))
                .andExpect(jsonPath("$.detail").value(containsString("already exists")));
    }

    // --- PUT /api/v1/employees/{id} ---

    @Test
    @DisplayName("PUT /api/v1/employees/{id} updates the employee")
    void updateEmployeeReturns200() throws Exception {
        Employee updated = employee("Ada Lovelace");
        updated.setEmail("ada@example.com");
        updated.setActive(false);
        when(employeeService.updateEmployee(eq(1L), any(Employee.class), eq(1L)))
                .thenReturn(updated);

        mockMvc.perform(put("/api/v1/employees/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"organizationId\":1,\"name\":\"Ada Lovelace\","
                                + "\"email\":\"ada@example.com\",\"active\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Ada Lovelace"))
                .andExpect(jsonPath("$.email").value("ada@example.com"))
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    @DisplayName("PUT /api/v1/employees/{id} returns 404 when missing")
    void updateEmployeeMissingReturns404() throws Exception {
        when(employeeService.updateEmployee(eq(99L), any(Employee.class), eq(1L)))
                .thenThrow(new EmployeeNotFoundException(99L));

        mockMvc.perform(put("/api/v1/employees/99")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"organizationId\":1,\"name\":\"Ada\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Employee not found"));
    }

    @Test
    @DisplayName("PUT /api/v1/employees/{id} rejects blank name with 400")
    void updateEmployeeBlankNameReturns400() throws Exception {
        mockMvc.perform(put("/api/v1/employees/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"organizationId\":1,\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation failed"));
    }

    @Test
    @DisplayName("PUT /api/v1/employees/{id} rejects a malformed email with 400")
    void updateEmployeeInvalidEmailReturns400() throws Exception {
        mockMvc.perform(put("/api/v1/employees/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"organizationId\":1,\"name\":\"Ada\",\"email\":\"nope\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.detail").value(containsString("email")));
    }

    // --- DELETE /api/v1/employees/{id} ---

    @Test
    @DisplayName("DELETE /api/v1/employees/{id} returns 204")
    void deleteEmployeeReturns204() throws Exception {
        mockMvc.perform(delete("/api/v1/employees/1"))
                .andExpect(status().isNoContent());

        verify(employeeService).deleteEmployee(1L);
    }

    @Test
    @DisplayName("DELETE /api/v1/employees/{id} returns 404 when missing")
    void deleteEmployeeMissingReturns404() throws Exception {
        doThrow(new EmployeeNotFoundException(99L))
                .when(employeeService).deleteEmployee(99L);

        mockMvc.perform(delete("/api/v1/employees/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Employee not found"));
    }
}
