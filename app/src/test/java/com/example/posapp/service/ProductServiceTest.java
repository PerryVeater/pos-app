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
import com.example.posapp.entity.Product;
import com.example.posapp.exception.ProductNotFoundException;
import com.example.posapp.repository.CategoryRepository;
import com.example.posapp.repository.ProductRepository;

/**
 * Unit tests for the {@link ProductService} business rules.
 * <p>
 * The repositories are mocked, so these tests exercise the service layer in
 * isolation: validation rules (price, SKU, duplicate SKUs, category
 * references) and delegation to the repositories. No Spring context or
 * database is required.
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepo;

    @Mock
    private CategoryRepository categoryRepo;

    @InjectMocks
    private ProductService productService;

    /**
     * Build an active, uncategorized product from a decimal string so test
     * money values use the exact {@code BigDecimal} construction required
     * for monetary data.
     */
    private static Product product(String name, String sku, String price) {
        return new Product(name, sku, new BigDecimal(price), true);
    }

    // --- createProduct ---

    @Test
    @DisplayName("createProduct: valid product is saved and returned")
    void createProductValidProductIsSaved() {
        Product input = product("Cola", "COLA-001", "2.50");
        when(productRepo.existsBySku("COLA-001")).thenReturn(false);
        when(productRepo.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        Product saved = productService.createProduct(input, null);

        assertThat(saved.getName()).isEqualTo("Cola");
        assertThat(saved.getSku()).isEqualTo("COLA-001");
        assertThat(saved.getPrice()).isEqualByComparingTo("2.50");
        assertThat(saved.isActive()).isTrue();
        verify(productRepo).save(input);
    }

    @Test
    @DisplayName("createProduct: zero price is accepted (only negative prices are rejected)")
    void createProductZeroPriceIsAccepted() {
        Product input = product("Tap water", "WATER-001", "0.00");
        when(productRepo.existsBySku("WATER-001")).thenReturn(false);
        when(productRepo.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThat(productService.createProduct(input, null).getPrice()).isZero();
    }

    @Test
    @DisplayName("createProduct: negative price is rejected and nothing is saved")
    void createProductNegativePriceIsRejected() {
        Product input = product("Broken", "BROKEN-001", "-1.00");

        assertThatThrownBy(() -> productService.createProduct(input, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("negative");

        verify(productRepo, never()).save(any(Product.class));
    }

    @Test
    @DisplayName("createProduct: null price is rejected")
    void createProductNullPriceIsRejected() {
        Product input = new Product("No price", "NOPRICE-001", null, true);

        assertThatThrownBy(() -> productService.createProduct(input, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("provided");

        verify(productRepo, never()).save(any(Product.class));
    }

    @Test
    @DisplayName("createProduct: price precision is preserved (no rounding or scale changes)")
    void createProductPreservesPricePrecision() {
        Product input = product("Espresso", "ESPRESSO-001", "3.99");
        when(productRepo.existsBySku("ESPRESSO-001")).thenReturn(false);
        when(productRepo.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        Product saved = productService.createProduct(input, null);

        assertThat(saved.getPrice()).isEqualByComparingTo("3.99");
        assertThat(saved.getPrice()).hasScaleOf(2);
    }

    @Test
    @DisplayName("createProduct: explicitly inactive product is accepted and stays inactive")
    void createProductInactiveProductIsAccepted() {
        Product input = new Product("Legacy Fries", "FRIES-002", new BigDecimal("3.00"), false);
        when(productRepo.existsBySku("FRIES-002")).thenReturn(false);
        when(productRepo.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        Product saved = productService.createProduct(input, null);

        assertThat(saved.isActive()).isFalse();
        verify(productRepo).save(input);
    }

    @Test
    @DisplayName("createProduct: duplicate SKU is rejected and nothing is saved")
    void createProductDuplicateSkuIsRejected() {
        Product input = product("Cola twin", "COLA-001", "2.50");
        when(productRepo.existsBySku("COLA-001")).thenReturn(true);

        assertThatThrownBy(() -> productService.createProduct(input, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SKU already exists");

        verify(productRepo, never()).save(any(Product.class));
    }

    @Test
    @DisplayName("createProduct: blank SKU is rejected")
    void createProductBlankSkuIsRejected() {
        Product input = product("No SKU", "   ", "2.50");

        assertThatThrownBy(() -> productService.createProduct(input, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SKU must be provided");

        verify(productRepo, never()).save(any(Product.class));
    }

    @Test
    @DisplayName("createProduct: null SKU is rejected")
    void createProductNullSkuIsRejected() {
        Product input = new Product("No SKU", null, new BigDecimal("2.50"), true);

        assertThatThrownBy(() -> productService.createProduct(input, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SKU must be provided");

        verify(productRepo, never()).save(any(Product.class));
    }

    @Test
    @DisplayName("createProduct: SKU longer than 64 characters is rejected")
    void createProductTooLongSkuIsRejected() {
        Product input = product("Long SKU", "S".repeat(65), "2.50");

        assertThatThrownBy(() -> productService.createProduct(input, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at most 64");

        verify(productRepo, never()).save(any(Product.class));
    }

    // --- category resolution on create ---

    @Test
    @DisplayName("createProduct: assigns the referenced category when it exists")
    void createProductAssignsExistingCategory() {
        Product input = product("Cola", "COLA-001", "2.50");
        Category beverages = new Category("Beverages");
        when(productRepo.existsBySku("COLA-001")).thenReturn(false);
        when(categoryRepo.findById(5L)).thenReturn(Optional.of(beverages));
        when(productRepo.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        Product saved = productService.createProduct(input, 5L);

        assertThat(saved.getCategory()).isSameAs(beverages);
        verify(productRepo).save(input);
    }

    @Test
    @DisplayName("createProduct: nonexistent category is rejected and nothing is saved")
    void createProductRejectsMissingCategory() {
        Product input = product("Cola", "COLA-001", "2.50");
        when(productRepo.existsBySku("COLA-001")).thenReturn(false);
        when(categoryRepo.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.createProduct(input, 99L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Category not found");

        verify(productRepo, never()).save(any(Product.class));
    }

    @Test
    @DisplayName("createProduct: omitted category leaves the product uncategorized")
    void createProductWithoutCategoryStaysUncategorized() {
        Product input = product("Cola", "COLA-001", "2.50");
        when(productRepo.existsBySku("COLA-001")).thenReturn(false);
        when(productRepo.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        Product saved = productService.createProduct(input, null);

        assertThat(saved.getCategory()).isNull();
        verify(categoryRepo, never()).findById(any());
    }

    // --- getProductById ---

    @Test
    @DisplayName("getProductById: returns the product when it exists")
    void getProductByIdReturnsExistingProduct() {
        Product existing = product("Cola", "COLA-001", "2.50");
        when(productRepo.findById(1L)).thenReturn(Optional.of(existing));

        assertThat(productService.getProductById(1L)).contains(existing);
    }

    @Test
    @DisplayName("getProductById: returns empty when the product does not exist")
    void getProductByIdReturnsEmptyForMissingProduct() {
        when(productRepo.findById(99L)).thenReturn(Optional.empty());

        assertThat(productService.getProductById(99L)).isEmpty();
    }

    @Test
    @DisplayName("getProductById: null id is rejected")
    void getProductByIdRejectsNullId() {
        assertThatThrownBy(() -> productService.getProductById(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // --- updateProduct ---

    @Test
    @DisplayName("updateProduct: applies new SKU, name, price, and active state to the existing product")
    void updateProductAppliesChanges() {
        Product existing = product("Cola", "COLA-001", "2.50");
        Product changes = new Product("Cola Zero", "COLA-002", new BigDecimal("3.00"), false);
        when(productRepo.findById(1L)).thenReturn(Optional.of(existing));
        when(productRepo.existsBySkuAndIdNot("COLA-002", 1L)).thenReturn(false);
        when(productRepo.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        Product updated = productService.updateProduct(1L, changes, null);

        assertThat(updated.getName()).isEqualTo("Cola Zero");
        assertThat(updated.getSku()).isEqualTo("COLA-002");
        assertThat(updated.getPrice()).isEqualByComparingTo("3.00");
        assertThat(updated.isActive()).isFalse();
        assertThat(updated.getCategory()).isNull();
        verify(productRepo).save(existing);
    }

    @Test
    @DisplayName("updateProduct: keeping the product's own SKU is allowed")
    void updateProductAllowsKeepingOwnSku() {
        Product existing = product("Cola", "COLA-001", "2.50");
        Product changes = product("Cola Zero", "COLA-001", "3.00");
        when(productRepo.findById(1L)).thenReturn(Optional.of(existing));
        when(productRepo.existsBySkuAndIdNot("COLA-001", 1L)).thenReturn(false);
        when(productRepo.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        Product updated = productService.updateProduct(1L, changes, null);

        assertThat(updated.getSku()).isEqualTo("COLA-001");
        verify(productRepo).save(existing);
    }

    @Test
    @DisplayName("updateProduct: SKU already used by another product is rejected and nothing is saved")
    void updateProductRejectsSkuOwnedByAnotherProduct() {
        Product existing = product("Cola", "COLA-001", "2.50");
        Product changes = product("Cola twin", "FRIES-001", "3.00");
        when(productRepo.findById(1L)).thenReturn(Optional.of(existing));
        when(productRepo.existsBySkuAndIdNot("FRIES-001", 1L)).thenReturn(true);

        assertThatThrownBy(() -> productService.updateProduct(1L, changes, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SKU already exists");

        verify(productRepo, never()).save(any(Product.class));
    }

    @Test
    @DisplayName("updateProduct: throws ProductNotFoundException when the product does not exist")
    void updateProductThrowsProductNotFoundForMissingProduct() {
        when(productRepo.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.updateProduct(99L, product("X", "X-001", "1.00"), null))
                .isInstanceOf(ProductNotFoundException.class)
                .hasMessageContaining("not found");
    }

    @Test
    @DisplayName("updateProduct: negative price is rejected and nothing is saved")
    void updateProductRejectsNegativePrice() {
        Product changes = product("Cola", "COLA-001", "-5.00");

        assertThatThrownBy(() -> productService.updateProduct(1L, changes, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("negative");

        verify(productRepo, never()).save(any(Product.class));
    }

    // --- category resolution on update ---

    @Test
    @DisplayName("updateProduct: assigns the referenced category when it exists")
    void updateProductAssignsCategory() {
        Product existing = product("Cola", "COLA-001", "2.50");
        Product changes = product("Cola Zero", "COLA-002", "3.00");
        Category drinks = new Category("Drinks");
        when(productRepo.findById(1L)).thenReturn(Optional.of(existing));
        when(productRepo.existsBySkuAndIdNot("COLA-002", 1L)).thenReturn(false);
        when(categoryRepo.findById(7L)).thenReturn(Optional.of(drinks));
        when(productRepo.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        Product updated = productService.updateProduct(1L, changes, 7L);

        assertThat(updated.getCategory()).isSameAs(drinks);
        verify(productRepo).save(existing);
    }

    @Test
    @DisplayName("updateProduct: nonexistent category is rejected and nothing is saved")
    void updateProductRejectsMissingCategory() {
        Product existing = product("Cola", "COLA-001", "2.50");
        Product changes = product("Cola Zero", "COLA-002", "3.00");
        when(productRepo.findById(1L)).thenReturn(Optional.of(existing));
        when(productRepo.existsBySkuAndIdNot("COLA-002", 1L)).thenReturn(false);
        when(categoryRepo.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.updateProduct(1L, changes, 99L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Category not found");

        verify(productRepo, never()).save(any(Product.class));
    }

    @Test
    @DisplayName("updateProduct: omitted category clears the existing category (full replacement)")
    void updateProductWithoutCategoryClearsCategory() {
        Category drinks = new Category("Drinks");
        Product existing = product("Cola", "COLA-001", "2.50");
        existing.setCategory(drinks);
        Product changes = product("Cola Zero", "COLA-002", "3.00");
        when(productRepo.findById(1L)).thenReturn(Optional.of(existing));
        when(productRepo.existsBySkuAndIdNot("COLA-002", 1L)).thenReturn(false);
        when(productRepo.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        Product updated = productService.updateProduct(1L, changes, null);

        assertThat(updated.getCategory()).isNull();
    }

    // --- deleteProduct / getAllProducts ---

    @Test
    @DisplayName("deleteProduct: delegates to the repository")
    void deleteProductDelegatesToRepository() {
        productService.deleteProduct(1L);

        verify(productRepo).deleteById(1L);
    }

    @Test
    @DisplayName("getAllProducts: returns every product in the repository")
    void getAllProductsReturnsAllProducts() {
        when(productRepo.findAll()).thenReturn(List.of(
                product("Cola", "COLA-001", "2.50"),
                product("Fries", "FRIES-001", "4.25")));

        List<Product> products = productService.getAllProducts();

        assertThat(products).hasSize(2)
                .extracting(Product::getName)
                .containsExactly("Cola", "Fries");
    }
}
