package io.mcpsearch.schema.select;

import io.mcpsearch.schema.model.TableMetadata;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Holds the precomputed embedding for each searchable table and ranks them
 * against a query embedding by cosine similarity. Table embeddings are
 * computed once at startup (via an {@link io.mcpsearch.core.spi.EmbeddingProvider},
 * over {@link TableMetadata#toEmbeddingText()}) and held here; only the query
 * is embedded per search.
 * <p>
 * The ranking logic is pure and testable; only the initial embedding of the
 * table texts is an I/O edge, and that's done by the caller, not here.
 */
public final class TableEmbeddingIndex {

    private final List<Entry> entries;

    private record Entry(TableMetadata table, float[] embedding) {
    }

    /**
     * @param tableEmbeddings map of table -> its precomputed metadata embedding
     */
    public TableEmbeddingIndex(Map<TableMetadata, float[]> tableEmbeddings) {
        this.entries = tableEmbeddings.entrySet().stream()
                .map(e -> new Entry(e.getKey(), e.getValue()))
                .toList();
    }

    /**
     * Rank all tables by cosine similarity to the query embedding and return the
     * top {@code shortlistSize}, best first. Tables scoring below
     * {@code minSimilarity} are excluded even if that yields fewer than
     * {@code shortlistSize} results - better to shortlist nothing than to search
     * clearly-irrelevant tables.
     */
    public List<TableMetadata> shortlist(float[] queryEmbedding, int shortlistSize, double minSimilarity) {
        List<Scored> scored = new ArrayList<>(entries.size());
        for (Entry entry : entries) {
            double sim = Cosine.similarity(queryEmbedding, entry.embedding());
            if (sim >= minSimilarity) {
                scored.add(new Scored(entry.table(), sim));
            }
        }
        scored.sort((x, y) -> Double.compare(y.score(), x.score()));

        int limit = Math.min(shortlistSize, scored.size());
        List<TableMetadata> result = new ArrayList<>(limit);
        for (int i = 0; i < limit; i++) {
            result.add(scored.get(i).table());
        }
        return result;
    }

    private record Scored(TableMetadata table, double score) {
    }
}
