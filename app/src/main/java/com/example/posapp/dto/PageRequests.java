package com.example.posapp.dto;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * The page a caller asked for, normalised to what the API will actually serve.
 * <p>
 * This is the one place the page-size policy lives, so a new list endpoint
 * cannot accidentally serve the whole table: {@code size} defaults to
 * {@value #DEFAULT_SIZE}, and anything above {@value #MAX_SIZE} is clamped
 * rather than refused. Clamping keeps an unreasonable request cheap without
 * adding a failure mode to query strings; because {@link PageResponse}
 * reports the effective size, a caller can always see what it received.
 * </p>
 * <p>
 * The sort is the primary key, ascending. It is the only ordering that is
 * unique, and therefore the only one that cannot drop or duplicate a row
 * across page boundaries when two rows share the sort value. Per-endpoint
 * ordering is a presentation concern that arrives with a caller that needs
 * it; correctness of paging does not wait for it.
 * </p>
 */
public record PageRequests(int page, int size) {

    /** Page size used when the caller does not ask for one. */
    public static final int DEFAULT_SIZE = 25;

    /** Hard ceiling on page size, whatever the caller asks for. */
    public static final int MAX_SIZE = 100;

    /**
     * Normalise the requested values into a serveable page.
     * <p>
     * A negative page is treated as the first page and a non-positive size as
     * {@value #DEFAULT_SIZE}; an oversized size is clamped to
     * {@value #MAX_SIZE}.
     * </p>
     * @param page the requested page
     * @param size the requested size
     */
    public PageRequests {
        page = Math.max(page, 0);
        size = size <= 0 ? DEFAULT_SIZE : Math.min(size, MAX_SIZE);
    }

    /**
     * Build from optional query parameters.
     * @param page the requested page, or null when absent
     * @param size the requested size, or null when absent
     * @return the normalised page
     */
    public static PageRequests of(Integer page, Integer size) {
        return new PageRequests(
                page == null ? 0 : page,
                size == null ? DEFAULT_SIZE : size);
    }

    /**
     * Convert to the Spring Data page request the repositories accept.
     * @return a page request over the primary key ascending
     */
    public Pageable toPageable() {
        return PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "id"));
    }
}
