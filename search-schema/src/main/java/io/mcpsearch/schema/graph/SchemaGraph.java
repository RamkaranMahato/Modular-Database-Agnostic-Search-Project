package io.mcpsearch.schema.graph;

import io.mcpsearch.schema.model.ForeignKey;
import io.mcpsearch.schema.model.SchemaMetadata;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A directed graph of tables linked by foreign keys, with bounded
 * breadth-first expansion. Pure and deterministic - no database, no I/O -
 * which is what makes the traversal logic unit-testable in isolation.
 * <p>
 * Built once from {@link SchemaMetadata} at startup; {@link #expand} is then
 * called per query to grow a set of query-matched "seed" tables outward
 * along relationships.
 */
public final class SchemaGraph {

    private final Map<String, List<String>> outgoing = new HashMap<>();
    private final Map<String, List<String>> incoming = new HashMap<>();

    public SchemaGraph(SchemaMetadata schema) {
        for (ForeignKey fk : schema.foreignKeys()) {
            outgoing.computeIfAbsent(fk.fromTable(), k -> new ArrayList<>()).add(fk.toTable());
            incoming.computeIfAbsent(fk.toTable(), k -> new ArrayList<>()).add(fk.fromTable());
        }
    }

    /**
     * Breadth-first expansion from a set of seed tables.
     *
     * @param seeds        query-matched tables to start from (always included in the result)
     * @param direction    which FK direction(s) to follow
     * @param maxDepth     how many hops to walk (0 = seeds only, 1 = seeds + direct neighbours, ...)
     * @param maxTables    hard cap on the total result size - stops the walk once reached, so a
     *                     highly-connected hub table can't drag in the whole schema. Seeds are never
     *                     dropped even if they alone exceed the cap.
     * @return seeds plus reachable tables, insertion-ordered (seeds first, then by BFS discovery)
     */
    public Set<String> expand(Set<String> seeds, Direction direction, int maxDepth, int maxTables) {
        Set<String> result = new LinkedHashSet<>(seeds);
        if (maxDepth <= 0) {
            return result;
        }

        Set<String> visited = new HashSet<>(seeds);
        Deque<String> frontier = new ArrayDeque<>(seeds);
        Map<String, Integer> depthOf = new HashMap<>();
        for (String seed : seeds) {
            depthOf.put(seed, 0);
        }

        while (!frontier.isEmpty()) {
            String current = frontier.poll();
            int currentDepth = depthOf.get(current);
            if (currentDepth >= maxDepth) {
                continue;
            }
            for (String neighbour : neighbours(current, direction)) {
                if (visited.add(neighbour)) {
                    depthOf.put(neighbour, currentDepth + 1);
                    // Respect the cap, but never at the expense of a seed table.
                    if (result.size() < maxTables) {
                        result.add(neighbour);
                        frontier.add(neighbour);
                    }
                }
            }
        }
        return result;
    }

    private List<String> neighbours(String table, Direction direction) {
        return switch (direction) {
            case OUTGOING -> outgoing.getOrDefault(table, List.of());
            case INCOMING -> incoming.getOrDefault(table, List.of());
            case BOTH -> {
                List<String> both = new ArrayList<>(outgoing.getOrDefault(table, List.of()));
                both.addAll(incoming.getOrDefault(table, List.of()));
                yield both;
            }
        };
    }
}
