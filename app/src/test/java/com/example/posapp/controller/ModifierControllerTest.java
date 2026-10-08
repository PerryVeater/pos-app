package com.example.posapp.controller;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
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

import com.example.posapp.entity.Modifier;
import com.example.posapp.exception.ModifierNotFoundException;
import com.example.posapp.exception.ModifierValidationException;
import com.example.posapp.service.ModifierService;

/**
 * Web-layer tests for {@link ModifierController} using MockMvc.
 * <p>
 * Verifies the HTTP contract for the standalone modifier resource:
 * routing, status codes, and the JSON representation of
 * {@link Modifier}s. Business rules are covered by the service unit
 * tests; here we only check the mapping to HTTP responses.
 * </p>
 */
@WebMvcTest(ModifierController.class)
class ModifierControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ModifierService modifierService;

    // --- GET /api/v1/modifiers ---

    @Test
    @DisplayName("GET /api/v1/modifiers returns every modifier as a JSON array")
    void getModifiersReturnsAll() throws Exception {
        Modifier cheese = new Modifier("Extra cheese", new BigDecimal("1.50"));
        Modifier onions = new Modifier("No onions", BigDecimal.ZERO);
        when(modifierService.getAllModifiers()).thenReturn(List.of(cheese, onions));

        mockMvc.perform(get("/api/v1/modifiers"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].name").value("Extra cheese"))
                .andExpect(jsonPath("$[0].priceAdjustment").value(1.50))
                .andExpect(jsonPath("$[0].active").value(true))
                .andExpect(jsonPath("$[1].name").value("No onions"));
    }

    // --- GET /api/v1/modifiers/{id} ---

    @Test
    @DisplayName("GET /api/v1/modifiers/{id} returns the modifier when it exists")
    void getModifierReturnsModifier() throws Exception {
        when(modifierService.getModifierById(1L))
                .thenReturn(Optional.of(new Modifier("Extra cheese", new BigDecimal("1.50"))));

        mockMvc.perform(get("/api/v1/modifiers/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Extra cheese"))
                .andExpect(jsonPath("$.priceAdjustment").value(1.50))
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    @DisplayName("GET /api/v1/modifiers/{id} returns 404 when the modifier does not exist")
    void getModifierReturns404ForMissing() throws Exception {
        when(modifierService.getModifierById(99L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/modifiers/99"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Modifier not found"));
    }

    // --- POST /api/v1/modifiers ---

    @Test
    @DisplayName("POST /api/v1/modifiers with a valid body returns 201 and the saved modifier")
    void postModifierReturns201() throws Exception {
        when(modifierService.createModifier(any(Modifier.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        mockMvc.perform(post("/api/v1/modifiers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Extra cheese\",\"priceAdjustment\":1.50}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Extra cheese"))
                .andExpect(jsonPath("$.priceAdjustment").value(1.50))
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    @DisplayName("POST /api/v1/modifiers with inactive body preserves active=false")
    void postModifierInactiveIsPreserved() throws Exception {
        when(modifierService.createModifier(any(Modifier.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        mockMvc.perform(post("/api/v1/modifiers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Retired\",\"priceAdjustment\":0.00,\"active\":false}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Retired"))
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    @DisplayName("POST /api/v1/modifiers with negative adjustment is accepted (discount)")
    void postModifierNegativeAdjustmentAllowed() throws Exception {
        when(modifierService.createModifier(any(Modifier.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        mockMvc.perform(post("/api/v1/modifiers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Loyalty discount\",\"priceAdjustment\":-0.50}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.priceAdjustment").value(-0.50));
    }

    @Test
    @DisplayName("POST /api/v1/modifiers with a blank name returns 400")
    void postModifierBlankNameReturns400() throws Exception {
        mockMvc.perform(post("/api/v1/modifiers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"   \",\"priceAdjustment\":1.00}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.detail").value(containsString("name")));

        verify(modifierService, never()).createModifier(any(Modifier.class));
    }

    @Test
    @DisplayName("POST /api/v1/modifiers with a missing price adjustment returns 400")
    void postModifierMissingPriceAdjustmentReturns400() throws Exception {
        mockMvc.perform(post("/api/v1/modifiers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"No price\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.detail").value(containsString("priceAdjustment")));
    }

    @Test
    @DisplayName("POST /api/v1/modifiers with a blank-name service rejection returns 400")
    void postModifierServiceRejectsBlankName() throws Exception {
        when(modifierService.createModifier(any(Modifier.class)))
                .thenThrow(new ModifierValidationException("Name must be provided"));

        mockMvc.perform(post("/api/v1/modifiers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"x\",\"priceAdjustment\":0.00}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid modifier"))
                .andExpect(jsonPath("$.detail").value(containsString("Name")));
    }

    // --- PUT /api/v1/modifiers/{id} ---

    @Test
    @DisplayName("PUT /api/v1/modifiers/{id} returns the updated modifier")
    void putModifierReturnsUpdated() throws Exception {
        Modifier updated = new Modifier("Double cheese", new BigDecimal("3.00"));
        updated.setActive(false);
        when(modifierService.updateModifier(eq(1L), any(Modifier.class))).thenReturn(updated);

        mockMvc.perform(put("/api/v1/modifiers/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Double cheese\",\"priceAdjustment\":3.00,\"active\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Double cheese"))
                .andExpect(jsonPath("$.priceAdjustment").value(3.00))
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    @DisplayName("PUT /api/v1/modifiers/{id} returns 404 when the modifier does not exist")
    void putMissingModifierReturns404() throws Exception {
        when(modifierService.updateModifier(eq(99L), any(Modifier.class)))
                .thenThrow(new ModifierNotFoundException(99L));

        mockMvc.perform(put("/api/v1/modifiers/99")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Ghost\",\"priceAdjustment\":0.00}"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Modifier not found"));
    }

    // --- DELETE /api/v1/modifiers/{id} ---

    @Test
    @DisplayName("DELETE /api/v1/modifiers/{id} returns 204")
    void deleteModifierReturns204() throws Exception {
        mockMvc.perform(delete("/api/v1/modifiers/1"))
                .andExpect(status().isNoContent());

        verify(modifierService).deleteModifier(1L);
    }

    @Test
    @DisplayName("DELETE /api/v1/modifiers/{id} returns 400 when the modifier is still assigned")
    void deleteAssignedModifierReturns400() throws Exception {
        org.mockito.Mockito.doThrow(new ModifierValidationException(
                "Cannot delete modifier still assigned to a modifier group: 1"))
                .when(modifierService).deleteModifier(1L);

        mockMvc.perform(delete("/api/v1/modifiers/1"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Invalid modifier"))
                .andExpect(jsonPath("$.detail").value(containsString("still assigned")));
    }

    @Test
    @DisplayName("DELETE /api/v1/modifiers/{id} returns 404 when the modifier does not exist")
    void deleteMissingModifierReturns404() throws Exception {
        org.mockito.Mockito.doThrow(new ModifierNotFoundException(99L))
                .when(modifierService).deleteModifier(99L);

        mockMvc.perform(delete("/api/v1/modifiers/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Modifier not found"));
    }
}
