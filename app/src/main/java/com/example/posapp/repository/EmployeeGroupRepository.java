package com.example.posapp.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
     * Page through the direct child groups of the given group, ordered by
     * primary key. The foundation carries no per-sibling display attribute
     * yet, so the key order is the only one that pages without gaps.
     * @param parentId the parent group ID
     * @param pageable the page to serve
     * @return the requested page of child groups
     */
    Page<EmployeeGroup> findByParentId(Long parentId, Pageable pageable);

    /**
     * Page through the root groups owned by the given organization, i.e.
     * the groups that have no parent group.
     * @param organizationId the owning organization ID
     * @param pageable the page to serve
     * @return the requested page of root groups
     */
    Page<EmployeeGroup> findByOrganizationIdAndParentIsNull(Long organizationId,
                                                            Pageable pageable);

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

    /**
     * Resolve a group only when it belongs to the given organization. The
     * brand is part of the predicate so another brand's group is
     * indistinguishable from a group that does not exist.
     * @param id the group ID
     * @param organizationId the brand the caller may act within
     * @return the group, or empty when it does not exist in that brand
     */
    Optional<EmployeeGroup> findByIdAndOrganizationId(Long id, Long organizationId);

    /**
     * Page through every group owned by the given organization, root and
     * nested alike, for the brand-wide listing.
     * @param organizationId the brand the caller may act within
     * @param pageable the page to serve
     * @return the requested page of that organization's groups
     */
    Page<EmployeeGroup> findByOrganizationId(Long organizationId, Pageable pageable);
}
