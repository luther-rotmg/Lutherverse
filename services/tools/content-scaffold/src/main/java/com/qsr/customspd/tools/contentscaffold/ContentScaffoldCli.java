package com.qsr.customspd.tools.contentscaffold;

import com.qsr.customspd.tools.contentaudit.RepoRoot;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Generates a compilable, fully-wired Mob/Item skeleton. */
public final class ContentScaffoldCli {
    private ContentScaffoldCli() {}

    public record GenResult(List<String> created, List<String> modified, List<String> skipped) {}

    public static void main(String[] args) throws IOException {
        if (args.length >= 1 && args[0].equals("mob")) {
            String name = args.length > 1 ? args[1] : null;
            Integer depth = intFlag(args, "--depth");
            if (name == null || depth == null) { usage(); return; }
            File repoRoot = RepoRoot.find();
            if (repoRoot == null) { System.err.println("Not in a git repository"); System.exit(2); return; }
            try {
                GenResult r = generateMob(repoRoot, name, depth);
                report(r);
                auditNewEntity(repoRoot, name);
                System.exit(0);
            } catch (AnchorInserter.MissingAnchorException | IllegalArgumentException e) {
                System.err.println("content-scaffold: " + e.getMessage());
                System.exit(2);
            }
            return;
        }
        if (args.length >= 1 && args[0].equals("item")) {
            String name = args.length > 1 ? args[1] : null;
            String category = strFlag(args, "--category");
            String tier = strFlag(args, "--tier");
            if (name == null || category == null || tier == null) { usage(); return; }
            File repoRoot = RepoRoot.find();
            if (repoRoot == null) { System.err.println("Not in a git repository"); System.exit(2); return; }
            try {
                GenResult r = generateItem(repoRoot, name, category, tier);
                report(r);
                auditNewEntity(repoRoot, name);
                System.exit(0);
            } catch (AnchorInserter.MissingAnchorException | IllegalArgumentException e) {
                System.err.println("content-scaffold: " + e.getMessage());
                System.exit(2);
            }
            return;
        }
        usage();
    }

    /**
     * Generation is compute-then-flush: every touchpoint is planned (read from disk and
     * validated in memory) before anything is written. addMob's depth check is one of
     * those plans, so a bad --depth throws in the planning phase, before the class file,
     * sprite, PNG, GeneralAsset entry, or localization entry ever reach disk.
     */
    static GenResult generateMob(File repoRoot, String name, int depth) throws IOException {
        Names n = Names.of(name);
        Path base = repoRoot.toPath();
        List<String> created = new ArrayList<>(), modified = new ArrayList<>(), skipped = new ArrayList<>();

        // ---- Plan (no writes yet) ----
        Path classFile = base.resolve("core/src/main/java/com/qsr/customspd/actors/mobs/" + n.className() + ".java");
        NewFile classPlan = planNewFile(classFile, () -> Templates.mobClass(n));

        Path spriteFile = base.resolve("core/src/main/java/com/qsr/customspd/sprites/" + n.className() + "Sprite.java");
        NewFile spritePlan = planNewFile(spriteFile, () -> Templates.mobSprite(n));

        Path png = base.resolve("core/src/main/assets/" + n.mobAssetPath());
        NewBinaryFile pngPlan = planPlaceholderPng(png);

        // GeneralAsset entry. Idempotency token is the FULL generated line, not just
        // "<UPPER_SNAKE>(" -- a bare "WISP(" token would false-positive as a substring of
        // a longer existing entry like "GREATER_WISP(", silently skipping the insertion.
        Insertion generalAsset = planInsert(base.resolve("core/src/main/java/com/qsr/customspd/assets/GeneralAsset.kt"),
                "GeneralAsset:" + n.upperSnake(),
                c -> AnchorInserter.insertAbove(c, "// @content-scaffold:mobs",
                        Templates.generalAssetLine(n, true), Templates.generalAssetLine(n, true)));

        // localization
        Insertion props = planInsert(base.resolve("core/src/main/assets/messages/actors/actors.properties"),
                "actors.mobs." + n.lower(),
                c -> AnchorInserter.insertAbove(c, "### @content-scaffold:mobs",
                        Templates.messageLines(n, true).stripTrailing(), "actors.mobs." + n.lower() + ".name="));

        // dungeon.json registration -- this is where an unknown --depth throws.
        Insertion dungeon = planInsert(base.resolve("core/src/main/assets/dungeon/dungeon.json"),
                "dungeon:" + n.className(),
                c -> JsonBestiaryInserter.addMob(c, depth, n.className()));

        // ---- Flush (every plan above succeeded) ----
        flushNewFile(classPlan, created, skipped);
        flushNewFile(spritePlan, created, skipped);
        flushNewBinaryFile(pngPlan, created, skipped);
        flushInsertion(generalAsset, modified, skipped);
        flushInsertion(props, modified, skipped);
        flushInsertion(dungeon, modified, skipped);

        return new GenResult(created, modified, skipped);
    }

    /**
     * Same compute-then-flush structure as {@link #generateMob}: GeneratorCategoryInserter's
     * category check runs during planning, so an unknown --category throws before any file
     * (class, PNG, GeneralAsset entry, localization entry) is written.
     */
    static GenResult generateItem(File repoRoot, String name, String category, String tier) throws IOException {
        Names n = Names.of(name);
        Path base = repoRoot.toPath();
        List<String> created = new ArrayList<>(), modified = new ArrayList<>(), skipped = new ArrayList<>();

        // ---- Plan (no writes yet) ----
        Path classFile = base.resolve("core/src/main/java/com/qsr/customspd/items/" + n.className() + ".java");
        NewFile classPlan = planNewFile(classFile, () -> Templates.itemClass(n));

        Path png = base.resolve("core/src/main/assets/" + n.itemAssetPath());
        NewBinaryFile pngPlan = planPlaceholderPng(png);

        // GeneralAsset entry. As with the mob path (see generateMob's comment), the
        // idempotency token must be the FULL generated line, not a bare "<UPPER_SNAKE>("
        // -- that would false-positive as a substring of a longer existing entry like
        // "SUPER_BERRY(" and silently skip the insertion.
        Insertion generalAsset = planInsert(base.resolve("core/src/main/java/com/qsr/customspd/assets/GeneralAsset.kt"),
                "GeneralAsset:" + n.upperSnake(),
                c -> AnchorInserter.insertAbove(c, "// @content-scaffold:items",
                        Templates.generalAssetLine(n, false), Templates.generalAssetLine(n, false)));

        Insertion props = planInsert(base.resolve("core/src/main/assets/messages/items/items.properties"),
                "items." + n.lower(),
                c -> AnchorInserter.insertAbove(c, "### @content-scaffold:items",
                        Templates.messageLines(n, false).stripTrailing(), "items." + n.lower() + ".name="));

        // Generator.java registration -- this is where an unknown --category throws.
        Insertion generator = planInsert(base.resolve("core/src/main/java/com/qsr/customspd/items/Generator.java"),
                "Generator:" + category + ":" + n.className(),
                c -> GeneratorCategoryInserter.addItem(c, category, n.className()));

        // ---- Flush (every plan above succeeded) ----
        flushNewFile(classPlan, created, skipped);
        flushNewBinaryFile(pngPlan, created, skipped);
        flushInsertion(generalAsset, modified, skipped);
        flushInsertion(props, modified, skipped);
        flushInsertion(generator, modified, skipped);

        return new GenResult(created, modified, skipped);
    }

    /**
     * Post-generate wiring check: run content-audit and print the new entity's findings.
     * The audit is a convenience on top of an already-successful generation, not part of
     * generation itself -- if it can't run (IOException), report it and continue rather
     * than letting the failure escape to the JVM default handler and mask the successful
     * generation with a stack trace and exit 1.
     */
    static void auditNewEntity(File repoRoot, String name) {
        try {
            var result = com.qsr.customspd.tools.contentaudit.ContentAuditCli.run(
                    repoRoot, com.qsr.customspd.tools.contentaudit.Allowlist.load(null));
            var mine = result.findings().stream().filter(f -> f.key().contains(" " + name + "#")).toList();
            if (mine.isEmpty()) {
                System.out.println("content-audit: " + name + " is fully wired.");
            } else {
                System.out.println("content-audit: " + name + " still has open touchpoints "
                        + "(M3/I3 registration findings are expected until the content-audit heuristic bead lands):");
                mine.forEach(f -> System.out.println("  " + f.message()));
            }
        } catch (IOException e) {
            System.err.println("content-scaffold: post-generate audit could not run: " + e.getMessage());
        }
    }

    private interface Insert { AnchorInserter.Result apply(String content); }
    private interface ContentSupplier { String get(); }

    /** A planned text-file write: either the file already exists (nothing to do) or
     *  {@code content} is the fully-rendered new file, ready to flush. */
    private record NewFile(Path path, boolean exists, String content) {}

    /** Same as {@link NewFile} but for the placeholder PNG, which is binary. */
    private record NewBinaryFile(Path path, boolean exists, byte[] content) {}

    /** A planned anchor insertion: the {@link AnchorInserter.Result} is already computed
     *  (and any {@code MissingAnchorException}/{@code IllegalArgumentException} already
     *  thrown) -- flushing just writes it out if {@code result.inserted()}. */
    private record Insertion(Path path, String label, AnchorInserter.Result result) {}

    private static NewFile planNewFile(Path file, ContentSupplier content) {
        if (Files.exists(file)) return new NewFile(file, true, null);
        return new NewFile(file, false, content.get());
    }

    private static void flushNewFile(NewFile plan, List<String> created, List<String> skipped) throws IOException {
        if (plan.exists()) { skipped.add(plan.path().toString()); return; }
        Files.createDirectories(plan.path().getParent());
        Files.writeString(plan.path(), plan.content());
        created.add(plan.path().toString());
    }

    private static NewBinaryFile planPlaceholderPng(Path png) throws IOException {
        if (Files.exists(png)) return new NewBinaryFile(png, true, null);
        return new NewBinaryFile(png, false, loadPlaceholderPngBytes());
    }

    private static byte[] loadPlaceholderPngBytes() throws IOException {
        try (InputStream in = ContentScaffoldCli.class.getResourceAsStream("/placeholder.png")) {
            if (in == null) throw new IOException("placeholder.png resource missing");
            return in.readAllBytes();
        }
    }

    private static void flushNewBinaryFile(NewBinaryFile plan, List<String> created, List<String> skipped)
            throws IOException {
        if (plan.exists()) { skipped.add(plan.path().toString()); return; }
        Files.createDirectories(plan.path().getParent());
        Files.write(plan.path(), plan.content());
        created.add(plan.path().toString());
    }

    private static Insertion planInsert(Path file, String label, Insert ins) throws IOException {
        String content = Files.readString(file);
        return new Insertion(file, label, ins.apply(content));
    }

    private static void flushInsertion(Insertion plan, List<String> modified, List<String> skipped)
            throws IOException {
        if (plan.result().inserted()) {
            Files.writeString(plan.path(), plan.result().newContent());
            modified.add(plan.label());
        } else {
            skipped.add(plan.label());
        }
    }

    private static Integer intFlag(String[] args, String flag) {
        for (int i = 0; i < args.length - 1; i++) if (args[i].equals(flag)) {
            try { return Integer.parseInt(args[i + 1]); } catch (NumberFormatException e) { return null; }
        }
        return null;
    }

    private static String strFlag(String[] args, String flag) {
        for (int i = 0; i < args.length - 1; i++) if (args[i].equals(flag)) return args[i + 1];
        return null;
    }

    private static void report(GenResult r) {
        r.created().forEach(c -> System.out.println("  created  " + c));
        r.modified().forEach(m -> System.out.println("  wired    " + m));
        r.skipped().forEach(s -> System.out.println("  skipped  " + s + " (already present)"));
        System.out.println("content-scaffold: done (" + r.created().size() + " created, "
                + r.modified().size() + " wired, " + r.skipped().size() + " skipped)");
    }

    private static void usage() {
        System.err.println("Usage: content-scaffold mob <Name> --depth <n>");
        System.err.println("       content-scaffold item <Name> --category <cat> --tier <n>");
        System.exit(2);
    }
}
