package com.example.posapp.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Global exception handler that maps service-layer exceptions to HTTP status
 * codes, so client errors are reported as 4xx responses carrying RFC 9457
 * problem details instead of escaping the DispatcherServlet and surfacing as
 * server errors.
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
     * Map an invalid argument (e.g. a negative price) to HTTP 400 Bad Request.
     * @param ex the exception thrown by the service layer
     * @return a problem detail describing the error
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleIllegalArgument(IllegalArgumentException ex) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid request", ex.getMessage());
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
