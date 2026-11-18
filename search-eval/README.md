# Search Evaluation

Measuring search quality, so changes can be judged by numbers instead of by
squinting at a few queries.

## Why this module exists

Every tuning decision in this project so far — RRF vs weighted fusion, the
`minSimilarity` threshold, the candidate pool size, hybrid vs pure vector — was
made on reasoning and spot checks. That works until two plausible options
disagree, or until a change quietly makes results worse for queries nobody
re-tested. A golden set plus metrics turns those into measurable questions.

## How it works

`EvaluationRunner` takes a list of `JudgedQuery` (a query plus the document ids
a human judged relevant) and a search function `query -> ranked ids`. It runs
each query and reports:

| Metric | Question it answers | Sensitive to |
|---|---|---|
| Precision@k | Of what I showed, how much was relevant? | noise in results |
| Recall@k | Of what was relevant, how much did I find? | misses |
| MRR | How high was the first good result? | top-of-list quality |
| NDCG@k | Weighted by position, how good is the ranking? | ordering, graded relevance |

The search function is injected, so the harness depends on nothing — no
database, no Spring. Use a stub in unit tests, a real `SearchAdapter` in an
integration test.

## The A/B use case

This is the payoff. Run the same golden set through two configurations:

```java
var runner = new EvaluationRunner(10);
var rrf      = runner.run(goldenSet, q -> searchWith(FusionMode.RRF, q));
var weighted = runner.run(goldenSet, q -> searchWith(FusionMode.WEIGHTED, q));

System.out.println(rrf.summary());
System.out.println(weighted.summary());
```

Now "is RRF better for my data?" has an answer with a number attached. The same
pattern compares search modes, thresholds, embedding models, or pool sizes.

## Building a golden set

Start small — 20 to 30 judged queries catches regressions. Cover the cases that
matter:

- **Exact matches** — query wording that appears verbatim in a document.
  Guards the text channel.
- **Vocabulary mismatch** — query wording that appears nowhere in the relevant
  document ("charger" should find "UPS Battery Backup"). Guards the vector
  channel; these are the queries pure keyword search fails.
- **Ambiguous or broad** — "something for my desk". Tests ranking rather than
  matching.
- **Queries that should return nothing** — guards against a system that always
  returns its ten nearest rows regardless of relevance.

A starter set against the sample `products` data:

```java
List<JudgedQuery> golden = List.of(
    // exact-ish: text channel should nail these
    JudgedQuery.of("wireless optical mouse", "HYBRID", List.of("1")),
    JudgedQuery.of("mechanical keyboard",    "HYBRID", List.of("2")),

    // vocabulary mismatch: only the vector channel can find these
    JudgedQuery.of("charger",                "HYBRID", List.of("141", "222", "203")),
    JudgedQuery.of("something to carry my laptop", "HYBRID", List.of("13", "14")),
    JudgedQuery.of("keep my devices powered while travelling", "HYBRID", List.of("203", "192")),

    // broad: tests ranking quality
    JudgedQuery.of("comfortable setup for long work hours", "HYBRID", List.of("17", "18", "11"))
);
```

Replace the ids with your own — they depend on what your table actually
contains. Judging is manual: run the query, look at the results, record which
ones a user would genuinely have wanted.

## Interpreting results

Absolute numbers matter less than *changes* to them. A jump in recall with a
drop in precision means you widened retrieval and let noise in. NDCG falling
while precision holds means the right documents are being found but ranked
badly — a fusion or scoring problem, not a retrieval one.

The `summary()` output lists the three worst queries by NDCG along with the ids
they missed. That list is the most useful output in this module: it tells you
exactly which query to debug next.
