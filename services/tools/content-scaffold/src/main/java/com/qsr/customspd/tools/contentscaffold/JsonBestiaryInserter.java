package com.qsr.customspd.tools.contentscaffold;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Adds a mob class name to the bestiary array of the dungeon.json level at a given depth.
 *  Text-targeted (not a JSON reserialize) so formatting and ordering are preserved. */
public final class JsonBestiaryInserter {
    private JsonBestiaryInserter() {}

    public static AnchorInserter.Result addMob(String dungeonJson, int depth, String className) {
        // Locate the level object whose "depth": <depth> appears, then its following "bestiary": [ ... ].
        Matcher depthM = Pattern.compile("\"depth\"\\s*:\\s*" + depth + "\\b").matcher(dungeonJson);
        if (!depthM.find()) {
            throw new IllegalArgumentException("No dungeon level with depth " + depth);
        }
        int bestiaryKey = dungeonJson.indexOf("\"bestiary\"", depthM.end());
        if (bestiaryKey < 0) {
            throw new IllegalArgumentException("Level at depth " + depth + " has no bestiary array");
        }
        int arrayOpen = dungeonJson.indexOf('[', bestiaryKey);
        int arrayClose = dungeonJson.indexOf(']', arrayOpen);
        if (arrayOpen < 0 || arrayClose < 0) {
            throw new IllegalArgumentException("Malformed bestiary array at depth " + depth);
        }
        String arrayBody = dungeonJson.substring(arrayOpen + 1, arrayClose);
        if (arrayBody.contains("\"" + className + "\"")) {
            return new AnchorInserter.Result(dungeonJson, false); // idempotent
        }
        // Find the last quoted element to copy its indentation and insert after it.
        Matcher last = Pattern.compile("(\\n(\\s*)\"[^\"]+\")(\\s*)$").matcher(arrayBody);
        String newBody;
        if (last.find()) {
            String indent = last.group(2);
            newBody = arrayBody.substring(0, last.end(1))
                    + ",\n" + indent + "\"" + className + "\""
                    + arrayBody.substring(last.end(1));
        } else {
            // empty array
            newBody = "\n        \"" + className + "\"\n      ";
        }
        String out = dungeonJson.substring(0, arrayOpen + 1) + newBody + dungeonJson.substring(arrayClose);
        return new AnchorInserter.Result(out, true);
    }
}
