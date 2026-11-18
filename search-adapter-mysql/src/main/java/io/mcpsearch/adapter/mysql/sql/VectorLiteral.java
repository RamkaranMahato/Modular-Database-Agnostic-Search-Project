package io.mcpsearch.adapter.mysql.sql;

/**
 * Encodes a Java {@code float[]} as the JSON-array text literal MySQL's
 * {@code STRING_TO_VECTOR()} accepts, e.g. {@code [0.12,0.98,-0.4]}. Bound as
 * a String parameter and wrapped in {@code STRING_TO_VECTOR(?)} in SQL, which
 * converts it to the native {@code VECTOR} type. (The format happens to match
 * pgvector's text form, but the two adapters keep their own copy rather than
 * share one.)
 */
public final class VectorLiteral {

    private VectorLiteral() {
    }

    public static String toJsonArray(float[] vector) {
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
}
