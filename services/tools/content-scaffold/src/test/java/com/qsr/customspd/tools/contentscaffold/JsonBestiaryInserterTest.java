package com.qsr.customspd.tools.contentscaffold;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class JsonBestiaryInserterTest {
    private static final String JSON =
            "{\n  \"dungeon\": {\n    \"1\": {\n      \"depth\": 1,\n      \"bestiary\": [\n"
          + "        \"Rat\",\n        \"Snake\"\n      ]\n    },\n"
          + "    \"3\": {\n      \"depth\": 3,\n      \"bestiary\": [\n        \"Gnoll\"\n      ]\n    }\n  }\n}\n";

    @Test
    void addsToTheMatchingDepthAsLastElement() {
        AnchorInserter.Result r = JsonBestiaryInserter.addMob(JSON, 3, "Wisp");
        assertTrue(r.inserted());
        assertTrue(r.newContent().contains("\"Gnoll\",\n        \"Wisp\""),
                () -> "Wisp should follow Gnoll in the depth-3 bestiary:\n" + r.newContent());
        // depth-1 bestiary untouched
        assertTrue(r.newContent().contains("\"Rat\",\n        \"Snake\"\n      ]"));
    }

    @Test
    void idempotentWhenAlreadyInThatDepth() {
        AnchorInserter.Result first = JsonBestiaryInserter.addMob(JSON, 3, "Wisp");
        AnchorInserter.Result second = JsonBestiaryInserter.addMob(first.newContent(), 3, "Wisp");
        assertFalse(second.inserted());
        assertEquals(first.newContent(), second.newContent());
    }

    @Test
    void unknownDepthThrows() {
        assertThrows(IllegalArgumentException.class, () -> JsonBestiaryInserter.addMob(JSON, 9, "Wisp"));
    }

    @Test
    void throwsWhenMatchedLevelHasNoBestiary() {
        String json =
                "{\n  \"dungeon\": {\n    \"2\": {\n      \"depth\": 2\n    },\n"
              + "    \"3\": {\n      \"depth\": 3,\n      \"bestiary\": [\n        \"Gnoll\"\n      ]\n    }\n  }\n}\n";
        assertThrows(IllegalArgumentException.class, () -> JsonBestiaryInserter.addMob(json, 2, "Wisp"));
    }

    @Test
    void singleLineArrayKeepsExistingEntries() {
        String json = "{\n  \"dungeon\": {\n    \"5\": {\n      \"depth\": 5,\n"
              + "      \"bestiary\": [\"Rat\", \"Snake\"]\n    }\n  }\n}\n";
        AnchorInserter.Result r = JsonBestiaryInserter.addMob(json, 5, "Wisp");
        assertTrue(r.inserted());
        assertTrue(r.newContent().contains("\"Rat\""));
        assertTrue(r.newContent().contains("\"Snake\""));
        assertTrue(r.newContent().contains("\"Wisp\""));
    }
}
