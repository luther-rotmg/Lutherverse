package com.qsr.customspd.tools.deletionaudit;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Repo-root-anchored git invocations.
 *
 * <p>Every command here sets its working directory to the repository root.
 * The api-diff tool omitted this and inherited Gradle's subproject CWD, so
 * {@code git ls-tree} listed only that subproject, matched zero of the paths
 * its glob targeted, and printed PASS while auditing nothing. Do not remove
 * the {@code directory(repoRoot())} calls.
 */
public final class GitCommands {

    private GitCommands() {
    }

    /**
     * Resolves the repository root so git invocations are independent of the
     * JVM's working directory. Returns {@code null} (meaning "inherit the
     * current directory") if the root cannot be determined.
     */
    public static File repoRoot() throws IOException {
        ProcessBuilder builder = new ProcessBuilder("git", "rev-parse", "--show-toplevel");
        builder.redirectErrorStream(true);
        Process process = builder.start();
        String out = readStream(process.getInputStream()).trim();
        try {
            if (process.waitFor() != 0 || out.isEmpty()) {
                return null;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted resolving repository root", e);
        }
        return new File(out);
    }

    /** Reads a file's content at {@code ref} via {@code git show ref:path}. */
    public static String readBlob(String ref, String path) throws IOException {
        String blobSpec = ref + ":" + path;
        ProcessBuilder builder = new ProcessBuilder("git", "show", blobSpec);
        builder.directory(repoRoot());
        return runCapturingStdout(builder, "git show " + blobSpec);
    }

    /** Lists every path in the tree at {@code ref}, repo-root-relative. */
    public static List<String> listTree(String ref) throws IOException {
        ProcessBuilder builder = new ProcessBuilder("git", "ls-tree", "-r", "--name-only", ref);
        builder.directory(repoRoot());
        String stdout = runCapturingStdout(builder, "git ls-tree " + ref);

        List<String> paths = new ArrayList<>();
        for (String line : stdout.split("\n")) {
            String trimmed = line.strip();
            if (!trimmed.isEmpty()) {
                paths.add(trimmed);
            }
        }
        return paths;
    }

    /**
     * Returns {@code true} if the exception indicates a genuinely absent path
     * rather than a process or ref failure.
     *
     * <p>git reports an absent path two different ways depending on whether the
     * file is present in the working tree:
     * <pre>
     *   fatal: path 'X' does not exist in 'REF'
     *   fatal: path 'X' exists on disk, but not in 'REF'
     * </pre>
     * The second form is what a file ADDED by the audited range produces, so
     * missing it makes the tool crash on exactly the commits it exists to audit.
     */
    public static boolean isPathNotFound(IOException e) {
        String message = e.getMessage();
        if (message == null) {
            return false;
        }
        return message.contains("does not exist in")
                || message.contains("exists on disk, but not in");
    }

    private static String runCapturingStdout(ProcessBuilder builder, String description)
            throws IOException {
        Process process = builder.start();

        StringBuilder stderrBuffer = new StringBuilder();
        Thread stderrDrain = new Thread(() -> {
            try {
                stderrBuffer.append(readStream(process.getErrorStream()));
            } catch (IOException ignored) {
                // best-effort: stderr is only used for the error message
            }
        });
        stderrDrain.start();

        String stdout = readStream(process.getInputStream());

        int exitCode;
        try {
            exitCode = process.waitFor();
            stderrDrain.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while running: " + description, e);
        }

        if (exitCode != 0) {
            throw new IOException(description + " failed with exit code " + exitCode
                    + ": " + stderrBuffer.toString().trim());
        }
        return stdout;
    }

    private static String readStream(InputStream inputStream) throws IOException {
        StringBuilder builder = new StringBuilder();
        try (BufferedReader reader =
                     new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            char[] buffer = new char[8192];
            int read;
            while ((read = reader.read(buffer)) != -1) {
                builder.append(buffer, 0, read);
            }
        }
        return builder.toString();
    }
}
