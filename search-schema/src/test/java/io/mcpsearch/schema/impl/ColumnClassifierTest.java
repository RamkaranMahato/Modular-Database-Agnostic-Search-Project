package io.mcpsearch.schema.impl;

import io.mcpsearch.schema.model.ColumnKind;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ColumnClassifierTest {

    @Test
    void primaryKeyIsIdRegardlessOfType() {
        assertEquals(ColumnKind.ID, ColumnClassifier.classify("int4", true));
        assertEquals(ColumnKind.ID, ColumnClassifier.classify("varchar", true));
    }

    @Test
    void vectorTypesAreVector() {
        assertEquals(ColumnKind.VECTOR, ColumnClassifier.classify("vector", false));
        assertEquals(ColumnKind.VECTOR, ColumnClassifier.classify("vector(1536)", false));
    }

    @Test
    void stringTypesAcrossDialectsAreText() {
        assertEquals(ColumnKind.TEXT, ColumnClassifier.classify("text", false));
        assertEquals(ColumnKind.TEXT, ColumnClassifier.classify("varchar(255)", false));
        assertEquals(ColumnKind.TEXT, ColumnClassifier.classify("TEXT", false));
        assertEquals(ColumnKind.TEXT, ColumnClassifier.classify("longtext", false));
        assertEquals(ColumnKind.TEXT, ColumnClassifier.classify("character varying", false));
    }

    @Test
    void otherTypesAreOther() {
        assertEquals(ColumnKind.OTHER, ColumnClassifier.classify("integer", false));
        assertEquals(ColumnKind.OTHER, ColumnClassifier.classify("timestamp", false));
        assertEquals(ColumnKind.OTHER, ColumnClassifier.classify(null, false));
    }
}
