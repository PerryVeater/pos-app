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
import com.example.posapp.entity.EmployeeGroup;
import com.example.posapp.exception.EmployeeGroupNotFoundException;
import com.example.posapp.exception.EmployeeGroupValidationException;
import com.example.posapp.exception.EmployeeNotFoundException;
import com.example.posapp.service.EmployeeGroupMembershipService;

/**
 * Web-layer tests for {@link EmployeeGroupMembershipController} using
 * MockMvc.
 * <p>
 * The {@link EmployeeGroupMembershipService} is replaced with a Mockito
 * mock, so these tests verify the HTTP contract only: routing, status
 * codes, and the RFC 9457 problem-detail responses returned for client
 * errors. Membership persistence and the same-organization rule are
 * covered by the service unit tests and the Testcontainers integration
 * tests, not here.
 * </p>
 */
@WebMvcTest(EmployeeGroupMembershipController.class)
class EmployeeGroupMembershipControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EmployeeGroupMembershipService membershipService;

    private static Employee employee(String name) {
        return new Employee(name);
    }

    private static EmployeeGroup group(String name) {
        return new EmployeeGroup(name);
    }

    // --- PUT /api/v1/employees/{employeeId}/groups/{groupId} ---

    @Test
    @DisplayName("PUT /api/v1/employees/{id}/groups/{groupId} returns 204 on a fresh add")
    void addMembershipReturns204() throws Exception {
        mockMvc.perform(put("/api/v1/employees/1/groups/2"))
                .andExpect(status().isNoContent());

        verify(membershipService).addMembership(1L, 2L);
    }

    @Test
    @DisplayName("PUT /api/v1/employees/{id}/groups/{groupId} returns 204 when the pair is already attached (idempotent)")
    void addExistingMembershipIsIdempotent() throws Exception {
        mockMvc.perform(put("/api/v1/employees/1/groups/2"))
                .andExpect(status().isNoContent());

        verify(membershipService).addMembership(1L, 2L);
    }

    @Test
    @DisplayName("PUT /api/v1/employees/{id}/groups/{groupId} returns 404 when the employee is missing")
    void addMembershipMissingEmployeeReturns404() throws Exception {
        when(membershipService.addMembership(99L, 2L))
                .thenThrow(new EmployeeNotFoundException(99L));

        mockMvc.perform(put("/api/v1/employees/99/groups/2"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Employee not found"))
                .andExpect(jsonPath("$.detail").value(containsString("99")));
    }

    @Test
    @DisplayName("PUT /api/v1/employees/{id}/groups/{groupId} returns 404 when the group is missing")
    void addMembershipMissingGroupReturns404() throws Exception {
        when(membershipService.addMembership(1L, 99L))
                .thenThrow(new EmployeeGroupNotFoundException(99L));

        mockMvc.perform(put("/api/v1/employees/1/groups/99"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Employee group not found"))
                .andExpect(jsonPath("$.detail").value(containsString("99")));
    }

    @Test
    @DisplayName("PUT /api/v1/employees/{id}/groups/{groupId} returns 400 for a cross-organization pair")
    void addMembershipCrossOrganizationReturns400() throws Exception {
        when(membershipService.addMembership(1L, 2L))
                .thenThrow(new EmployeeGroupValidationException(
                        "Employee and employee group must belong to the same organization:"
                                + " employeeId=1, employeeGroupId=2"));

        mockMvc.perform(put("/api/v1/employees/1/groups/2"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Invalid employee group"))
                .andExpect(jsonPath("$.detail").value(containsString("same organization")));
    }

    @Test
    @DisplayName("PUT /api/v1/employees/{id}/groups/{groupId} rejects non-numeric IDs with 400")
    void addMembershipNonNumericIdReturns400() throws Exception {
        mockMvc.perform(put("/api/v1/employees/abc/groups/2"))
                .andExpect(status().isBadRequest());

        verify(membershipService, never()).addMembership(anyLong(), anyLong());
    }

    // --- DELETE /api/v1/employees/{employeeId}/groups/{groupId} ---

    @Test
    @DisplayName("DELETE /api/v1/employees/{id}/groups/{groupId} returns 204 on a real detach")
    void removeMembershipReturns204() throws Exception {
        mockMvc.perform(delete("/api/v1/employees/1/groups/2"))
                .andExpect(status().isNoContent());

        verify(membershipService).removeMembership(1L, 2L);
    }

    @Test
    @DisplayName("DELETE /api/v1/employees/{id}/groups/{groupId} returns 204 when the pair is not attached (idempotent)")
    void removeMissingMembershipIsIdempotent() throws Exception {
        // The service's contract is that a missing pair is a silent no-op,
        // so the controller still returns 204 with nothing mocked to throw.
        mockMvc.perform(delete("/api/v1/employees/1/groups/2"))
                .andExpect(status().isNoContent());

        verify(membershipService).removeMembership(1L, 2L);
    }

    @Test
    @DisplayName("DELETE /api/v1/employees/{id}/groups/{groupId} returns 404 when the employee is missing")
    void removeMembershipMissingEmployeeReturns404() throws Exception {
        doThrow(new EmployeeNotFoundException(99L)).when(membershipService).removeMembership(99L, 2L);

        mockMvc.perform(delete("/api/v1/employees/99/groups/2"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Employee not found"));
    }

    @Test
    @DisplayName("DELETE /api/v1/employees/{id}/groups/{groupId} returns 404 when the group is missing")
    void removeMembershipMissingGroupReturns404() throws Exception {
        doThrow(new EmployeeGroupNotFoundException(99L)).when(membershipService).removeMembership(1L, 99L);

        mockMvc.perform(delete("/api/v1/employees/1/groups/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Employee group not found"));
    }

    @Test
    @DisplayName("DELETE /api/v1/employees/{id}/groups/{groupId} returns 400 for a cross-organization pair")
    void removeMembershipCrossOrganizationReturns400() throws Exception {
        doThrow(new EmployeeGroupValidationException(
                "Employee and employee group must belong to the same organization:"
                        + " employeeId=1, employeeGroupId=2"))
                .when(membershipService).removeMembership(1L, 2L);

        mockMvc.perform(delete("/api/v1/employees/1/groups/2"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid employee group"))
                .andExpect(jsonPath("$.detail").value(containsString("same organization")));
    }

    // --- GET /api/v1/employees/{employeeId}/groups ---

    @Test
    @DisplayName("GET /api/v1/employees/{id}/groups returns the employee's groups")
    void listGroupsForEmployeeReturnsList() throws Exception {
        when(membershipService.listGroupsForEmployee(1L))
                .thenReturn(List.of(group("Front of House"), group("Baristas")));

        mockMvc.perform(get("/api/v1/employees/1/groups"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].name").value("Front of House"))
                .andExpect(jsonPath("$[1].name").value("Baristas"));
    }

    @Test
    @DisplayName("GET /api/v1/employees/{id}/groups returns an empty array when the employee has no memberships")
    void listGroupsForEmployeeEmptyReturnsEmptyArray() throws Exception {
        when(membershipService.listGroupsForEmployee(1L)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/employees/1/groups"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @DisplayName("GET /api/v1/employees/{id}/groups returns 404 when the employee is missing")
    void listGroupsForEmployeeMissingReturns404() throws Exception {
        when(membershipService.listGroupsForEmployee(99L))
                .thenThrow(new EmployeeNotFoundException(99L));

        mockMvc.perform(get("/api/v1/employees/99/groups"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Employee not found"))
                .andExpect(jsonPath("$.detail").value(containsString("99")));
    }

    // --- GET /api/v1/employee-groups/{groupId}/employees ---

    @Test
    @DisplayName("GET /api/v1/employee-groups/{id}/employees returns the group's members")
    void listEmployeesForGroupReturnsList() throws Exception {
        when(membershipService.listEmployeesForGroup(2L))
                .thenReturn(List.of(employee("Ada"), employee("Grace")));

        mockMvc.perform(get("/api/v1/employee-groups/2/employees"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].name").value("Ada"))
                .andExpect(jsonPath("$[1].name").value("Grace"));
    }

    @Test
    @DisplayName("GET /api/v1/employee-groups/{id}/employees returns an empty array when the group has no members")
    void listEmployeesForGroupEmptyReturnsEmptyArray() throws Exception {
        when(membershipService.listEmployeesForGroup(2L)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/employee-groups/2/employees"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @DisplayName("GET /api/v1/employee-groups/{id}/employees returns 404 when the group is missing")
    void listEmployeesForGroupMissingReturns404() throws Exception {
        when(membershipService.listEmployeesForGroup(99L))
                .thenThrow(new EmployeeGroupNotFoundException(99L));

        mockMvc.perform(get("/api/v1/employee-groups/99/employees"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Employee group not found"))
                .andExpect(jsonPath("$.detail").value(containsString("99")));
    }
}
