package com.example.posapp.controller;

import java.util.List;

import static com.example.posapp.controller.BrandScopeTestSupport.BRAND_ID;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.example.posapp.entity.Employee;
import com.example.posapp.entity.EmployeeGroup;
import com.example.posapp.entity.EmployeeGroupMembership;
import com.example.posapp.exception.EmployeeGroupNotFoundException;
import com.example.posapp.exception.EmployeeNotFoundException;
import com.example.posapp.service.EmployeeGroupMembershipService;

/**
 * Web-layer tests for {@link EmployeeGroupMembershipController} using
 * MockMvc.
 * <p>
 * The {@link EmployeeGroupMembershipService} is replaced with a Mockito
 * mock, so these tests verify the HTTP contract only: routing, status
 * codes, and the RFC 9457 problem-detail responses returned for client
 * errors. Membership persistence is covered by the service unit tests and
 * the Testcontainers integration tests, not here.
 * </p>
 * <p>
 * Every call is asserted to carry the caller's brand. Because both lookups
 * are scoped to it, an employee or group belonging to another brand reaches
 * the controller as a 404 - the same answer a genuinely missing one gets -
 * so no cross-brand pair can ever be read or written through this route.
 * </p>
 */
@WebMvcTest(EmployeeGroupMembershipController.class)
@AutoConfigureMockMvc(addFilters = false)
class EmployeeGroupMembershipControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EmployeeGroupMembershipService membershipService;

    @BeforeEach
    void signIn() {
        BrandScopeTestSupport.signIn();
    }

    @AfterEach
    void signOut() {
        BrandScopeTestSupport.signOut();
    }

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

        verify(membershipService).addMembership(1L, 2L, BRAND_ID);
    }

    @Test
    @DisplayName("PUT /api/v1/employees/{id}/groups/{groupId} returns 204 when the pair is already attached (idempotent)")
    void addExistingMembershipIsIdempotent() throws Exception {
        mockMvc.perform(put("/api/v1/employees/1/groups/2"))
                .andExpect(status().isNoContent());

        verify(membershipService).addMembership(1L, 2L, BRAND_ID);
    }

    @Test
    @DisplayName("PUT /api/v1/employees/{id}/groups/{groupId} returns 404 when the employee is missing")
    void addMembershipMissingEmployeeReturns404() throws Exception {
        when(membershipService.addMembership(99L, 2L, BRAND_ID))
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
        when(membershipService.addMembership(1L, 99L, BRAND_ID))
                .thenThrow(new EmployeeGroupNotFoundException(99L));

        mockMvc.perform(put("/api/v1/employees/1/groups/99"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Employee group not found"))
                .andExpect(jsonPath("$.detail").value(containsString("99")));
    }

    @Test
    @DisplayName("PUT /api/v1/employees/{id}/groups/{groupId} reports another brand's group as 404")
    void addMembershipGroupOutsideBrandReturns404() throws Exception {
        // The lookup carries the brand, so a group in another brand is
        // answered exactly like a group that does not exist.
        when(membershipService.addMembership(1L, 2L, BRAND_ID))
                .thenThrow(new EmployeeGroupNotFoundException(2L));

        mockMvc.perform(put("/api/v1/employees/1/groups/2"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Employee group not found"));

        verify(membershipService).addMembership(1L, 2L, BRAND_ID);
    }

    @Test
    @DisplayName("PUT /api/v1/employees/{id}/groups/{groupId} rejects non-numeric IDs with 400")
    void addMembershipNonNumericIdReturns400() throws Exception {
        mockMvc.perform(put("/api/v1/employees/abc/groups/2"))
                .andExpect(status().isBadRequest());

        verify(membershipService, never()).addMembership(anyLong(), anyLong(), anyLong());
    }

    // --- DELETE /api/v1/employees/{employeeId}/groups/{groupId} ---

    @Test
    @DisplayName("DELETE /api/v1/employees/{id}/groups/{groupId} returns 204 on a real detach")
    void removeMembershipReturns204() throws Exception {
        mockMvc.perform(delete("/api/v1/employees/1/groups/2"))
                .andExpect(status().isNoContent());

        verify(membershipService).removeMembership(1L, 2L, BRAND_ID);
    }

    @Test
    @DisplayName("DELETE /api/v1/employees/{id}/groups/{groupId} returns 204 when the pair is not attached (idempotent)")
    void removeMissingMembershipIsIdempotent() throws Exception {
        // The service's contract is that a missing pair is a silent no-op,
        // so the controller still returns 204 with nothing mocked to throw.
        mockMvc.perform(delete("/api/v1/employees/1/groups/2"))
                .andExpect(status().isNoContent());

        verify(membershipService).removeMembership(1L, 2L, BRAND_ID);
    }

    @Test
    @DisplayName("DELETE /api/v1/employees/{id}/groups/{groupId} returns 404 when the employee is missing")
    void removeMembershipMissingEmployeeReturns404() throws Exception {
        doThrow(new EmployeeNotFoundException(99L))
                .when(membershipService).removeMembership(99L, 2L, BRAND_ID);

        mockMvc.perform(delete("/api/v1/employees/99/groups/2"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Employee not found"));
    }

    @Test
    @DisplayName("DELETE /api/v1/employees/{id}/groups/{groupId} returns 404 when the group is missing")
    void removeMembershipMissingGroupReturns404() throws Exception {
        doThrow(new EmployeeGroupNotFoundException(99L))
                .when(membershipService).removeMembership(1L, 99L, BRAND_ID);

        mockMvc.perform(delete("/api/v1/employees/1/groups/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Employee group not found"));
    }

    // --- GET /api/v1/employees/{employeeId}/groups ---

    @Test
    @DisplayName("GET /api/v1/employees/{id}/groups returns one page of the employee's groups")
    void listGroupsForEmployeeReturnsList() throws Exception {
        EmployeeGroupMembership front = mock(EmployeeGroupMembership.class);
        when(front.getEmployeeGroup()).thenReturn(group("Front of House"));
        EmployeeGroupMembership baristas = mock(EmployeeGroupMembership.class);
        when(baristas.getEmployeeGroup()).thenReturn(group("Baristas"));
        when(membershipService.listGroupsForEmployee(eq(1L), eq(BRAND_ID), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(front, baristas)));

        mockMvc.perform(get("/api/v1/employees/1/groups"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[0].name").value("Front of House"))
                .andExpect(jsonPath("$.content[1].name").value("Baristas"))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(1));
    }

    @Test
    @DisplayName("GET /api/v1/employees/{id}/groups returns an empty page when the employee has no memberships")
    void listGroupsForEmployeeEmptyReturnsEmptyArray() throws Exception {
        when(membershipService.listGroupsForEmployee(eq(1L), eq(BRAND_ID), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/api/v1/employees/1/groups"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(0)))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    @DisplayName("GET /api/v1/employees/{id}/groups returns 404 when the employee is missing")
    void listGroupsForEmployeeMissingReturns404() throws Exception {
        when(membershipService.listGroupsForEmployee(eq(99L), eq(BRAND_ID), any(Pageable.class)))
                .thenThrow(new EmployeeNotFoundException(99L));

        mockMvc.perform(get("/api/v1/employees/99/groups"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Employee not found"))
                .andExpect(jsonPath("$.detail").value(containsString("99")));
    }

    // --- GET /api/v1/employee-groups/{groupId}/employees ---

    @Test
    @DisplayName("GET /api/v1/employee-groups/{id}/employees returns one page of members")
    void listEmployeesForGroupReturnsList() throws Exception {
        EmployeeGroupMembership ada = mock(EmployeeGroupMembership.class);
        when(ada.getEmployee()).thenReturn(employee("Ada"));
        EmployeeGroupMembership grace = mock(EmployeeGroupMembership.class);
        when(grace.getEmployee()).thenReturn(employee("Grace"));
        when(membershipService.listEmployeesForGroup(eq(2L), eq(BRAND_ID), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(ada, grace)));

        mockMvc.perform(get("/api/v1/employee-groups/2/employees"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[0].name").value("Ada"))
                .andExpect(jsonPath("$.content[1].name").value("Grace"))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(1));
    }

    @Test
    @DisplayName("GET /api/v1/employee-groups/{id}/employees returns an empty page when the group has no members")
    void listEmployeesForGroupEmptyReturnsEmptyArray() throws Exception {
        when(membershipService.listEmployeesForGroup(eq(2L), eq(BRAND_ID), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/api/v1/employee-groups/2/employees"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(0)))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    @DisplayName("GET /api/v1/employee-groups/{id}/employees returns 404 when the group is missing")
    void listEmployeesForGroupMissingReturns404() throws Exception {
        when(membershipService.listEmployeesForGroup(eq(99L), eq(BRAND_ID), any(Pageable.class)))
                .thenThrow(new EmployeeGroupNotFoundException(99L));

        mockMvc.perform(get("/api/v1/employee-groups/99/employees"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Employee group not found"))
                .andExpect(jsonPath("$.detail").value(containsString("99")));
    }
}
