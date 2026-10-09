package com.example.posapp.exception;

/**
 * Thrown when a requested employee group is not found.
 * <p>
 * Used by the employee group service and controller to signal that a
 * group with the given ID does not exist, either on the aggregate root
 * itself, as the requested parent of another group, or as the parent
 * whose children were requested. Mapped to HTTP 404 by the global
 * exception handler.
 * </p>
 */
public class EmployeeGroupNotFoundException extends RuntimeException {

    /**
     * Constructor with the employee group ID.
     * @param id the ID of the employee group that was not found
     */
    public EmployeeGroupNotFoundException(Long id) {
        super("Employee group not found: " + id);
    }
}
