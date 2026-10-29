package com.example.posapp.dto;

import java.util.List;
import java.util.function.Function;

import org.springframework.data.domain.Page;

/**
 * API representation of one page of a collection.
 * <p>
 * Every list endpoint answers with this envelope instead of a bare array, so
 * a caller can tell "that is all there is" from "that is all you were given".
 * The shape is ours rather than Spring Data's {@code Page} on purpose: a
 * serialized {@code Page} carries {@code pageable}, {@code sort},
 * {@code first}, {@code last} and {@code numberOfElements}, which is a
 * library's internal view rather than a contract the API chose, and it
 * changes with the library.
 * </p>
 * <p>
 * {@code size} reports the page size actually used, not the one requested:
 * {@link PageRequests} clamps an oversized request, and the caller needs to
 * see the effective value to reason about {@code totalPages}.
 * </p>
 *
 * @param <T> the type of the items in the page
 * @param content the items on this page, possibly empty
 * @param page the zero-based index of this page
 * @param size the effective page size
 * @param totalElements the number of items across every page
 * @param totalPages the number of pages at this size
 */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages) {

    /**
     * Map a page of entities to a page of API representations.
     * @param <E> the entity type
     * @param <R> the API representation type
     * @param source the page returned by the repository
     * @param mapper the entity-to-representation mapping
     * @return the page envelope carrying the mapped content
     */
    public static <E, R> PageResponse<R> from(Page<E> source,
                                              Function<? super E, R> mapper) {
        List<R> content = source.getContent().stream().map(mapper).toList();
        return new PageResponse<R>(
                content,
                source.getNumber(),
                source.getSize(),
                source.getTotalElements(),
                source.getTotalPages());
    }

    /**
     * Wrap a page whose elements need no further mapping.
     * @param <E> the element type
     * @param source the page to wrap
     * @return the page envelope
     */
    public static <E> PageResponse<E> from(Page<E> source) {
        return from(source, Function.identity());
    }

    /**
     * Map this page's content while keeping its position.
     * <p>
     * Used where the service pages a join row and the endpoint exposes the
     * row's other side, so the page metadata travels with the mapped content
     * instead of being rebuilt.
     * </p>
     * @param <R> the mapped element type
     * @param mapper the element mapping
     * @return a page envelope with the same position and the mapped content
     */
    public <R> PageResponse<R> map(Function<? super T, R> mapper) {
        List<R> content = this.content.stream().map(mapper).toList();
        return new PageResponse<R>(
                content,
                page,
                size,
                totalElements,
                totalPages);
    }
}
