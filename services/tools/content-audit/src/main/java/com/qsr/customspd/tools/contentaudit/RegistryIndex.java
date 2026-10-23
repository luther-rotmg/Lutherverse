package com.qsr.customspd.tools.contentaudit;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** References to content classes inside the registries: word tokens in Bestiary.kt /
 *  Generator.java, plus the class names core dungeon.json spawns by reflection.
 *  Loose by design (matching asset-audit); intentional non-registration rides the allowlist. */
public final class RegistryIndex {
    private static final Pattern SPAWN_LIST =
            Pattern.compile("\"(bestiary|extra_mobs|extra_items)\"\\s*:\\s*\\[([^\\]]*)\\]");
    private static final Pattern RARE_MOB = Pattern.compile("\"rare_mob\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern STRING = Pattern.compile("\"([^\"]+)\"");
    private static final Pattern TYPE = Pattern.compile("\"type\"\\s*:\\s*\"([^\"]+)\"");

    private final String bestiarySource;
    private final String generatorSource;
    private final Set<String> dungeonMobs = new TreeSet<>();
    private final Set<String> dungeonItems = new TreeSet<>();

    private RegistryIndex(String bestiarySource, String generatorSource, String dungeonJson) {
        this.bestiarySource = bestiarySource;
        this.generatorSource = generatorSource;
        indexDungeon(dungeonJson);
    }

    /** Empty index for the -Canary negative control. */
    public static RegistryIndex empty() { return new RegistryIndex("", "", ""); }

    /** Index over in-memory sources, for the -Canary control. */
    static RegistryIndex of(String bestiarySource, String generatorSource, String dungeonJson) {
        return new RegistryIndex(bestiarySource, generatorSource, dungeonJson);
    }

    public static RegistryIndex load(File repoRoot) throws IOException {
        return new RegistryIndex(
                read(repoRoot, "core/src/main/java/com/qsr/customspd/actors/mobs/Bestiary.kt"),
                read(repoRoot, "core/src/main/java/com/qsr/customspd/items/Generator.java"),
                read(repoRoot, "core/src/main/assets/dungeon/dungeon.json"));
    }

    private static String read(File repoRoot, String rel) throws IOException {
        Path p = repoRoot.toPath().resolve(rel);
        return Files.exists(p) ? Files.readString(p) : "";
    }

    /** Bestiary.kt reflects each level's "bestiary" and "rare_mob" entries into
     *  com.qsr.customspd.actors.mobs.<entry>; RegularLevel does the same for each
     *  "extra_mobs" type, and for "extra_items" types under com.qsr.customspd.items. */
    private void indexDungeon(String json) {
        Matcher list = SPAWN_LIST.matcher(json);
        while (list.find()) {
            String key = list.group(1);
            Matcher m = (key.equals("bestiary") ? STRING : TYPE).matcher(list.group(2));
            while (m.find()) {
                (key.equals("extra_items") ? dungeonItems : dungeonMobs).add(simpleName(m.group(1)));
            }
        }
        Matcher rare = RARE_MOB.matcher(json);
        while (rare.find()) dungeonMobs.add(simpleName(rare.group(1)));
    }

    private static String simpleName(String packageRelative) {
        return packageRelative.substring(packageRelative.lastIndexOf('.') + 1);
    }

    public boolean bestiaryReferences(String simpleName) {
        return wordPresent(bestiarySource, simpleName) || dungeonMobs.contains(simpleName);
    }

    public boolean generatorReferences(String simpleName) {
        return wordPresent(generatorSource, simpleName) || dungeonItems.contains(simpleName);
    }

    private static boolean wordPresent(String haystack, String word) {
        if (word == null) return false;
        return Pattern.compile("\\b" + Pattern.quote(word) + "\\b").matcher(haystack).find();
    }
}
