package com.example.posapp.controller;

import java.math.BigDecimal;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

import com.example.posapp.entity.MenuItem;
import com.example.posapp.entity.MenuItemModifierGroupAssignment;
import com.example.posapp.entity.ModifierGroup;
import com.example.posapp.exception.MenuValidationException;
import com.example.posapp.exception.MenuItemNotFoundException;
import com.example.posapp.exception.ModifierGroupNotFoundException;
import com.example.posapp.service.MenuItemModifierGroupService;

/**
 * Web-layer tests for {@link MenuItemModifierGroupController} using
 * MockMvc.
 * <p>
 * Verifies the HTTP contract of the
 * {@code /api/v1/menu-items/{id}/modifier-groups} sub-resource: routing,
 * status codes, request validation, and the JSON representation of
 * assignments. Business rules are exercised by the service unit tests;
 * here we only check the mapping to HTTP responses.
 * </p>
 */
@WebMvcTest(MenuItemModifierGroupController.class)
class MenuItemModifierGroupControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MenuItemModifierGroupService assignmentService;

    // --- GET /api/v1/menu-items/{id}/modifier-groups ---

    @Test
    @DisplayName("GET /api/v1/menu-items/{id}/modifier-groups returns assignments in display order")
    void listModifierGroupsReturnsOrdered() throws Exception {
        MenuItem pizza = new MenuItem("Pizza", "PIZZA-1", new BigDecimal("9.99"), true);
        ModifierGroup toppings = new ModifierGroup("Toppings", 0, 3);
        ModifierGroup crusts = new ModifierGroup("Crusts", 1, 1);
        when(assignmentService.listAssignments(1L)).thenReturn(List.of(
                new MenuItemModifierGroupAssignment(pizza, toppings, 1),
                new MenuItemModifierGroupAssignment(pizza, crusts, 2)));

        mockMvc.perform(get("/api/v1/menu-items/1/modifier-groups"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].modifierGroupName").value("Toppings"))
                .andExpect(jsonPath("$[0].minSelections").value(0))
                .andExpect(jsonPath("$[0].maxSelections").value(3))
                .andExpect(jsonPath("$[0].displayOrder").value(1))
                .andExpect(jsonPath("$[1].modifierGroupName").value("Crusts"))
                .andExpect(jsonPath("$[1].displayOrder").value(2));
    }

    @Test
    @DisplayName("GET /api/v1/menu-items/{id}/modifier-groups returns 404 for a missing item")
    void listModifierGroupsReturns404ForMissingItem() throws Exception {
        when(assignmentService.listAssignments(99L)).thenThrow(new MenuItemNotFoundException(99L));

        mockMvc.perform(get("/api/v1/menu-items/99/modifier-groups"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Menu item not found"));
    }

    // --- POST /api/v1/menu-items/{id}/modifier-groups ---

    @Test
    @DisplayName("POST /api/v1/menu-items/{id}/modifier-groups returns 201 with the new assignment")
    void assignModifierGroupReturns201() throws Exception {
        MenuItem pizza = new MenuItem("Pizza", "PIZZA-1", new BigDecimal("9.99"), true);
        ModifierGroup toppings = new ModifierGroup("Toppings", 0, 3);
        when(assignmentService.assign(1L, 2L, 3))
                .thenReturn(new MenuItemModifierGroupAssignment(pizza, toppings, 3));

        mockMvc.perform(post("/api/v1/menu-items/1/modifier-groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"modifierGroupId\":2,\"displayOrder\":3}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.modifierGroupName").value("Toppings"))
                .andExpect(jsonPath("$.minSelections").value(0))
                .andExpect(jsonPath("$.maxSelections").value(3))
                .andExpect(jsonPath("$.displayOrder").value(3));
    }

    @Test
    @DisplayName("POST /api/v1/menu-items/{id}/modifier-groups with missing modifierGroupId returns 400")
    void assignModifierGroupMissingGroupIdReturns400() throws Exception {
        mockMvc.perform(post("/api/v1/menu-items/1/modifier-groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayOrder\":3}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.detail").value(containsString("modifierGroupId")));

        verify(assignmentService, never()).assign(1L, 2L, 3);
    }

    @Test
    @DisplayName("POST /api/v1/menu-items/{id}/modifier-groups with missing displayOrder returns 400")
    void assignModifierGroupMissingDisplayOrderReturns400() throws Exception {
        mockMvc.perform(post("/api/v1/menu-items/1/modifier-groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"modifierGroupId\":2}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.detail").value(containsString("displayOrder")));
    }

    @Test
    @DisplayName("POST /api/v1/menu-items/{id}/modifier-groups for a missing item returns 404")
    void assignModifierGroupMissingItemReturns404() throws Exception {
        when(assignmentService.assign(99L, 1L, 1)).thenThrow(new MenuItemNotFoundException(99L));

        mockMvc.perform(post("/api/v1/menu-items/99/modifier-groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"modifierGroupId\":1,\"displayOrder\":1}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Menu item not found"));
    }

    @Test
    @DisplayName("POST /api/v1/menu-items/{id}/modifier-groups for a missing group returns 404")
    void assignModifierGroupMissingGroupReturns404() throws Exception {
        when(assignmentService.assign(1L, 99L, 1)).thenThrow(new ModifierGroupNotFoundException(99L));

        mockMvc.perform(post("/api/v1/menu-items/1/modifier-groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"modifierGroupId\":99,\"displayOrder\":1}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Modifier group not found"));
    }

    @Test
    @DisplayName("POST /api/v1/menu-items/{id}/modifier-groups for a duplicate pair returns 400")
    void assignModifierGroupDuplicateReturns400() throws Exception {
        when(assignmentService.assign(1L, 2L, 1)).thenThrow(new MenuValidationException(
                "Modifier group already assigned to menu item: menuItemId=1, modifierGroupId=2"));

        mockMvc.perform(post("/api/v1/menu-items/1/modifier-groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"modifierGroupId\":2,\"displayOrder\":1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid menu"))
                .andExpect(jsonPath("$.detail").value(containsString("already assigned")));
    }

    // --- DELETE /api/v1/menu-items/{id}/modifier-groups/{modifierGroupId} ---

    @Test
    @DisplayName("DELETE /api/v1/menu-items/{id}/modifier-groups/{modifierGroupId} returns 204")
    void unassignModifierGroupReturns204() throws Exception {
        mockMvc.perform(delete("/api/v1/menu-items/1/modifier-groups/2"))
                .andExpect(status().isNoContent());

        verify(assignmentService).unassign(1L, 2L);
    }

    @Test
    @DisplayName("DELETE /api/v1/menu-items/{id}/modifier-groups/{modifierGroupId} returns 404 for a missing assignment")
    void unassignModifierGroupMissingReturns404() throws Exception {
        org.mockito.Mockito.doThrow(new ModifierGroupNotFoundException(2L))
                .when(assignmentService).unassign(1L, 2L);

        mockMvc.perform(delete("/api/v1/menu-items/1/modifier-groups/2"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Modifier group not found"));
    }

    @Test
    @DisplayName("DELETE /api/v1/menu-items/{id}/modifier-groups/{modifierGroupId} returns 404 for a missing item")
    void unassignModifierGroupMissingItemReturns404() throws Exception {
        org.mockito.Mockito.doThrow(new MenuItemNotFoundException(99L))
                .when(assignmentService).unassign(99L, 1L);

        mockMvc.perform(delete("/api/v1/menu-items/99/modifier-groups/1"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Menu item not found"));
    }
}
