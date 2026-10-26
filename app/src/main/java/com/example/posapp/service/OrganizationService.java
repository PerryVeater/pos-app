package com.example.posapp.service;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.example.posapp.entity.EmployeeGroup;
import com.example.posapp.entity.Organization;
import com.example.posapp.entity.Store;
import com.example.posapp.exception.BrandScopeException;
import com.example.posapp.exception.OrganizationNotFoundException;
import com.example.posapp.exception.OrganizationValidationException;
import com.example.posapp.repository.EmployeeGroupRepository;
import com.example.posapp.repository.OrganizationRepository;
import com.example.posapp.repository.StoreRepository;

/**
 * Service layer for {@link Organization} entities and their owned stores.
 * <p>
 * Encapsulates the tenancy business rules: names are required and unique;
 * an organization cannot be deleted while it still owns any store,
 * employee group, or employee, so callers must remove, re-parent, or
 * delete those first. Stores, employee groups, and employees themselves
 * are managed by {@link StoreService}, {@link EmployeeGroupService}, and
 * {@link EmployeeService}; this service exposes the read side of the
 * ownership relationships.
 * </p>
 * <p>
 * Every method is confined to one organization - the caller's brand. A
 * brand does not create or destroy brands, so those two operations are
 * refused outright rather than scoped: see {@link #createOrganization} and
 * {@link #deleteOrganization}.
 * </p>
 */
@Service
public class OrganizationService {

    private final OrganizationRepository organizationRepo;
    private final StoreRepository storeRepo;
    private final EmployeeGroupRepository employeeGroupRepo;

    /**
     * Constructor for OrganizationService.
     * @param organizationRepo the repository for organizations
     * @param storeRepo the repository for stores, used by the store listing
     * @param employeeGroupRepo the repository for employee groups, used by
     *        the root employee group listing
     */
    public OrganizationService(OrganizationRepository organizationRepo,
                               StoreRepository storeRepo,
                               EmployeeGroupRepository employeeGroupRepo) {
        this.organizationRepo = organizationRepo;
        this.storeRepo = storeRepo;
        this.employeeGroupRepo = employeeGroupRepo;
    }

    /**
     * Refuse to create an organization.
     * <p>
     * A brand administrator acts inside one brand, and a brand is not
     * something a brand can conjure: a new organization would be a second
     * tenant the caller has no relationship with. The rule this suspends is
     * a name-uniqueness check plus an insert, kept here in prose for the
     * platform-level role that will own it - the realm export grants no such
     * role today, and the first brand is created by a seeded migration.
     * </p>
     * @param organization the transient organization the caller asked to save
     * @param brandId the organization the caller may act within
     * @return never returns; the operation is outside every brand
     * @throws BrandScopeException always
     */
    public Organization createOrganization(Organization organization, Long brandId) {
        throw new BrandScopeException("creating an organization");
    }

    /**
     * Update an existing organization. The name is validated for uniqueness
     * against other organizations; keeping the organization's own name is
     * allowed. The organization must be the caller's brand, so another
     * brand's organization is reported as missing.
     * @param id the organization ID
     * @param updated the replacement values
     * @param brandId the organization the caller may act within
     * @return the updated organization
     * @throws OrganizationNotFoundException if no organization exists with
     *         the ID in the brand
     * @throws OrganizationValidationException if the name is blank or used
     *         by another organization
     */
    public Organization updateOrganization(Long id, Organization updated, Long brandId) {
        validateName(updated.getName());
        if (!id.equals(brandId)) {
            throw new OrganizationNotFoundException(id);
        }
        return organizationRepo.findById(id)
                .map(existing -> {
                    if (organizationRepo.existsByNameAndIdNot(updated.getName(), id)) {
                        throw new OrganizationValidationException(
                                "Organization name already exists: " + updated.getName());
                    }
                    existing.setName(updated.getName());
                    existing.setActive(updated.isActive());
                    return organizationRepo.save(existing);
                })
                .orElseThrow(() -> new OrganizationNotFoundException(id));
    }

    /**
     * Refuse to delete an organization.
     * <p>
     * Deleting the brand a caller is authenticated inside would destroy the
     * tenant every one of its rows depends on. The rule this suspends - and
     * that a platform-level role will need back - is a guard against
     * deleting an organization that still owns stores, employee groups or
     * employees, so the caller has to detach those first.
     * </p>
     * @param id the organization the caller asked to delete
     * @param brandId the organization the caller may act within
     * @throws BrandScopeException always
     */
    public void deleteOrganization(Long id, Long brandId) {
        throw new BrandScopeException("deleting an organization");
    }

    /**
     * Retrieve the caller's brand. The back office is scoped to one
     * organization, so this list holds exactly one entry whenever the
     * brand row is still readable.
     * @param brandId the organization the caller may act within
     * @return a single-element list, or an empty list if the brand vanished
     */
    public List<Organization> getAllOrganizations(Long brandId) {
        return organizationRepo.findById(brandId).map(List::of).orElseGet(List::of);
    }

    /**
     * Retrieve an organization by ID, within the caller's brand.
     * @param id the organization ID
     * @param brandId the organization the caller may act within
     * @return the Optional containing the organization if it is the brand
     * @throws IllegalArgumentException if {@code id} is null
     */
    public Optional<Organization> getOrganizationById(Long id, Long brandId) {
        if (id == null) {
            throw new IllegalArgumentException("ID cannot be null");
        }
        if (!id.equals(brandId)) {
            return Optional.empty();
        }
        return organizationRepo.findById(id);
    }

    /**
     * Return the stores owned by the caller's brand.
     * @param organizationId the organization ID named in the path
     * @param brandId the organization the caller may act within
     * @return the stores owned by the organization (empty when it has none)
     * @throws OrganizationNotFoundException if the named organization is
     *         not the caller's brand
     */
    public List<Store> listStores(Long organizationId, Long brandId) {
        if (!organizationId.equals(brandId)) {
            throw new OrganizationNotFoundException(organizationId);
        }
        return storeRepo.findByOrganizationId(organizationId);
    }

    /**
     * Return the root employee groups owned by the caller's brand, i.e. the
     * groups that have no parent group. Child groups are reachable through
     * {@code GET /api/v1/employee-groups/{id}/children}.
     * @param organizationId the organization ID named in the path
     * @param brandId the organization the caller may act within
     * @return the root employee groups (empty when the brand has none)
     * @throws OrganizationNotFoundException if the named organization is
     *         not the caller's brand
     */
    public List<EmployeeGroup> listRootEmployeeGroups(Long organizationId, Long brandId) {
        if (!organizationId.equals(brandId)) {
            throw new OrganizationNotFoundException(organizationId);
        }
        return employeeGroupRepo.findByOrganizationIdAndParentIsNull(organizationId);
    }

    /**
     * Reject blank or missing names for any write operation, mirroring the
     * API boundary rules.
     * @param name the name to validate
     * @throws OrganizationValidationException if the name is missing or blank
     */
    private static void validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new OrganizationValidationException("Name must be provided");
        }
    }
}
