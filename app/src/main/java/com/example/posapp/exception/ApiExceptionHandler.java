package com.example.posapp.exception;

import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Global exception handler that maps service-layer exceptions and request
 * validation failures to HTTP status codes, so client errors are reported as
 * 4xx responses carrying RFC 9457 problem details instead of escaping the
 * DispatcherServlet and surfacing as server errors.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    /**
     * Map a missing menu item to HTTP 404 Not Found.
     * @param ex the exception thrown by the service layer
     * @return a problem detail describing the error
     */
    @ExceptionHandler(MenuItemNotFoundException.class)
    public ProblemDetail handleMenuItemNotFound(MenuItemNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "Menu item not found", ex.getMessage());
    }

    /**
     * Map a missing order to HTTP 404 Not Found.
     * @param ex the exception thrown by the service layer
     * @return a problem detail describing the error
     */
    @ExceptionHandler(OrderNotFoundException.class)
    public ProblemDetail handleOrderNotFound(OrderNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "Order not found", ex.getMessage());
    }

    /**
     * Map an order validation failure to HTTP 400 Bad Request.
     * @param ex the exception thrown by the service layer
     * @return a problem detail describing the error
     */
    @ExceptionHandler(OrderValidationException.class)
    public ProblemDetail handleOrderValidation(OrderValidationException ex) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid order", ex.getMessage());
    }

    /**
     * Map a missing payment to HTTP 404 Not Found.
     * @param ex the exception thrown by the service layer
     * @return a problem detail describing the error
     */
    @ExceptionHandler(PaymentNotFoundException.class)
    public ProblemDetail handlePaymentNotFound(PaymentNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "Payment not found", ex.getMessage());
    }

    /**
     * Map a missing menu to HTTP 404 Not Found.
     * @param ex the exception thrown by the service layer
     * @return a problem detail describing the error
     */
    @ExceptionHandler(MenuNotFoundException.class)
    public ProblemDetail handleMenuNotFound(MenuNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "Menu not found", ex.getMessage());
    }

    /**
     * Map a missing menu group (or a missing assignment) to HTTP 404 Not Found.
     * @param ex the exception thrown by the service layer
     * @return a problem detail describing the error
     */
    @ExceptionHandler(MenuGroupNotFoundException.class)
    public ProblemDetail handleMenuGroupNotFound(MenuGroupNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "Menu group not found", ex.getMessage());
    }

    /**
     * Map a menu / menu group business rule violation to HTTP 400 Bad Request.
     * @param ex the exception thrown by the service layer
     * @return a problem detail describing the error
     */
    @ExceptionHandler(MenuValidationException.class)
    public ProblemDetail handleMenuValidation(MenuValidationException ex) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid menu", ex.getMessage());
    }

    /**
     * Map a missing modifier group to HTTP 404 Not Found.
     * @param ex the exception thrown by the service layer
     * @return a problem detail describing the error
     */
    @ExceptionHandler(ModifierGroupNotFoundException.class)
    public ProblemDetail handleModifierGroupNotFound(ModifierGroupNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "Modifier group not found", ex.getMessage());
    }

    /**
     * Map a missing modifier (or a missing group ↔ modifier assignment) to
     * HTTP 404 Not Found.
     * @param ex the exception thrown by the service layer
     * @return a problem detail describing the error
     */
    @ExceptionHandler(ModifierNotFoundException.class)
    public ProblemDetail handleModifierNotFound(ModifierNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "Modifier not found", ex.getMessage());
    }

    /**
     * Map a modifier / modifier group business rule violation (blank name,
     * duplicate group name, invalid selection policy, deletion blocked by an
     * assignment, duplicate assignment pair) to HTTP 400 Bad Request.
     * @param ex the exception thrown by the service layer
     * @return a problem detail describing the error
     */
    @ExceptionHandler(ModifierValidationException.class)
    public ProblemDetail handleModifierValidation(ModifierValidationException ex) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid modifier", ex.getMessage());
    }

    /**
     * Map a payment validation failure to HTTP 400 Bad Request.
     * @param ex the exception thrown by the service layer
     * @return a problem detail describing the error
     */
    @ExceptionHandler(PaymentValidationException.class)
    public ProblemDetail handlePaymentValidation(PaymentValidationException ex) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid payment", ex.getMessage());
    }

    /**
     * Map a missing organization (either the aggregate root itself or a
     * store's required owning organization reference) to HTTP 404 Not Found.
     * @param ex the exception thrown by the service layer
     * @return a problem detail describing the error
     */
    @ExceptionHandler(OrganizationNotFoundException.class)
    public ProblemDetail handleOrganizationNotFound(OrganizationNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "Organization not found", ex.getMessage());
    }

    /**
     * Map a missing store to HTTP 404 Not Found.
     * @param ex the exception thrown by the service layer
     * @return a problem detail describing the error
     */
    @ExceptionHandler(StoreNotFoundException.class)
    public ProblemDetail handleStoreNotFound(StoreNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "Store not found", ex.getMessage());
    }

    /**
     * Map an organization or store business rule violation (blank name,
     * duplicate name, deletion blocked by attached stores) to HTTP 400 Bad
     * Request.
     * @param ex the exception thrown by the service layer
     * @return a problem detail describing the error
     */
    @ExceptionHandler(OrganizationValidationException.class)
    public ProblemDetail handleOrganizationValidation(OrganizationValidationException ex) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid organization", ex.getMessage());
    }

    /**
     * Map a missing employee group (the aggregate root itself, a group's
     * requested parent, or the parent whose children were requested) to
     * HTTP 404 Not Found.
     * @param ex the exception thrown by the service layer
     * @return a problem detail describing the error
     */
    @ExceptionHandler(EmployeeGroupNotFoundException.class)
    public ProblemDetail handleEmployeeGroupNotFound(EmployeeGroupNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "Employee group not found", ex.getMessage());
    }

    /**
     * Map an employee group business rule violation (blank name,
     * cross-organization parent, self-parenting, circular hierarchy,
     * deletion blocked by child groups) to HTTP 400 Bad Request.
     * @param ex the exception thrown by the service layer
     * @return a problem detail describing the error
     */
    @ExceptionHandler(EmployeeGroupValidationException.class)
    public ProblemDetail handleEmployeeGroupValidation(EmployeeGroupValidationException ex) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid employee group", ex.getMessage());
    }

    /**
     * Map a missing employee to HTTP 404 Not Found.
     * @param ex the exception thrown by the service layer
     * @return a problem detail describing the error
     */
    @ExceptionHandler(EmployeeNotFoundException.class)
    public ProblemDetail handleEmployeeNotFound(EmployeeNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "Employee not found", ex.getMessage());
    }

    /**
     * Map an employee business rule violation (blank name, email already
     * used by another employee in the same organization) to HTTP 400 Bad
     * Request.
     * @param ex the exception thrown by the service layer
     * @return a problem detail describing the error
     */
    @ExceptionHandler(EmployeeValidationException.class)
    public ProblemDetail handleEmployeeValidation(EmployeeValidationException ex) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid employee", ex.getMessage());
    }

    /**
     * Map an invalid argument (e.g. a negative price) to HTTP 400 Bad Request.
     * @param ex the exception thrown by the service layer
     * @return a problem detail describing the error
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleIllegalArgument(IllegalArgumentException ex) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid request", ex.getMessage());
    }

    /**
     * Map a database integrity violation (e.g. a duplicate SKU that slipped
     * past the service-level check in a concurrent request) to HTTP 400 Bad
     * Request, so constraint enforcement surfaces as a client error instead
     * of a generic server error.
     * @param ex the exception raised by the persistence layer
     * @return a problem detail describing the conflict
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        return problem(HttpStatus.BAD_REQUEST, "Conflicting data",
                "The request violates a data constraint: " + ex.getMostSpecificCause().getMessage());
    }

    /**
     * Map request-body validation failures to HTTP 400, joining the field
     * errors into the problem detail.
     * @param ex the exception raised by validating a request body
     * @return a problem detail listing the constraint violations
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidationFailure(MethodArgumentNotValidException ex) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .sorted()
                .collect(Collectors.joining("; "));
        return problem(HttpStatus.BAD_REQUEST, "Validation failed", detail);
    }

    /**
     * Map unreadable or malformed request bodies to HTTP 400 Bad Request.
     * This covers cases such as invalid enum values, malformed JSON, or
     * type mismatches during deserialization.
     * @param ex the exception raised during request body deserialization
     * @return a problem detail describing the parse error
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail handleMessageNotReadable(HttpMessageNotReadableException ex) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid request body",
                "Failed to parse request body: " + ex.getMostSpecificCause().getMessage());
    }

    /**
     * Build a problem detail with the given status, title, and detail.
     * @param status the HTTP status for the problem
     * @param title a short, human-readable summary
     * @param detail a human-readable explanation of this occurrence
     * @return the populated problem detail
     */
    private static ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        return problem;
    }
}
