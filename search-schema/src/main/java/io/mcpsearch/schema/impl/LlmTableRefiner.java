package io.mcpsearch.schema.impl;

import io.mcpsearch.schema.model.TableMetadata;
import io.mcpsearch.schema.spi.TableRefiner;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

/**
 * A {@link TableRefiner} that asks an LLM which shortlisted tables are
 * relevant. The LLM transport is injected as a plain
 * {@code UnaryOperator<String>} (prompt in, completion out), so this class
 * has no dependency on any particular AI SDK - wire it to Spring AI's
 * {@code ChatClient}, an OpenAI call, Bedrock, or a fake in tests.
 * <p>
 * The judgement-bearing logic here - build prompt, parse response, map names
 * back to the shortlist's {@link TableMetadata} - is pure once the transport
 * is a fake, and is unit-tested that way. Only the real transport is an I/O
 * edge.
 * <p>
 * Note this maps parsed names back to metadata via the shortlist, so a
 * hallucinated name simply finds no match and is dropped here too - belt and
 * suspenders with {@code TableSelector}'s own intersection.
 */
public final class LlmTableRefiner implements TableRefiner {

    private final UnaryOperator<String> chat;

    /**
     * @param chat prompt -> completion. Any exception it throws propagates to
     *             {@code TableSelector}, which catches it and falls back to the
     *             shortlist - so a transport failure degrades gracefully.
     */
    public LlmTableRefiner(UnaryOperator<String> chat) {
        this.chat = chat;
    }

    @Override
    public List<TableMetadata> refine(String query, List<TableMetadata> shortlist) {
        String prompt = RefinerPromptBuilder.build(query, shortlist);
        String response = chat.apply(prompt);
        List<String> names = LlmResponseParser.parseTableNames(response);
        if (names.isEmpty()) {
            return List.of();
        }
        Map<String, TableMetadata> byName = shortlist.stream()
                .collect(Collectors.toMap(TableMetadata::name, Function.identity(), (a, b) -> a));
        return names.stream()
                .map(byName::get)
                .filter(java.util.Objects::nonNull)
                .toList();
    }
}
