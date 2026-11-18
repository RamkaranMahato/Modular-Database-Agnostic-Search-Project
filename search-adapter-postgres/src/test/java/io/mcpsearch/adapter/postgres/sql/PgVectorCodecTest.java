package io.mcpsearch.adapter.postgres.sql;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PgVectorCodecTest {

    @Test
    void encodesFloatArrayAsPgVectorLiteral() {
        assertEquals("[1.0,2.0,3.0]", PgVectorCodec.toLiteral(new float[]{1f, 2f, 3f}));
    }

    @Test
    void encodesSingleElementVector() {
        assertEquals("[0.5]", PgVectorCodec.toLiteral(new float[]{0.5f}));
    }

    @Test
    void encodesEmptyVector() {
        assertEquals("[]", PgVectorCodec.toLiteral(new float[]{}));
    }
}
