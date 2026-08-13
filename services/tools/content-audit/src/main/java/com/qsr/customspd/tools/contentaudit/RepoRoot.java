package com.qsr.customspd.tools.contentaudit;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/** Locates the repository top level. {@code gradle run} sets CWD to the subproject,
 *  so every working-tree path must resolve against this, not the CWD. */
public final class RepoRoot {
    private RepoRoot() {}

    public static File find() throws IOException {
        Process p = new ProcessBuilder("git", "rev-parse", "--show-toplevel")
                .redirectErrorStream(true).start();
        String line;
        try (BufferedReader r = new BufferedReader(
                new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
            line = r.readLine();
        }
        try {
            if (p.waitFor() != 0 || line == null || line.isBlank()) return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
        return new File(line.trim());
    }
}
