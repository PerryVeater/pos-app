package com.example.posapp.service;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.example.posapp.entity.EmployeeGroup;
import com.example.posapp.entity.Organization;
import com.example.posapp.exception.EmployeeGroupNotFoundException;
import com.example.posapp.exception.EmployeeGroupValidationException;
import com.example.posapp.exception.OrganizationNotFoundException;
import com.example.posapp.repository.EmployeeGroupRepository;
import com.example.posapp.repository.OrganizationRepository;

/**
 * Service layer for {@link EmployeeGroup} entities.
 * <p>
 * Encapsulates the employee group hierarchy rules: the name is required;
 * every group must reference an existing owning {@link Organization}; a
 * group's optional parent must reference an existing group in the same
 * organization; a group cannot be its own parent and re-parenting must
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

    /**
     * Constructor for EmployeeGroupService.
     * @param employeeGroupRepo the repository for employee groups
     * @param organizationRepo the repository for organizations, used to
     *        resolve the owning organization on create
     */
    public EmployeeGroupService(EmployeeGroupRepository employeeGroupRepo,
                                OrganizationRepository organizationRepo) {
        this.employeeGroupRepo = employeeGroupRepo;
        this.organizationRepo = organizationRepo;
    }

    /**
     * Create a new employee group after validating its name and resolving
     * the owning organization and optional parent group.
     * @param group the transient group to save (its organization and
     *        parent fields are overwritten by the resolved entities)
     * @param organizationId the ID of the owning organization, required
     * @param parentId the optional parent group ID (null creates a root)
     * @return the saved group
     * @throws EmployeeGroupValidationException if the name is blank, the
     *         parent belongs to a different organization, or the parent
     *         is the group itself
     * @throws OrganizationNotFoundException if the organization ID is
     *         missing or does not exist
     * @throws EmployeeGroupNotFoundException if the parent ID does not exist
     */
    public EmployeeGroup createEmployeeGroup(EmployeeGroup group, Long organizationId, Long parentId) {
        Organization organization = resolveOrganization(organizationId);
        validateName(group.getName());
        EmployeeGroup parent = resolveParent(parentId, organization, null);
        group.setOrganization(organization);
        group.setParent(parent);
        return employeeGroupRepo.save(group);
    }

    /**
     * Update an existing employee group's name, active flag, and parent.
     * The owning organization cannot change: re-parenting is validated
     * against the group's current organization, so the parent must belong
     * to the same organization and must not create a cycle.
     * @param id the employee group ID
     * @param updated the replacement values
     * @param parentId the optional parent group ID (null makes the group a root)
     * @return the updated group
     * @throws EmployeeGroupNotFoundException if no group exists with the
     *         ID, or the requested parent does not exist
     * @throws EmployeeGroupValidationException if the name is blank, the
     *         parent belongs to a different organization, the parent is
     *         the group itself, or the re-parenting would create a cycle
     */
    public EmployeeGroup updateEmployeeGroup(Long id, EmployeeGroup updated, Long parentId) {
        validateName(updated.getName());
        return employeeGroupRepo.findById(id)
                .map(existing -> {
                    EmployeeGroup parent = resolveParent(parentId, existing.getOrganization(), id);
                    existing.setName(updated.getName());
                    existing.setActive(updated.isActive());
                    existing.setParent(parent);
                    return employeeGroupRepo.save(existing);
                })
                .orElseThrow(() -> new EmployeeGroupNotFoundException(id));
    }

    /**
     * Delete an employee group by ID. Refuses to delete a group that still
     * has child groups so callers must remove or re-parent the children
     * first.
     * @param id the employee group ID
     * @throws EmployeeGroupNotFoundException if no group exists with the ID
     * @throws EmployeeGroupValidationException if the group still has child groups
     */
    public void deleteEmployeeGroup(Long id) {
        if (!employeeGroupRepo.existsById(id)) {
            throw new EmployeeGroupNotFoundException(id);
        }
        if (employeeGroupRepo.countByParentId(id) > 0) {
            throw new EmployeeGroupValidationException(
                    "Cannot delete employee group still having child groups: " + id);
        }
        employeeGroupRepo.deleteById(id);
    }

    /**
     * Retrieve every employee group.
     * @return the list of employee groups
     */
    public List<EmployeeGroup> getAllEmployeeGroups() {
        return employeeGroupRepo.findAll();
    }

    /**
     * Retrieve an employee group by ID.
     * @param id the employee group ID
     * @return the Optional containing the group if found
     * @throws IllegalArgumentException if {@code id} is null
     */
    public Optional<EmployeeGroup> getEmployeeGroupById(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("ID cannot be null");
        }
        return employeeGroupRepo.findById(id);
    }

    /**
     * Return the direct child groups of an employee group.
     * @param parentId the parent group ID
     * @return the child groups (empty when the parent has none)
     * @throws EmployeeGroupNotFoundException if no group exists with the ID
     */
    public List<EmployeeGroup> listChildGroups(Long parentId) {
        if (!employeeGroupRepo.existsById(parentId)) {
            throw new EmployeeGroupNotFoundException(parentId);
        }
        return employeeGroupRepo.findByParentId(parentId);
    }

    /**
     * Load the referenced organization or fail with
     * {@link OrganizationNotFoundException}. A null ID is treated the same
     * way as a missing organization so callers cannot bypass the FK
     * requirement.
     * @param organizationId the ID to resolve (may be null)
     * @return the persisted organization
     */
    private Organization resolveOrganization(Long organizationId) {
        if (organizationId == null) {
            throw new OrganizationNotFoundException(null);
        }
        return organizationRepo.findById(organizationId)
                .orElseThrow(() -> new OrganizationNotFoundException(organizationId));
    }

    /**
     * Resolve the optional parent group for a create or update operation.
     * A null {@code parentId} means "no parent" (a root group). A
     * non-null ID must reference an existing group in the same
     * organization, must not be the group being updated itself, and must
     * not be a descendant of it.
     * @param parentId the requested parent group ID (may be null)
     * @param organization the organization the child group belongs to
     * @param updatingId the ID of the group being updated, or {@code null}
     *        on create
     * @return the resolved parent group, or {@code null} for a root
     * @throws EmployeeGroupNotFoundException if the parent does not exist
     * @throws EmployeeGroupValidationException if the parent is the group
     *         itself, belongs to a different organization, or would
     *         introduce a cycle
     */
    private EmployeeGroup resolveParent(Long parentId, Organization organization, Long updatingId) {
        if (parentId == null) {
            return null;
        }
        if (parentId.equals(updatingId)) {
            throw new EmployeeGroupValidationException(
                    "Employee group cannot be its own parent: " + parentId);
        }
        EmployeeGroup parent = employeeGroupRepo.findById(parentId)
                .orElseThrow(() -> new EmployeeGroupNotFoundException(parentId));
        if (!Objects.equals(parent.getOrganization().getId(), organization.getId())) {
            throw new EmployeeGroupValidationException(
                    "Parent and child employee groups must belong to the same organization: parentId="
                            + parentId + ", organizationId=" + organization.getId());
        }
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
