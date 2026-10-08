package com.example.posapp.loader;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.posapp.entity.Category;
import com.example.posapp.entity.Product;
import com.example.posapp.repository.CategoryRepository;
import com.example.posapp.repository.ProductRepository;

/**
 * Unit tests for the {@link DataLoader} idempotent seeding behavior.
 * <p>
 * The repositories are mocked, so these tests verify the seed decisions
 * (create the category and product on first run, reuse an existing category,
 * skip entirely when the seed product exists) and the exact product seeded
 * (name, SKU, price, active, category). No Spring context or database is
 * required.
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class DataLoaderTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @InjectMocks
    private DataLoader dataLoader;

    @Test
    @DisplayName("run: seeds the category and the product on an empty database")
    void runSeedsProductOnEmptyDatabase() throws Exception {
        when(productRepository.existsByName("Test Product")).thenReturn(false);
        when(categoryRepository.findByName("Test Category")).thenReturn(Optional.empty());
        when(categoryRepository.save(any(Category.class))).thenAnswer(inv -> inv.getArgument(0));

        dataLoader.run();

        verify(productRepository).existsByName("Test Product");
        verify(categoryRepository).save(any(Category.class));
        verify(productRepository).save(any(Product.class));
    }

    @Test
    @DisplayName("run: the seeded product keeps its fields and is assigned to the seed category")
    void runSeedsExpectedProductFields() throws Exception {
        when(productRepository.existsByName("Test Product")).thenReturn(false);
        when(categoryRepository.findByName("Test Category")).thenReturn(Optional.empty());
        when(categoryRepository.save(any(Category.class))).thenAnswer(inv -> inv.getArgument(0));

        dataLoader.run();

        ArgumentCaptor<Product> saved = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).save(saved.capture());
        assertThat(saved.getValue().getName()).isEqualTo("Test Product");
        assertThat(saved.getValue().getSku()).isEqualTo("TEST-PRODUCT-001");
        assertThat(saved.getValue().getPrice()).isEqualByComparingTo("19.99");
        assertThat(saved.getValue().isActive()).isTrue();
        assertThat(saved.getValue().getCategory()).isNotNull();
        assertThat(saved.getValue().getCategory().getName()).isEqualTo("Test Category");
    }

    @Test
    @DisplayName("run: reuses the existing seed category instead of creating a duplicate")
    void runReusesExistingSeedCategory() throws Exception {
        Category existingCategory = new Category("Test Category");
        when(productRepository.existsByName("Test Product")).thenReturn(false);
        when(categoryRepository.findByName("Test Category")).thenReturn(Optional.of(existingCategory));

        dataLoader.run();

        verify(categoryRepository, never()).save(any(Category.class));
        ArgumentCaptor<Product> saved = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).save(saved.capture());
        assertThat(saved.getValue().getCategory()).isSameAs(existingCategory);
    }

    @Test
    @DisplayName("run: skips seeding entirely when the product already exists")
    void runSkipsSeedingWhenSeedProductExists() throws Exception {
        when(productRepository.existsByName("Test Product")).thenReturn(true);

        dataLoader.run();

        verify(productRepository).existsByName("Test Product");
        verify(productRepository, never()).save(any(Product.class));
        verifyNoInteractions(categoryRepository);
    }
}
