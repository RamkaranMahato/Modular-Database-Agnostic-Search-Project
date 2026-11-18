package io.mcpsearch.schema.graph;

/**
 * Which way to walk foreign keys when expanding from a matched table.
 * <p>
 * Given {@code orders.customer_id -> customers.id}:
 * <ul>
 *   <li>{@code OUTGOING} from {@code orders} reaches {@code customers}
 *       (follow the FK you hold - "what does this row point at").</li>
 *   <li>{@code INCOMING} from {@code customers} reaches {@code orders}
 *       (follow FKs pointing at you - "what points at this row").</li>
 *   <li>{@code BOTH} does both.</li>
 * </ul>
 * {@code OUTGOING} is the safe default: it pulls in the handful of parent
 * rows a matched row references, whereas {@code INCOMING}/{@code BOTH} from a
 * hub table like {@code users} can fan out across much of the schema (which
 * is what the per-expansion cap guards against).
 */
public enum Direction {
    OUTGOING,
    INCOMING,
    BOTH
}
