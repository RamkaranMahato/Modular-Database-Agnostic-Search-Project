package io.mcpsearch.adapter.mysql.sql;

/**
 * Parses the text form MySQL's {@code VECTOR_TO_STRING()} produces - a
 * bracketed, comma-separated list of floats in scientific notation, e.g.
 * {@code [1.05000e+00,-1.78000e+01,3.20000e+01]} - back into a
 * {@code float[]}.
 * <p>
 * This exists because MySQL Community Edition has no {@code DISTANCE()}
 * function (that's HeatWave/MySQL AI only), so the adapter reads each
 * candidate row's stored vector back out as a string and computes cosine
 * similarity in Java instead. Pure and unit-testable.
 */
public final class VectorStringParser {

    private VectorStringParser() {
    }

    public static float[] parse(String vectorString) {
        if (vectorString == null || vectorString.isBlank()) {
            return new float[0];
        }
        String trimmed = vectorString.trim();
        int start = trimmed.indexOf('[');
        int end = trimmed.lastIndexOf(']');
        if (start < 0 || end < 0 || end <= start) {
            throw new IllegalArgumentException("Not a bracketed vector string: " + vectorString);
        }
        String inner = trimmed.substring(start + 1, end).trim();
        if (inner.isEmpty()) {
            return new float[0];
        }
        String[] parts = inner.split(",");
        float[] result = new float[parts.length];
        for (int i = 0; i < parts.length; i++) {
            result[i] = Float.parseFloat(parts[i].trim());
        }
        return result;
    }
}
