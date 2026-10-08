package com.example.posapp.controller;

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

import com.example.posapp.entity.MenuGroup;
import com.example.posapp.entity.MenuItem;
import com.example.posapp.entity.MenuItemAssignment;
import com.example.posapp.exception.MenuGroupNotFoundException;
import com.example.posapp.exception.MenuItemNotFoundException;
import com.example.posapp.exception.MenuValidationException;
import com.example.posapp.service.MenuGroupService;

/**
 * Web-layer tests for {@link MenuGroupController} using MockMvc.
 * <p>
 * Verifies the HTTP contract for the standalone menu group resource:
 * routing, status codes, and the JSON representation of
 * {@link MenuGroup}s. Business rules are covered by the service unit
 * tests; here we only check the mapping to HTTP responses.
 * </p>
 */
@WebMvcTest(MenuGroupController.class)
class MenuGroupControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MenuGroupService menuGroupService;

    // --- GET /api/v1/menu-groups ---

    @Test
    @DisplayName("GET /api/v1/menu-groups returns every group as a JSON array")
    void getMenuGroupsReturnsAll() throws Exception {
        when(menuGroupService.getAllMenuGroups()).thenReturn(List.of(
                new MenuGroup("Appetizers"),
                new MenuGroup("Desserts")));

        mockMvc.perform(get("/api/v1/menu-groups"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].name").value("Appetizers"))
                .andExpect(jsonPath("$[0].active").value(true))
                .andExpect(jsonPath("$[1].name").value("Desserts"));
    }

    // --- GET /api/v1/menu-groups/{id} ---

    @Test
    @DisplayName("GET /api/v1/menu-groups/{id} returns the group when it exists")
    void getMenuGroupReturnsGroup() throws Exception {
        when(menuGroupService.getMenuGroupById(1L))
                .thenReturn(Optional.of(new MenuGroup("Appetizers")));

        mockMvc.perform(get("/api/v1/menu-groups/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Appetizers"))
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    @DisplayName("GET /api/v1/menu-groups/{id} returns 404 when the group does not exist")
    void getMenuGroupReturns404ForMissing() throws Exception {
        when(menuGroupService.getMenuGroupById(99L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/menu-groups/99"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Menu group not found"));
    }

    // --- POST /api/v1/menu-groups ---

    @Test
    @DisplayName("POST /api/v1/menu-groups with a valid body returns 201 and the saved group")
    void postMenuGroupReturns201() throws Exception {
        when(menuGroupService.createMenuGroup(any(MenuGroup.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        mockMvc.perform(post("/api/v1/menu-groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Appetizers\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Appetizers"))
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    @DisplayName("POST /api/v1/menu-groups with inactive body preserves active=false")
    void postMenuGroupInactiveIsPreserved() throws Exception {
        when(menuGroupService.createMenuGroup(any(MenuGroup.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        mockMvc.perform(post("/api/v1/menu-groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Retired\",\"active\":false}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Retired"))
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    @DisplayName("POST /api/v1/menu-groups with a blank name returns 400")
    void postMenuGroupBlankNameReturns400() throws Exception {
        mockMvc.perform(post("/api/v1/menu-groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.detail").value(containsString("name")));

        verify(menuGroupService, never()).createMenuGroup(any(MenuGroup.class));
    }

    @Test
    @DisplayName("POST /api/v1/menu-groups with a missing name returns 400")
    void postMenuGroupMissingNameReturns400() throws Exception {
        mockMvc.perform(post("/api/v1/menu-groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.detail").value(containsString("name")));
    }

    @Test
    @DisplayName("POST /api/v1/menu-groups with a duplicate name returns 400")
    void postMenuGroupDuplicateNameReturns400() throws Exception {
        when(menuGroupService.createMenuGroup(any(MenuGroup.class)))
                .thenThrow(new MenuValidationException("Menu group name already exists: Appetizers"));

        mockMvc.perform(post("/api/v1/menu-groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Appetizers\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Invalid menu"))
                .andExpect(jsonPath("$.detail").value(containsString("already exists")));
    }

    // --- PUT /api/v1/menu-groups/{id} ---

    @Test
    @DisplayName("PUT /api/v1/menu-groups/{id} returns the updated group")
    void putMenuGroupReturnsUpdated() throws Exception {
        MenuGroup updated = new MenuGroup("Starters");
        updated.setActive(false);
        when(menuGroupService.updateMenuGroup(eq(1L), any(MenuGroup.class))).thenReturn(updated);

        mockMvc.perform(put("/api/v1/menu-groups/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Starters\",\"active\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Starters"))
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    @DisplayName("PUT /api/v1/menu-groups/{id} returns 404 when the group does not exist")
    void putMissingMenuGroupReturns404() throws Exception {
        when(menuGroupService.updateMenuGroup(eq(99L), any(MenuGroup.class)))
                .thenThrow(new MenuGroupNotFoundException(99L));

        mockMvc.perform(put("/api/v1/menu-groups/99")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Ghost\"}"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Menu group not found"));
    }

    // --- DELETE /api/v1/menu-groups/{id} ---

    @Test
    @DisplayName("DELETE /api/v1/menu-groups/{id} returns 204")
    void deleteMenuGroupReturns204() throws Exception {
        mockMvc.perform(delete("/api/v1/menu-groups/1"))
                .andExpect(status().isNoContent());

        verify(menuGroupService).deleteMenuGroup(1L);
    }

    @Test
    @DisplayName("DELETE /api/v1/menu-groups/{id} returns 400 when the group is still assigned")
    void deleteAssignedMenuGroupReturns400() throws Exception {
        org.mockito.Mockito.doThrow(new MenuValidationException(
                "Cannot delete menu group still assigned to a menu: 1"))
                .when(menuGroupService).deleteMenuGroup(1L);

        mockMvc.perform(delete("/api/v1/menu-groups/1"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Invalid menu"))
                .andExpect(jsonPath("$.detail").value(containsString("still assigned")));
    }

    // --- GET /api/v1/menu-groups/{id}/menu-items ---

    @Test
    @DisplayName("GET /api/v1/menu-groups/{id}/menu-items returns assignments in display order")
    void listMenuItemsReturnsOrdered() throws Exception {
        MenuGroup group = new MenuGroup("Appetizers");
        MenuItem cola = new MenuItem("Cola", "COLA-001", null, true);
        MenuItem fries = new MenuItem("Fries", "FRIES-001", null, true);
        when(menuGroupService.listItemAssignments(1L)).thenReturn(java.util.List.of(
                new MenuItemAssignment(group, cola, 1),
                new MenuItemAssignment(group, fries, 2)));

        mockMvc.perform(get("/api/v1/menu-groups/1/menu-items"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].menuItemName").value("Cola"))
                .andExpect(jsonPath("$[0].displayOrder").value(1))
                .andExpect(jsonPath("$[1].menuItemName").value("Fries"))
                .andExpect(jsonPath("$[1].displayOrder").value(2));
    }

    @Test
    @DisplayName("GET /api/v1/menu-groups/{id}/menu-items returns 404 for a missing group")
    void listMenuItemsReturns404ForMissingGroup() throws Exception {
        when(menuGroupService.listItemAssignments(99L)).thenThrow(new MenuGroupNotFoundException(99L));

        mockMvc.perform(get("/api/v1/menu-groups/99/menu-items"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Menu group not found"));
    }

    // --- POST /api/v1/menu-groups/{id}/menu-items ---

    @Test
    @DisplayName("POST /api/v1/menu-groups/{id}/menu-items returns 201 with the new assignment")
    void assignMenuItemReturns201() throws Exception {
        MenuGroup group = new MenuGroup("Appetizers");
        MenuItem cola = new MenuItem("Cola", "COLA-001", null, true);
        when(menuGroupService.assignItem(1L, 2L, 3))
                .thenReturn(new MenuItemAssignment(group, cola, 3));

        mockMvc.perform(post("/api/v1/menu-groups/1/menu-items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"menuItemId\":2,\"displayOrder\":3}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.menuItemName").value("Cola"))
                .andExpect(jsonPath("$.displayOrder").value(3));
    }

    @Test
    @DisplayName("POST /api/v1/menu-groups/{id}/menu-items with missing menuItemId returns 400")
    void assignMenuItemMissingIdReturns400() throws Exception {
        mockMvc.perform(post("/api/v1/menu-groups/1/menu-items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayOrder\":3}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.detail").value(containsString("menuItemId")));
    }

    @Test
    @DisplayName("POST /api/v1/menu-groups/{id}/menu-items with missing displayOrder returns 400")
    void assignMenuItemMissingDisplayOrderReturns400() throws Exception {
        mockMvc.perform(post("/api/v1/menu-groups/1/menu-items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"menuItemId\":2}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.detail").value(containsString("displayOrder")));
    }

    @Test
    @DisplayName("POST /api/v1/menu-groups/{id}/menu-items for a missing item returns 404")
    void assignMenuItemMissingItemReturns404() throws Exception {
        when(menuGroupService.assignItem(1L, 99L, 1)).thenThrow(new MenuItemNotFoundException(99L));

        mockMvc.perform(post("/api/v1/menu-groups/1/menu-items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"menuItemId\":99,\"displayOrder\":1}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Menu item not found"));
    }

    @Test
    @DisplayName("POST /api/v1/menu-groups/{id}/menu-items for a duplicate pair returns 400")
    void assignMenuItemDuplicateReturns400() throws Exception {
        when(menuGroupService.assignItem(1L, 2L, 1)).thenThrow(new MenuValidationException(
                "Menu item already assigned to menu group: menuGroupId=1, menuItemId=2"));

        mockMvc.perform(post("/api/v1/menu-groups/1/menu-items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"menuItemId\":2,\"displayOrder\":1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid menu"))
                .andExpect(jsonPath("$.detail").value(containsString("already assigned")));
    }

    // --- DELETE /api/v1/menu-groups/{id}/menu-items/{menuItemId} ---

    @Test
    @DisplayName("DELETE /api/v1/menu-groups/{id}/menu-items/{menuItemId} returns 204")
    void unassignMenuItemReturns204() throws Exception {
        mockMvc.perform(delete("/api/v1/menu-groups/1/menu-items/2"))
                .andExpect(status().isNoContent());

        verify(menuGroupService).unassignItem(1L, 2L);
    }

    @Test
    @DisplayName("DELETE /api/v1/menu-groups/{id}/menu-items/{menuItemId} returns 404 for a missing assignment")
    void unassignMenuItemMissingReturns404() throws Exception {
        org.mockito.Mockito.doThrow(new MenuItemNotFoundException(2L))
                .when(menuGroupService).unassignItem(1L, 2L);

        mockMvc.perform(delete("/api/v1/menu-groups/1/menu-items/2"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Menu item not found"));
    }
}
