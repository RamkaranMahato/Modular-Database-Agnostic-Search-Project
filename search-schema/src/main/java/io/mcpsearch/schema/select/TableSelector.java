package io.mcpsearch.schema.select;

import io.mcpsearch.schema.model.TableMetadata;
import io.mcpsearch.schema.spi.TableRefiner;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Orchestrates hybrid shortlist-then-refine table selection:
 * <ol>
 *   <li>embedding shortlist (via {@link TableEmbeddingIndex}) narrows 50+
 *       tables down to a handful;</li>
 *   <li>an optional {@link TableRefiner} (LLM) prunes the shortlist to the
 *       genuinely relevant subset.</li>
 * </ol>
 * The orchestration - including every defensive guard around the refiner -
 * is pure and testable with a fake refiner; only the refiner's own
 * implementation is an I/O edge.
 * <p>
 * Two guarantees enforced here rather than trusted to the refiner:
 * hallucinated table names are dropped (result intersected with the
 * shortlist), and any refiner failure or empty result falls back to the full
 * shortlist rather than failing the search.
 */
public final class TableSelector {

    private final TableEmbeddingIndex embeddingIndex;
    private final TableRefiner refiner; // nullable: refine stage is optional

    public TableSelector(TableEmbeddingIndex embeddingIndex, TableRefiner refiner) {
        this.embeddingIndex = embeddingIndex;
        this.refiner = refiner;
    }

    public List<TableMetadata> select(
            String query,
            float[] queryEmbedding,
            int shortlistSize,
            double minSimilarity
    ) {
        List<TableMetadata> shortlist = embeddingIndex.shortlist(queryEmbedding, shortlistSize, minSimilarity);
        if (refiner == null || shortlist.isEmpty()) {
            return shortlist;
        }

        List<TableMetadata> refined;
        try {
            refined = refiner.refine(query, shortlist);
        } catch (RuntimeException e) {
            // Refiner failure must not fail the search - degrade to the shortlist.
            return shortlist;
        }
        if (refined == null || refined.isEmpty()) {
            return shortlist;
        }

        // Intersect back against the shortlist by table name: drop anything the
        // refiner invented, preserve the shortlist's relevance ordering.
        Set<String> allowed = new LinkedHashSet<>();
        for (TableMetadata table : refined) {
            allowed.add(table.name());
        }
        List<TableMetadata> intersected = shortlist.stream()
                .filter(t -> allowed.contains(t.name()))
                .toList();

        // If the refiner returned only hallucinated names, fall back rather than search nothing.
        return intersected.isEmpty() ? shortlist : intersected;
    }
}
