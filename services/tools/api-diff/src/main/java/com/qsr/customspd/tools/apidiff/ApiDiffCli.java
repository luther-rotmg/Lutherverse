package com.qsr.customspd.tools.apidiff;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import java.util.regex.Pattern;

/**
 * CLI entry point for the API-diff auditor.
 *
 * <p>Compares the public/protected surface of every Java source file
 * matching a glob between two git refs, and reports removed, added, and
 * signature-changed members. Exits non-zero if any member was removed or
 * had its signature changed, so it can be wired into a build as a gate.
 */
public final class ApiDiffCli {

    static final String DEFAULT_FILES_GLOB = "core/src/main/java/**/*.java";

    private ApiDiffCli() {
    }

    public static void main(String[] args) throws IOException {
        Args parsed;
        try {
            parsed = Args.parse(args);
        } catch (IllegalArgumentException e) {
            System.err.println("Usage: ApiDiffCli --base <ref> --head <ref> [--files <glob>]");
            System.err.println(e.getMessage());
            System.exit(2);
            return;
        }

        Result result = run(parsed.base, parsed.head, parsed.filesGlob);
        print(parsed.base, parsed.head, parsed.filesGlob, result);
        System.exit(result.hasBlockingChanges() ? 1 : 0);
    }

    /**
     * Runs the audit and returns the aggregate result, without printing or
     * exiting. Exposed at package visibility for testability.
     */
    static Result run(String base, String head, String filesGlob) throws IOException {
        Pattern globPattern = globToPattern(filesGlob);

        TreeSet<String> candidatePaths = new TreeSet<>();
        candidatePaths.addAll(listTree(base));
        candidatePaths.addAll(listTree(head));

        List<String> matchedPaths = new ArrayList<>();
        for (String path : candidatePaths) {
            if (globPattern.matcher(path).matches()) {
                matchedPaths.add(path);
            }
        }

        int totalRemoved = 0;
        int totalAdded = 0;
        int totalChanged = 0;
        List<String> detailLines = new ArrayList<>();

        for (String path : matchedPaths) {
            JavaSurface beforeSurface = surfaceAt(base, path);
            JavaSurface afterSurface = surfaceAt(head, path);
            DiffReport report = DiffReport.compare(beforeSurface, afterSurface);

            if (!report.removed().isEmpty() || !report.added().isEmpty() || !report.signatureChanged().isEmpty()) {
                detailLines.add("  " + path + ":");
                for (DiffReport.Removed removed : report.removed()) {
                    detailLines.add("    REMOVED  " + describe(removed.symbol()));
                }
                for (DiffReport.Added added : report.added()) {
                    detailLines.add("    ADDED    " + describe(added.symbol()));
                }
                for (DiffReport.SignatureChanged changed : report.signatureChanged()) {
                    detailLines.add("    CHANGED  " + describe(changed.before()) + "  ->  " + describe(changed.after()));
                }
            }

            totalRemoved += report.removed().size();
            totalAdded += report.added().size();
            totalChanged += report.signatureChanged().size();
        }

        return new Result(matchedPaths.size(), totalRemoved, totalAdded, totalChanged, detailLines);
    }

    /**
     * Reads and parses a file's surface at a ref. A file that does not
     * exist at that ref (e.g. it was added or removed by the range) is
     * treated as an empty surface rather than an error. A genuine
     * Git/blob/process failure (bad ref, git not found, etc.) is
     * propagated to the caller, making the CLI exit non-zero.
     */
    private static JavaSurface surfaceAt(String ref, String path) throws IOException {
        String source;
        try {
            source = GitBlobReader.read(ref, path);
        } catch (IOException e) {
            if (isPathNotFound(e)) {
                return new JavaSurface(List.of());
            }
            throw e;
        }
        return JavaSurfaceExtractor.extract(path, source);
    }

    /**
     * Returns {@code true} if the exception from {@link GitBlobReader}
     * indicates a genuinely absent path rather than a process/ref failure.
     * git-show exits 128 for a missing file with a message containing
     * "does not exist in".
     */
    private static boolean isPathNotFound(IOException e) {
        String message = e.getMessage();
        if (message == null) {
            return false;
        }
        // git reports an absent path two different ways depending on whether the
        // file is present in the working tree:
        //   fatal: path 'X' does not exist in 'REF'
        //   fatal: path 'X' exists on disk, but not in 'REF'
        // The second form is what a file ADDED by the audited range produces, so
        // missing it makes the tool crash on exactly the commits it exists to audit.
        return message.contains("does not exist in")
                || message.contains("exists on disk, but not in");
    }

    private static String describe(JavaSurface.Symbol symbol) {
        return symbol.typeName() + "#" + symbol.signature() + " : " + symbol.returnType()
                + " (" + symbol.visibility() + ")";
    }

    private static void print(String base, String head, String filesGlob, Result result) {
        System.out.println("API-diff audit: " + base + " -> " + head + " (files: " + filesGlob + ")");
        System.out.println("Files scanned: " + result.filesScanned());
        System.out.println("Removed: " + result.removed()
                + "  Added: " + result.added()
                + "  Signature changed: " + result.changed());

        if (!result.detailLines().isEmpty()) {
            System.out.println("Details:");
            for (String line : result.detailLines()) {
                System.out.println(line);
            }
        }

        System.out.println(result.hasBlockingChanges()
                ? "RESULT: FAIL (unexpected API removals or signature changes detected)"
                : "RESULT: PASS (no unexpected API removals or signature changes)");
    }

    /**
     * Lists every file path in the tree at {@code ref} via
     * {@code git ls-tree -r --name-only <ref>}.
     */
    /**
     * Resolves the repository root so git invocations are independent of the
     * JVM's working directory. Returns {@code null} (meaning "inherit the
     * current directory") if the root cannot be determined, which keeps the
     * tool usable outside a git checkout rather than hard-failing.
     */
    private static java.io.File repoRoot() throws IOException {
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
        return new java.io.File(out);
    }

    private static List<String> listTree(String ref) throws IOException {
        ProcessBuilder builder = new ProcessBuilder("git", "ls-tree", "-r", "--name-only", ref);
        // git ls-tree scopes its output to the current directory prefix, so running
        // from a subproject (as `gradle :services:tools:api-diff:run` does) would list
        // only that subproject's files and silently scan zero of the paths the glob
        // targets -- reporting PASS without auditing anything. Anchor to the repo root.
        builder.directory(repoRoot());
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
            throw new IOException("Interrupted while listing tree for ref " + ref, e);
        }

        if (exitCode != 0) {
            throw new IOException("git ls-tree failed for ref " + ref + ": " + stderrBuffer.toString().trim());
        }

        List<String> paths = new ArrayList<>();
        for (String line : stdout.split("\n")) {
            String trimmed = line.strip();
            if (!trimmed.isEmpty()) {
                paths.add(trimmed);
            }
        }
        return paths;
    }

    private static String readStream(InputStream inputStream) throws IOException {
        StringBuilder builder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            char[] buffer = new char[8192];
            int read;
            while ((read = reader.read(buffer)) != -1) {
                builder.append(buffer, 0, read);
            }
        }
        return builder.toString();
    }

    /**
     * Translates a {@code /}-delimited glob (as produced by git, always
     * forward-slashed regardless of host OS) into a regex. Hand-rolled
     * rather than {@code java.nio.file.PathMatcher}, whose glob syntax ties
     * {@code /} to the platform separator (backslash on Windows) and would
     * silently fail to match git's paths there.
     */
    private static Pattern globToPattern(String glob) {
        StringBuilder regex = new StringBuilder();
        int i = 0;
        int length = glob.length();
        while (i < length) {
            if (glob.startsWith("**/", i)) {
                regex.append("(?:.*/)?");
                i += 3;
            } else if (glob.startsWith("**", i)) {
                regex.append(".*");
                i += 2;
            } else {
                char c = glob.charAt(i);
                if (c == '*') {
                    regex.append("[^/]*");
                } else if (c == '?') {
                    regex.append("[^/]");
                } else if ("\\.[]{}()+-^$|".indexOf(c) >= 0) {
                    regex.append('\\').append(c);
                } else {
                    regex.append(c);
                }
                i += 1;
            }
        }
        return Pattern.compile(regex.toString());
    }

    /** Aggregate outcome of an audit run across every matched file. */
    record Result(int filesScanned, int removed, int added, int changed, List<String> detailLines) {
        boolean hasBlockingChanges() {
            return removed > 0 || changed > 0;
        }
    }

    private static final class Args {
        String base;
        String head;
        String filesGlob = DEFAULT_FILES_GLOB;

        static Args parse(String[] args) {
            Args result = new Args();
            for (int i = 0; i < args.length; i++) {
                switch (args[i]) {
                    case "--base" -> result.base = requireValue(args, ++i, "--base");
                    case "--head" -> result.head = requireValue(args, ++i, "--head");
                    case "--files" -> result.filesGlob = requireValue(args, ++i, "--files");
                    default -> throw new IllegalArgumentException("Unrecognized argument: " + args[i]);
                }
            }
            if (result.base == null || result.head == null) {
                throw new IllegalArgumentException("Both --base <ref> and --head <ref> are required");
            }
            return result;
        }

        private static String requireValue(String[] args, int index, String flag) {
            if (index >= args.length) {
                throw new IllegalArgumentException("Missing value for " + flag);
            }
            return args[index];
        }
    }
}
