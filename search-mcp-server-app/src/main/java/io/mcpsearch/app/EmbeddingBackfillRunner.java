package io.mcpsearch.app;

import io.mcpsearch.core.spi.EmbeddingProvider;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * One-shot backfill that populates the {@code embedding} column for rows that
 * don't have one yet. Runs at startup ONLY when
 * {@code app.backfill.enabled=true}, does its work, and (by default) stops the
 * application so it doesn't linger as a server. Turn the flag off for normal
 * operation.
 * <p>
 * Reuses the app's already-wired {@link EmbeddingProvider} (OpenAI) and
 * {@link DataSource} - no separate credentials or main method. For each row
 * missing an embedding it concatenates the configured text columns, embeds
 * that text, and writes the vector back with {@code STRING_TO_VECTOR}.
 * <p>
 * This targets the MySQL {@code products} demo table by default via
 * {@code app.backfill.*} properties; point those at your table/columns as needed.
 * Deliberately simple (row-by-row, single-threaded) - it's a one-time utility,
 * not a hot path. For very large tables you'd batch, but for a demo table it's
 * more than enough.
 */
@Component
@ConditionalOnProperty(prefix = "app.backfill", name = "enabled", havingValue = "true")
public class EmbeddingBackfillRunner implements ApplicationRunner {

    private static final System.Logger LOG = System.getLogger(EmbeddingBackfillRunner.class.getName());

    private final DataSource dataSource;
    private final EmbeddingProvider embeddingProvider;
    private final BackfillProperties props;

    public EmbeddingBackfillRunner(DataSource dataSource, EmbeddingProvider embeddingProvider, BackfillProperties props) {
        this.dataSource = dataSource;
        this.embeddingProvider = embeddingProvider;
        this.props = props;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        String table = props.table();
        String idColumn = props.idColumn();
        String vectorColumn = props.vectorColumn();
        List<String> textColumns = props.textColumns();

        // Validate identifiers defensively - these come from config, but interpolate into SQL.
        requireIdentifier(table, "table");
        requireIdentifier(idColumn, "idColumn");
        requireIdentifier(vectorColumn, "vectorColumn");
        textColumns.forEach(c -> requireIdentifier(c, "textColumn"));

        String textConcat = buildTextConcat(textColumns);
        String selectSql = "SELECT `" + idColumn + "` AS id, " + textConcat + " AS combined_text "
                + "FROM `" + table + "` WHERE `" + vectorColumn + "` IS NULL";
        String updateSql = "UPDATE `" + table + "` SET `" + vectorColumn + "` = STRING_TO_VECTOR(?) "
                + "WHERE `" + idColumn + "` = ?";

        LOG.log(System.Logger.Level.INFO, "Backfill starting for table `{0}` (embedding column `{1}`)", table, vectorColumn);

        List<Row> rows = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(selectSql)) {
            while (rs.next()) {
                rows.add(new Row(rs.getString("id"), rs.getString("combined_text")));
            }
        }

        if (rows.isEmpty()) {
            LOG.log(System.Logger.Level.INFO, "Backfill: no rows need embeddings. Nothing to do.");
            maybeStop();
            return;
        }

        int batchSize = props.batchSize();
        LOG.log(System.Logger.Level.INFO, "Backfill: {0} row(s) need embeddings; batch size {1}.",
                rows.size(), batchSize);

        int done = 0;
        int failed = 0;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(updateSql)) {

            for (int start = 0; start < rows.size(); start += batchSize) {
                int end = Math.min(start + batchSize, rows.size());
                List<Row> batch = rows.subList(start, end);

                // One embedding API call for the whole batch.
                List<float[]> vectors;
                try {
                    List<String> texts = new ArrayList<>(batch.size());
                    for (Row r : batch) {
                        texts.add(r.text() == null ? "" : r.text());
                    }
                    vectors = embeddingProvider.embedBatch(texts);
                } catch (Exception e) {
                    // Whole-batch failure: fall back to per-row so one bad row doesn't sink the batch,
                    // and so we still get partial progress and precise per-row error reporting.
                    LOG.log(System.Logger.Level.WARNING,
                            "Batch embed failed (rows " + start + ".." + (end - 1) + "); retrying row-by-row: "
                                    + e.getMessage());
                    int[] counts = updateRowByRow(ps, batch);
                    done += counts[0];
                    failed += counts[1];
                    continue;
                }

                // Write each vector back. A mismatch in count means we can't trust the alignment,
                // so fall back to per-row for safety.
                if (vectors.size() != batch.size()) {
                    LOG.log(System.Logger.Level.WARNING,
                            "Batch returned {0} vectors for {1} rows; retrying row-by-row for safety.",
                            vectors.size(), batch.size());
                    int[] counts = updateRowByRow(ps, batch);
                    done += counts[0];
                    failed += counts[1];
                    continue;
                }

                for (int i = 0; i < batch.size(); i++) {
                    try {
                        ps.setString(1, toVectorLiteral(vectors.get(i)));
                        ps.setString(2, batch.get(i).id());
                        ps.addBatch();
                    } catch (Exception e) {
                        failed++;
                        LOG.log(System.Logger.Level.WARNING,
                                "Backfill update-prep failed for id=" + batch.get(i).id() + ": " + e.getMessage());
                    }
                }
                ps.executeBatch();          // one DB round-trip per batch, too
                done += batch.size();
                LOG.log(System.Logger.Level.INFO, "Backfill progress: {0}/{1}", Math.min(done, rows.size()), rows.size());
            }
        }

        LOG.log(System.Logger.Level.INFO, "Backfill complete: {0} embedded, {1} failed.", done, failed);
        maybeStop();
    }

    /** Per-row fallback used when a batch embed fails - preserves partial progress. Returns {done, failed}. */
    private int[] updateRowByRow(PreparedStatement ps, List<Row> batch) {
        int done = 0;
        int failed = 0;
        for (Row row : batch) {
            try {
                float[] vector = embeddingProvider.embed(row.text() == null ? "" : row.text());
                ps.setString(1, toVectorLiteral(vector));
                ps.setString(2, row.id());
                ps.executeUpdate();
                done++;
            } catch (Exception e) {
                failed++;
                LOG.log(System.Logger.Level.WARNING, "Backfill failed for id=" + row.id() + ": " + e.getMessage());
            }
        }
        return new int[]{done, failed};
    }

    private void maybeStop() {
        if (props.stopAfter()) {
            LOG.log(System.Logger.Level.INFO,
                    "Backfill done and app.backfill.stop-after=true - exiting. "
                            + "Set app.backfill.enabled=false to run the server normally.");
            // Exit cleanly; the backfill's job is done and we don't want to hold a server open.
            System.exit(0);
        }
    }

    /** COALESCE each text column to '' and space-join, so a NULL column doesn't null the whole concat. */
    private String buildTextConcat(List<String> textColumns) {
        List<String> parts = new ArrayList<>();
        for (String c : textColumns) {
            parts.add("COALESCE(`" + c + "`, '')");
        }
        return "CONCAT_WS(' ', " + String.join(", ", parts) + ")";
    }

    private String toVectorLiteral(float[] vector) {
        StringBuilder sb = new StringBuilder(vector.length * 8 + 2);
        sb.append('[');
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(vector[i]);
        }
        sb.append(']');
        return sb.toString();
    }

    private void requireIdentifier(String id, String what) {
        if (id == null || !id.matches("^[a-zA-Z_][a-zA-Z0-9_]*$")) {
            throw new IllegalArgumentException(what + " must be a valid SQL identifier: " + id);
        }
    }

    private record Row(String id, String text) {
    }
}
