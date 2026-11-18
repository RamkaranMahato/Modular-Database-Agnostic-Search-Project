package io.mcpsearch.core.model;

import java.util.ArrayList;
import java.util.List;

/**
 * A search request. Immutable; build with {@link #builder()}.
 * <p>
 * Only {@code text} and/or {@code vector} are required (at least one of the
 * two, matching {@code mode}) - everything else has a default suitable for
 * "just search and give me relevant results":
 * {@link SearchMode#HYBRID}, {@link FusionMode#RRF}, page 0, size 10.
 */
public final class SearchQuery {

    private final String text;
    private final float[] vector;
    private final List<Filter> filters;
    private final SearchMode mode;
    private final FusionMode fusionMode;
    private final Double alpha;
    private final Integer rrfK;
    private final int page;
    private final int size;
    private final List<SortField> sort;

    private SearchQuery(Builder b) {
        this.text = b.text;
        this.vector = b.vector == null ? null : b.vector.clone();
        this.filters = List.copyOf(b.filters);
        this.mode = b.mode;
        this.fusionMode = b.fusionMode;
        this.alpha = b.alpha;
        this.rrfK = b.rrfK;
        this.page = b.page;
        this.size = b.size;
        this.sort = List.copyOf(b.sort);
    }

    public String text() {
        return text;
    }

    public float[] vector() {
        return vector == null ? null : vector.clone();
    }

    public List<Filter> filters() {
        return filters;
    }

    public SearchMode mode() {
        return mode;
    }

    public FusionMode fusionMode() {
        return fusionMode;
    }

    /** Resolves to {@link FusionConfig}'s default alpha if not explicitly set on this query. */
    public FusionConfig fusionConfig() {
        FusionConfig defaults = FusionConfig.defaults(size);
        return new FusionConfig(
                alpha != null ? alpha : defaults.alpha(),
                rrfK != null ? rrfK : defaults.rrfK(),
                size
        );
    }

    public int page() {
        return page;
    }

    public int size() {
        return size;
    }

    public List<SortField> sort() {
        return sort;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String text;
        private float[] vector;
        private final List<Filter> filters = new ArrayList<>();
        private SearchMode mode = SearchMode.HYBRID;
        private FusionMode fusionMode = FusionMode.RRF;
        private Double alpha;
        private Integer rrfK;
        private int page = 0;
        private int size = 10;
        private final List<SortField> sort = new ArrayList<>();

        private Builder() {
        }

        public Builder text(String text) {
            this.text = text;
            return this;
        }

        public Builder vector(float[] vector) {
            this.vector = vector == null ? null : vector.clone();
            return this;
        }

        public Builder filter(Filter filter) {
            this.filters.add(filter);
            return this;
        }

        public Builder filters(List<Filter> filters) {
            this.filters.clear();
            this.filters.addAll(filters);
            return this;
        }

        public Builder mode(SearchMode mode) {
            this.mode = mode;
            return this;
        }

        public Builder fusionMode(FusionMode fusionMode) {
            this.fusionMode = fusionMode;
            return this;
        }

        public Builder alpha(double alpha) {
            this.alpha = alpha;
            return this;
        }

        public Builder rrfK(int rrfK) {
            this.rrfK = rrfK;
            return this;
        }

        public Builder page(int page) {
            this.page = page;
            return this;
        }

        public Builder size(int size) {
            this.size = size;
            return this;
        }

        public Builder sort(SortField sortField) {
            this.sort.add(sortField);
            return this;
        }

        public SearchQuery build() {
            if (mode == SearchMode.TEXT || mode == SearchMode.HYBRID) {
                if (text == null || text.isBlank()) {
                    throw new IllegalStateException("text is required for mode " + mode);
                }
            }
            if (mode == SearchMode.VECTOR && vector == null && (text == null || text.isBlank())) {
                // VECTOR needs *something* to search with: either a precomputed vector, or text
                // that an adapter with an EmbeddingProvider can turn into one. Only fail when
                // neither is present.
                throw new IllegalStateException(
                        "mode VECTOR requires either a precomputed vector or query text to embed");
            }
            if (page < 0) {
                throw new IllegalStateException("page must be >= 0, was " + page);
            }
            if (size < 1) {
                throw new IllegalStateException("size must be >= 1, was " + size);
            }
            return new SearchQuery(this);
        }
    }
}
