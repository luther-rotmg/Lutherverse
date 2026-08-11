package com.qsr.customspd.tools.deletionaudit;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

/**
 * CLI entry point for the silent-deletion auditor.
 *
 * <p>Answers the one question api-diff structurally cannot: did any code quietly
 * disappear between two refs? api-diff compares the public/protected declaration
 * surface, so it is blind to private members and to statements removed from
 * inside a body whose signature never changed.
 *
 * <p>Exits non-zero when any non-allowlisted removal is found, so it can gate a build.
 */
public final class DeletionAuditCli {

    static final String DEFAULT_FILES_GLOB = "core/src/main/java/**/*.java";
    static final int DEFAULT_MIN_SHRINK = 3;
    static final int DEFAULT_MAX_FINDINGS = 0;

    private DeletionAuditCli() {
    }

    public static void main(String[] args) throws IOException {
        Args parsed;
        try {
            parsed = Args.parse(args);
        } catch (IllegalArgumentException e) {
            System.err.println("Usage: DeletionAuditCli --base <ref> --head <ref> "
                    + "[--files <glob>] [--min-shrink <n>] [--allowlist <path>] "
                    + "[--max-findings <n>]");
            System.err.println(e.getMessage());
            System.exit(2);
            return;
        }

        Path allowlistPath = resolveAgainstRepoRoot(parsed.allowlist);
        if (allowlistPath != null && !Files.exists(allowlistPath)) {
            // Failing loudly here is deliberate. Silently loading an empty allowlist
            // is how api-diff printed PASS while auditing nothing: a path problem
            // that looks like a clean result.
            System.err.println("Allowlist not found: " + allowlistPath);
            System.exit(2);
            return;
        }

        Result result = run(parsed.base, parsed.head, parsed.filesGlob, parsed.minShrink,
                Allowlist.load(allowlistPath));
        print(parsed, result);
        System.exit(result.exceeds(parsed.maxFindings) ? 1 : 0);
    }

    /**
     * Resolves a relative allowlist path against the repository root.
     *
     * <p>{@code gradle run} sets the JVM's working directory to the subproject, so
     * a repo-root-relative path passed on the command line would not exist and the
     * allowlist would load empty — silently reporting every reviewed removal again.
     * This is the same defect class that made api-diff scan zero files.
     */
    private static Path resolveAgainstRepoRoot(Path path) throws IOException {
        if (path == null || path.isAbsolute()) {
            return path;
        }
        File root = GitCommands.repoRoot();
        return root == null ? path : root.toPath().resolve(path);
    }

    /** Runs the audit without printing or exiting. Exposed for testability. */
    public static Result run(String base, String head, String filesGlob, int minShrink,
                             Allowlist allowlist) throws IOException {
        return run(GitCommands.repoRoot(), base, head, filesGlob, minShrink, allowlist);
    }

    /**
     * Overload taking an explicit repository root, so a test can point the audit at an
     * isolated fixture repo instead of this project's own history.
     */
    static Result run(File repoRoot, String base, String head, String filesGlob, int minShrink,
                       Allowlist allowlist) throws IOException {
        Pattern globPattern = globToPattern(filesGlob);

        TreeSet<String> candidatePaths = new TreeSet<>();
        candidatePaths.addAll(GitCommands.listTree(repoRoot, base));
        candidatePaths.addAll(GitCommands.listTree(repoRoot, head));

        List<String> matchedPaths = new ArrayList<>();
        for (String path : candidatePaths) {
            if (globPattern.matcher(path).matches()) {
                matchedPaths.add(path);
            }
        }
        Set<String> matchedPathSet = new HashSet<>(matchedPaths);

        // A moved file has no path in common between base and head, so comparing
        // each path against itself reports every one of its callables as DELETED.
        // Where git recognises a pure rename, compare the old path's inventory
        // against the new path's content instead, and skip auditing the new path
        // a second time as if it were an unrelated addition.
        Map<String, String> renames = GitCommands.detectRenames(repoRoot, base, head);
        Set<String> renameTargetsToSkip = new HashSet<>();
        for (Map.Entry<String, String> rename : renames.entrySet()) {
            if (matchedPathSet.contains(rename.getKey()) && matchedPathSet.contains(rename.getValue())) {
                renameTargetsToSkip.add(rename.getValue());
            }
        }

        int totalDeleted = 0;
        int totalShrunk = 0;
        List<String> detailLines = new ArrayList<>();

        for (String path : matchedPaths) {
            if (renameTargetsToSkip.contains(path)) {
                continue;
            }
            String headPath = renames.getOrDefault(path, path);
            InventoryDiff.Report report = InventoryDiff.compare(
                    inventoryAt(repoRoot, base, path), inventoryAt(repoRoot, head, headPath), minShrink);

            List<InventoryDiff.Deleted> deleted = report.deleted().stream()
                    .filter(d -> !allowlist.permits(d.key())).toList();
            List<InventoryDiff.Shrunk> shrunk = report.shrunk().stream()
                    .filter(s -> !allowlist.permits(s.key())).toList();

            if (!deleted.isEmpty() || !shrunk.isEmpty()) {
                detailLines.add("  " + path + ":");
                for (InventoryDiff.Deleted entry : deleted) {
                    detailLines.add("    DELETED  " + entry.key()
                            + " (" + entry.visibility() + ", " + entry.statementsLost() + " statements)");
                }
                for (InventoryDiff.Shrunk entry : shrunk) {
                    detailLines.add("    SHRUNK   " + entry.key()
                            + " (" + entry.before() + " -> " + entry.after() + " statements)");
                }
            }

            totalDeleted += deleted.size();
            totalShrunk += shrunk.size();
        }

        return new Result(matchedPaths.size(), totalDeleted, totalShrunk, detailLines);
    }

    /**
     * A file absent at a ref (added or removed by the range) is an empty
     * inventory rather than an error. A genuine git failure propagates.
     */
    private static CallableInventory inventoryAt(File repoRoot, String ref, String path) throws IOException {
        String source;
        try {
            source = GitCommands.readBlob(repoRoot, ref, path);
        } catch (IOException e) {
            if (GitCommands.isPathNotFound(e)) {
                return new CallableInventory(List.of());
            }
            throw e;
        }
        return InventoryExtractor.extract(path, source);
    }

    private static void print(Args args, Result result) {
        System.out.println("Deletion audit: " + args.base + " -> " + args.head
                + " (files: " + args.filesGlob + ", min-shrink: " + args.minShrink + ")");
        System.out.println("Files scanned: " + result.filesScanned());
        System.out.println("Deleted: " + result.deleted() + "  Shrunk: " + result.shrunk());

        if (!result.detailLines().isEmpty()) {
            System.out.println("Details:");
            for (String line : result.detailLines()) {
                System.out.println(line);
            }
        }

        if (result.exceeds(args.maxFindings)) {
            System.out.println("RESULT: FAIL (" + result.total() + " findings exceeds ceiling "
                    + args.maxFindings + "; review then allowlist, or raise the ceiling deliberately)");
        } else if (result.hasFindings()) {
            System.out.println("RESULT: PASS (" + result.total() + " known findings, ceiling "
                    + args.maxFindings + " -- these are TRACKED, NOT ACCEPTED; lower the ceiling as they are resolved)");
        } else {
            System.out.println("RESULT: PASS (no unreviewed removals)");
        }
    }

    /**
     * Translates a {@code /}-delimited glob into a regex. Hand-rolled rather than
     * {@code java.nio.file.PathMatcher}, whose glob syntax ties {@code /} to the
     * platform separator and would silently fail to match git's paths on Windows.
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

    /** Aggregate outcome across every matched file. */
    public record Result(int filesScanned, int deleted, int shrunk, List<String> detailLines) {
        public boolean hasFindings() {
            return deleted > 0 || shrunk > 0;
        }

        public int total() {
            return deleted + shrunk;
        }

        /**
         * Ratchet, matching the lint baselines: a known backlog is parked by a ceiling so
         * anything NEW fails. Unlike the allowlist this keeps every finding in the printed
         * output, because "tracked in a bead" and "reviewed and accepted" are different
         * states and only the latter belongs in reviewed-removals.txt.
         */
        public boolean exceeds(int maxFindings) {
            return total() > maxFindings;
        }
    }

    private static final class Args {
        String base;
        String head;
        String filesGlob = DEFAULT_FILES_GLOB;
        int minShrink = DEFAULT_MIN_SHRINK;
        int maxFindings = DEFAULT_MAX_FINDINGS;
        Path allowlist;

        static Args parse(String[] args) {
            Args result = new Args();
            for (int i = 0; i < args.length; i++) {
                switch (args[i]) {
                    case "--base" -> result.base = requireValue(args, ++i, "--base");
                    case "--head" -> result.head = requireValue(args, ++i, "--head");
                    case "--files" -> result.filesGlob = requireValue(args, ++i, "--files");
                    case "--min-shrink" ->
                            result.minShrink = Integer.parseInt(requireValue(args, ++i, "--min-shrink"));
                    case "--max-findings" ->
                            result.maxFindings = Integer.parseInt(requireValue(args, ++i, "--max-findings"));
                    case "--allowlist" ->
                            result.allowlist = Path.of(requireValue(args, ++i, "--allowlist"));
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
