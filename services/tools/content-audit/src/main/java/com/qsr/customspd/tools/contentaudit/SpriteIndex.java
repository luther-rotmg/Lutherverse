package com.qsr.customspd.tools.contentaudit;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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
        List<NestedSprite> nested = new ArrayList<>();
        Path sprites = base.resolve("core/src/main/java/com/qsr/customspd/sprites");
        if (Files.isDirectory(sprites)) {
            try (Stream<Path> walk = Files.walk(sprites)) {
                for (Path p : (Iterable<Path>) walk::iterator) {
                    String fn = p.getFileName().toString();
                    if (!fn.endsWith(".java")) continue;
                    String source = Files.readString(p);
                    Matcher m = ASSET_REF.matcher(source);
                    if (m.find()) spriteToAsset.put(fn.substring(0, fn.length() - ".java".length()), m.group(1));
                    if (NESTED_HINT.matcher(source).find()) nested.addAll(nestedSprites(source));
                }
            }
        }
        // A mob names a nested sprite as Outer.Inner (MimicSprite.Golden); the file-name keys above
        // can't see it. A nested class textures with its own GeneralAsset ref, else inherits the
        // asset of the sprite class it extends (Golden/Crystal extend MimicSprite).
        Map<String, NestedSprite> nestedBySimple = new HashMap<>();
        for (NestedSprite n : nested) nestedBySimple.putIfAbsent(n.simpleName(), n);
        for (NestedSprite n : nested) {
            String asset = nestedAsset(n, spriteToAsset, nestedBySimple, 0);
            if (asset != null) spriteToAsset.put(n.qualifiedName(), asset);
        }
        return new SpriteIndex(assetToPath, spriteToAsset, base.resolve("core/src/main/assets").toFile());
    }

    // Cheap pre-filter so only sprite files with a nested class pay for a real parse.
    private static final Pattern NESTED_HINT = Pattern.compile("\\bstatic\\s+class\\b");

    private record NestedSprite(String qualifiedName, String simpleName, String superName, String ownAsset) {}

    private static List<NestedSprite> nestedSprites(String source) {
        CompilationUnit cu;
        try {
            cu = StaticJavaParser.parse(source);
        } catch (RuntimeException e) {
            return List.of(); // unparseable source defines no nested sprites
        }
        List<NestedSprite> out = new ArrayList<>();
        for (ClassOrInterfaceDeclaration type : cu.findAll(ClassOrInterfaceDeclaration.class)) {
            if (type.isInterface() || !type.isNestedType()) continue;
            StringBuilder qualified = new StringBuilder(type.getNameAsString());
            for (Node parent = type.getParentNode().orElse(null); parent != null; parent = parent.getParentNode().orElse(null)) {
                if (parent instanceof ClassOrInterfaceDeclaration outer) qualified.insert(0, outer.getNameAsString() + ".");
            }
            Matcher m = ASSET_REF.matcher(type.toString());
            String sup = type.getExtendedTypes().isEmpty() ? null : type.getExtendedTypes(0).getNameAsString();
            out.add(new NestedSprite(qualified.toString(), type.getNameAsString(), sup, m.find() ? m.group(1) : null));
        }
        return out;
    }

    private static String nestedAsset(NestedSprite n, Map<String, String> topLevel,
                                      Map<String, NestedSprite> nestedBySimple, int depth) {
        if (n.ownAsset() != null) return n.ownAsset();
        if (n.superName() == null || depth > 20) return null;
        String viaTop = topLevel.get(n.superName());
        if (viaTop != null) return viaTop;
        NestedSprite parent = nestedBySimple.get(n.superName());
        return parent == null || parent == n ? null : nestedAsset(parent, topLevel, nestedBySimple, depth + 1);
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
