package com.qsr.customspd.tools.contentaudit;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public final class SpriteIndex {
    // Matches e.g.   RAT("sprites/mobs/rat.png"),
    private static final Pattern ENTRY =
            Pattern.compile("^\\s*([A-Z][A-Z0-9_]*)\\s*\\(\\s*\"([^\"]+)\"");
    // Matches   GeneralAsset.RAT   inside a *Sprite source file.
    private static final Pattern ASSET_REF =
            Pattern.compile("GeneralAsset\\.([A-Z][A-Z0-9_]*)");

    private final Map<String, String> assetToPath;   // RAT -> sprites/mobs/rat.png
    private final Map<String, String> spriteToAsset;  // RatSprite -> RAT
    private final File assetsRoot;

    private SpriteIndex(Map<String, String> assetToPath, Map<String, String> spriteToAsset, File assetsRoot) {
        this.assetToPath = assetToPath;
        this.spriteToAsset = spriteToAsset;
        this.assetsRoot = assetsRoot;
    }

    /** Empty index for the -Canary negative control (nothing is wired). */
    public static SpriteIndex empty() { return new SpriteIndex(Map.of(), Map.of(), new File(".")); }

    public static SpriteIndex load(File repoRoot) throws IOException {
        Path base = repoRoot.toPath();
        Map<String, String> assetToPath = new HashMap<>();
        Path kt = base.resolve("core/src/main/java/com/qsr/customspd/assets/GeneralAsset.kt");
        if (Files.exists(kt)) {
            for (String line : Files.readAllLines(kt)) {
                Matcher m = ENTRY.matcher(line);
                if (m.find()) assetToPath.put(m.group(1), m.group(2));
            }
        }
        Map<String, String> spriteToAsset = new HashMap<>();
        Path sprites = base.resolve("core/src/main/java/com/qsr/customspd/sprites");
        if (Files.isDirectory(sprites)) {
            try (Stream<Path> walk = Files.walk(sprites)) {
                for (Path p : (Iterable<Path>) walk::iterator) {
                    String fn = p.getFileName().toString();
                    if (!fn.endsWith(".java")) continue;
                    Matcher m = ASSET_REF.matcher(Files.readString(p));
                    if (m.find()) spriteToAsset.put(fn.substring(0, fn.length() - ".java".length()), m.group(1));
                }
            }
        }
        return new SpriteIndex(assetToPath, spriteToAsset, base.resolve("core/src/main/assets").toFile());
    }

    /** Item I1: GeneralAsset.<assetName> resolves to a PNG that exists. */
    public boolean spriteExists(String assetName) {
        String path = assetName == null ? null : assetToPath.get(assetName);
        return path != null && new File(assetsRoot, path).isFile();
    }

    /** Mob M1: <spriteClass> textures with a GeneralAsset whose PNG exists. */
    public boolean mobSpriteExists(String spriteClass) {
        String asset = spriteClass == null ? null : spriteToAsset.get(spriteClass);
        return asset != null && spriteExists(asset);
    }
}
