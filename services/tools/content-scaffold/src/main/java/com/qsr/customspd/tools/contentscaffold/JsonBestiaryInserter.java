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
        int nextDepthKey = dungeonJson.indexOf("\"depth\"", depthM.end());
        if (bestiaryKey < 0 || (nextDepthKey >= 0 && nextDepthKey < bestiaryKey)) {
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
        // Find the last quoted element (regardless of formatting) and insert after it.
        Matcher elems = Pattern.compile("\"[^\"]*\"").matcher(arrayBody);
        int lastStart = -1, lastEnd = -1;
        while (elems.find()) {
            lastStart = elems.start();
            lastEnd = elems.end();
        }
        String newBody;
        if (lastEnd < 0) {
            // genuinely empty array
            newBody = "\n        \"" + className + "\"\n      ";
        } else {
            int lineStart = arrayBody.lastIndexOf('\n', lastStart);
            if (lineStart >= 0) {
                // multi-line: mimic the last element's indentation
                String indent = arrayBody.substring(lineStart + 1, lastStart);
                newBody = arrayBody.substring(0, lastEnd) + ",\n" + indent + "\"" + className + "\""
                        + arrayBody.substring(lastEnd);
            } else {
                // single-line: comma-separate
                newBody = arrayBody.substring(0, lastEnd) + ", \"" + className + "\"" + arrayBody.substring(lastEnd);
            }
        }
        String out = dungeonJson.substring(0, arrayOpen + 1) + newBody + dungeonJson.substring(arrayClose);
        return new AnchorInserter.Result(out, true);
    }
}
