package io.mcpsearch.adapter.postgres.sql;

/**
 * Encodes a Java {@code float[]} embedding as a pgvector text literal, e.g.
 * {@code [0.12,0.98,-0.4]}, suitable for binding as a String and casting
 * with {@code ?::vector} in SQL. No pgvector driver extension is required -
 * the plain text format is always accepted by the {@code vector} type's
 * input function.
 */
public final class PgVectorCodec {

    private PgVectorCodec() {
    }

    public static String toLiteral(float[] vector) {
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
