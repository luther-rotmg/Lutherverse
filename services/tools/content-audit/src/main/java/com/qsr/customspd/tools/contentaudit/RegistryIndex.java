package com.qsr.customspd.tools.contentaudit;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

/** Word-token references to content classes inside the registries. Loose by
 *  design (matching asset-audit); intentional non-registration rides the allowlist. */
public final class RegistryIndex {
    private final String bestiarySource;
    private final String generatorSource;

    private RegistryIndex(String bestiarySource, String generatorSource) {
        this.bestiarySource = bestiarySource;
        this.generatorSource = generatorSource;
    }

    /** Empty index for the -Canary negative control. */
    public static RegistryIndex empty() { return new RegistryIndex("", ""); }

    public static RegistryIndex load(File repoRoot) throws IOException {
        return new RegistryIndex(
                read(repoRoot, "core/src/main/java/com/qsr/customspd/actors/mobs/Bestiary.kt"),
                read(repoRoot, "core/src/main/java/com/qsr/customspd/items/Generator.java"));
    }

    private static String read(File repoRoot, String rel) throws IOException {
        Path p = repoRoot.toPath().resolve(rel);
        return Files.exists(p) ? Files.readString(p) : "";
    }

    public boolean bestiaryReferences(String simpleName) { return wordPresent(bestiarySource, simpleName); }

    public boolean generatorReferences(String simpleName) { return wordPresent(generatorSource, simpleName); }

    private static boolean wordPresent(String haystack, String word) {
        return Pattern.compile("\\b" + Pattern.quote(word) + "\\b").matcher(haystack).find();
    }
}
