package com.example.posapp.dto;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * Unit tests for the list-endpoint page contract: {@link PageRequests}
 * normalises what a caller asked for, and {@link PageResponse} reports what
 * was actually served.
 * <p>
 * These lock the policy every list endpoint shares, so a new endpoint cannot
 * quietly serve an unbounded result set: an absent size defaults to
 * {@value PageRequests#DEFAULT_SIZE}, an oversized one is clamped to
 * {@value PageRequests#MAX_SIZE} rather than refused, and a nonsense value
 * falls back instead of failing the request. The last test covers the
 * envelope's own promise - {@code size} is the effective size, so
 * {@code totalPages} can be derived from a clamped response.
 * </p>
 */
class PageRequestsTest {

    @Test
    @DisplayName("absent parameters default to the first page at the default size")
    void absentParametersUseDefaults() {
        PageRequests requests = PageRequests.of(null, null);

        assertThat(requests.page()).isZero();
        assertThat(requests.size()).isEqualTo(PageRequests.DEFAULT_SIZE);
    }

    @Test
    @DisplayName("an oversized size is clamped to the maximum, not rejected")
    void oversizedSizeIsClamped() {
        PageRequests requests = PageRequests.of(0, 5000);

        assertThat(requests.size()).isEqualTo(PageRequests.MAX_SIZE);
        assertThat(requests.page()).isZero();
    }

    @Test
    @DisplayName("a negative page is treated as the first page")
    void negativePageFallsBackToFirst() {
        PageRequests requests = PageRequests.of(-3, 10);

        assertThat(requests.page()).isZero();
        assertThat(requests.size()).isEqualTo(10);
    }

    @Test
    @DisplayName("a non-positive size falls back to the default")
    void nonPositiveSizeFallsBackToDefault() {
        assertThat(PageRequests.of(0, 0).size()).isEqualTo(PageRequests.DEFAULT_SIZE);
        assertThat(PageRequests.of(0, -5).size()).isEqualTo(PageRequests.DEFAULT_SIZE);
    }

    @Test
    @DisplayName("an in-range request is served as asked")
    void inRangeRequestIsPreserved() {
        PageRequests requests = PageRequests.of(2, 50);

        assertThat(requests.page()).isEqualTo(2);
        assertThat(requests.size()).isEqualTo(50);
    }

    @Test
    @DisplayName("the page request orders by primary key ascending")
    void pageableSortsByIdAscending() {
        Pageable pageable = PageRequests.of(0, 25).toPageable();

        assertThat(pageable.getPageNumber()).isZero();
        assertThat(pageable.getPageSize()).isEqualTo(25);
        assertThat(pageable.getSort()).isEqualTo(Sort.by(Sort.Direction.ASC, "id"));
    }

    @Test
    @DisplayName("a clamped request is reported at the size actually served")
    void responseReportsEffectiveSize() {
        // 250 rows at the cap of 100 span three pages, and the caller can
        // only know that if size reports 100 rather than the 5000 asked for.
        Page<String> served = new PageImpl<>(
                List.of("a", "b"), PageRequest.of(0, PageRequests.MAX_SIZE), 250);

        PageResponse<String> response = PageResponse.from(served);

        assertThat(response.content()).containsExactly("a", "b");
        assertThat(response.page()).isZero();
        assertThat(response.size()).isEqualTo(PageRequests.MAX_SIZE);
        assertThat(response.totalElements()).isEqualTo(250);
        assertThat(response.totalPages()).isEqualTo(3);
    }

    @Test
    @DisplayName("map keeps the position of the page it maps")
    void mapKeepsPageMetadata() {
        Page<String> served = new PageImpl<>(
                List.of("1"), PageRequest.of(1, 1), 2);
        PageResponse<String> envelope = PageResponse.from(served);

        PageResponse<Long> mapped = envelope.map(Long::valueOf);

        assertThat(mapped.content()).containsExactly(1L);
        assertThat(mapped.page()).isEqualTo(1);
        assertThat(mapped.size()).isEqualTo(1);
        assertThat(mapped.totalElements()).isEqualTo(2);
        assertThat(mapped.totalPages()).isEqualTo(2);
    }
}
