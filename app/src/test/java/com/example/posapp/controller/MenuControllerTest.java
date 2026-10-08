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

import com.example.posapp.entity.Menu;
import com.example.posapp.entity.MenuGroup;
import com.example.posapp.entity.MenuGroupAssignment;
import com.example.posapp.exception.MenuGroupNotFoundException;
import com.example.posapp.exception.MenuNotFoundException;
import com.example.posapp.exception.MenuValidationException;
import com.example.posapp.service.MenuService;

/**
 * Web-layer tests for {@link MenuController} using MockMvc.
 * <p>
 * The {@link MenuService} is replaced with a Mockito mock, so these tests
 * verify the HTTP contract only: routing, status codes, the JSON
 * representation of menus and their assignments, and the RFC 9457
 * problem-details responses returned for client errors.
 * </p>
 */
@WebMvcTest(MenuController.class)
class MenuControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MenuService menuService;

    // --- GET /api/v1/menus ---

    @Test
    @DisplayName("GET /api/v1/menus returns all menus with an empty groups list")
    void getMenusReturnsCollection() throws Exception {
        when(menuService.getAllMenus()).thenReturn(List.of(
                new Menu("Lunch"),
                new Menu("Dinner")));

        mockMvc.perform(get("/api/v1/menus"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].name").value("Lunch"))
                .andExpect(jsonPath("$[0].active").value(true))
                .andExpect(jsonPath("$[0].groups", hasSize(0)))
                .andExpect(jsonPath("$[1].name").value("Dinner"));
    }

    // --- GET /api/v1/menus/{id} ---

    @Test
    @DisplayName("GET /api/v1/menus/{id} returns the menu with assignments in display order")
    void getMenuReturnsDetail() throws Exception {
        Menu lunch = new Menu("Lunch");
        MenuGroup apps = new MenuGroup("Appetizers");
        MenuGroupAssignment assignment = new MenuGroupAssignment(lunch, apps, 1);
        when(menuService.getMenuById(1L)).thenReturn(Optional.of(lunch));
        when(menuService.listAssignments(1L)).thenReturn(List.of(assignment));

        mockMvc.perform(get("/api/v1/menus/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Lunch"))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.groups", hasSize(1)))
                .andExpect(jsonPath("$.groups[0].menuGroupName").value("Appetizers"))
                .andExpect(jsonPath("$.groups[0].displayOrder").value(1));
    }

    @Test
    @DisplayName("GET /api/v1/menus/{id} returns 404 when the menu does not exist")
    void getMenuReturns404ForMissing() throws Exception {
        when(menuService.getMenuById(99L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/menus/99"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Menu not found"));
    }

    // --- POST /api/v1/menus ---

    @Test
    @DisplayName("POST /api/v1/menus with a valid body returns 201 and the saved menu")
    void postMenuReturns201() throws Exception {
        when(menuService.createMenu(any(Menu.class))).thenAnswer(inv -> inv.getArgument(0));

        mockMvc.perform(post("/api/v1/menus")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Lunch\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Lunch"))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.groups", hasSize(0)));
    }

    @Test
    @DisplayName("POST /api/v1/menus with an inactive body returns 201 with active=false")
    void postMenuInactiveIsPreserved() throws Exception {
        when(menuService.createMenu(any(Menu.class))).thenAnswer(inv -> inv.getArgument(0));

        mockMvc.perform(post("/api/v1/menus")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Legacy\",\"active\":false}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Legacy"))
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    @DisplayName("POST /api/v1/menus with a blank name returns 400")
    void postMenuBlankNameReturns400() throws Exception {
        mockMvc.perform(post("/api/v1/menus")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.detail").value(containsString("name")));
    }

    @Test
    @DisplayName("POST /api/v1/menus with a missing name returns 400")
    void postMenuMissingNameReturns400() throws Exception {
        mockMvc.perform(post("/api/v1/menus")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.detail").value(containsString("name")));
    }

    @Test
    @DisplayName("POST /api/v1/menus with a duplicate name returns 400")
    void postMenuDuplicateNameReturns400() throws Exception {
        when(menuService.createMenu(any(Menu.class)))
                .thenThrow(new MenuValidationException("Menu name already exists: Lunch"));

        mockMvc.perform(post("/api/v1/menus")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Lunch\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Invalid menu"))
                .andExpect(jsonPath("$.detail").value(containsString("already exists")));
    }

    // --- PUT /api/v1/menus/{id} ---

    @Test
    @DisplayName("PUT /api/v1/menus/{id} returns the updated menu with its assignments")
    void putMenuReturnsUpdated() throws Exception {
        Menu updated = new Menu("Lunch Special");
        updated.setActive(false);
        when(menuService.updateMenu(eq(1L), any(Menu.class))).thenReturn(updated);
        when(menuService.listAssignments(1L)).thenReturn(List.of());

        mockMvc.perform(put("/api/v1/menus/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Lunch Special\",\"active\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Lunch Special"))
                .andExpect(jsonPath("$.active").value(false))
                .andExpect(jsonPath("$.groups", hasSize(0)));
    }

    @Test
    @DisplayName("PUT /api/v1/menus/{id} returns 404 when the menu does not exist")
    void putMissingMenuReturns404() throws Exception {
        when(menuService.updateMenu(eq(99L), any(Menu.class)))
                .thenThrow(new MenuNotFoundException(99L));

        mockMvc.perform(put("/api/v1/menus/99")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Ghost\"}"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Menu not found"));
    }

    // --- DELETE /api/v1/menus/{id} ---

    @Test
    @DisplayName("DELETE /api/v1/menus/{id} returns 204")
    void deleteMenuReturns204() throws Exception {
        mockMvc.perform(delete("/api/v1/menus/1"))
                .andExpect(status().isNoContent());

        verify(menuService).deleteMenu(1L);
    }

    // --- GET /api/v1/menus/{id}/menu-groups ---

    @Test
    @DisplayName("GET /api/v1/menus/{id}/menu-groups returns assignments in display order")
    void listAssignmentsReturnsOrdered() throws Exception {
        Menu lunch = new Menu("Lunch");
        MenuGroup apps = new MenuGroup("Appetizers");
        MenuGroup desserts = new MenuGroup("Desserts");
        when(menuService.listAssignments(1L)).thenReturn(List.of(
                new MenuGroupAssignment(lunch, apps, 1),
                new MenuGroupAssignment(lunch, desserts, 2)));

        mockMvc.perform(get("/api/v1/menus/1/menu-groups"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].menuGroupName").value("Appetizers"))
                .andExpect(jsonPath("$[0].displayOrder").value(1))
                .andExpect(jsonPath("$[1].menuGroupName").value("Desserts"))
                .andExpect(jsonPath("$[1].displayOrder").value(2));
    }

    @Test
    @DisplayName("GET /api/v1/menus/{id}/menu-groups returns 404 for a missing menu")
    void listAssignmentsReturns404ForMissingMenu() throws Exception {
        when(menuService.listAssignments(99L)).thenThrow(new MenuNotFoundException(99L));

        mockMvc.perform(get("/api/v1/menus/99/menu-groups"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Menu not found"));
    }

    // --- POST /api/v1/menus/{id}/menu-groups ---

    @Test
    @DisplayName("POST /api/v1/menus/{id}/menu-groups returns 201 with the new assignment")
    void assignGroupReturns201() throws Exception {
        Menu lunch = new Menu("Lunch");
        MenuGroup apps = new MenuGroup("Appetizers");
        when(menuService.assignGroup(1L, 2L, 3))
                .thenReturn(new MenuGroupAssignment(lunch, apps, 3));

        mockMvc.perform(post("/api/v1/menus/1/menu-groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"menuGroupId\":2,\"displayOrder\":3}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.menuGroupName").value("Appetizers"))
                .andExpect(jsonPath("$.displayOrder").value(3));
    }

    @Test
    @DisplayName("POST /api/v1/menus/{id}/menu-groups with missing menuGroupId returns 400")
    void assignGroupMissingIdReturns400() throws Exception {
        mockMvc.perform(post("/api/v1/menus/1/menu-groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayOrder\":3}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.detail").value(containsString("menuGroupId")));
    }

    @Test
    @DisplayName("POST /api/v1/menus/{id}/menu-groups with missing displayOrder returns 400")
    void assignGroupMissingDisplayOrderReturns400() throws Exception {
        mockMvc.perform(post("/api/v1/menus/1/menu-groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"menuGroupId\":2}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.detail").value(containsString("displayOrder")));
    }

    @Test
    @DisplayName("POST /api/v1/menus/{id}/menu-groups for a missing menu group returns 404")
    void assignGroupMissingGroupReturns404() throws Exception {
        when(menuService.assignGroup(1L, 99L, 1))
                .thenThrow(new MenuGroupNotFoundException(99L));

        mockMvc.perform(post("/api/v1/menus/1/menu-groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"menuGroupId\":99,\"displayOrder\":1}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Menu group not found"));
    }

    @Test
    @DisplayName("POST /api/v1/menus/{id}/menu-groups for a duplicate pair returns 400")
    void assignGroupDuplicateReturns400() throws Exception {
        when(menuService.assignGroup(1L, 2L, 1))
                .thenThrow(new MenuValidationException(
                        "Menu group already assigned to menu: menuId=1, menuGroupId=2"));

        mockMvc.perform(post("/api/v1/menus/1/menu-groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"menuGroupId\":2,\"displayOrder\":1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid menu"))
                .andExpect(jsonPath("$.detail").value(containsString("already assigned")));
    }

    // --- DELETE /api/v1/menus/{id}/menu-groups/{menuGroupId} ---

    @Test
    @DisplayName("DELETE /api/v1/menus/{id}/menu-groups/{menuGroupId} returns 204")
    void unassignGroupReturns204() throws Exception {
        mockMvc.perform(delete("/api/v1/menus/1/menu-groups/2"))
                .andExpect(status().isNoContent());

        verify(menuService).unassignGroup(1L, 2L);
    }

    @Test
    @DisplayName("DELETE /api/v1/menus/{id}/menu-groups/{menuGroupId} returns 404 when the assignment is missing")
    void unassignGroupMissingReturns404() throws Exception {
        doThrow(new MenuGroupNotFoundException(2L)).when(menuService).unassignGroup(1L, 2L);

        mockMvc.perform(delete("/api/v1/menus/1/menu-groups/2"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Menu group not found"));
    }
}
