package com.example.posapp.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.posapp.entity.Category;
import com.example.posapp.entity.MenuItem;
import com.example.posapp.exception.MenuItemNotFoundException;
import com.example.posapp.repository.CategoryRepository;
import com.example.posapp.repository.MenuItemRepository;

/**
 * Unit tests for the {@link MenuItemService} business rules.
 * <p>
 * The repositories are mocked, so these tests exercise the service layer in
 * isolation: validation rules (price, SKU, duplicate SKUs, category
 * references) and delegation to the repositories. No Spring context or
 * database is required.
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class MenuItemServiceTest {

    @Mock
    private MenuItemRepository menuItemRepo;

    @Mock
    private CategoryRepository categoryRepo;

    @InjectMocks
    private MenuItemService menuItemService;

    /**
     * Build an active, uncategorized menu item from a decimal string so test
     * money values use the exact {@code BigDecimal} construction required
     * for monetary data.
     */
    private static MenuItem menuItem(String name, String sku, String price) {
        return new MenuItem(name, sku, new BigDecimal(price), true);
    }

    // --- createMenuItem ---

    @Test
    @DisplayName("createMenuItem: valid menu item is saved and returned")
    void createMenuItemValidIsSaved() {
        MenuItem input = menuItem("Cola", "COLA-001", "2.50");
        when(menuItemRepo.existsBySku("COLA-001")).thenReturn(false);
        when(menuItemRepo.save(any(MenuItem.class))).thenAnswer(inv -> inv.getArgument(0));

        MenuItem saved = menuItemService.createMenuItem(input, null);

        assertThat(saved.getName()).isEqualTo("Cola");
        assertThat(saved.getSku()).isEqualTo("COLA-001");
        assertThat(saved.getPrice()).isEqualByComparingTo("2.50");
        assertThat(saved.isActive()).isTrue();
        verify(menuItemRepo).save(input);
    }

    @Test
    @DisplayName("createMenuItem: zero price is accepted (only negative prices are rejected)")
    void createMenuItemZeroPriceIsAccepted() {
        MenuItem input = menuItem("Tap water", "WATER-001", "0.00");
        when(menuItemRepo.existsBySku("WATER-001")).thenReturn(false);
        when(menuItemRepo.save(any(MenuItem.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThat(menuItemService.createMenuItem(input, null).getPrice()).isZero();
    }

    @Test
    @DisplayName("createMenuItem: negative price is rejected and nothing is saved")
    void createMenuItemNegativePriceIsRejected() {
        MenuItem input = menuItem("Broken", "BROKEN-001", "-1.00");

        assertThatThrownBy(() -> menuItemService.createMenuItem(input, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("negative");

        verify(menuItemRepo, never()).save(any(MenuItem.class));
    }

    @Test
    @DisplayName("createMenuItem: null price is rejected")
    void createMenuItemNullPriceIsRejected() {
        MenuItem input = new MenuItem("No price", "NOPRICE-001", null, true);

        assertThatThrownBy(() -> menuItemService.createMenuItem(input, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("provided");

        verify(menuItemRepo, never()).save(any(MenuItem.class));
    }

    @Test
    @DisplayName("createMenuItem: price precision is preserved (no rounding or scale changes)")
    void createMenuItemPreservesPricePrecision() {
        MenuItem input = menuItem("Espresso", "ESPRESSO-001", "3.99");
        when(menuItemRepo.existsBySku("ESPRESSO-001")).thenReturn(false);
        when(menuItemRepo.save(any(MenuItem.class))).thenAnswer(inv -> inv.getArgument(0));

        MenuItem saved = menuItemService.createMenuItem(input, null);

        assertThat(saved.getPrice()).isEqualByComparingTo("3.99");
        assertThat(saved.getPrice()).hasScaleOf(2);
    }

    @Test
    @DisplayName("createMenuItem: explicitly inactive menu item is accepted and stays inactive")
    void createMenuItemInactiveIsAccepted() {
        MenuItem input = new MenuItem("Legacy Fries", "FRIES-002", new BigDecimal("3.00"), false);
        when(menuItemRepo.existsBySku("FRIES-002")).thenReturn(false);
        when(menuItemRepo.save(any(MenuItem.class))).thenAnswer(inv -> inv.getArgument(0));

        MenuItem saved = menuItemService.createMenuItem(input, null);

        assertThat(saved.isActive()).isFalse();
        verify(menuItemRepo).save(input);
    }

    @Test
    @DisplayName("createMenuItem: duplicate SKU is rejected and nothing is saved")
    void createMenuItemDuplicateSkuIsRejected() {
        MenuItem input = menuItem("Cola twin", "COLA-001", "2.50");
        when(menuItemRepo.existsBySku("COLA-001")).thenReturn(true);

        assertThatThrownBy(() -> menuItemService.createMenuItem(input, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SKU already exists");

        verify(menuItemRepo, never()).save(any(MenuItem.class));
    }

    @Test
    @DisplayName("createMenuItem: blank SKU is rejected")
    void createMenuItemBlankSkuIsRejected() {
        MenuItem input = menuItem("No SKU", "   ", "2.50");

        assertThatThrownBy(() -> menuItemService.createMenuItem(input, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SKU must be provided");

        verify(menuItemRepo, never()).save(any(MenuItem.class));
    }

    @Test
    @DisplayName("createMenuItem: null SKU is rejected")
    void createMenuItemNullSkuIsRejected() {
        MenuItem input = new MenuItem("No SKU", null, new BigDecimal("2.50"), true);

        assertThatThrownBy(() -> menuItemService.createMenuItem(input, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SKU must be provided");

        verify(menuItemRepo, never()).save(any(MenuItem.class));
    }

    @Test
    @DisplayName("createMenuItem: SKU longer than 64 characters is rejected")
    void createMenuItemTooLongSkuIsRejected() {
        MenuItem input = menuItem("Long SKU", "S".repeat(65), "2.50");

        assertThatThrownBy(() -> menuItemService.createMenuItem(input, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at most 64");

        verify(menuItemRepo, never()).save(any(MenuItem.class));
    }

    // --- category resolution on create ---

    @Test
    @DisplayName("createMenuItem: assigns the referenced category when it exists")
    void createMenuItemAssignsExistingCategory() {
        MenuItem input = menuItem("Cola", "COLA-001", "2.50");
        Category beverages = new Category("Beverages");
        when(menuItemRepo.existsBySku("COLA-001")).thenReturn(false);
        when(categoryRepo.findById(5L)).thenReturn(Optional.of(beverages));
        when(menuItemRepo.save(any(MenuItem.class))).thenAnswer(inv -> inv.getArgument(0));

        MenuItem saved = menuItemService.createMenuItem(input, 5L);

        assertThat(saved.getCategory()).isSameAs(beverages);
        verify(menuItemRepo).save(input);
    }

    @Test
    @DisplayName("createMenuItem: nonexistent category is rejected and nothing is saved")
    void createMenuItemRejectsMissingCategory() {
        MenuItem input = menuItem("Cola", "COLA-001", "2.50");
        when(menuItemRepo.existsBySku("COLA-001")).thenReturn(false);
        when(categoryRepo.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> menuItemService.createMenuItem(input, 99L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Category not found");

        verify(menuItemRepo, never()).save(any(MenuItem.class));
    }

    @Test
    @DisplayName("createMenuItem: omitted category leaves the menu item uncategorized")
    void createMenuItemWithoutCategoryStaysUncategorized() {
        MenuItem input = menuItem("Cola", "COLA-001", "2.50");
        when(menuItemRepo.existsBySku("COLA-001")).thenReturn(false);
        when(menuItemRepo.save(any(MenuItem.class))).thenAnswer(inv -> inv.getArgument(0));

        MenuItem saved = menuItemService.createMenuItem(input, null);

        assertThat(saved.getCategory()).isNull();
        verify(categoryRepo, never()).findById(any());
    }

    // --- getMenuItemById ---

    @Test
    @DisplayName("getMenuItemById: returns the menu item when it exists")
    void getMenuItemByIdReturnsExisting() {
        MenuItem existing = menuItem("Cola", "COLA-001", "2.50");
        when(menuItemRepo.findById(1L)).thenReturn(Optional.of(existing));

        assertThat(menuItemService.getMenuItemById(1L)).contains(existing);
    }

    @Test
    @DisplayName("getMenuItemById: returns empty when the menu item does not exist")
    void getMenuItemByIdReturnsEmptyForMissing() {
        when(menuItemRepo.findById(99L)).thenReturn(Optional.empty());

        assertThat(menuItemService.getMenuItemById(99L)).isEmpty();
    }

    @Test
    @DisplayName("getMenuItemById: null id is rejected")
    void getMenuItemByIdRejectsNullId() {
        assertThatThrownBy(() -> menuItemService.getMenuItemById(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // --- updateMenuItem ---

    @Test
    @DisplayName("updateMenuItem: applies new SKU, name, price, and active state to the existing menu item")
    void updateMenuItemAppliesChanges() {
        MenuItem existing = menuItem("Cola", "COLA-001", "2.50");
        MenuItem changes = new MenuItem("Cola Zero", "COLA-002", new BigDecimal("3.00"), false);
        when(menuItemRepo.findById(1L)).thenReturn(Optional.of(existing));
        when(menuItemRepo.existsBySkuAndIdNot("COLA-002", 1L)).thenReturn(false);
        when(menuItemRepo.save(any(MenuItem.class))).thenAnswer(inv -> inv.getArgument(0));

        MenuItem updated = menuItemService.updateMenuItem(1L, changes, null);

        assertThat(updated.getName()).isEqualTo("Cola Zero");
        assertThat(updated.getSku()).isEqualTo("COLA-002");
        assertThat(updated.getPrice()).isEqualByComparingTo("3.00");
        assertThat(updated.isActive()).isFalse();
        assertThat(updated.getCategory()).isNull();
        verify(menuItemRepo).save(existing);
    }

    @Test
    @DisplayName("updateMenuItem: keeping the menu item's own SKU is allowed")
    void updateMenuItemAllowsKeepingOwnSku() {
        MenuItem existing = menuItem("Cola", "COLA-001", "2.50");
        MenuItem changes = menuItem("Cola Zero", "COLA-001", "3.00");
        when(menuItemRepo.findById(1L)).thenReturn(Optional.of(existing));
        when(menuItemRepo.existsBySkuAndIdNot("COLA-001", 1L)).thenReturn(false);
        when(menuItemRepo.save(any(MenuItem.class))).thenAnswer(inv -> inv.getArgument(0));

        MenuItem updated = menuItemService.updateMenuItem(1L, changes, null);

        assertThat(updated.getSku()).isEqualTo("COLA-001");
        verify(menuItemRepo).save(existing);
    }

    @Test
    @DisplayName("updateMenuItem: SKU already used by another menu item is rejected and nothing is saved")
    void updateMenuItemRejectsSkuOwnedByAnother() {
        MenuItem existing = menuItem("Cola", "COLA-001", "2.50");
        MenuItem changes = menuItem("Cola twin", "FRIES-001", "3.00");
        when(menuItemRepo.findById(1L)).thenReturn(Optional.of(existing));
        when(menuItemRepo.existsBySkuAndIdNot("FRIES-001", 1L)).thenReturn(true);

        assertThatThrownBy(() -> menuItemService.updateMenuItem(1L, changes, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SKU already exists");

        verify(menuItemRepo, never()).save(any(MenuItem.class));
    }

    @Test
    @DisplayName("updateMenuItem: throws MenuItemNotFoundException when the menu item does not exist")
    void updateMenuItemThrowsNotFoundForMissing() {
        when(menuItemRepo.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> menuItemService.updateMenuItem(99L, menuItem("X", "X-001", "1.00"), null))
                .isInstanceOf(MenuItemNotFoundException.class)
                .hasMessageContaining("not found");
    }

    @Test
    @DisplayName("updateMenuItem: negative price is rejected and nothing is saved")
    void updateMenuItemRejectsNegativePrice() {
        MenuItem changes = menuItem("Cola", "COLA-001", "-5.00");

        assertThatThrownBy(() -> menuItemService.updateMenuItem(1L, changes, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("negative");

        verify(menuItemRepo, never()).save(any(MenuItem.class));
    }

    // --- category resolution on update ---

    @Test
    @DisplayName("updateMenuItem: assigns the referenced category when it exists")
    void updateMenuItemAssignsCategory() {
        MenuItem existing = menuItem("Cola", "COLA-001", "2.50");
        MenuItem changes = menuItem("Cola Zero", "COLA-002", "3.00");
        Category drinks = new Category("Drinks");
        when(menuItemRepo.findById(1L)).thenReturn(Optional.of(existing));
        when(menuItemRepo.existsBySkuAndIdNot("COLA-002", 1L)).thenReturn(false);
        when(categoryRepo.findById(7L)).thenReturn(Optional.of(drinks));
        when(menuItemRepo.save(any(MenuItem.class))).thenAnswer(inv -> inv.getArgument(0));

        MenuItem updated = menuItemService.updateMenuItem(1L, changes, 7L);

        assertThat(updated.getCategory()).isSameAs(drinks);
        verify(menuItemRepo).save(existing);
    }

    @Test
    @DisplayName("updateMenuItem: nonexistent category is rejected and nothing is saved")
    void updateMenuItemRejectsMissingCategory() {
        MenuItem existing = menuItem("Cola", "COLA-001", "2.50");
        MenuItem changes = menuItem("Cola Zero", "COLA-002", "3.00");
        when(menuItemRepo.findById(1L)).thenReturn(Optional.of(existing));
        when(menuItemRepo.existsBySkuAndIdNot("COLA-002", 1L)).thenReturn(false);
        when(categoryRepo.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> menuItemService.updateMenuItem(1L, changes, 99L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Category not found");

        verify(menuItemRepo, never()).save(any(MenuItem.class));
    }

    @Test
    @DisplayName("updateMenuItem: omitted category clears the existing category (full replacement)")
    void updateMenuItemWithoutCategoryClearsCategory() {
        Category drinks = new Category("Drinks");
        MenuItem existing = menuItem("Cola", "COLA-001", "2.50");
        existing.setCategory(drinks);
        MenuItem changes = menuItem("Cola Zero", "COLA-002", "3.00");
        when(menuItemRepo.findById(1L)).thenReturn(Optional.of(existing));
        when(menuItemRepo.existsBySkuAndIdNot("COLA-002", 1L)).thenReturn(false);
        when(menuItemRepo.save(any(MenuItem.class))).thenAnswer(inv -> inv.getArgument(0));

        MenuItem updated = menuItemService.updateMenuItem(1L, changes, null);

        assertThat(updated.getCategory()).isNull();
    }

    // --- deleteMenuItem / getAllMenuItems ---

    @Test
    @DisplayName("deleteMenuItem: delegates to the repository")
    void deleteMenuItemDelegatesToRepository() {
        menuItemService.deleteMenuItem(1L);

        verify(menuItemRepo).deleteById(1L);
    }

    @Test
    @DisplayName("getAllMenuItems: returns every menu item in the repository")
    void getAllMenuItemsReturnsAll() {
        when(menuItemRepo.findAll()).thenReturn(List.of(
                menuItem("Cola", "COLA-001", "2.50"),
                menuItem("Fries", "FRIES-001", "4.25")));

        List<MenuItem> items = menuItemService.getAllMenuItems();

        assertThat(items).hasSize(2)
                .extracting(MenuItem::getName)
                .containsExactly("Cola", "Fries");
    }
}
