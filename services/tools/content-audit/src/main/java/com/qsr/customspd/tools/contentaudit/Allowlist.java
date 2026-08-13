package com.qsr.customspd.tools.contentaudit;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Entity/check keys a human reviewed and accepted as permanently correct.
 *  Format: one {@code "Type Name#Check"} key per line; blank lines and {@code #}
 *  comments ignored so each entry can carry a rationale above it. */
public final class Allowlist {
    private final Set<String> permitted;

    private Allowlist(Set<String> permitted) { this.permitted = permitted; }

    public static Allowlist load(Path path) throws IOException {
        if (path == null || !Files.exists(path)) return new Allowlist(Set.of());
        Set<String> keys = new HashSet<>();
        for (String line : Files.readAllLines(path)) {
            String t = line.strip();
            if (!t.isEmpty() && !t.startsWith("#")) keys.add(t);
        }
        return new Allowlist(keys);
    }

    public boolean permits(String key) { return permitted.contains(key); }
}
