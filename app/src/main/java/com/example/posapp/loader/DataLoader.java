package com.example.posapp.loader;

import java.math.BigDecimal;

import com.example.posapp.entity.Category;
import com.example.posapp.entity.MenuItem;
import com.example.posapp.repository.CategoryRepository;
import com.example.posapp.repository.MenuItemRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * Component class for loading data into the database.
 * <p>
 * This class is annotated with {@code @Component} to indicate that it's a Spring component,
 * and implements {@code CommandLineRunner} to run the data loading logic when the application starts.
 * </p>
 * <p>
 * Seeding is idempotent: the demo product is only inserted when no product with
 * its name exists yet, so repeated application restarts never create duplicate
 * seed rows. The demo product belongs to a demo category, which is created
 * only when the product is seeded for the first time.
 * </p>
 * <p>
 * Typical usage:
 * <ul>
 *   <li>Called by Spring Boot to load data into the database when the application starts.</li>
 * </ul>
 * </p>
 * 
 * @see com.example.posapp.entity.MenuItem
 * @see com.example.posapp.repository.MenuItemRepository
 */
@Component
public class DataLoader implements CommandLineRunner {

    /**
     * Name of the demo product. Also serves as the idempotency key: if a
     * product with this name already exists, seeding is skipped.
     */
    private static final String SEED_PRODUCT_NAME = "Test Product";

    /**
     * SKU of the demo product: a stable, caller-style identifier so the seed
     * row satisfies the NOT NULL / UNIQUE sku column.
     */
    private static final String SEED_PRODUCT_SKU = "TEST-PRODUCT-001";

    /**
     * Name of the demo category the demo product is assigned to.
     */
    private static final String SEED_CATEGORY_NAME = "Test Category";

    private final MenuItemRepository menuItemRepository;

    private final CategoryRepository categoryRepository;

    /**
     * Constructor for DataLoader.
     * @param menuItemRepository the repository for {@link MenuItem}s.
     * @param categoryRepository the repository for {@link Category}s.
     */
    public DataLoader(MenuItemRepository menuItemRepository, CategoryRepository categoryRepository) {
        this.menuItemRepository = menuItemRepository;
        this.categoryRepository = categoryRepository;
    }

    /**
     * Run the data loading logic: insert the seed category and seed product
     * unless a product with the seed name already exists.
     * @param args the command line arguments
     * @throws Exception if an error occurs
     */
    @Override
    public void run(String... args) throws Exception {
        if (menuItemRepository.existsByName(SEED_PRODUCT_NAME)) {
            return;
        }

        // Find or create the demo category and assign it to the seed product
        Category seedCategory = categoryRepository.findByName(SEED_CATEGORY_NAME)
                .orElseGet(() -> categoryRepository.save(new Category(SEED_CATEGORY_NAME)));

        // Save the seed product only on first use
        MenuItem p = new MenuItem(SEED_PRODUCT_NAME, SEED_PRODUCT_SKU, new BigDecimal("19.99"), true);
        p.setCategory(seedCategory);
        menuItemRepository.save(p);

        // Fetch all menu items
        System.out.println("Menu items in DB:");
        menuItemRepository.findAll().forEach(System.out::println);
    }
}
