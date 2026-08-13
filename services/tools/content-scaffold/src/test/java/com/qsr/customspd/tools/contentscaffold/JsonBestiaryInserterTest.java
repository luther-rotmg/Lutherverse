package com.qsr.customspd.tools.contentscaffold;

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
    }

    @Test
    void unknownDepthThrows() {
        assertThrows(IllegalArgumentException.class, () -> JsonBestiaryInserter.addMob(JSON, 9, "Wisp"));
    }
}
