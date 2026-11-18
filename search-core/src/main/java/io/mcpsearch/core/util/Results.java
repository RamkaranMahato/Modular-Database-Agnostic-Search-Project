package io.mcpsearch.core.util;

import io.mcpsearch.core.model.SearchResult;
import io.mcpsearch.core.model.SortField;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Post-processing applied to a ranked result list before it is returned:
 * explicit sorting, then pagination.
 * <p>
 * Both steps happen <em>in the application</em>, not in SQL, and that is
 * deliberate. In {@code HYBRID} mode the final ordering only exists after the
 * text and vector channels have been fused in-app - there is no single SQL
 * query whose {@code ORDER BY}/{@code OFFSET} could produce it. Pushing
 * {@code OFFSET} into each channel's query separately would offset the two
 * channels independently and fuse mismatched slices, which is silently wrong.
 * Doing it here keeps one correct implementation for all three search modes.
 * <p>
 * The consequence worth knowing: these operate on the retrieved candidate
 * pool, not the whole table. Sorting means "rank by relevance, then order
 * those candidates", and deep pages need a pool large enough to reach them -
 * which is why adapters size the pool from {@code (page + 1) * size}.
 */
public final class Results {

    private Results() {
    }

    /**
     * Apply explicit sort fields, if any. Results are compared on their
     * {@link SearchResult#source()} values; rows missing the field, or holding a
     * non-comparable value, sort last rather than throwing. With no sort fields the
     * list is returned unchanged, preserving relevance order.
     */
    public static List<SearchResult> applySort(List<SearchResult> results, List<SortField> sortFields) {
        if (sortFields == null || sortFields.isEmpty() || results.isEmpty()) {
            return results;
        }
        Comparator<SearchResult> comparator = null;
        for (SortField sortField : sortFields) {
            Comparator<SearchResult> next = Comparator.comparing(
                    r -> asComparable(r.source().get(sortField.field())),
                    nullsLastNaturalOrder());
            if (sortField.direction() == SortField.SortDirection.DESC) {
                next = next.reversed();
            }
            comparator = (comparator == null) ? next : comparator.thenComparing(next);
        }
        List<SearchResult> sorted = new ArrayList<>(results);
        sorted.sort(comparator);
        return sorted;
    }

    /**
     * Return the slice for the requested page. Ranks are left as they are, so a
     * result's rank stays meaningful across pages rather than restarting at 1.
     *
     * @param page 0-based page number
     * @param size page size
     * @return the requested window, or an empty list if the page lies past the end
     */
    public static List<SearchResult> paginate(List<SearchResult> results, int page, int size) {
        int offset = page * size;
        if (offset >= results.size()) {
            return List.of();
        }
        int end = Math.min(offset + size, results.size());
        return List.copyOf(results.subList(offset, end));
    }

    @SuppressWarnings("unchecked")
    private static Comparable<Object> asComparable(Object value) {
        return (value instanceof Comparable<?>) ? (Comparable<Object>) value : null;
    }

    private static Comparator<Comparable<Object>> nullsLastNaturalOrder() {
        return Comparator.nullsLast(Comparator.naturalOrder());
    }
}
