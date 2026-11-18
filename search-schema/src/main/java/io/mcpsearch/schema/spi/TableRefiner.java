package io.mcpsearch.schema.spi;

import io.mcpsearch.schema.model.TableMetadata;

import java.util.List;

/**
 * Optional second stage of table selection: given the query and the
 * embedding-based shortlist, return the subset actually relevant. This is
 * the "refine" in shortlist-then-refine.
 * <p>
 * I/O edge: the production implementation calls an LLM, so it isn't
 * sandbox-testable. Two hard rules a correct implementation must follow,
 * enforced defensively by the caller ({@code TableSelector}) rather than
 * trusted here:
 * <ol>
 *   <li>never return a table not in {@code shortlist} (LLMs hallucinate
 *       table names) - the caller intersects the result back against the
 *       shortlist;</li>
 *   <li>on any failure, the caller falls back to the full shortlist rather
 *       than failing the search.</li>
 * </ol>
 */
public interface TableRefiner {

    /**
     * @param query     the user's search text
     * @param shortlist embedding-ranked candidate tables (best first)
     * @return the relevant subset; ordering need not be preserved
     */
    List<TableMetadata> refine(String query, List<TableMetadata> shortlist);
}
