package com.qsr.customspd.tools.deletionaudit;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
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

    private DeletionAuditCli() {
    }

    public static void main(String[] args) throws IOException {
        Args parsed;
        try {
            parsed = Args.parse(args);
        } catch (IllegalArgumentException e) {
            System.err.println("Usage: DeletionAuditCli --base <ref> --head <ref> "
                    + "[--files <glob>] [--min-shrink <n>] [--allowlist <path>]");
            System.err.println(e.getMessage());
            System.exit(2);
            return;
        }

        Result result = run(parsed.base, parsed.head, parsed.filesGlob, parsed.minShrink,
                Allowlist.load(parsed.allowlist));
        print(parsed, result);
        System.exit(result.hasFindings() ? 1 : 0);
    }

    /** Runs the audit without printing or exiting. Exposed for testability. */
    public static Result run(String base, String head, String filesGlob, int minShrink,
                             Allowlist allowlist) throws IOException {
        Pattern globPattern = globToPattern(filesGlob);

        TreeSet<String> candidatePaths = new TreeSet<>();
        candidatePaths.addAll(GitCommands.listTree(base));
        candidatePaths.addAll(GitCommands.listTree(head));

        List<String> matchedPaths = new ArrayList<>();
        for (String path : candidatePaths) {
            if (globPattern.matcher(path).matches()) {
                matchedPaths.add(path);
            }
        }

        int totalDeleted = 0;
        int totalShrunk = 0;
        List<String> detailLines = new ArrayList<>();

        for (String path : matchedPaths) {
            InventoryDiff.Report report = InventoryDiff.compare(
                    inventoryAt(base, path), inventoryAt(head, path), minShrink);

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
    private static CallableInventory inventoryAt(String ref, String path) throws IOException {
        String source;
        try {
            source = GitCommands.readBlob(ref, path);
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

        System.out.println(result.hasFindings()
                ? "RESULT: FAIL (unreviewed removals detected; review then add to --allowlist)"
                : "RESULT: PASS (no unreviewed removals)");
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
    }

    private static final class Args {
        String base;
        String head;
        String filesGlob = DEFAULT_FILES_GLOB;
        int minShrink = DEFAULT_MIN_SHRINK;
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
