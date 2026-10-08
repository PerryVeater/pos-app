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
import com.example.posapp.entity.ModifierGroup;
import com.example.posapp.entity.ModifierGroupAssignment;
import com.example.posapp.exception.ModifierGroupNotFoundException;
import com.example.posapp.exception.ModifierNotFoundException;
import com.example.posapp.exception.ModifierValidationException;
import com.example.posapp.service.ModifierGroupService;

/**
 * Web-layer tests for {@link ModifierGroupController} using MockMvc.
 * <p>
 * Verifies the HTTP contract for the modifier group resource and its
 * {@code /modifiers} sub-resource: routing, status codes, and the JSON
 * representation. Business rules are covered by the service unit tests;
 * here we focus on mapping service outcomes to HTTP responses.
 * </p>
 */
@WebMvcTest(ModifierGroupController.class)
class ModifierGroupControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ModifierGroupService modifierGroupService;

    // --- GET /api/v1/modifier-groups ---

    @Test
    @DisplayName("GET /api/v1/modifier-groups returns every group as a JSON array")
    void getModifierGroupsReturnsAll() throws Exception {
        when(modifierGroupService.getAllModifierGroups()).thenReturn(List.of(
                new ModifierGroup("Pizza toppings", 0, 3),
                new ModifierGroup("Drink additions", 0, 2)));

        mockMvc.perform(get("/api/v1/modifier-groups"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].name").value("Pizza toppings"))
                .andExpect(jsonPath("$[0].minSelections").value(0))
                .andExpect(jsonPath("$[0].maxSelections").value(3))
                .andExpect(jsonPath("$[0].active").value(true))
                .andExpect(jsonPath("$[0].modifiers", hasSize(0)))
                .andExpect(jsonPath("$[1].name").value("Drink additions"));
    }

    // --- GET /api/v1/modifier-groups/{id} ---

    @Test
    @DisplayName("GET /api/v1/modifier-groups/{id} returns the group with its modifiers")
    void getModifierGroupReturnsGroup() throws Exception {
        ModifierGroup group = new ModifierGroup("Pizza toppings", 0, 3);
        Modifier cheese = new Modifier("Extra cheese", new BigDecimal("1.00"));
        when(modifierGroupService.getModifierGroupById(1L)).thenReturn(Optional.of(group));
        when(modifierGroupService.listModifierAssignments(1L))
                .thenReturn(List.of(new ModifierGroupAssignment(group, cheese, 1)));

        mockMvc.perform(get("/api/v1/modifier-groups/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Pizza toppings"))
                .andExpect(jsonPath("$.minSelections").value(0))
                .andExpect(jsonPath("$.maxSelections").value(3))
                .andExpect(jsonPath("$.modifiers", hasSize(1)))
                .andExpect(jsonPath("$.modifiers[0].modifierName").value("Extra cheese"))
                .andExpect(jsonPath("$.modifiers[0].displayOrder").value(1));
    }

    @Test
    @DisplayName("GET /api/v1/modifier-groups/{id} returns 404 when the group does not exist")
    void getModifierGroupReturns404ForMissing() throws Exception {
        when(modifierGroupService.getModifierGroupById(99L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/modifier-groups/99"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Modifier group not found"));
    }

    // --- POST /api/v1/modifier-groups ---

    @Test
    @DisplayName("POST /api/v1/modifier-groups with a valid body returns 201")
    void postModifierGroupReturns201() throws Exception {
        when(modifierGroupService.createModifierGroup(any(ModifierGroup.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        mockMvc.perform(post("/api/v1/modifier-groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Pizza toppings\",\"minSelections\":0,\"maxSelections\":3}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Pizza toppings"))
                .andExpect(jsonPath("$.minSelections").value(0))
                .andExpect(jsonPath("$.maxSelections").value(3))
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    @DisplayName("POST /api/v1/modifier-groups with inactive body preserves active=false")
    void postModifierGroupInactiveIsPreserved() throws Exception {
        when(modifierGroupService.createModifierGroup(any(ModifierGroup.class)))
                .thenAnswer(inv -> {
                    ModifierGroup g = inv.getArgument(0);
                    return g;
                });

        mockMvc.perform(post("/api/v1/modifier-groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Retired\",\"minSelections\":0,\"maxSelections\":1,\"active\":false}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Retired"))
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    @DisplayName("POST /api/v1/modifier-groups with a blank name returns 400")
    void postModifierGroupBlankNameReturns400() throws Exception {
        mockMvc.perform(post("/api/v1/modifier-groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"   \",\"minSelections\":0,\"maxSelections\":1}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.detail").value(containsString("name")));

        verify(modifierGroupService, never()).createModifierGroup(any(ModifierGroup.class));
    }

    @Test
    @DisplayName("POST /api/v1/modifier-groups with a negative min returns 400")
    void postModifierGroupNegativeMinReturns400() throws Exception {
        mockMvc.perform(post("/api/v1/modifier-groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"X\",\"minSelections\":-1,\"maxSelections\":2}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.detail").value(containsString("minSelections")));
    }

    @Test
    @DisplayName("POST /api/v1/modifier-groups with a duplicate name returns 400")
    void postModifierGroupDuplicateNameReturns400() throws Exception {
        when(modifierGroupService.createModifierGroup(any(ModifierGroup.class)))
                .thenThrow(new ModifierValidationException(
                        "Modifier group name already exists: Pizza toppings"));

        mockMvc.perform(post("/api/v1/modifier-groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Pizza toppings\",\"minSelections\":0,\"maxSelections\":3}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Invalid modifier"))
                .andExpect(jsonPath("$.detail").value(containsString("already exists")));
    }

    @Test
    @DisplayName("POST /api/v1/modifier-groups with max below min returns 400 from service")
    void postModifierGroupInvalidPolicyReturns400() throws Exception {
        when(modifierGroupService.createModifierGroup(any(ModifierGroup.class)))
                .thenThrow(new ModifierValidationException(
                        "maxSelections cannot be less than minSelections: min=3, max=2"));

        mockMvc.perform(post("/api/v1/modifier-groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"X\",\"minSelections\":3,\"maxSelections\":2}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid modifier"))
                .andExpect(jsonPath("$.detail").value(containsString("maxSelections")));
    }

    // --- PUT /api/v1/modifier-groups/{id} ---

    @Test
    @DisplayName("PUT /api/v1/modifier-groups/{id} returns the updated group")
    void putModifierGroupReturnsUpdated() throws Exception {
        ModifierGroup updated = new ModifierGroup("Hot dog toppings", 1, 2);
        updated.setActive(false);
        when(modifierGroupService.updateModifierGroup(eq(1L), any(ModifierGroup.class)))
                .thenReturn(updated);
        when(modifierGroupService.listModifierAssignments(1L)).thenReturn(List.of());

        mockMvc.perform(put("/api/v1/modifier-groups/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Hot dog toppings\",\"minSelections\":1,\"maxSelections\":2,\"active\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Hot dog toppings"))
                .andExpect(jsonPath("$.minSelections").value(1))
                .andExpect(jsonPath("$.maxSelections").value(2))
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    @DisplayName("PUT /api/v1/modifier-groups/{id} returns 404 when the group does not exist")
    void putMissingModifierGroupReturns404() throws Exception {
        when(modifierGroupService.updateModifierGroup(eq(99L), any(ModifierGroup.class)))
                .thenThrow(new ModifierGroupNotFoundException(99L));

        mockMvc.perform(put("/api/v1/modifier-groups/99")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Ghost\",\"minSelections\":0,\"maxSelections\":1}"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Modifier group not found"));
    }

    // --- DELETE /api/v1/modifier-groups/{id} ---

    @Test
    @DisplayName("DELETE /api/v1/modifier-groups/{id} returns 204")
    void deleteModifierGroupReturns204() throws Exception {
        mockMvc.perform(delete("/api/v1/modifier-groups/1"))
                .andExpect(status().isNoContent());

        verify(modifierGroupService).deleteModifierGroup(1L);
    }

    // --- GET /api/v1/modifier-groups/{id}/modifiers ---

    @Test
    @DisplayName("GET /api/v1/modifier-groups/{id}/modifiers returns assignments in display order")
    void listModifiersReturnsOrdered() throws Exception {
        ModifierGroup group = new ModifierGroup("Pizza toppings", 0, 3);
        Modifier cheese = new Modifier("Extra cheese", new BigDecimal("1.00"));
        Modifier pepperoni = new Modifier("Pepperoni", new BigDecimal("2.00"));
        when(modifierGroupService.listModifierAssignments(1L)).thenReturn(List.of(
                new ModifierGroupAssignment(group, cheese, 1),
                new ModifierGroupAssignment(group, pepperoni, 2)));

        mockMvc.perform(get("/api/v1/modifier-groups/1/modifiers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].modifierName").value("Extra cheese"))
                .andExpect(jsonPath("$[0].priceAdjustment").value(1.00))
                .andExpect(jsonPath("$[0].displayOrder").value(1))
                .andExpect(jsonPath("$[1].modifierName").value("Pepperoni"))
                .andExpect(jsonPath("$[1].displayOrder").value(2));
    }

    @Test
    @DisplayName("GET /api/v1/modifier-groups/{id}/modifiers returns 404 for a missing group")
    void listModifiersReturns404ForMissingGroup() throws Exception {
        when(modifierGroupService.listModifierAssignments(99L))
                .thenThrow(new ModifierGroupNotFoundException(99L));

        mockMvc.perform(get("/api/v1/modifier-groups/99/modifiers"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Modifier group not found"));
    }

    // --- POST /api/v1/modifier-groups/{id}/modifiers ---

    @Test
    @DisplayName("POST /api/v1/modifier-groups/{id}/modifiers returns 201 with the new assignment")
    void assignModifierReturns201() throws Exception {
        ModifierGroup group = new ModifierGroup("Pizza toppings", 0, 3);
        Modifier cheese = new Modifier("Extra cheese", new BigDecimal("1.00"));
        when(modifierGroupService.assignModifier(1L, 2L, 3))
                .thenReturn(new ModifierGroupAssignment(group, cheese, 3));

        mockMvc.perform(post("/api/v1/modifier-groups/1/modifiers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"modifierId\":2,\"displayOrder\":3}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.modifierName").value("Extra cheese"))
                .andExpect(jsonPath("$.displayOrder").value(3));
    }

    @Test
    @DisplayName("POST /api/v1/modifier-groups/{id}/modifiers with missing modifierId returns 400")
    void assignModifierMissingIdReturns400() throws Exception {
        mockMvc.perform(post("/api/v1/modifier-groups/1/modifiers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayOrder\":3}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.detail").value(containsString("modifierId")));
    }

    @Test
    @DisplayName("POST /api/v1/modifier-groups/{id}/modifiers with missing displayOrder returns 400")
    void assignModifierMissingDisplayOrderReturns400() throws Exception {
        mockMvc.perform(post("/api/v1/modifier-groups/1/modifiers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"modifierId\":2}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.detail").value(containsString("displayOrder")));
    }

    @Test
    @DisplayName("POST /api/v1/modifier-groups/{id}/modifiers for a missing modifier returns 404")
    void assignModifierMissingModifierReturns404() throws Exception {
        when(modifierGroupService.assignModifier(1L, 99L, 1))
                .thenThrow(new ModifierNotFoundException(99L));

        mockMvc.perform(post("/api/v1/modifier-groups/1/modifiers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"modifierId\":99,\"displayOrder\":1}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Modifier not found"));
    }

    @Test
    @DisplayName("POST /api/v1/modifier-groups/{id}/modifiers for a duplicate pair returns 400")
    void assignModifierDuplicateReturns400() throws Exception {
        when(modifierGroupService.assignModifier(1L, 2L, 1))
                .thenThrow(new ModifierValidationException(
                        "Modifier already assigned to modifier group: modifierGroupId=1, modifierId=2"));

        mockMvc.perform(post("/api/v1/modifier-groups/1/modifiers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"modifierId\":2,\"displayOrder\":1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid modifier"))
                .andExpect(jsonPath("$.detail").value(containsString("already assigned")));
    }

    // --- DELETE /api/v1/modifier-groups/{id}/modifiers/{modifierId} ---

    @Test
    @DisplayName("DELETE /api/v1/modifier-groups/{id}/modifiers/{modifierId} returns 204")
    void unassignModifierReturns204() throws Exception {
        mockMvc.perform(delete("/api/v1/modifier-groups/1/modifiers/2"))
                .andExpect(status().isNoContent());

        verify(modifierGroupService).unassignModifier(1L, 2L);
    }

    @Test
    @DisplayName("DELETE /api/v1/modifier-groups/{id}/modifiers/{modifierId} returns 404 for a missing assignment")
    void unassignModifierMissingReturns404() throws Exception {
        org.mockito.Mockito.doThrow(new ModifierNotFoundException(2L))
                .when(modifierGroupService).unassignModifier(1L, 2L);

        mockMvc.perform(delete("/api/v1/modifier-groups/1/modifiers/2"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Modifier not found"));
    }
}
