package io.mcpsearch.eval;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EvaluationRunnerTest {

    @Test
    void aggregatesMetricsAcrossTheGoldenSet() {
        List<JudgedQuery> golden = List.of(
                JudgedQuery.of("charger", "VECTOR", List.of("p1", "p2")),
                JudgedQuery.of("mouse", "HYBRID", List.of("m1")));
        Map<String, List<String>> stubResults = Map.of(
                "charger", List.of("p1", "zz", "p2"),
                "mouse", List.of("zz", "m1"));

        var report = new EvaluationRunner(3).run(golden, q -> stubResults.get(q.query()));

        assertEquals(2, report.perQuery().size());
        assertEquals(0.75, report.mrr(), 1e-6);   // 1.0 and 0.5
        assertTrue(report.summary().contains("MRR"));
    }

    @Test
    void detectsWhichOfTwoConfigurationsRanksBetter() {
        List<JudgedQuery> golden = List.of(JudgedQuery.of("charger", null, List.of("good")));
        var runner = new EvaluationRunner(5);

        var betterConfig = runner.run(golden, q -> List.of("good", "junk"));
        var worseConfig = runner.run(golden, q -> List.of("junk", "good"));

        assertTrue(betterConfig.meanNdcg() > worseConfig.meanNdcg());
    }
}
