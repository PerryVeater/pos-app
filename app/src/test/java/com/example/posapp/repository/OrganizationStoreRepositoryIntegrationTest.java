package com.example.posapp.repository;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.example.posapp.entity.Organization;
import com.example.posapp.entity.Store;

/**
 * Integration tests for the Organization + Store tenancy foundation against
 * a real PostgreSQL.
 * <p>
 * Flyway applies V11 to create {@code organization} and {@code stores};
 * Hibernate {@code ddl-auto=validate} then confirms the JPA model matches.
 * These tests verify schema metadata, the enforced constraints (organization
 * name uniqueness, the composite organization-scoped store-number
 * uniqueness with NULLs allowed, the store → organization FK, and the NOT
 * NULL address/timezone columns), and the ownership semantics required by
 * the foundation: one organization owns many stores, a store always belongs
 * to exactly one organization, deleting an organization with attached
 * stores is rejected at the database level, and store names may collide
 * freely because they are not unique.
 * </p>
 */
@SpringBootTest
@Testcontainers
class OrganizationStoreRepositoryIntegrationTest {

    /**
     * Disposable PostgreSQL 16 database; {@code @ServiceConnection} feeds its
     * JDBC URL and credentials into the Spring environment in place of the
     * configured localhost datasource.
     */
    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private StoreRepository storeRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * Build a store populated with valid required fields so tests can
     * persist it without tripping a NOT NULL violation on the address or
     * timezone columns.
     * @param owner the owning organization
     * @param name the store name
     * @return a transient store ready to save
     */
    private static Store store(Organization owner, String name) {
        return new Store(owner, name, "US", "CA", "Los Angeles",
                "123 Main St", "90001", "America/Los_Angeles");
    }

    // --- schema metadata matches the JPA model ---

    @Test
    @DisplayName("organization table matches the JPA model: id, name, active")
    void organizationTableMatchesJpaModel() {
        List<Map<String, Object>> columns = jdbcTemplate.queryForList(
                "SELECT column_name, data_type, is_nullable"
                + " FROM information_schema.columns WHERE table_name = 'organization'"
                + " ORDER BY ordinal_position");

        assertThat(columns)
                .extracting(column -> column.get("column_name"))
                .containsExactly("id", "name", "active");
        assertThat(columns.get(1))
                .containsEntry("column_name", "name")
                .containsEntry("data_type", "character varying")
                .containsEntry("is_nullable", "NO");
        assertThat(columns.get(2))
                .containsEntry("column_name", "active")
                .containsEntry("data_type", "boolean")
                .containsEntry("is_nullable", "NO");
    }

    @Test
    @DisplayName("stores table matches the JPA model: identity, ownership, address, contact, timezone")
    void storesTableMatchesJpaModel() {
        List<Map<String, Object>> columns = jdbcTemplate.queryForList(
                "SELECT column_name, data_type, is_nullable"
                + " FROM information_schema.columns WHERE table_name = 'stores'"
                + " ORDER BY ordinal_position");

        assertThat(columns)
                .extracting(column -> column.get("column_name"))
                .containsExactly(
                        "id", "organization_id", "store_number", "name", "active",
                        "country", "state_province", "city", "address_line1",
                        "address_line2", "postal_code", "phone_number", "email",
                        "timezone");

        // Optional columns: id is a NOT NULL primary key even though it is
        // generated; the true nullable set covers the optional business
        // fields only.
        assertThat(columns)
                .filteredOn(column -> "YES".equals(column.get("is_nullable")))
                .extracting(column -> column.get("column_name"))
                .containsExactlyInAnyOrder(
                        "store_number", "address_line2", "phone_number", "email");
        assertThat(columns)
                .filteredOn(column -> "NO".equals(column.get("is_nullable")))
                .extracting(column -> column.get("column_name"))
                .contains("id", "organization_id", "name", "active", "country",
                        "state_province", "city", "address_line1", "postal_code",
                        "timezone");
    }

    @Test
    @DisplayName("stores.organization_id foreign key references organization(id) via fk_stores_organization")
    void storeForeignKeyPointsAtOrganization() {
        List<Map<String, Object>> fks = jdbcTemplate.queryForList(
                "SELECT tc.constraint_name, kcu.column_name, ccu.table_name AS referenced_table"
                + " FROM information_schema.table_constraints tc"
                + " JOIN information_schema.key_column_usage kcu ON tc.constraint_name = kcu.constraint_name"
                + " JOIN information_schema.constraint_column_usage ccu ON ccu.constraint_name = tc.constraint_name"
                + " WHERE tc.constraint_type = 'FOREIGN KEY' AND tc.table_name = 'stores'");

        assertThat(fks)
                .anySatisfy(row -> assertThat(row)
                        .containsEntry("constraint_name", "fk_stores_organization")
                        .containsEntry("column_name", "organization_id")
                        .containsEntry("referenced_table", "organization"));
    }

    // --- unique constraints ---

    @Test
    @DisplayName("organization.name is enforced unique at the database level")
    void organizationNameIsEnforcedUnique() {
        organizationRepository.save(new Organization("INT-ORG-Unique Acme"));

        assertThatThrownBy(() -> organizationRepository.save(new Organization("INT-ORG-Unique Acme")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("stores.name is NOT unique: two stores may share a name inside one organization")
    void storeNamesCanCollideWithinOrganization() {
        Organization owner = organizationRepository.save(new Organization("INT-STORE-NAME Acme"));
        Store first = storeRepository.save(store(owner, "INT-STORE-NAME Downtown"));
        Store second = storeRepository.save(store(owner, "INT-STORE-NAME Downtown"));

        assertThat(first.getId()).isNotEqualTo(second.getId());
        assertThat(storeRepository.findByOrganizationId(owner.getId()))
                .extracting(Store::getName)
                .containsExactlyInAnyOrder("INT-STORE-NAME Downtown", "INT-STORE-NAME Downtown");

        // Cleanup
        storeRepository.deleteAll(List.of(first, second));
        organizationRepository.deleteById(owner.getId());
    }

    @Test
    @DisplayName("stores.name is NOT unique across organizations either")
    void storeNamesCanCollideAcrossOrganizations() {
        Organization acme = organizationRepository.save(new Organization("INT-STORE-XORG Acme"));
        Organization globex = organizationRepository.save(new Organization("INT-STORE-XORG Globex"));
        Store a = storeRepository.save(store(acme, "INT-STORE-XORG共享"));
        Store b = storeRepository.save(store(globex, "INT-STORE-XORG共享"));

        assertThat(a.getId()).isNotEqualTo(b.getId());

        // Cleanup
        storeRepository.deleteAll(List.of(a, b));
        organizationRepository.deleteAll(List.of(acme, globex));
    }

    @Test
    @DisplayName("store_number is enforced unique within an organization")
    void storeNumberIsUniquePerOrganization() {
        Organization owner = organizationRepository.save(new Organization("INT-STORE-NUM Acme"));
        Store withNumber = store(owner, "INT-STORE-NUM A");
        withNumber.setStoreNumber("0042");
        storeRepository.saveAndFlush(withNumber);

        Store duplicate = store(owner, "INT-STORE-NUM B");
        duplicate.setStoreNumber("0042");

        assertThatThrownBy(() -> storeRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);

        // Cleanup
        storeRepository.deleteAll(storeRepository.findByOrganizationId(owner.getId()));
        organizationRepository.deleteById(owner.getId());
    }

    @Test
    @DisplayName("the same store_number may exist under two different organizations")
    void storeNumberIsScopedPerOrganization() {
        Organization acme = organizationRepository.save(new Organization("INT-STORE-NUM2 Acme"));
        Organization globex = organizationRepository.save(new Organization("INT-STORE-NUM2 Globex"));
        Store a = store(acme, "INT-STORE-NUM2 A");
        a.setStoreNumber("0042");
        Store b = store(globex, "INT-STORE-NUM2 B");
        b.setStoreNumber("0042");

        storeRepository.saveAndFlush(a);
        storeRepository.saveAndFlush(b);

        assertThat(storeRepository.findByOrganizationIdAndStoreNumber(acme.getId(), "0042"))
                .map(Store::getId).contains(a.getId());
        assertThat(storeRepository.findByOrganizationIdAndStoreNumber(globex.getId(), "0042"))
                .map(Store::getId).contains(b.getId());

        // Cleanup
        storeRepository.deleteAll(List.of(a, b));
        organizationRepository.deleteAll(List.of(acme, globex));
    }

    @Test
    @DisplayName("multiple stores in the same organization may omit store_number (NULLs are distinct)")
    void nullStoreNumbersAreAllowedInSameOrganization() {
        Organization owner = organizationRepository.save(new Organization("INT-STORE-NULL Acme"));
        Store a = storeRepository.save(store(owner, "INT-STORE-NULL A"));
        Store b = storeRepository.save(store(owner, "INT-STORE-NULL B"));

        assertThat(a.getStoreNumber()).isNull();
        assertThat(b.getStoreNumber()).isNull();

        // Cleanup
        storeRepository.deleteAll(List.of(a, b));
        organizationRepository.deleteById(owner.getId());
    }

    @Test
    @DisplayName("every required address and timezone column rejects a null value")
    void requiredAddressAndTimezoneColumnsAreNotNull() {
        Organization owner = organizationRepository.save(new Organization("INT-STORE-REQ Acme"));
        Store broken = new Store();
        broken.setOrganization(owner);
        broken.setName("INT-STORE-REQ Downtown");
        broken.setActive(true);
        broken.setTimezone("America/Los_Angeles");
        broken.setPostalCode("90001");
        broken.setCity("Los Angeles");
        broken.setStateProvince("CA");
        // country and addressLine1 intentionally left null

        assertThatThrownBy(() -> storeRepository.saveAndFlush(broken))
                .isInstanceOf(DataIntegrityViolationException.class);

        // Cleanup: the transactional test rolls back the organization anyway,
        // but be explicit that no store was persisted.
        assertThat(storeRepository.findByOrganizationId(owner.getId())).isEmpty();
    }

    // --- FK enforcement ---

    @Test
    @DisplayName("store requires an existing organization: null reference is rejected by the JPA model")
    void storeCannotBePersistedWithoutOrganization() {
        Store orphan = store(null, "INT-STORE-ORPHAN Downtown");

        assertThatThrownBy(() -> storeRepository.saveAndFlush(orphan))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("deleting an organization that still owns stores is blocked by the FK")
    void deletingOrganizationWithStoresIsBlocked() {
        Organization owner = organizationRepository.save(new Organization("INT-STORE-DEL Acme"));
        storeRepository.save(store(owner, "INT-STORE-DEL Downtown"));

        assertThatThrownBy(() -> organizationRepository.deleteById(owner.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);

        // Cleanup: drop the store first so the organization can go away.
        storeRepository.deleteAll(storeRepository.findByOrganizationId(owner.getId()));
        organizationRepository.deleteById(owner.getId());
        assertThat(organizationRepository.findById(owner.getId())).isEmpty();
    }

    // --- ownership semantics ---

    @Test
    @DisplayName("one organization can own many stores")
    void organizationOwnsManyStores() {
        Organization owner = organizationRepository.save(new Organization("INT-STORE-MANY Acme"));
        storeRepository.save(store(owner, "INT-STORE-MANY Downtown"));
        storeRepository.save(store(owner, "INT-STORE-MANY Airport"));
        storeRepository.save(store(owner, "INT-STORE-MANY Suburb"));

        List<Store> owned = storeRepository.findByOrganizationId(owner.getId());

        assertThat(owned).hasSize(3)
                .extracting(Store::getName)
                .contains("INT-STORE-MANY Downtown", "INT-STORE-MANY Airport", "INT-STORE-MANY Suburb");

        // Cleanup
        storeRepository.deleteAll(owned);
        organizationRepository.deleteById(owner.getId());
    }

    @Test
    @DisplayName("a store can be re-parented to a different organization")
    void storeCanBeReparented() {
        Organization acme = organizationRepository.save(new Organization("INT-STORE-REPA Acme"));
        Organization globex = organizationRepository.save(new Organization("INT-STORE-REPA Globex"));
        Store downtown = storeRepository.save(store(acme, "INT-STORE-REPA Downtown"));

        downtown.setOrganization(globex);
        storeRepository.saveAndFlush(downtown);

        Store reloaded = storeRepository.findById(downtown.getId()).orElseThrow();
        assertThat(reloaded.getOrganization().getId()).isEqualTo(globex.getId());
        assertThat(storeRepository.findByOrganizationId(acme.getId())).isEmpty();
        assertThat(storeRepository.findByOrganizationId(globex.getId()))
                .extracting(Store::getName)
                .contains("INT-STORE-REPA Downtown");

        // Cleanup
        storeRepository.delete(reloaded);
        organizationRepository.delete(acme);
        organizationRepository.delete(globex);
    }

    @Test
    @DisplayName("deleting a store does not affect its owning organization")
    void deletingStoreLeavesOrganizationIntact() {
        Organization owner = organizationRepository.save(new Organization("INT-STORE-DELS Acme"));
        Store doomed = storeRepository.save(store(owner, "INT-STORE-DELS Downtown"));
        Store keeper = storeRepository.save(store(owner, "INT-STORE-DELS Airport"));

        storeRepository.deleteById(doomed.getId());

        assertThat(storeRepository.findById(doomed.getId())).isEmpty();
        assertThat(organizationRepository.findById(owner.getId())).isPresent();
        assertThat(storeRepository.findByOrganizationId(owner.getId()))
                .extracting(Store::getId)
                .containsExactly(keeper.getId());

        // Cleanup
        storeRepository.delete(keeper);
        organizationRepository.deleteById(owner.getId());
    }

    @Test
    @DisplayName("countByOrganizationId reflects the number of stores owned by the organization")
    void countByOrganizationIdReturnsCurrentOwnership() {
        Organization owner = organizationRepository.save(new Organization("INT-STORE-CNT Acme"));
        Organization other = organizationRepository.save(new Organization("INT-STORE-CNT Globex"));
        storeRepository.save(store(owner, "INT-STORE-CNT A"));
        storeRepository.save(store(owner, "INT-STORE-CNT B"));
        storeRepository.save(store(other, "INT-STORE-CNT C"));

        assertThat(storeRepository.countByOrganizationId(owner.getId())).isEqualTo(2L);
        assertThat(storeRepository.countByOrganizationId(other.getId())).isEqualTo(1L);

        // Cleanup
        storeRepository.deleteAll(storeRepository.findByOrganizationId(owner.getId()));
        storeRepository.deleteAll(storeRepository.findByOrganizationId(other.getId()));
        organizationRepository.delete(owner);
        organizationRepository.delete(other);
    }
}
