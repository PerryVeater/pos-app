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
import com.example.posapp.exception.StoreNotFoundException;
import com.example.posapp.service.StoreService;

/**
 * Web-layer tests for {@link StoreController} using MockMvc.
 * <p>
 * The {@link StoreService} is replaced with a Mockito mock, so these tests
 * verify the HTTP contract only: routing, status codes, the JSON
 * representation of stores (with the owning organization flattened to an
 * id/name pair, plus the store's address, contact, and timezone fields),
 * and the RFC 9457 problem-details responses returned for client errors.
 * Bean Validation on {@link com.example.posapp.dto.StoreRequest} is
 * exercised through requests missing required fields or supplying a
 * malformed country code / email / timezone.
 * </p>
 */
@WebMvcTest(StoreController.class)
class StoreControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private StoreService storeService;

    private static Organization acme() {
        return new Organization("Acme Restaurants");
    }

    /**
     * Build a transient store populated with every required column so the
     * response mapper can serialize a realistic payload without a null
     * address blowing up an assertion.
     * @param org the owning organization
     * @param name the store name
     * @return a fully-populated store
     */
    private static Store store(Organization org, String name) {
        return new Store(org, name, "US", "CA", "Los Angeles",
                "123 Main St", "90001", "America/Los_Angeles");
    }

    /**
     * Body shared by the happy-path create and update tests. Every required
     * field on {@link com.example.posapp.dto.StoreRequest} is present so
     * Bean Validation passes and the mock service is actually called.
     * @param organizationId the owning organization ID
     * @param name the store name
     * @return the JSON request body
     */
    private static String validBody(long organizationId, String name) {
        return "{\"organizationId\":" + organizationId
                + ",\"name\":\"" + name + "\""
                + ",\"country\":\"US\""
                + ",\"stateProvince\":\"CA\""
                + ",\"city\":\"Los Angeles\""
                + ",\"addressLine1\":\"123 Main St\""
                + ",\"postalCode\":\"90001\""
                + ",\"timezone\":\"America/Los_Angeles\"}";
    }

    // --- GET /api/v1/stores ---

    @Test
    @DisplayName("GET /api/v1/stores returns an empty array")
    void listStoresEmpty() throws Exception {
        when(storeService.getAllStores()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/stores"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @DisplayName("GET /api/v1/stores returns every store with its flattened organization info")
    void listStoresReturnsAll() throws Exception {
        Organization acme = acme();
        when(storeService.getAllStores()).thenReturn(List.of(
                store(acme, "Downtown"),
                store(acme, "Airport")));

        mockMvc.perform(get("/api/v1/stores"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].name").value("Downtown"))
                .andExpect(jsonPath("$[0].organizationName").value("Acme Restaurants"))
                .andExpect(jsonPath("$[0].country").value("US"))
                .andExpect(jsonPath("$[0].timezone").value("America/Los_Angeles"))
                .andExpect(jsonPath("$[1].name").value("Airport"));
    }

    // --- GET /api/v1/stores/{id} ---

    @Test
    @DisplayName("GET /api/v1/stores/{id} returns the store")
    void getStoreReturns200() throws Exception {
        when(storeService.getStoreById(1L)).thenReturn(Optional.of(store(acme(), "Downtown")));

        mockMvc.perform(get("/api/v1/stores/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Downtown"))
                .andExpect(jsonPath("$.organizationName").value("Acme Restaurants"))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.city").value("Los Angeles"))
                .andExpect(jsonPath("$.postalCode").value("90001"));
    }

    @Test
    @DisplayName("GET /api/v1/stores/{id} returns 404 ProblemDetail when missing")
    void getStoreMissingReturns404() throws Exception {
        when(storeService.getStoreById(99L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/stores/99"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Store not found"))
                .andExpect(jsonPath("$.detail").value(containsString("99")));
    }

    // --- POST /api/v1/stores ---

    @Test
    @DisplayName("POST /api/v1/stores creates a new store and returns 201")
    void createStoreReturns201() throws Exception {
        when(storeService.createStore(any(Store.class), eq(1L)))
                .thenAnswer(inv -> inv.getArgument(0));

        mockMvc.perform(post("/api/v1/stores")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody(1L, "Downtown")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Downtown"))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.country").value("US"))
                .andExpect(jsonPath("$.timezone").value("America/Los_Angeles"));
    }

    @Test
    @DisplayName("POST /api/v1/stores honours active=false")
    void createStoreHonoursInactive() throws Exception {
        when(storeService.createStore(any(Store.class), eq(1L)))
                .thenAnswer(inv -> {
                    Store s = inv.getArgument(0);
                    s.setActive(false);
                    return s;
                });

        mockMvc.perform(post("/api/v1/stores")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody(1L, "Downtown").replace(
                                "\"postalCode\":\"90001\"",
                                "\"postalCode\":\"90001\",\"active\":false")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    @DisplayName("POST /api/v1/stores accepts an optional storeNumber and echoes it back")
    void createStoreWithStoreNumberReturns201() throws Exception {
        when(storeService.createStore(any(Store.class), eq(1L)))
                .thenAnswer(inv -> inv.getArgument(0));

        mockMvc.perform(post("/api/v1/stores")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody(1L, "Downtown").replace(
                                "\"name\":\"Downtown\"",
                                "\"storeNumber\":\"0042\",\"name\":\"Downtown\"")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.storeNumber").value("0042"))
                .andExpect(jsonPath("$.name").value("Downtown"));
    }

    @Test
    @DisplayName("POST /api/v1/stores rejects missing organizationId with 400")
    void createStoreMissingOrganizationReturns400() throws Exception {
        mockMvc.perform(post("/api/v1/stores")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody(1L, "Downtown").replace("\"organizationId\":1,", "")))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.detail").value(containsString("organizationId")));
    }

    @Test
    @DisplayName("POST /api/v1/stores rejects blank name with 400")
    void createStoreBlankNameReturns400() throws Exception {
        mockMvc.perform(post("/api/v1/stores")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody(1L, "")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.detail").value(containsString("name")));
    }

    @Test
    @DisplayName("POST /api/v1/stores rejects missing required address fields with 400")
    void createStoreMissingAddressReturns400() throws Exception {
        mockMvc.perform(post("/api/v1/stores")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"organizationId\":1,\"name\":\"Downtown\""
                                + ",\"stateProvince\":\"CA\",\"city\":\"Los Angeles\""
                                + ",\"addressLine1\":\"123 Main St\",\"postalCode\":\"90001\""
                                + ",\"timezone\":\"America/Los_Angeles\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.detail").value(containsString("country")));
    }

    @Test
    @DisplayName("POST /api/v1/stores rejects a non-ISO country code with 400")
    void createStoreInvalidCountryCodeReturns400() throws Exception {
        mockMvc.perform(post("/api/v1/stores")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody(1L, "Downtown").replace("\"country\":\"US\"",
                                "\"country\":\"USA\"")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.detail").value(containsString("country")));
    }

    @Test
    @DisplayName("POST /api/v1/stores rejects a malformed email when provided")
    void createStoreInvalidEmailReturns400() throws Exception {
        mockMvc.perform(post("/api/v1/stores")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody(1L, "Downtown").replace(
                                "\"timezone\":\"America/Los_Angeles\"",
                                "\"timezone\":\"America/Los_Angeles\",\"email\":\"not-an-email\"")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.detail").value(containsString("email")));
    }

    @Test
    @DisplayName("POST /api/v1/stores rejects a missing timezone with 400")
    void createStoreMissingTimezoneReturns400() throws Exception {
        mockMvc.perform(post("/api/v1/stores")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody(1L, "Downtown").replace(
                                ",\"timezone\":\"America/Los_Angeles\"", "")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.detail").value(containsString("timezone")));
    }

    @Test
    @DisplayName("POST /api/v1/stores surfaces unknown organization as 404")
    void createStoreUnknownOrganizationReturns404() throws Exception {
        when(storeService.createStore(any(Store.class), eq(99L)))
                .thenThrow(new OrganizationNotFoundException(99L));

        mockMvc.perform(post("/api/v1/stores")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody(99L, "Downtown")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Organization not found"));
    }

    @Test
    @DisplayName("POST /api/v1/stores surfaces a duplicate store number as 400")
    void createStoreDuplicateStoreNumberReturns400() throws Exception {
        when(storeService.createStore(any(Store.class), eq(1L)))
                .thenThrow(new OrganizationValidationException(
                        "Store number already exists in organization: organizationId=1, storeNumber=0042"));

        mockMvc.perform(post("/api/v1/stores")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody(1L, "Downtown").replace(
                                "\"name\":\"Downtown\"",
                                "\"storeNumber\":\"0042\",\"name\":\"Downtown\"")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid organization"))
                .andExpect(jsonPath("$.detail").value(containsString("Store number already exists")));
    }

    // --- PUT /api/v1/stores/{id} ---

    @Test
    @DisplayName("PUT /api/v1/stores/{id} updates the store including re-parenting")
    void updateStoreReturns200() throws Exception {
        Organization globex = new Organization("Globex");
        Store updated = store(globex, "New Name");
        updated.setActive(false);
        when(storeService.updateStore(eq(1L), any(Store.class), eq(2L))).thenReturn(updated);

        mockMvc.perform(put("/api/v1/stores/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody(2L, "New Name").replace(
                                "\"timezone\":\"America/Los_Angeles\"",
                                "\"timezone\":\"America/Los_Angeles\",\"active\":false")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("New Name"))
                .andExpect(jsonPath("$.organizationName").value("Globex"))
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    @DisplayName("PUT /api/v1/stores/{id} returns 404 when the store is missing")
    void updateStoreMissingReturns404() throws Exception {
        when(storeService.updateStore(eq(99L), any(Store.class), eq(1L)))
                .thenThrow(new StoreNotFoundException(99L));

        mockMvc.perform(put("/api/v1/stores/99")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody(1L, "Whatever")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Store not found"));
    }

    @Test
    @DisplayName("PUT /api/v1/stores/{id} returns 404 when the target organization is missing")
    void updateStoreUnknownOrganizationReturns404() throws Exception {
        when(storeService.updateStore(eq(1L), any(Store.class), eq(99L)))
                .thenThrow(new OrganizationNotFoundException(99L));

        mockMvc.perform(put("/api/v1/stores/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody(99L, "Whatever")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Organization not found"));
    }

    // --- DELETE /api/v1/stores/{id} ---

    @Test
    @DisplayName("DELETE /api/v1/stores/{id} returns 204")
    void deleteStoreReturns204() throws Exception {
        mockMvc.perform(delete("/api/v1/stores/1"))
                .andExpect(status().isNoContent());

        verify(storeService).deleteStore(1L);
    }

    @Test
    @DisplayName("DELETE /api/v1/stores/{id} surfaces service errors as 400 ProblemDetail")
    void deleteStoreRejectsWhenServiceRefuses() throws Exception {
        doThrow(new OrganizationValidationException("unexpected rule"))
                .when(storeService).deleteStore(1L);

        mockMvc.perform(delete("/api/v1/stores/1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid organization"));
    }
}
