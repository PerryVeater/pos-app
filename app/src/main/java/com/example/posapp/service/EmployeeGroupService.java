package com.example.posapp.service;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.example.posapp.entity.EmployeeGroup;
import com.example.posapp.entity.Organization;
import com.example.posapp.exception.EmployeeGroupNotFoundException;
import com.example.posapp.exception.EmployeeGroupValidationException;
import com.example.posapp.exception.OrganizationNotFoundException;
import com.example.posapp.repository.EmployeeGroupMembershipRepository;
import com.example.posapp.repository.EmployeeGroupRepository;
import com.example.posapp.repository.OrganizationRepository;

/**
 * Service layer for {@link EmployeeGroup} entities.
 * <p>
 * Encapsulates the employee group hierarchy rules: the name is required;
 * every group must reference the caller's brand as its owning
 * {@link Organization}; a group's optional parent must reference an
 * existing group in the same brand, which the lookup enforces by scoping to
 * the brand rather than by comparing organizations afterwards, so a parent
 * in another brand is reported exactly like a parent that does not exist; a
 * group cannot be its own parent and re-parenting must
 * not introduce an indirect cycle; and a group that still has child
 * groups cannot be deleted, so callers must remove or re-parent the
 * children first. The owning organization is fixed at creation because a
 * move between organizations would break the same-organization rule
 * linking a group to its parent and children.
 * </p>
 */
@Service
public class EmployeeGroupService {

    private final EmployeeGroupRepository employeeGroupRepo;
    private final OrganizationRepository organizationRepo;
    private final EmployeeGroupMembershipRepository membershipRepo;

    /**
     * Constructor for EmployeeGroupService.
     * @param employeeGroupRepo the repository for employee groups
     * @param organizationRepo the repository for organizations, used to
     *        resolve the owning organization on create
     * @param membershipRepo the repository for employee group memberships,
     *        used by the delete guard
     */
    public EmployeeGroupService(EmployeeGroupRepository employeeGroupRepo,
                                OrganizationRepository organizationRepo,
                                EmployeeGroupMembershipRepository membershipRepo) {
        this.employeeGroupRepo = employeeGroupRepo;
        this.organizationRepo = organizationRepo;
        this.membershipRepo = membershipRepo;
    }

    /**
     * Create a new employee group after validating its name and resolving
     * the owning organization and optional parent group.
     * @param group the transient group to save (its organization and
     *        parent fields are overwritten by the resolved entities)
     * @param organizationId the ID of the owning organization, required; it
     *        must be the caller's brand
     * @param parentId the optional parent group ID (null creates a root)
     * @param brandId the organization the caller may act within
     * @return the saved group
     * @throws EmployeeGroupValidationException if the name is blank or the
     *         parent is the group itself
     * @throws OrganizationNotFoundException if the organization ID is
     *         missing, does not exist, or lies outside the brand
     * @throws EmployeeGroupNotFoundException if the parent ID does not
     *         exist in the brand
     */
    public EmployeeGroup createEmployeeGroup(EmployeeGroup group,
                                             Long organizationId,
                                             Long parentId,
                                             Long brandId) {
        Organization organization = resolveOrganization(organizationId, brandId);
        validateName(group.getName());
        EmployeeGroup parent = resolveParent(parentId, brandId, null);
        group.setOrganization(organization);
        group.setParent(parent);
        return employeeGroupRepo.save(group);
    }

    /**
     * Update an existing employee group's name, active flag, and parent.
     * The group must belong to the caller's brand, and the owning
     * organization cannot change: re-parenting is validated against the
     * group's current organization, so the parent must belong to the same
     * brand and must not create a cycle.
     * @param id the employee group ID
     * @param updated the replacement values
     * @param parentId the optional parent group ID (null makes the group a root)
     * @param brandId the organization the caller may act within
     * @return the updated group
     * @throws EmployeeGroupNotFoundException if no group exists with the
     *         ID in the brand, or the requested parent does not exist there
     * @throws EmployeeGroupValidationException if the name is blank, the
     *         parent is the group itself, or the re-parenting would create
     *         a cycle
     */
    public EmployeeGroup updateEmployeeGroup(Long id, EmployeeGroup updated, Long parentId, Long brandId) {
        validateName(updated.getName());
        return employeeGroupRepo.findByIdAndOrganizationId(id, brandId)
                .map(existing -> {
                    EmployeeGroup parent = resolveParent(parentId, brandId, id);
                    existing.setName(updated.getName());
                    existing.setActive(updated.isActive());
                    existing.setParent(parent);
                    return employeeGroupRepo.save(existing);
                })
                .orElseThrow(() -> new EmployeeGroupNotFoundException(id));
    }

    /**
     * Delete an employee group by ID. Refuses to delete a group that still
     * has child groups or attached employee memberships so callers must
     * remove the children and detach the members first.
     * @param id the employee group ID
     * @param brandId the organization the caller may act within
     * @throws EmployeeGroupNotFoundException if no group exists with the ID
     *         in the brand
     * @throws EmployeeGroupValidationException if the group still has child groups
     *         or employee members
     */
    public void deleteEmployeeGroup(Long id, Long brandId) {
        if (employeeGroupRepo.findByIdAndOrganizationId(id, brandId).isEmpty()) {
            throw new EmployeeGroupNotFoundException(id);
        }
        if (employeeGroupRepo.countByParentId(id) > 0) {
            throw new EmployeeGroupValidationException(
                    "Cannot delete employee group still having child groups: " + id);
        }
        if (membershipRepo.countByEmployeeGroupId(id) > 0) {
            throw new EmployeeGroupValidationException(
                    "Cannot delete employee group still having employee members: " + id);
        }
        employeeGroupRepo.deleteById(id);
    }

    /**
     * Retrieve every employee group in the caller's brand.
     * @param brandId the organization the caller may act within
     * @return the employee groups of that organization
     */
    public List<EmployeeGroup> getAllEmployeeGroups(Long brandId) {
        return employeeGroupRepo.findByOrganizationId(brandId);
    }

    /**
     * Retrieve an employee group by ID, within the caller's brand.
     * @param id the employee group ID
     * @param brandId the organization the caller may act within
     * @return the Optional containing the group if found in that brand
     * @throws IllegalArgumentException if {@code id} is null
     */
    public Optional<EmployeeGroup> getEmployeeGroupById(Long id, Long brandId) {
        if (id == null) {
            throw new IllegalArgumentException("ID cannot be null");
        }
        return employeeGroupRepo.findByIdAndOrganizationId(id, brandId);
    }

    /**
     * Return the direct child groups of an employee group, within the
     * caller's brand.
     * @param parentId the parent group ID
     * @param brandId the organization the caller may act within
     * @return the child groups (empty when the parent has none)
     * @throws EmployeeGroupNotFoundException if no group exists with the ID
     *         in the brand
     */
    public List<EmployeeGroup> listChildGroups(Long parentId, Long brandId) {
        if (employeeGroupRepo.findByIdAndOrganizationId(parentId, brandId).isEmpty()) {
            throw new EmployeeGroupNotFoundException(parentId);
        }
        return employeeGroupRepo.findByParentId(parentId);
    }

    /**
     * Load the referenced organization or fail with
     * {@link OrganizationNotFoundException}. A null ID is treated the same
     * way as a missing organization so callers cannot bypass the FK
     * requirement, and an ID outside the caller's brand is treated the same
     * way as a missing one so the endpoint reveals nothing about other
     * brands.
     * @param organizationId the ID to resolve (may be null)
     * @param brandId the organization the caller may act within
     * @return the persisted organization
     */
    private Organization resolveOrganization(Long organizationId, Long brandId) {
        if (organizationId == null || !organizationId.equals(brandId)) {
            throw new OrganizationNotFoundException(organizationId);
        }
        return organizationRepo.findById(organizationId)
                .orElseThrow(() -> new OrganizationNotFoundException(organizationId));
    }

    /**
     * Resolve the optional parent group for a create or update operation.
     * A null {@code parentId} means "no parent" (a root group). A
     * non-null ID must reference an existing group in the caller's brand,
     * must not be the group being updated itself, and must not be a
     * descendant of it. The lookup is scoped to the brand, so a parent
     * outside it surfaces as a missing parent.
     * @param parentId the requested parent group ID (may be null)
     * @param brandId the organization the caller may act within
     * @param updatingId the ID of the group being updated, or {@code null}
     *        on create
     * @return the resolved parent group, or {@code null} for a root
     * @throws EmployeeGroupNotFoundException if the parent does not exist
     *         in the brand
     * @throws EmployeeGroupValidationException if the parent is the group
     *         itself or would introduce a cycle
     */
    private EmployeeGroup resolveParent(Long parentId, Long brandId, Long updatingId) {
        if (parentId == null) {
            return null;
        }
        if (parentId.equals(updatingId)) {
            throw new EmployeeGroupValidationException(
                    "Employee group cannot be its own parent: " + parentId);
        }
        EmployeeGroup parent = employeeGroupRepo.findByIdAndOrganizationId(parentId, brandId)
                .orElseThrow(() -> new EmployeeGroupNotFoundException(parentId));
        if (updatingId != null) {
            validateNoCycle(parent, updatingId);
        }
        return parent;
    }

    /**
     * Walk up from the requested parent and reject the re-parenting when
     * the group being updated is found in the ancestor chain, which would
     * create a cycle. The visited set defensively stops the walk if
     * stored data already contains a cycle that does not involve the
     * group being updated.
     * @param parent the resolved parent group
     * @param updatingId the ID of the group being re-parented
     * @throws EmployeeGroupValidationException if {@code updatingId} is an
     *         ancestor of the requested parent
     */
    private static void validateNoCycle(EmployeeGroup parent, Long updatingId) {
        Set<Long> visited = new HashSet<>();
        EmployeeGroup current = parent;
        while (current != null) {
            if (updatingId.equals(current.getId())) {
                throw new EmployeeGroupValidationException(
                        "Circular employee group hierarchy: employee group " + updatingId
                                + " is an ancestor of the requested parent");
            }
            if (!visited.add(current.getId())) {
                return;
            }
            current = current.getParent();
        }
    }

    /**
     * Reject blank or missing names for any write operation, mirroring the
     * API boundary rules.
     * @param name the name to validate
     * @throws EmployeeGroupValidationException if the name is missing or blank
     */
    private static void validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new EmployeeGroupValidationException("Name must be provided");
        }
    }
}
