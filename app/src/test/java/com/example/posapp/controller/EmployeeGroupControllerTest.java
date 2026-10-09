package com.example.posapp.controller;

import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
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

import com.example.posapp.entity.EmployeeGroup;
import com.example.posapp.exception.EmployeeGroupNotFoundException;
import com.example.posapp.exception.EmployeeGroupValidationException;
import com.example.posapp.exception.OrganizationNotFoundException;
import com.example.posapp.service.EmployeeGroupService;

/**
 * Web-layer tests for {@link EmployeeGroupController} using MockMvc.
 * <p>
 * The {@link EmployeeGroupService} is replaced with a Mockito mock, so
 * these tests verify the HTTP contract only: routing, status codes, the
 * JSON representation of employee groups, and the RFC 9457 problem-details
 * responses returned for client errors. Entities are never persisted, so
 * only the non-ID fields of the representation are asserted here.
 * </p>
 */
@WebMvcTest(EmployeeGroupController.class)
class EmployeeGroupControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EmployeeGroupService employeeGroupService;

    private static EmployeeGroup group(String name) {
        return new EmployeeGroup(name);
    }

    // --- GET /api/v1/employee-groups ---

    @Test
    @DisplayName("GET /api/v1/employee-groups returns an empty list")
    void listEmployeeGroupsEmpty() throws Exception {
        when(employeeGroupService.getAllEmployeeGroups()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/employee-groups"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @DisplayName("GET /api/v1/employee-groups returns every group without embedding children")
    void listEmployeeGroupsReturnsAll() throws Exception {
        when(employeeGroupService.getAllEmployeeGroups())
                .thenReturn(List.of(group("Front of House"), group("Back of House")));

        mockMvc.perform(get("/api/v1/employee-groups"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].name").value("Front of House"))
                .andExpect(jsonPath("$[1].name").value("Back of House"))
                .andExpect(jsonPath("$[0].children").doesNotExist());
    }

    // --- GET /api/v1/employee-groups/{id} ---

    @Test
    @DisplayName("GET /api/v1/employee-groups/{id} returns the group")
    void getEmployeeGroupReturns200() throws Exception {
        when(employeeGroupService.getEmployeeGroupById(1L))
                .thenReturn(Optional.of(group("Front of House")));

        mockMvc.perform(get("/api/v1/employee-groups/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Front of House"))
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    @DisplayName("GET /api/v1/employee-groups/{id} returns 404 ProblemDetail when missing")
    void getEmployeeGroupMissingReturns404() throws Exception {
        when(employeeGroupService.getEmployeeGroupById(99L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/employee-groups/99"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Employee group not found"))
                .andExpect(jsonPath("$.detail").value(containsString("99")));
    }

    @Test
    @DisplayName("GET /api/v1/employee-groups/{id} rejects non-numeric IDs with 400")
    void getEmployeeGroupNonNumericIdReturns400() throws Exception {
        mockMvc.perform(get("/api/v1/employee-groups/abc"))
                .andExpect(status().isBadRequest());
    }

    // --- POST /api/v1/employee-groups ---

    @Test
    @DisplayName("POST /api/v1/employee-groups creates a root group and returns 201")
    void createEmployeeGroupReturns201() throws Exception {
        when(employeeGroupService.createEmployeeGroup(any(EmployeeGroup.class), eq(1L), isNull()))
                .thenAnswer(inv -> inv.getArgument(0));

        mockMvc.perform(post("/api/v1/employee-groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"organizationId\":1,\"name\":\"Front of House\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Front of House"))
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    @DisplayName("POST /api/v1/employee-groups honours parentId and active=false")
    void createEmployeeGroupWithParentHonoursInactive() throws Exception {
        when(employeeGroupService.createEmployeeGroup(any(EmployeeGroup.class), eq(1L), eq(2L)))
                .thenAnswer(inv -> inv.getArgument(0));

        mockMvc.perform(post("/api/v1/employee-groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"organizationId\":1,\"name\":\"Cashiers\",\"parentId\":2,\"active\":false}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Cashiers"))
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    @DisplayName("POST /api/v1/employee-groups rejects blank name with 400 at the boundary")
    void createEmployeeGroupBlankNameReturns400() throws Exception {
        mockMvc.perform(post("/api/v1/employee-groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"organizationId\":1,\"name\":\"  \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.detail").value(containsString("name")));
    }

    @Test
    @DisplayName("POST /api/v1/employee-groups rejects missing organizationId with 400")
    void createEmployeeGroupMissingOrganizationReturns400() throws Exception {
        mockMvc.perform(post("/api/v1/employee-groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Front of House\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.detail").value(containsString("organizationId")));
    }

    @Test
    @DisplayName("POST /api/v1/employee-groups rejects missing name with 400")
    void createEmployeeGroupMissingNameReturns400() throws Exception {
        mockMvc.perform(post("/api/v1/employee-groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"organizationId\":1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation failed"));
    }

    @Test
    @DisplayName("POST /api/v1/employee-groups surfaces an unknown organization as 404 ProblemDetail")
    void createEmployeeGroupUnknownOrganizationReturns404() throws Exception {
        when(employeeGroupService.createEmployeeGroup(any(EmployeeGroup.class), eq(99L), isNull()))
                .thenThrow(new OrganizationNotFoundException(99L));

        mockMvc.perform(post("/api/v1/employee-groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"organizationId\":99,\"name\":\"Front of House\"}"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Organization not found"))
                .andExpect(jsonPath("$.detail").value(containsString("99")));
    }

    @Test
    @DisplayName("POST /api/v1/employee-groups surfaces a cross-organization parent as 400 ProblemDetail")
    void createEmployeeGroupCrossOrganizationParentReturns400() throws Exception {
        when(employeeGroupService.createEmployeeGroup(any(EmployeeGroup.class), eq(1L), eq(2L)))
                .thenThrow(new EmployeeGroupValidationException(
                        "Parent and child employee groups must belong to the same organization:"
                                + " parentId=2, organizationId=1"));

        mockMvc.perform(post("/api/v1/employee-groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"organizationId\":1,\"name\":\"Cashiers\",\"parentId\":2}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Invalid employee group"))
                .andExpect(jsonPath("$.detail").value(containsString("same organization")));
    }

    @Test
    @DisplayName("POST /api/v1/employee-groups surfaces an unknown parent as 404 ProblemDetail")
    void createEmployeeGroupUnknownParentReturns404() throws Exception {
        when(employeeGroupService.createEmployeeGroup(any(EmployeeGroup.class), eq(1L), eq(99L)))
                .thenThrow(new EmployeeGroupNotFoundException(99L));

        mockMvc.perform(post("/api/v1/employee-groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"organizationId\":1,\"name\":\"Cashiers\",\"parentId\":99}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Employee group not found"))
                .andExpect(jsonPath("$.detail").value(containsString("99")));
    }

    // --- PUT /api/v1/employee-groups/{id} ---

    @Test
    @DisplayName("PUT /api/v1/employee-groups/{id} updates the group")
    void updateEmployeeGroupReturns200() throws Exception {
        EmployeeGroup updated = group("Front of House Team");
        updated.setActive(false);
        when(employeeGroupService.updateEmployeeGroup(eq(1L), any(EmployeeGroup.class), isNull()))
                .thenReturn(updated);

        mockMvc.perform(put("/api/v1/employee-groups/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Front of House Team\",\"active\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Front of House Team"))
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    @DisplayName("PUT /api/v1/employee-groups/{id} returns 404 when missing")
    void updateEmployeeGroupMissingReturns404() throws Exception {
        when(employeeGroupService.updateEmployeeGroup(eq(99L), any(EmployeeGroup.class), isNull()))
                .thenThrow(new EmployeeGroupNotFoundException(99L));

        mockMvc.perform(put("/api/v1/employee-groups/99")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Whatever\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Employee group not found"));
    }

    @Test
    @DisplayName("PUT /api/v1/employee-groups/{id} rejects blank name with 400")
    void updateEmployeeGroupBlankNameReturns400() throws Exception {
        mockMvc.perform(put("/api/v1/employee-groups/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation failed"));
    }

    @Test
    @DisplayName("PUT /api/v1/employee-groups/{id} surfaces self-parenting as 400 ProblemDetail")
    void updateEmployeeGroupSelfParentReturns400() throws Exception {
        when(employeeGroupService.updateEmployeeGroup(eq(1L), any(EmployeeGroup.class), eq(1L)))
                .thenThrow(new EmployeeGroupValidationException(
                        "Employee group cannot be its own parent: 1"));

        mockMvc.perform(put("/api/v1/employee-groups/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Front of House\",\"parentId\":1}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Invalid employee group"))
                .andExpect(jsonPath("$.detail").value(containsString("its own parent")));
    }

    // --- DELETE /api/v1/employee-groups/{id} ---

    @Test
    @DisplayName("DELETE /api/v1/employee-groups/{id} returns 204")
    void deleteEmployeeGroupReturns204() throws Exception {
        mockMvc.perform(delete("/api/v1/employee-groups/1"))
                .andExpect(status().isNoContent());

        verify(employeeGroupService).deleteEmployeeGroup(1L);
    }

    @Test
    @DisplayName("DELETE /api/v1/employee-groups/{id} returns 404 when missing")
    void deleteEmployeeGroupMissingReturns404() throws Exception {
        doThrow(new EmployeeGroupNotFoundException(99L))
                .when(employeeGroupService).deleteEmployeeGroup(99L);

        mockMvc.perform(delete("/api/v1/employee-groups/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Employee group not found"));
    }

    @Test
    @DisplayName("DELETE /api/v1/employee-groups/{id} returns 400 when the group still has children")
    void deleteEmployeeGroupWithChildrenReturns400() throws Exception {
        doThrow(new EmployeeGroupValidationException(
                "Cannot delete employee group still having child groups: 1"))
                .when(employeeGroupService).deleteEmployeeGroup(1L);

        mockMvc.perform(delete("/api/v1/employee-groups/1"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Invalid employee group"))
                .andExpect(jsonPath("$.detail").value(containsString("still having child groups")));
    }

    // --- GET /api/v1/employee-groups/{id}/children ---

    @Test
    @DisplayName("GET /api/v1/employee-groups/{id}/children returns the direct children")
    void listChildrenReturnsChildren() throws Exception {
        when(employeeGroupService.listChildGroups(1L))
                .thenReturn(List.of(group("Cashiers"), group("Hosts")));

        mockMvc.perform(get("/api/v1/employee-groups/1/children"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].name").value("Cashiers"))
                .andExpect(jsonPath("$[1].name").value("Hosts"));
    }

    @Test
    @DisplayName("GET /api/v1/employee-groups/{id}/children returns an empty array for a leaf group")
    void listChildrenEmptyReturnsEmptyArray() throws Exception {
        when(employeeGroupService.listChildGroups(1L)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/employee-groups/1/children"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @DisplayName("GET /api/v1/employee-groups/{id}/children returns 404 when the parent is missing")
    void listChildrenMissingParentReturns404() throws Exception {
        when(employeeGroupService.listChildGroups(99L))
                .thenThrow(new EmployeeGroupNotFoundException(99L));

        mockMvc.perform(get("/api/v1/employee-groups/99/children"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Employee group not found"));
    }
}
