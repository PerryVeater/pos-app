package com.example.posapp.service;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.example.posapp.entity.EmployeeGroup;
import com.example.posapp.entity.Organization;
import com.example.posapp.entity.Store;
import com.example.posapp.exception.OrganizationNotFoundException;
import com.example.posapp.exception.OrganizationValidationException;
import com.example.posapp.repository.EmployeeGroupRepository;
import com.example.posapp.repository.EmployeeRepository;
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
 * ownership relationships and the delete guards.
 * </p>
 */
@Service
public class OrganizationService {

    private final OrganizationRepository organizationRepo;
    private final StoreRepository storeRepo;
    private final EmployeeGroupRepository employeeGroupRepo;
    private final EmployeeRepository employeeRepo;

    /**
     * Constructor for OrganizationService.
     * @param organizationRepo the repository for organizations
     * @param storeRepo the repository for stores, used by the delete guard
     *        and the store listing
     * @param employeeGroupRepo the repository for employee groups, used by
     *        the delete guard and the root employee group listing
     * @param employeeRepo the repository for employees, used by the delete
     *        guard
     */
    public OrganizationService(OrganizationRepository organizationRepo,
                               StoreRepository storeRepo,
                               EmployeeGroupRepository employeeGroupRepo,
                               EmployeeRepository employeeRepo) {
        this.organizationRepo = organizationRepo;
        this.storeRepo = storeRepo;
        this.employeeGroupRepo = employeeGroupRepo;
        this.employeeRepo = employeeRepo;
    }

    /**
     * Create a new organization after validating its name.
     * @param organization the transient organization to save
     * @return the saved organization
     * @throws OrganizationValidationException if the name is blank or already used
     */
    public Organization createOrganization(Organization organization) {
        validateName(organization.getName());
        if (organizationRepo.existsByName(organization.getName())) {
            throw new OrganizationValidationException(
                    "Organization name already exists: " + organization.getName());
        }
        return organizationRepo.save(organization);
    }

    /**
     * Update an existing organization. The name is validated for uniqueness
     * against other organizations; keeping the organization's own name is
     * allowed.
     * @param id the organization ID
     * @param updated the replacement values
     * @return the updated organization
     * @throws OrganizationNotFoundException if no organization exists with the ID
     * @throws OrganizationValidationException if the name is blank or used by another organization
     */
    public Organization updateOrganization(Long id, Organization updated) {
        validateName(updated.getName());
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
     * Delete an organization by ID. Refuses to delete an organization that
     * still owns any store, employee group, or employee so callers must
     * remove, re-parent, or delete those first.
     * @param id the organization ID
     * @throws OrganizationNotFoundException if no organization exists with the ID
     * @throws OrganizationValidationException if the organization still owns
     *         stores, employee groups, or employees
     */
    public void deleteOrganization(Long id) {
        if (!organizationRepo.existsById(id)) {
            throw new OrganizationNotFoundException(id);
        }
        if (storeRepo.countByOrganizationId(id) > 0) {
            throw new OrganizationValidationException(
                    "Cannot delete organization still owning stores: " + id);
        }
        if (employeeGroupRepo.countByOrganizationId(id) > 0) {
            throw new OrganizationValidationException(
                    "Cannot delete organization still owning employee groups: " + id);
        }
        if (employeeRepo.countByOrganizationId(id) > 0) {
            throw new OrganizationValidationException(
                    "Cannot delete organization still owning employees: " + id);
        }
        organizationRepo.deleteById(id);
    }

    /**
     * Retrieve every organization.
     * @return the list of organizations
     */
    public List<Organization> getAllOrganizations() {
        return organizationRepo.findAll();
    }

    /**
     * Retrieve an organization by ID.
     * @param id the organization ID
     * @return the Optional containing the organization if found
     * @throws IllegalArgumentException if {@code id} is null
     */
    public Optional<Organization> getOrganizationById(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("ID cannot be null");
        }
        return organizationRepo.findById(id);
    }

    /**
     * Return the stores owned by an organization.
     * @param organizationId the organization ID
     * @return the stores owned by the organization (empty when it has none)
     * @throws OrganizationNotFoundException if no organization exists with the ID
     */
    public List<Store> listStores(Long organizationId) {
        if (!organizationRepo.existsById(organizationId)) {
            throw new OrganizationNotFoundException(organizationId);
        }
        return storeRepo.findByOrganizationId(organizationId);
    }

    /**
     * Return the root employee groups owned by an organization, i.e. the
     * groups that have no parent group. Child groups are reachable through
     * {@code GET /api/v1/employee-groups/{id}/children}.
     * @param organizationId the organization ID
     * @return the root employee groups (empty when the organization has none)
     * @throws OrganizationNotFoundException if no organization exists with the ID
     */
    public List<EmployeeGroup> listRootEmployeeGroups(Long organizationId) {
        if (!organizationRepo.existsById(organizationId)) {
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
