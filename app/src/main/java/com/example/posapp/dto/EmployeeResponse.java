package com.example.posapp.dto;

import com.example.posapp.entity.Employee;

/**
 * API representation of an employee returned by the employee endpoints.
 * <p>
 * Flattens the owning organization to its ID so the HTTP contract stays
 * decoupled from the JPA object graph. The email is {@code null} when the
 * employee has none on file.
 * </p>
 *
 * @param id the employee ID
 * @param name the employee name
 * @param email the optional contact email, or null when unset
 * @param active whether the employee is currently in service
 * @param organizationId the owning organization ID
 */
public record EmployeeResponse(
        Long id,
        String name,
        String email,
        boolean active,
        Long organizationId) {

    /**
     * Map an {@link Employee} entity to its API representation.
     * @param employee the entity to map
     * @return the API representation
     */
    public static EmployeeResponse from(Employee employee) {
        return new EmployeeResponse(
                employee.getId(),
                employee.getName(),
                employee.getEmail(),
                employee.isActive(),
                employee.getOrganization() == null ? null : employee.getOrganization().getId());
    }
}
