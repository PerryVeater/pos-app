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

import com.example.posapp.entity.Organization;
import com.example.posapp.entity.Store;
import com.example.posapp.exception.OrganizationNotFoundException;
import com.example.posapp.exception.OrganizationValidationException;
import com.example.posapp.service.OrganizationService;

/**
 * Web-layer tests for {@link OrganizationController} using MockMvc.
 * <p>
 * The {@link OrganizationService} is replaced with a Mockito mock, so these
 * tests verify the HTTP contract only: routing, status codes, the JSON
 * representation of organizations and their stores, and the RFC 9457
 * problem-details responses returned for client errors.
 * </p>
 */
@WebMvcTest(OrganizationController.class)
class OrganizationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OrganizationService organizationService;

    private static Organization acme() {
        return new Organization("Acme Restaurants");
    }

    // --- GET /api/v1/organizations ---

    @Test
    @DisplayName("GET /api/v1/organizations returns an empty list")
    void listOrganizationsEmpty() throws Exception {
        when(organizationService.getAllOrganizations()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/organizations"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @DisplayName("GET /api/v1/organizations returns every organization without embedding stores")
    void listOrganizationsReturnsAll() throws Exception {
        when(organizationService.getAllOrganizations()).thenReturn(List.of(acme(), acme()));

        mockMvc.perform(get("/api/v1/organizations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].name").value("Acme Restaurants"))
                .andExpect(jsonPath("$[0].stores").doesNotExist());
    }

    // --- GET /api/v1/organizations/{id} ---

    @Test
    @DisplayName("GET /api/v1/organizations/{id} returns only the organization; stores come from the sub-resource")
    void getOrganizationReturnsStores() throws Exception {
        Organization acme = acme();
        when(organizationService.getOrganizationById(1L)).thenReturn(Optional.of(acme));

        mockMvc.perform(get("/api/v1/organizations/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Acme Restaurants"))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.stores").doesNotExist());
    }

    @Test
    @DisplayName("GET /api/v1/organizations/{id} returns 404 ProblemDetail when missing")
    void getOrganizationMissingReturns404() throws Exception {
        when(organizationService.getOrganizationById(99L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/organizations/99"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Organization not found"))
                .andExpect(jsonPath("$.detail").value(containsString("99")));
    }

    @Test
    @DisplayName("GET /api/v1/organizations/{id} rejects non-numeric IDs with 400")
    void getOrganizationNonNumericIdReturns400() throws Exception {
        mockMvc.perform(get("/api/v1/organizations/abc"))
                .andExpect(status().isBadRequest());
    }

    // --- POST /api/v1/organizations ---

    @Test
    @DisplayName("POST /api/v1/organizations creates a new organization and returns 201")
    void createOrganizationReturns201() throws Exception {
        when(organizationService.createOrganization(any(Organization.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        mockMvc.perform(post("/api/v1/organizations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Acme Restaurants\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Acme Restaurants"))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.stores").doesNotExist());
    }

    @Test
    @DisplayName("POST /api/v1/organizations honours active=false")
    void createOrganizationHonoursInactive() throws Exception {
        when(organizationService.createOrganization(any(Organization.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        mockMvc.perform(post("/api/v1/organizations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Acme Restaurants\",\"active\":false}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    @DisplayName("POST /api/v1/organizations rejects blank name with 400 at the boundary")
    void createOrganizationBlankNameReturns400() throws Exception {
        mockMvc.perform(post("/api/v1/organizations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"  \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.detail").value(containsString("name")));
    }

    @Test
    @DisplayName("POST /api/v1/organizations rejects missing name with 400")
    void createOrganizationMissingNameReturns400() throws Exception {
        mockMvc.perform(post("/api/v1/organizations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation failed"));
    }

    @Test
    @DisplayName("POST /api/v1/organizations surfaces duplicate name as 400 ProblemDetail")
    void createOrganizationDuplicateReturns400() throws Exception {
        when(organizationService.createOrganization(any(Organization.class)))
                .thenThrow(new OrganizationValidationException(
                        "Organization name already exists: Acme Restaurants"));

        mockMvc.perform(post("/api/v1/organizations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Acme Restaurants\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Invalid organization"))
                .andExpect(jsonPath("$.detail").value(containsString("already exists")));
    }

    // --- PUT /api/v1/organizations/{id} ---

    @Test
    @DisplayName("PUT /api/v1/organizations/{id} updates the organization")
    void updateOrganizationReturns200() throws Exception {
        Organization updated = acme();
        updated.setActive(false);
        when(organizationService.updateOrganization(eq(1L), any(Organization.class)))
                .thenReturn(updated);

        mockMvc.perform(put("/api/v1/organizations/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Acme Restaurants\",\"active\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Acme Restaurants"))
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    @DisplayName("PUT /api/v1/organizations/{id} returns 404 when missing")
    void updateOrganizationMissingReturns404() throws Exception {
        when(organizationService.updateOrganization(eq(99L), any(Organization.class)))
                .thenThrow(new OrganizationNotFoundException(99L));

        mockMvc.perform(put("/api/v1/organizations/99")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Whatever\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Organization not found"));
    }

    @Test
    @DisplayName("PUT /api/v1/organizations/{id} rejects blank name with 400")
    void updateOrganizationBlankNameReturns400() throws Exception {
        mockMvc.perform(put("/api/v1/organizations/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation failed"));
    }

    // --- DELETE /api/v1/organizations/{id} ---

    @Test
    @DisplayName("DELETE /api/v1/organizations/{id} returns 204")
    void deleteOrganizationReturns204() throws Exception {
        mockMvc.perform(delete("/api/v1/organizations/1"))
                .andExpect(status().isNoContent());

        verify(organizationService).deleteOrganization(1L);
    }

    @Test
    @DisplayName("DELETE /api/v1/organizations/{id} returns 404 when missing")
    void deleteOrganizationMissingReturns404() throws Exception {
        doThrow(new OrganizationNotFoundException(99L))
                .when(organizationService).deleteOrganization(99L);

        mockMvc.perform(delete("/api/v1/organizations/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Organization not found"));
    }

    @Test
    @DisplayName("DELETE /api/v1/organizations/{id} returns 400 when the organization still owns stores")
    void deleteOrganizationWithStoresReturns400() throws Exception {
        doThrow(new OrganizationValidationException(
                "Cannot delete organization still owning stores: 1"))
                .when(organizationService).deleteOrganization(1L);

        mockMvc.perform(delete("/api/v1/organizations/1"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Invalid organization"))
                .andExpect(jsonPath("$.detail").value(containsString("still owning stores")));
    }

    // --- GET /api/v1/organizations/{id}/stores ---

    @Test
    @DisplayName("GET /api/v1/organizations/{id}/stores returns the owned stores")
    void listStoresReturnsAssignedStores() throws Exception {
        Organization acme = acme();
        Store downtown = new Store(acme, "Downtown", "US", "CA", "Los Angeles",
                "123 Main St", "90001", "America/Los_Angeles");
        Store airport = new Store(acme, "Airport", "US", "CA", "San Francisco",
                "55 Sky Rd", "94102", "America/Los_Angeles");
        when(organizationService.listStores(1L)).thenReturn(List.of(downtown, airport));

        mockMvc.perform(get("/api/v1/organizations/1/stores"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].name").value("Downtown"))
                .andExpect(jsonPath("$[1].name").value("Airport"));
    }

    @Test
    @DisplayName("GET /api/v1/organizations/{id}/stores returns 404 when the organization is missing")
    void listStoresMissingOrganizationReturns404() throws Exception {
        when(organizationService.listStores(99L))
                .thenThrow(new OrganizationNotFoundException(99L));

        mockMvc.perform(get("/api/v1/organizations/99/stores"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Organization not found"));
    }

    @Test
    @DisplayName("GET /api/v1/organizations/{id}/stores returns an empty array for an organization with no stores")
    void listStoresEmptyReturnsEmptyArray() throws Exception {
        when(organizationService.listStores(1L)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/organizations/1/stores"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }
}
