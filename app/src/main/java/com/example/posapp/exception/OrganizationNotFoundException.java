package com.example.posapp.exception;

/**
 * Thrown when a requested organization is not found.
 * <p>
 * Used by the organization and store services and controllers to signal
 * that an organization with the given ID does not exist, either on the
 * aggregate root itself or as the required owner of a store.
 * </p>
 */
public class OrganizationNotFoundException extends RuntimeException {

    /**
     * Constructor with the organization ID.
     * @param id the ID of the organization that was not found
     */
    public OrganizationNotFoundException(Long id) {
        super("Organization not found: " + id);
    }
}
