package com.qsr.customspd.tools.contentscaffold;

/** Inserts a line immediately above an anchor marker. Idempotent and fail-safe:
 *  never reserializes the file, never duplicates, never guesses when the marker is gone. */
public final class AnchorInserter {
    private AnchorInserter() {}

    public record Result(String newContent, boolean inserted) {}

    public static final class MissingAnchorException extends RuntimeException {
        public MissingAnchorException(String marker) { super("Anchor marker not found: " + marker); }
    }

    public static Result insertAbove(String fileContent, String marker, String lineToInsert,
                                     String idempotencyToken) {
        if (fileContent.contains(idempotencyToken)) {
            return new Result(fileContent, false);
        }
        int markerIdx = fileContent.indexOf(marker);
        if (markerIdx < 0) {
            throw new MissingAnchorException(marker);
        }
        // Find the start of the line the marker sits on.
        int lineStart = fileContent.lastIndexOf('\n', markerIdx) + 1; // 0 if marker on first line
        String before = fileContent.substring(0, lineStart);
        String after = fileContent.substring(lineStart);
        return new Result(before + lineToInsert + "\n" + after, true);
    }
}
