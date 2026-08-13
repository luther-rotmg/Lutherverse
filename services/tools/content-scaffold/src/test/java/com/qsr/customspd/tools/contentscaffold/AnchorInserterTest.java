package com.qsr.customspd.tools.contentscaffold;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AnchorInserterTest {
    private static final String FILE =
            "enum X {\n    ANKH(\"a.png\"),\n    // @content-scaffold:items\n}\n";

    @Test
    void insertsAboveTheMarker() {
        AnchorInserter.Result r = AnchorInserter.insertAbove(
                FILE, "// @content-scaffold:items", "    WISP(\"w.png\"),", "WISP(");
        assertTrue(r.inserted());
        assertEquals(
                "enum X {\n    ANKH(\"a.png\"),\n    WISP(\"w.png\"),\n    // @content-scaffold:items\n}\n",
                r.newContent());
    }

    @Test
    void idempotentWhenTokenAlreadyPresent() {
        String already = "enum X {\n    WISP(\"w.png\"),\n    // @content-scaffold:items\n}\n";
        AnchorInserter.Result r = AnchorInserter.insertAbove(
                already, "// @content-scaffold:items", "    WISP(\"w.png\"),", "WISP(");
        assertFalse(r.inserted());
        assertEquals(already, r.newContent());
    }

    @Test
    void missingMarkerThrows() {
        assertThrows(AnchorInserter.MissingAnchorException.class, () ->
                AnchorInserter.insertAbove("enum X {}\n", "// @content-scaffold:items", "x", "x"));
    }
}
