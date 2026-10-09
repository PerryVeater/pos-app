package com.example.posapp.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.posapp.entity.EmployeeGroup;

/**
 * Repository interface for managing {@link EmployeeGroup} entities.
 * <p>
 * Extends {@code JpaRepository} so the standard CRUD operations are
 * available. Adds hierarchy and organization-scoped queries used by the
 * service layer for child and root listings, the delete guard that blocks
 * removing a group that still has children, and the organization delete
 * guard. Employee group names are not unique, so no name-based lookup is
 * exposed.
 * </p>
 */
public interface EmployeeGroupRepository extends JpaRepository<EmployeeGroup, Long> {

    /**
     * Find the direct child groups of the given group. Insertion order is
     * not guaranteed; the foundation carries no per-sibling display
     * attribute yet.
     * @param parentId the parent group ID
     * @return the child groups (possibly empty)
     */
    List<EmployeeGroup> findByParentId(Long parentId);

    /**
     * Find the root groups owned by the given organization, i.e. the
     * groups that have no parent group.
     * @param organizationId the owning organization ID
     * @return the root groups (possibly empty)
     */
    List<EmployeeGroup> findByOrganizationIdAndParentIsNull(Long organizationId);

    /**
     * Count the direct child groups of the given group. Used by the
     * employee group delete guard so a group with children is rejected at
     * the service layer instead of surfacing a raw FK violation.
     * @param parentId the parent group ID
     * @return the number of groups referencing the parent
     */
    long countByParentId(Long parentId);

    /**
     * Count the employee groups owned by the given organization. Used by
     * the Organization delete guard so an organization with attached
     * groups is rejected at the service layer instead of surfacing a raw
     * FK violation.
     * @param organizationId the owning organization ID
     * @return the number of groups referencing the organization
     */
    long countByOrganizationId(Long organizationId);
}
