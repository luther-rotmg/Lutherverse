package com.qsr.customspd.tools.deletionaudit;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Removal keys that a human has reviewed and accepted.
 *
 * <p>File format: one {@code TypeName#signature} key per line. Blank lines and
 * lines starting with {@code #} are ignored, so each entry can carry a comment
 * above it recording who accepted it and why.
 *
 * <p>Without this, the tool reports the same reviewed removals on every run and
 * gets ignored — which is how the previous gates died.
 */
public final class Allowlist {

    private final Set<String> permitted;

    private Allowlist(Set<String> permitted) {
        this.permitted = permitted;
    }

    /** Loads the allowlist, treating a null or absent path as "permit nothing". */
    public static Allowlist load(Path path) throws IOException {
        if (path == null || !Files.exists(path)) {
            return new Allowlist(Set.of());
        }
        Set<String> keys = new HashSet<>();
        List<String> lines = Files.readAllLines(path);
        for (String line : lines) {
            String trimmed = line.strip();
            if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
                keys.add(trimmed);
            }
        }
        return new Allowlist(keys);
    }

    public boolean permits(String key) {
        return permitted.contains(key);
    }
}
