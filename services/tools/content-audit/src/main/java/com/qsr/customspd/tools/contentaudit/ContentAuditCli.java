package com.qsr.customspd.tools.contentaudit;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/** CLI entry for the content completeness auditor. Scans the working tree; fails
 *  when non-allowlisted findings exceed the --max-findings ceiling. */
public final class ContentAuditCli {
    static final int MIN_ENTITIES = 50; // under this, the scan is broken, not clean

    private ContentAuditCli() {}

    public record Result(int entitiesScanned, List<Finding> findings) {}

    public static void main(String[] args) throws IOException {
        Path allowlistPath = null;
        int maxFindings = 0;
        boolean canary = false;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--allowlist" -> allowlistPath = Path.of(args[++i]);
                case "--max-findings" -> maxFindings = Integer.parseInt(args[++i]);
                case "-Canary" -> canary = true;
                default -> { System.err.println("Unrecognized argument: " + args[i]); System.exit(2); return; }
            }
        }

        File repoRoot = RepoRoot.find();
        if (repoRoot == null) { System.err.println("Not in a git repository"); System.exit(2); return; }

        Path allowlist = resolve(repoRoot, allowlistPath);
        if (allowlist != null && !Files.exists(allowlist)) {
            System.err.println("Allowlist not found: " + allowlist); System.exit(2); return;
        }

        if (canary) { System.exit(runCanary()); return; }

        Result result = run(repoRoot, Allowlist.load(allowlist));
        if (result.entitiesScanned() < MIN_ENTITIES) {
            // Reading almost nothing must not read as "all content complete" — the
            // same defect class that made api-diff print PASS while scanning zero files.
            System.err.println("Only " + result.entitiesScanned() + " entities scanned (< "
                    + MIN_ENTITIES + ") -- the scan is broken, not clean");
            System.exit(2);
            return;
        }
        print(result, maxFindings);
        System.exit(result.findings().size() > maxFindings ? 1 : 0);
    }

    private static Path resolve(File repoRoot, Path p) {
        if (p == null || p.isAbsolute()) return p;
        return repoRoot.toPath().resolve(p);
    }

    /** Runs the audit without printing/exiting. Exposed for tests. */
    public static Result run(File repoRoot, Allowlist allowlist) throws IOException {
        SpriteIndex sprites = SpriteIndex.load(repoRoot);
        MessageIndex msgs = MessageIndex.load(repoRoot);
        RegistryIndex reg = RegistryIndex.load(repoRoot);

        List<ContentClass> classes = new ArrayList<>();
        classes.addAll(parseDir(repoRoot, "core/src/main/java/com/qsr/customspd/actors/mobs"));
        classes.addAll(parseDir(repoRoot, "core/src/main/java/com/qsr/customspd/items"));

        List<EntityGraph.Entity> entities = EntityGraph.build(classes);
        List<Finding> findings = new ArrayList<>();
        for (EntityGraph.Entity e : entities) {
            for (Finding f : Checks.run(e, sprites, msgs, reg)) {
                if (!allowlist.permits(f.key())) findings.add(f);
            }
        }
        findings.sort((a, b) -> a.key().compareTo(b.key()));
        return new Result(entities.size(), findings);
    }

    private static List<ContentClass> parseDir(File repoRoot, String rel) throws IOException {
        Path dir = repoRoot.toPath().resolve(rel);
        List<ContentClass> out = new ArrayList<>();
        if (!Files.isDirectory(dir)) return out;
        try (Stream<Path> walk = Files.walk(dir)) {
            for (Path p : (Iterable<Path>) walk::iterator) {
                if (!p.toString().endsWith(".java")) continue;
                ContentClass c = ContentClassParser.parse(Files.readString(p));
                if (c != null) out.add(c);
            }
        }
        return out;
    }

    private static void print(Result result, int maxFindings) {
        System.out.println("Content audit: " + result.entitiesScanned() + " entities scanned");
        System.out.println("Findings: " + result.findings().size() + " (ceiling " + maxFindings + ")");
        for (Finding f : result.findings()) System.out.println("  " + f.message() + "   [" + f.key() + "]");
        if (result.findings().size() > maxFindings) {
            System.out.println("RESULT: FAIL (" + result.findings().size()
                    + " findings exceeds ceiling " + maxFindings + "; wire the content, allowlist it, or raise the ceiling deliberately)");
        } else if (!result.findings().isEmpty()) {
            System.out.println("RESULT: PASS (" + result.findings().size()
                    + " known findings within ceiling -- TRACKED, NOT ACCEPTED; lower the ceiling as they are fixed)");
        } else {
            System.out.println("RESULT: PASS (all scanned content fully wired)");
        }
    }

    /** Negative control: an in-memory broken entity MUST be flagged, else the tool is not checking. */
    static int runCanary() {
        EntityGraph.Entity broken = new EntityGraph.Entity("Mob",
                new ContentClass("CanaryMob", "com.qsr.customspd.actors.mobs", "Mob", false, "NoSuchSprite", null),
                "NoSuchSprite", null);
        // Empty indexes: nothing is wired, so every check must fire.
        SpriteIndex sprites = SpriteIndex.empty();
        MessageIndex msgs = MessageIndex.empty();
        RegistryIndex reg = RegistryIndex.empty();
        List<Finding> f = Checks.run(broken, sprites, msgs, reg);
        boolean caught = f.stream().anyMatch(x -> x.key().equals("Mob CanaryMob#M1"))
                && f.stream().anyMatch(x -> x.key().equals("Mob CanaryMob#M2"))
                && f.stream().anyMatch(x -> x.key().equals("Mob CanaryMob#M3"));
        System.out.println(caught
                ? "CANARY OK (the checks flag a deliberately-broken entity)"
                : "CANARY FAILED (broken entity not flagged -- the tool is not actually checking)");
        return caught ? 0 : 1;
    }
}
