package com.example.posapp.exception;

import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
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
     * Map a missing product to HTTP 404 Not Found.
     * @param ex the exception thrown by the service layer
     * @return a problem detail describing the error
     */
    @ExceptionHandler(ProductNotFoundException.class)
    public ProblemDetail handleProductNotFound(ProductNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "Product not found", ex.getMessage());
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
