package com.qsr.customspd.tools.contentscaffold;

import com.qsr.customspd.tools.contentaudit.RepoRoot;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Generates a compilable, fully-wired Mob/Item skeleton. Item path lands in Task 9. */
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
                System.exit(0);
            } catch (AnchorInserter.MissingAnchorException | IllegalArgumentException e) {
                System.err.println("content-scaffold: " + e.getMessage());
                System.exit(2);
            }
            return;
        }
        usage();
    }

    static GenResult generateMob(File repoRoot, String name, int depth) throws IOException {
        Names n = Names.of(name);
        Path base = repoRoot.toPath();
        List<String> created = new ArrayList<>(), modified = new ArrayList<>(), skipped = new ArrayList<>();

        // 1. class + sprite files (created if absent; skipped if present)
        writeIfAbsent(base.resolve("core/src/main/java/com/qsr/customspd/actors/mobs/" + n.className() + ".java"),
                Templates.mobClass(n), created, skipped);
        writeIfAbsent(base.resolve("core/src/main/java/com/qsr/customspd/sprites/" + n.className() + "Sprite.java"),
                Templates.mobSprite(n), created, skipped);

        // 2. placeholder PNG
        Path png = base.resolve("core/src/main/assets/" + n.mobAssetPath());
        if (Files.exists(png)) { skipped.add(png.toString()); }
        else {
            Files.createDirectories(png.getParent());
            try (InputStream in = ContentScaffoldCli.class.getResourceAsStream("/placeholder.png")) {
                if (in == null) throw new IOException("placeholder.png resource missing");
                Files.copy(in, png);
            }
            created.add(png.toString());
        }

        // 3. GeneralAsset entry. Idempotency token is the FULL generated line, not just
        // "<UPPER_SNAKE>(" -- a bare "WISP(" token would false-positive as a substring of
        // a longer existing entry like "GREATER_WISP(", silently skipping the insertion.
        applyInsert(base.resolve("core/src/main/java/com/qsr/customspd/assets/GeneralAsset.kt"),
                c -> AnchorInserter.insertAbove(c, "// @content-scaffold:mobs",
                        Templates.generalAssetLine(n, true), Templates.generalAssetLine(n, true)),
                "GeneralAsset:" + n.upperSnake(), modified, skipped);

        // 4. localization
        applyInsert(base.resolve("core/src/main/assets/messages/actors/actors.properties"),
                c -> AnchorInserter.insertAbove(c, "### @content-scaffold:mobs",
                        Templates.messageLines(n, true).stripTrailing(), "actors.mobs." + n.lower() + ".name="),
                "actors.mobs." + n.lower(), modified, skipped);

        // 5. dungeon.json registration
        applyInsert(base.resolve("core/src/main/assets/dungeon/dungeon.json"),
                c -> JsonBestiaryInserter.addMob(c, depth, n.className()),
                "dungeon:" + n.className(), modified, skipped);

        return new GenResult(created, modified, skipped);
    }

    private interface Insert { AnchorInserter.Result apply(String content); }

    private static void applyInsert(Path file, Insert ins, String label,
                                    List<String> modified, List<String> skipped) throws IOException {
        String content = Files.readString(file);
        AnchorInserter.Result r = ins.apply(content);
        if (r.inserted()) { Files.writeString(file, r.newContent()); modified.add(label); }
        else { skipped.add(label); }
    }

    private static void writeIfAbsent(Path file, String content, List<String> created, List<String> skipped)
            throws IOException {
        if (Files.exists(file)) { skipped.add(file.toString()); return; }
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
        created.add(file.toString());
    }

    private static Integer intFlag(String[] args, String flag) {
        for (int i = 0; i < args.length - 1; i++) if (args[i].equals(flag)) {
            try { return Integer.parseInt(args[i + 1]); } catch (NumberFormatException e) { return null; }
        }
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
        System.exit(2);
    }
}
