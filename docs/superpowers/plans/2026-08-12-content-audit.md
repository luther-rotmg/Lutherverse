# content-audit Implementation Plan

> **Status (2026-08-14): DONE.** All 9 tasks' deliverables exist under `services/tools/content-audit/` (module registered in `settings.gradle`, depended on by `content-scaffold`). All 7 test classes (23 tests) pass with 0 failures/0 errors — see `build/test-results/test/*.xml`, timestamped 2026-08-13T02:18:37, including `CANARY OK`. `reviewed-exceptions.txt` is triaged with real entries (2026-08-12 first run: 314 entities/189 findings; 2026-08-13 Keybearer class-weapon exceptions). The gate is wired into `CLAUDE.md` (advisory, ceiling 189, over-reporting M3/I3 noted) and `.github/workflows/ci.yml` (content-audit step + canary step). Checkboxes below were backfilled to match reality — the work was already complete, just never marked (same situation as `2026-08-12-content-scaffold.md` before its backfill).

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a source-scanning gate that fails the build when any core `Mob`/`Item` is not fully wired (sprite, localization, registration), ratcheted like `deletion-audit` and proven-failable via `-Canary`.

**Architecture:** A standalone Gradle `application` module `services/tools/content-audit`, mirroring `deletion-audit`. It parses core's **working-tree** Java sources with JavaParser to enumerate content classes and extract their `spriteClass`/`image`, and line-scans the Kotlin `GeneralAsset`/`Bestiary`, Java `Generator`, and `.properties` files to know what is wired. It resolves inheritance, runs six checks, filters through an allowlist, and fails past a `--max-findings` ceiling.

**Tech Stack:** Java 17, JavaParser 3.25.5, JUnit 5. Gradle `application` plugin (gives the `run` task).

## Global Constraints

- **This module is Java 17** (like every `services/tools/*`), NOT the Java 8 game code. Modern Java is fine here.
- **Scans the working tree, not git refs.** Unlike `deletion-audit`, it reads files from disk under the repo root; it does not use `git show`/blobs.
- **Repo-root resolution is mandatory.** `gradle run` sets CWD to the subproject, so every path (allowlist, core sources) resolves against the repo root found via `git rev-parse --show-toplevel`. A relative path that silently resolves wrong is the exact defect class (`api-diff` scanned zero files) this repo exists to prevent.
- **Every gate must be provably able to fail.** The `-Canary` negative control is not optional; the tool must be demonstrated red before it is trusted green.
- **Ratchet, not baseline.** Findings are parked behind `--max-findings` and always printed (`TRACKED, NOT ACCEPTED`); permanent correct exceptions go in `reviewed-exceptions.txt`.
- **v1 scope: Mobs + Items only.** Bosses/biomes/talents/traps/plants are out of scope (bosses ride the allowlist).
- Package root: `com.qsr.customspd.tools.contentaudit`.
- No AI attribution in commit messages.

---

### Task 1: Module scaffold + registration

**Files:**
- Create: `services/tools/content-audit/build.gradle`
- Modify: `settings.gradle` (add the include next to the other tools)
- Create: `services/tools/content-audit/src/test/java/com/qsr/customspd/tools/contentaudit/ScaffoldTest.java`

**Interfaces:**
- Produces: a buildable Gradle module `:services:tools:content-audit` with JavaParser + JUnit 5 on the classpath and mainClass `com.qsr.customspd.tools.contentaudit.ContentAuditCli` (created in Task 7).

- [x] **Step 1: Write the build file**

`services/tools/content-audit/build.gradle`:
```groovy
plugins {
    id 'application'
    id 'java'
}

repositories { mavenCentral() }

dependencies {
    implementation 'com.github.javaparser:javaparser-core:3.25.5'
    testImplementation 'org.junit.jupiter:junit-jupiter:5.10.0'
    testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
}

application {
    mainClass = 'com.qsr.customspd.tools.contentaudit.ContentAuditCli'
}

test {
    useJUnitPlatform()
}
```

- [x] **Step 2: Register the module**

In `settings.gradle`, add after the `':services:tools:deletion-audit'` line:
```groovy
    include ':services:tools:content-audit'
```

- [x] **Step 3: Write a scaffold test**

`src/test/java/com/qsr/customspd/tools/contentaudit/ScaffoldTest.java`:
```java
package com.qsr.customspd.tools.contentaudit;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.javaparser.StaticJavaParser;
import org.junit.jupiter.api.Test;

class ScaffoldTest {
    @Test
    void javaParserIsOnTheClasspath() {
        // Proves the module builds and JavaParser resolves before any real code exists.
        assertTrue(StaticJavaParser.parse("class A {}").getType(0).getNameAsString().equals("A"));
    }
}
```

- [x] **Step 4: Run the test**

Run: `./gradlew.bat :services:tools:content-audit:test`
Expected: PASS (1 test).

- [x] **Step 5: Commit**

```bash
git add services/tools/content-audit/build.gradle settings.gradle services/tools/content-audit/src/test/java/com/qsr/customspd/tools/contentaudit/ScaffoldTest.java
git commit -m "feat(content-audit): scaffold the module"
```

---

### Task 2: RepoRoot + Allowlist

**Files:**
- Create: `.../contentaudit/RepoRoot.java`
- Create: `.../contentaudit/Allowlist.java`
- Test: `.../contentaudit/AllowlistTest.java`

**Interfaces:**
- Produces: `RepoRoot.find()` returns `java.io.File` (the repo top-level, or `null` if not in a repo). `Allowlist.load(Path)` returns an `Allowlist`; `allowlist.permits(String key)` returns boolean. Keys are the exact strings `Checks` will emit, e.g. `"Mob YogDzewa#M3"`.

- [x] **Step 1: Write the Allowlist test**

`AllowlistTest.java`:
```java
package com.qsr.customspd.tools.contentaudit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AllowlistTest {
    @Test
    void permitsListedKeysAndIgnoresCommentsAndBlanks(@TempDir Path dir) throws Exception {
        Path f = dir.resolve("exceptions.txt");
        Files.writeString(f, "# a comment\n\nMob YogDzewa#M3\nItem Amulet#I3\n");
        Allowlist a = Allowlist.load(f);
        assertTrue(a.permits("Mob YogDzewa#M3"));
        assertTrue(a.permits("Item Amulet#I3"));
        assertFalse(a.permits("# a comment"));
        assertFalse(a.permits("Mob Rat#M2"));
    }

    @Test
    void absentPathPermitsNothing() throws Exception {
        Allowlist a = Allowlist.load(Path.of("does-not-exist.txt"));
        assertFalse(a.permits("Mob Rat#M2"));
    }
}
```

- [x] **Step 2: Run to verify it fails**

Run: `./gradlew.bat :services:tools:content-audit:test`
Expected: FAIL (compile error, `Allowlist` not defined).

- [x] **Step 3: Implement RepoRoot and Allowlist**

`RepoRoot.java`:
```java
package com.qsr.customspd.tools.contentaudit;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/** Locates the repository top level. {@code gradle run} sets CWD to the subproject,
 *  so every working-tree path must resolve against this, not the CWD. */
public final class RepoRoot {
    private RepoRoot() {}

    public static File find() throws IOException {
        Process p = new ProcessBuilder("git", "rev-parse", "--show-toplevel")
                .redirectErrorStream(true).start();
        String line;
        try (BufferedReader r = new BufferedReader(
                new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
            line = r.readLine();
        }
        try {
            if (p.waitFor() != 0 || line == null || line.isBlank()) return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
        return new File(line.trim());
    }
}
```

`Allowlist.java` (identical shape to `deletion-audit/Allowlist.java`):
```java
package com.qsr.customspd.tools.contentaudit;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Entity/check keys a human reviewed and accepted as permanently correct.
 *  Format: one {@code "Type Name#Check"} key per line; blank lines and {@code #}
 *  comments ignored so each entry can carry a rationale above it. */
public final class Allowlist {
    private final Set<String> permitted;

    private Allowlist(Set<String> permitted) { this.permitted = permitted; }

    public static Allowlist load(Path path) throws IOException {
        if (path == null || !Files.exists(path)) return new Allowlist(Set.of());
        Set<String> keys = new HashSet<>();
        for (String line : Files.readAllLines(path)) {
            String t = line.strip();
            if (!t.isEmpty() && !t.startsWith("#")) keys.add(t);
        }
        return new Allowlist(keys);
    }

    public boolean permits(String key) { return permitted.contains(key); }
}
```

- [x] **Step 4: Run to verify it passes**

Run: `./gradlew.bat :services:tools:content-audit:test`
Expected: PASS.

- [x] **Step 5: Commit**

```bash
git add services/tools/content-audit/src
git commit -m "feat(content-audit): repo-root resolver and allowlist"
```

---

### Task 3: ContentClass model + JavaParser extraction

**Files:**
- Create: `.../contentaudit/ContentClass.java`
- Create: `.../contentaudit/ContentClassParser.java`
- Test: `.../contentaudit/ContentClassParserTest.java`

**Interfaces:**
- Produces: `record ContentClass(String simpleName, String packageName, String superSimpleName, boolean isAbstract, String spriteClass, String imageAsset)` where `spriteClass`/`imageAsset` are nullable. `ContentClassParser.parse(String source)` returns `ContentClass` for the primary type (or `null` if the file has no top-level class).

- [x] **Step 1: Write the parser test**

`ContentClassParserTest.java`:
```java
package com.qsr.customspd.tools.contentaudit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class ContentClassParserTest {
    @Test
    void extractsMobWithInheritedSpriteInInitializer() {
        String src = "package com.qsr.customspd.actors.mobs;\n"
                + "public class Rat extends Mob {\n"
                + "  { spriteClass = RatSprite.class; }\n"
                + "}\n";
        ContentClass c = ContentClassParser.parse(src);
        assertEquals("Rat", c.simpleName());
        assertEquals("com.qsr.customspd.actors.mobs", c.packageName());
        assertEquals("Mob", c.superSimpleName());
        assertFalse(c.isAbstract());
        assertEquals("RatSprite", c.spriteClass());
        assertNull(c.imageAsset());
    }

    @Test
    void extractsItemImageAsset() {
        String src = "package com.qsr.customspd.items.food;\n"
                + "public class SupplyRation extends Food {\n"
                + "  { image = GeneralAsset.SUPPLY_RATION; }\n"
                + "}\n";
        ContentClass c = ContentClassParser.parse(src);
        assertEquals("SupplyRation", c.simpleName());
        assertEquals("Food", c.superSimpleName());
        assertEquals("SUPPLY_RATION", c.imageAsset());
        assertNull(c.spriteClass());
    }

    @Test
    void marksAbstractClasses() {
        ContentClass c = ContentClassParser.parse("public abstract class Mob extends Char {}");
        assertEquals("Mob", c.simpleName());
        // abstract classes are enumerated but skipped by EntityGraph
        org.junit.jupiter.api.Assertions.assertTrue(c.isAbstract());
    }

    @Test
    void returnsNullForNoTopLevelClass() {
        assertNull(ContentClassParser.parse("package x;\n"));
    }
}
```

- [x] **Step 2: Run to verify it fails**

Run: `./gradlew.bat :services:tools:content-audit:test --tests '*ContentClassParserTest'`
Expected: FAIL (compile error).

- [x] **Step 3: Implement ContentClass and ContentClassParser**

`ContentClass.java`:
```java
package com.qsr.customspd.tools.contentaudit;

/** A parsed content class. {@code spriteClass} and {@code imageAsset} are the
 *  simple names assigned in this class's own body (null if it does not assign one;
 *  EntityGraph resolves inherited values). */
public record ContentClass(
        String simpleName,
        String packageName,
        String superSimpleName,
        boolean isAbstract,
        String spriteClass,
        String imageAsset) {}
```

`ContentClassParser.java`:
```java
package com.qsr.customspd.tools.contentaudit;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.ClassExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.NameExpr;

/** Parses one Java source file into a {@link ContentClass}. */
public final class ContentClassParser {
    private ContentClassParser() {}

    public static ContentClass parse(String source) {
        CompilationUnit cu;
        try {
            cu = StaticJavaParser.parse(source);
        } catch (RuntimeException e) {
            return null; // unparseable source is not content; callers skip nulls
        }
        ClassOrInterfaceDeclaration type = cu.findFirst(ClassOrInterfaceDeclaration.class)
                .filter(t -> !t.isInterface()).orElse(null);
        if (type == null) return null;

        String pkg = cu.getPackageDeclaration().map(p -> p.getNameAsString()).orElse("");
        String sup = type.getExtendedTypes().isEmpty()
                ? null : type.getExtendedTypes(0).getNameAsString();

        String spriteClass = assignedSimpleName(type, "spriteClass", /*classExpr=*/true);
        String imageAsset = assignedSimpleName(type, "image", /*classExpr=*/false);

        return new ContentClass(type.getNameAsString(), pkg, sup, type.isAbstract(),
                spriteClass, imageAsset);
    }

    /** Finds {@code <field> = X.class} (classExpr) or {@code <field> = Enum.MEMBER}
     *  (field access) anywhere in the class body and returns X / MEMBER, or null. */
    private static String assignedSimpleName(ClassOrInterfaceDeclaration type,
                                             String field, boolean classExpr) {
        for (AssignExpr assign : type.findAll(AssignExpr.class)) {
            if (!(assign.getTarget() instanceof NameExpr target)) continue;
            if (!target.getNameAsString().equals(field)) continue;
            Expression value = assign.getValue();
            if (classExpr && value instanceof ClassExpr ce) {
                return ce.getType().asString();
            }
            if (!classExpr && value instanceof FieldAccessExpr fa) {
                return fa.getNameAsString(); // e.g. GeneralAsset.SUPPLY_RATION -> SUPPLY_RATION
            }
        }
        return null;
    }
}
```

- [x] **Step 4: Run to verify it passes**

Run: `./gradlew.bat :services:tools:content-audit:test --tests '*ContentClassParserTest'`
Expected: PASS.

- [x] **Step 5: Commit**

```bash
git add services/tools/content-audit/src
git commit -m "feat(content-audit): parse content classes with JavaParser"
```

---

### Task 4: Working-tree indexes (sprites, messages, registration)

**Files:**
- Create: `.../contentaudit/SpriteIndex.java`
- Create: `.../contentaudit/MessageIndex.java`
- Create: `.../contentaudit/RegistryIndex.java`
- Test: `.../contentaudit/IndexesTest.java`

**Interfaces:**
- Produces:
  - `SpriteIndex.load(File repoRoot)` → `SpriteIndex`; `spriteExists(String assetName)` returns true iff `GeneralAsset.<assetName>`'s path resolves to a PNG that exists on disk.
  - `MessageIndex.load(File repoRoot)` → `MessageIndex`; `hasKey(String key)` returns true iff that key is present in any `.properties` file under `core/src/main/assets/messages/`.
  - `RegistryIndex.load(File repoRoot)` → `RegistryIndex`; `bestiaryReferences(String simpleName)` and `generatorReferences(String simpleName)` return booleans (word-token presence in `Bestiary.kt` / `Generator.java`).

- [x] **Step 1: Write the indexes test**

`IndexesTest.java` (fixture-based, so it does not depend on the live repo):
```java
package com.qsr.customspd.tools.contentaudit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class IndexesTest {
    private static void write(Path p, String s) throws Exception {
        Files.createDirectories(p.getParent());
        Files.writeString(p, s);
    }

    @Test
    void spriteIndexResolvesAssetPathToPng(@TempDir Path root) throws Exception {
        write(root.resolve("core/src/main/java/com/qsr/customspd/assets/GeneralAsset.kt"),
                "enum class GeneralAsset(val path: String) {\n"
                        + "    RAT(\"sprites/mobs/rat.png\"),\n"
                        + "    GHOST(\"sprites/mobs/ghost.png\"),\n}");
        write(root.resolve("core/src/main/assets/sprites/mobs/rat.png"), "png");
        // A sprite class textures with GeneralAsset.RAT; lets a mob resolve via spriteClass.
        write(root.resolve("core/src/main/java/com/qsr/customspd/sprites/RatSprite.java"),
                "class RatSprite { { texture(GeneralAsset.RAT); } }");
        write(root.resolve("core/src/main/java/com/qsr/customspd/sprites/GhostSprite.java"),
                "class GhostSprite { { texture(GeneralAsset.GHOST); } }");
        SpriteIndex s = SpriteIndex.load(root.toFile());
        assertTrue(s.spriteExists("RAT"));
        assertFalse(s.spriteExists("GHOST")); // declared but file missing
        assertFalse(s.spriteExists("NONEXISTENT"));
        assertTrue(s.mobSpriteExists("RatSprite"));    // RatSprite -> RAT -> rat.png exists
        assertFalse(s.mobSpriteExists("GhostSprite"));  // GhostSprite -> GHOST -> file missing
        assertFalse(s.mobSpriteExists("NoSuchSprite"));
    }

    @Test
    void messageIndexFindsKeysAcrossPropertiesFiles(@TempDir Path root) throws Exception {
        write(root.resolve("core/src/main/assets/messages/actors/actors.properties"),
                "actors.mobs.rat.name=marsupial rat\nactors.mobs.rat.desc=nasty\n");
        MessageIndex m = MessageIndex.load(root.toFile());
        assertTrue(m.hasKey("actors.mobs.rat.name"));
        assertTrue(m.hasKey("actors.mobs.rat.desc"));
        assertFalse(m.hasKey("actors.mobs.ghost.name"));
    }

    @Test
    void registryIndexDetectsWordTokenReferences(@TempDir Path root) throws Exception {
        write(root.resolve("core/src/main/java/com/qsr/customspd/actors/mobs/Bestiary.kt"),
                "fun mobClass() { if (cl == Rat::class.java) {} }");
        write(root.resolve("core/src/main/java/com/qsr/customspd/items/Generator.java"),
                "class Generator { Category c = new Category(Ration.class); }");
        RegistryIndex r = RegistryIndex.load(root.toFile());
        assertTrue(r.bestiaryReferences("Rat"));
        assertFalse(r.bestiaryReferences("Ration")); // substring of nothing; word-bounded
        assertTrue(r.generatorReferences("Ration"));
    }
}
```

- [x] **Step 2: Run to verify it fails**

Run: `./gradlew.bat :services:tools:content-audit:test --tests '*IndexesTest'`
Expected: FAIL (compile error).

- [x] **Step 3: Implement the three indexes**

`SpriteIndex.java` (resolves sprites two ways — an item's `image = GeneralAsset.X`
directly, and a mob's `spriteClass = XSprite` via the `GeneralAsset` that sprite class
textures with):
```java
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
```

`MessageIndex.java`:
```java
package com.qsr.customspd.tools.contentaudit;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;

/** All localization keys present under {@code core/src/main/assets/messages/}. */
public final class MessageIndex {
    private final Set<String> keys;

    private MessageIndex(Set<String> keys) { this.keys = keys; }

    /** Empty index for the -Canary negative control. */
    public static MessageIndex empty() { return new MessageIndex(Set.of()); }

    public static MessageIndex load(File repoRoot) throws IOException {
        Path msgs = repoRoot.toPath().resolve("core/src/main/assets/messages");
        Set<String> keys = new HashSet<>();
        if (Files.isDirectory(msgs)) {
            try (Stream<Path> walk = Files.walk(msgs)) {
                for (Path p : (Iterable<Path>) walk::iterator) {
                    if (!p.toString().endsWith(".properties")) continue;
                    for (String line : Files.readAllLines(p)) {
                        String t = line.strip();
                        if (t.isEmpty() || t.startsWith("#")) continue;
                        int eq = t.indexOf('=');
                        if (eq > 0) keys.add(t.substring(0, eq).strip());
                    }
                }
            }
        }
        return new MessageIndex(keys);
    }

    public boolean hasKey(String key) { return keys.contains(key); }
}
```

`RegistryIndex.java`:
```java
package com.qsr.customspd.tools.contentaudit;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
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
```

- [x] **Step 4: Run to verify it passes**

Run: `./gradlew.bat :services:tools:content-audit:test --tests '*IndexesTest'`
Expected: PASS.

- [x] **Step 5: Commit**

```bash
git add services/tools/content-audit/src
git commit -m "feat(content-audit): sprite, message, and registry indexes"
```

---

### Task 5: EntityGraph — enumerate entities and resolve inheritance

**Files:**
- Create: `.../contentaudit/EntityGraph.java`
- Test: `.../contentaudit/EntityGraphTest.java`

**Interfaces:**
- Consumes: `ContentClass` (Task 3).
- Produces: `record Entity(String kind, ContentClass cls, String resolvedSpriteClass, String resolvedImageAsset)` where `kind` is `"Mob"` or `"Item"`. `EntityGraph.build(List<ContentClass> all)` returns `List<Entity>` — every concrete class whose ancestry reaches `Mob` (in an `actors.mobs` package) or `Item` (in an `items` package), with `spriteClass`/`image` resolved from the nearest ancestor that assigns one. Abstract classes and the base-class allowlist (`Mob`, `Item`, `MeleeWeapon`, `MissileWeapon`, `Armor`, `Wand`, `Ring`, `Artifact`, `Potion`, `Scroll`, `Food`) are excluded from the output.

- [x] **Step 1: Write the EntityGraph test**

`EntityGraphTest.java`:
```java
package com.qsr.customspd.tools.contentaudit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class EntityGraphTest {
    private static ContentClass cc(String name, String pkg, String sup, boolean abs,
                                   String sprite, String image) {
        return new ContentClass(name, pkg, sup, abs, sprite, image);
    }

    @Test
    void enumeratesConcreteMobsAndResolvesInheritedSprite() {
        List<ContentClass> all = List.of(
                cc("Mob", "com.qsr.customspd.actors.mobs", "Char", true, null, null),
                cc("Rat", "com.qsr.customspd.actors.mobs", "Mob", false, "RatSprite", null),
                cc("Albino", "com.qsr.customspd.actors.mobs", "Rat", false, null, null));
        List<EntityGraph.Entity> es = EntityGraph.build(all);
        assertEquals(2, es.size()); // Mob is abstract+base, excluded
        EntityGraph.Entity albino = es.stream()
                .filter(e -> e.cls().simpleName().equals("Albino")).findFirst().orElseThrow();
        assertEquals("Mob", albino.kind());
        assertEquals("RatSprite", albino.resolvedSpriteClass()); // inherited from Rat
    }

    @Test
    void enumeratesItemsAndResolvesInheritedImage() {
        List<ContentClass> all = List.of(
                cc("Item", "com.qsr.customspd.items", "Object", true, null, null),
                cc("Food", "com.qsr.customspd.items.food", "Item", true, null, null),
                cc("SupplyRation", "com.qsr.customspd.items.food", "Food", false, null, "SUPPLY_RATION"));
        List<EntityGraph.Entity> es = EntityGraph.build(all);
        assertEquals(1, es.size()); // Item, Food are abstract/base
        assertEquals("Item", es.get(0).kind());
        assertEquals("SUPPLY_RATION", es.get(0).resolvedImageAsset());
    }

    @Test
    void excludesClassesWhoseAncestryNeverReachesMobOrItem() {
        List<ContentClass> all = List.of(
                cc("Helper", "com.qsr.customspd.utils", "Object", false, null, null));
        assertTrue(EntityGraph.build(all).isEmpty());
    }
}
```

- [x] **Step 2: Run to verify it fails**

Run: `./gradlew.bat :services:tools:content-audit:test --tests '*EntityGraphTest'`
Expected: FAIL (compile error).

- [x] **Step 3: Implement EntityGraph**

`EntityGraph.java`:
```java
package com.qsr.customspd.tools.contentaudit;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Builds the extends-ancestry graph, enumerates concrete Mob/Item entities,
 *  and resolves sprite/image touchpoints through inheritance. */
public final class EntityGraph {
    private static final Set<String> BASE_TYPES = Set.of(
            "Mob", "Item", "MeleeWeapon", "MissileWeapon", "Armor", "Wand",
            "Ring", "Artifact", "Potion", "Scroll", "Food");

    private EntityGraph() {}

    public record Entity(String kind, ContentClass cls,
                         String resolvedSpriteClass, String resolvedImageAsset) {}

    public static List<Entity> build(List<ContentClass> all) {
        Map<String, ContentClass> byName = new HashMap<>();
        for (ContentClass c : all) byName.put(c.simpleName(), c);

        List<Entity> out = new ArrayList<>();
        for (ContentClass c : all) {
            if (c.isAbstract() || BASE_TYPES.contains(c.simpleName())) continue;
            String root = ancestryRoot(c, byName); // "Mob", "Item", or null
            if (root == null) continue;
            out.add(new Entity(root, c,
                    resolve(c, byName, /*sprite=*/true),
                    resolve(c, byName, /*sprite=*/false)));
        }
        return out;
    }

    /** Walks up superclasses; returns "Mob" or "Item" if the ancestry reaches one, else null. */
    private static String ancestryRoot(ContentClass c, Map<String, ContentClass> byName) {
        ContentClass cur = c;
        int guard = 0;
        while (cur != null && guard++ < 50) {
            if ("Mob".equals(cur.simpleName())) return "Mob";
            if ("Item".equals(cur.simpleName())) return "Item";
            cur = cur.superSimpleName() == null ? null : byName.get(cur.superSimpleName());
        }
        return null;
    }

    /** Nearest assigned spriteClass (sprite=true) or imageAsset (sprite=false) in the ancestry. */
    private static String resolve(ContentClass c, Map<String, ContentClass> byName, boolean sprite) {
        ContentClass cur = c;
        int guard = 0;
        while (cur != null && guard++ < 50) {
            String v = sprite ? cur.spriteClass() : cur.imageAsset();
            if (v != null) return v;
            cur = cur.superSimpleName() == null ? null : byName.get(cur.superSimpleName());
        }
        return null;
    }
}
```

- [x] **Step 4: Run to verify it passes**

Run: `./gradlew.bat :services:tools:content-audit:test --tests '*EntityGraphTest'`
Expected: PASS.

- [x] **Step 5: Commit**

```bash
git add services/tools/content-audit/src
git commit -m "feat(content-audit): entity graph with inheritance resolution"
```

---

### Task 6: Checks — M1/M2/M3, I1/I2/I3

**Files:**
- Create: `.../contentaudit/Finding.java`
- Create: `.../contentaudit/Checks.java`
- Test: `.../contentaudit/ChecksTest.java`

**Interfaces:**
- Consumes: `EntityGraph.Entity`, `SpriteIndex`, `MessageIndex`, `RegistryIndex`.
- Produces: `record Finding(String key, String message)` where `key` is `"<kind> <simpleName>#<checkId>"` (e.g. `"Mob Ghost#M1"`). `Checks.run(Entity e, SpriteIndex sprites, MessageIndex msgs, RegistryIndex reg)` returns `List<Finding>` (empty if fully wired). Localization keys: mobs → `actors.mobs.<lowercase-simpleName>.{name,desc}`; items → `items.<package-after-"items.">.<lowercase-simpleName>.{name,desc}`.

- [x] **Step 1: Write the Checks test**

`ChecksTest.java`:
```java
package com.qsr.customspd.tools.contentaudit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ChecksTest {
    private static void write(Path p, String s) throws Exception {
        Files.createDirectories(p.getParent());
        Files.writeString(p, s);
    }

    /** A fully-wired mob fixture: sprite chain present, both localization keys, in Bestiary. */
    private static File wiredMobRepo(Path root) throws Exception {
        write(root.resolve("core/src/main/java/com/qsr/customspd/assets/GeneralAsset.kt"),
                "enum { RAT(\"sprites/mobs/rat.png\"), }");
        write(root.resolve("core/src/main/assets/sprites/mobs/rat.png"), "png");
        write(root.resolve("core/src/main/java/com/qsr/customspd/sprites/RatSprite.java"),
                "class RatSprite { { texture(GeneralAsset.RAT); } }");
        write(root.resolve("core/src/main/assets/messages/actors/actors.properties"),
                "actors.mobs.rat.name=rat\nactors.mobs.rat.desc=nasty\n");
        write(root.resolve("core/src/main/java/com/qsr/customspd/actors/mobs/Bestiary.kt"),
                "if (cl == Rat::class.java) {}");
        write(root.resolve("core/src/main/java/com/qsr/customspd/items/Generator.java"), "class Generator {}");
        return root.toFile();
    }

    private static EntityGraph.Entity mob(String name, String sprite, String image) {
        return new EntityGraph.Entity("Mob",
                new ContentClass(name, "com.qsr.customspd.actors.mobs", "Mob", false, sprite, image),
                sprite, image);
    }

    @Test
    void wiredMobHasNoFindings(@TempDir Path root) throws Exception {
        File repo = wiredMobRepo(root);
        List<Finding> f = Checks.run(mob("Rat", "RatSprite", null),
                SpriteIndex.load(repo), MessageIndex.load(repo), RegistryIndex.load(repo));
        assertTrue(f.isEmpty(), () -> "expected no findings, got " + f);
    }

    @Test
    void mobMissingEverythingReportsM1M2M3(@TempDir Path root) throws Exception {
        File repo = wiredMobRepo(root);
        // "Ghost": no sprite asset named GHOST, no localization, not in Bestiary
        List<Finding> f = Checks.run(mob("Ghost", "GhostSprite", null),
                SpriteIndex.load(repo), MessageIndex.load(repo), RegistryIndex.load(repo));
        List<String> ids = f.stream().map(Finding::key).collect(Collectors.toList());
        assertTrue(ids.contains("Mob Ghost#M1"));
        assertTrue(ids.contains("Mob Ghost#M2"));
        assertTrue(ids.contains("Mob Ghost#M3"));
        assertEquals(3, f.size());
    }
}
```

- [x] **Step 2: Run to verify it fails**

Run: `./gradlew.bat :services:tools:content-audit:test --tests '*ChecksTest'`
Expected: FAIL (compile error).

- [x] **Step 3: Implement Finding and Checks**

Mob M1 uses `SpriteIndex.mobSpriteExists(resolvedSpriteClass)` (spriteClass → GeneralAsset
→ PNG, built in Task 4); item I1 uses `SpriteIndex.spriteExists(resolvedImageAsset)` (the
item references the asset directly).

`Finding.java`:
```java
package com.qsr.customspd.tools.contentaudit;

public record Finding(String key, String message) {}
```

`Checks.java`:
```java
package com.qsr.customspd.tools.contentaudit;

import java.util.ArrayList;
import java.util.List;

/** Runs the six completeness checks against one entity. */
public final class Checks {
    private Checks() {}

    public static List<Finding> run(EntityGraph.Entity e, SpriteIndex sprites,
                                    MessageIndex msgs, RegistryIndex reg) {
        List<Finding> findings = new ArrayList<>();
        String kind = e.kind();
        String name = e.cls().simpleName();
        String prefix = kind + " " + name + "#";

        if (kind.equals("Mob")) {
            if (!sprites.mobSpriteExists(e.resolvedSpriteClass())) {
                findings.add(new Finding(prefix + "M1",
                        "M1 sprite: spriteClass " + e.resolvedSpriteClass() + " -> asset PNG NOT FOUND"));
            }
            String base = "actors.mobs." + name.toLowerCase();
            if (!msgs.hasKey(base + ".name") || !msgs.hasKey(base + ".desc")) {
                findings.add(new Finding(prefix + "M2", "M2 localization: " + base + ".{name,desc} missing"));
            }
            if (!reg.bestiaryReferences(name)) {
                findings.add(new Finding(prefix + "M3", "M3 registration: not referenced in Bestiary.kt"));
            }
        } else { // Item
            if (!sprites.spriteExists(e.resolvedImageAsset())) {
                findings.add(new Finding(prefix + "I1",
                        "I1 sprite: image GeneralAsset." + e.resolvedImageAsset() + " -> PNG NOT FOUND"));
            }
            String base = itemKeyBase(e.cls());
            if (!msgs.hasKey(base + ".name") || !msgs.hasKey(base + ".desc")) {
                findings.add(new Finding(prefix + "I2", "I2 localization: " + base + ".{name,desc} missing"));
            }
            if (!reg.generatorReferences(name)) {
                findings.add(new Finding(prefix + "I3", "I3 registration: not referenced in Generator.java"));
            }
        }
        return findings;
    }

    /** items.<package-after-"com.qsr.customspd.items">.<lowercase name> */
    static String itemKeyBase(ContentClass c) {
        String pkg = c.packageName();
        String marker = "com.qsr.customspd.items";
        String tail = pkg.length() > marker.length() ? pkg.substring(marker.length() + 1) : "";
        String sub = tail.isEmpty() ? "" : tail + ".";
        return "items." + sub + c.simpleName().toLowerCase();
    }
}
```

- [x] **Step 4: Run to verify it passes**

Run: `./gradlew.bat :services:tools:content-audit:test --tests '*ChecksTest'`
Expected: PASS.

- [x] **Step 5: Commit**

```bash
git add services/tools/content-audit/src
git commit -m "feat(content-audit): the six completeness checks"
```

---

### Task 7: CLI — wire it together, ratchet, print, exit, -Canary

**Files:**
- Create: `.../contentaudit/ContentAuditCli.java`
- Test: `.../contentaudit/ContentAuditCliTest.java`

**Interfaces:**
- Consumes: everything above.
- Produces: `main(String[])` with args `--allowlist <path>`, `--max-findings <n>`, `-Canary`; exit codes `0` (within ceiling), `1` (over ceiling / canary failed), `2` (usage error or scan read too few files). `ContentAuditCli.run(File repoRoot, Allowlist allowlist)` returns `Result(int entitiesScanned, List<Finding> findings)` for testability.

- [x] **Step 1: Write the CLI test**

`ContentAuditCliTest.java`:
```java
package com.qsr.customspd.tools.contentaudit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ContentAuditCliTest {
    private static void write(Path p, String s) throws Exception {
        Files.createDirectories(p.getParent());
        Files.writeString(p, s);
    }

    /** Fixture repo: one wired mob (Rat) and one broken mob (Ghost, no localization/sprite/registration). */
    private static File fixture(Path root) throws Exception {
        write(root.resolve("core/src/main/java/com/qsr/customspd/assets/GeneralAsset.kt"),
                "enum { RAT(\"sprites/mobs/rat.png\"), }");
        write(root.resolve("core/src/main/assets/sprites/mobs/rat.png"), "png");
        write(root.resolve("core/src/main/assets/messages/actors/actors.properties"),
                "actors.mobs.rat.name=rat\nactors.mobs.rat.desc=nasty\n");
        write(root.resolve("core/src/main/java/com/qsr/customspd/actors/mobs/Bestiary.kt"),
                "if (cl == Rat::class.java) {}");
        write(root.resolve("core/src/main/java/com/qsr/customspd/items/Generator.java"), "class Generator {}");
        write(root.resolve("core/src/main/java/com/qsr/customspd/sprites/RatSprite.java"),
                "class RatSprite { { texture(GeneralAsset.RAT); } }");
        write(root.resolve("core/src/main/java/com/qsr/customspd/actors/mobs/Mob.java"),
                "public abstract class Mob {}");
        write(root.resolve("core/src/main/java/com/qsr/customspd/actors/mobs/Rat.java"),
                "public class Rat extends Mob { { spriteClass = RatSprite.class; } }");
        write(root.resolve("core/src/main/java/com/qsr/customspd/actors/mobs/Ghost.java"),
                "public class Ghost extends Mob { { spriteClass = GhostSprite.class; } }");
        return root.toFile();
    }

    @Test
    void findsTheBrokenEntityButNotTheWiredOne(@TempDir Path root) throws Exception {
        ContentAuditCli.Result r = ContentAuditCli.run(fixture(root), Allowlist.load(null));
        assertEquals(2, r.entitiesScanned());
        assertTrue(r.findings().stream().anyMatch(f -> f.key().startsWith("Mob Ghost#")));
        assertFalse(r.findings().stream().anyMatch(f -> f.key().startsWith("Mob Rat#")));
    }

    @Test
    void allowlistSuppressesAKnownException(@TempDir Path root) throws Exception {
        File repo = fixture(root);
        Path al = root.resolve("exceptions.txt");
        Files.writeString(al, "Mob Ghost#M1\nMob Ghost#M2\nMob Ghost#M3\n");
        ContentAuditCli.Result r = ContentAuditCli.run(repo, Allowlist.load(al));
        assertTrue(r.findings().isEmpty());
    }
}
```

- [x] **Step 2: Run to verify it fails**

Run: `./gradlew.bat :services:tools:content-audit:test --tests '*ContentAuditCliTest'`
Expected: FAIL (compile error).

- [x] **Step 3: Implement ContentAuditCli**

`ContentAuditCli.java`:
```java
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
```

(The `empty()` factories the canary uses were added to each index in Task 4.)

- [x] **Step 4: Run to verify it passes**

Run: `./gradlew.bat :services:tools:content-audit:test`
Expected: PASS (all tests).

- [x] **Step 5: Prove the canary red-then-green**

Temporarily break `runCanary()` (change the required key to `"Mob CanaryMob#XX"` so `caught` is false). Run:
```bash
./gradlew.bat :services:tools:content-audit:run --args="-Canary"
```
Expected: prints `CANARY FAILED`, exits 1. Restore the correct keys, re-run, expect `CANARY OK`, exit 0. Note this in the commit.

- [x] **Step 6: Commit**

```bash
git add services/tools/content-audit/src
git commit -m "feat(content-audit): CLI, ratchet, and -Canary negative control (proven red then green)"
```

---

### Task 8: First real run — triage into reviewed-exceptions.txt and set the ceiling

**Files:**
- Create: `services/tools/content-audit/reviewed-exceptions.txt`

**Interfaces:**
- Consumes: the working CLI.
- Produces: a triaged allowlist and a documented ceiling number for the gate command (Task 9).

- [x] **Step 1: Run against the real repo, no ceiling suppression**

```bash
./gradlew.bat :services:tools:content-audit:run --args="--max-findings 0" 2>&1 | tee /tmp/content-audit-first-run.txt
```
Expected: a list of findings and `RESULT: FAIL`. Confirm `entities scanned` is well over `MIN_SOURCES` (non-vacuous).

- [x] **Step 2: Triage each finding into one of two buckets**

For every finding, decide:
- **Permanent correct exception** (boss not in Bestiary, quest/ability item not in Generator, an item whose localization key legitimately differs) → add to `reviewed-exceptions.txt` with a comment block explaining why.
- **Real gap to fix later** → leave it to be parked by the ceiling.

Seed `reviewed-exceptions.txt`:
```
# Reviewed permanent exceptions for content-audit. One "Type Name#Check" per line.
# Anything here is CORRECT FOREVER, not a gap. Gaps stay under the --max-findings ceiling.
#
# Reviewed 2026-08-12.

# --- Bosses are placed by their boss level, never spawned via Bestiary (M3) ---
# (add the actual boss classes surfaced by the first run, e.g.)
# Mob Goo#M3
# Mob DM300#M3
# Mob Tengu#M3

# --- Quest / ability-created items are not Generator-generated (I3) ---
# Item Amulet#I3
# Item SpiritBow#I3
```
Fill it in from the real first-run output (replace the commented examples with the actual classes).

- [x] **Step 3: Determine the ceiling**

Re-run with the allowlist applied:
```bash
./gradlew.bat :services:tools:content-audit:run --args="--allowlist services/tools/content-audit/reviewed-exceptions.txt --max-findings 999"
```
Note the remaining findings count `R` (genuine backlog). The gate ceiling is exactly `R` (parks today's backlog; anything new fails). Record `R` for Task 9.

- [x] **Step 4: Verify the ratchet holds**

```bash
./gradlew.bat :services:tools:content-audit:run --args="--allowlist services/tools/content-audit/reviewed-exceptions.txt --max-findings R"
```
Expected: `RESULT: PASS (R known findings ... TRACKED, NOT ACCEPTED)`, exit 0. Then run with `--max-findings (R-1)` and confirm it FAILs — proving the ceiling actually gates.

- [x] **Step 5: Commit**

```bash
git add services/tools/content-audit/reviewed-exceptions.txt
git commit -m "feat(content-audit): triage first run into reviewed-exceptions.txt, ceiling R"
```

---

### Task 9: README authoring guide + gate/CI integration

**Files:**
- Create: `services/tools/content-audit/README.md`
- Modify: `CLAUDE.md` (add to the "anything touching `core/`" gate block)
- Modify: `.github/workflows/ci.yml` (add a content-audit step asserting non-vacuity)

**Interfaces:**
- Consumes: the ceiling `R` from Task 8.

- [x] **Step 1: Write the README (this is the authoring guide)**

`services/tools/content-audit/README.md`:
```markdown
# content-audit

Fails the build when a core Mob or Item is not fully wired. The check list below
IS the "how to add content" checklist — satisfy every row and the gate passes.

```
./gradlew.bat :services:tools:content-audit:run \
  --args="--allowlist services/tools/content-audit/reviewed-exceptions.txt --max-findings <R>"
```

Exit 0 within the ceiling, 1 over it or canary failed, 2 if the scan itself broke.

## The touchpoints (the authoring checklist)

Adding a **mob** (`core/.../actors/mobs/YourMob.java`):
- **M1 Sprite** — set `spriteClass = YourMobSprite.class`; the sprite textures with a
  `GeneralAsset.YOUR_MOB`; the PNG exists at that asset's path.
- **M2 Localization** — `actors.mobs.yourmob.name` and `.desc` in `actors.properties`.
- **M3 Registration** — referenced in `Bestiary.kt`, or allowlisted (bosses, summons).

Adding an **item** (`core/.../items/<category>/YourItem.java`):
- **I1 Sprite** — set `image = GeneralAsset.YOUR_ITEM`; the PNG exists.
- **I2 Localization** — `items.<category>.youritem.name`/`.desc`.
- **I3 Registration** — referenced in a `Generator` `Category`, or allowlisted.

## Two knobs
- `reviewed-exceptions.txt` — permanent, correct exceptions (`Type Name#Check`), like
  bosses that legitimately never spawn via Bestiary.
- `--max-findings` — the ratchet ceiling parking the known backlog; lower it as content
  gets wired. Findings are always printed: TRACKED, NOT ACCEPTED.

## Provably able to fail
`--args="-Canary"` runs the checks against a deliberately-broken entity and fails if they
don't flag it. A green result you've never seen go red is not evidence.

## Scope
v1 audits core Mobs and Items. Bosses/biomes/talents and pack (JSON) content are out of
scope; the scaffold/generator that speeds authoring builds on this next.
```

- [x] **Step 2: Add to the CPDU gate block in CLAUDE.md**

In `CLAUDE.md`, in the "Then, for anything touching `core/`" section, after the `deletion-audit` command, add:
```bash
./gradlew.bat :services:tools:content-audit:run --quiet --args="--allowlist services/tools/content-audit/reviewed-exceptions.txt --max-findings <R>"
```
(replace `<R>` with the number from Task 8).

- [x] **Step 3: Add the CI step**

In `.github/workflows/ci.yml`, add a step after the deletion-audit step:
```yaml
      - name: content-audit (content completeness)
        run: |
          ./gradlew :services:tools:content-audit:run --args="--allowlist services/tools/content-audit/reviewed-exceptions.txt --max-findings <R>" | tee ca.txt
          grep -qE "entities scanned" ca.txt || (echo "content-audit scanned nothing" && exit 1)
          scanned=$(grep -oE "[0-9]+ entities scanned" ca.txt | grep -oE "[0-9]+")
          test "$scanned" -gt 50 || (echo "content-audit non-vacuity failed: $scanned entities" && exit 1)
```
Match the exact indentation/style of the surrounding steps; replace `<R>`.

- [x] **Step 4: Verify the full gate still passes**

```bash
./gradlew.bat :services:tools:content-audit:run --args="--allowlist services/tools/content-audit/reviewed-exceptions.txt --max-findings <R>"
```
Expected: `RESULT: PASS`, exit 0.

- [x] **Step 5: Commit**

```bash
git add services/tools/content-audit/README.md CLAUDE.md .github/workflows/ci.yml
git commit -m "docs(content-audit): authoring-guide README + gate and CI integration"
```

---

## Notes for the implementer

- **`StaticJavaParser` is not thread-safe** but this tool is single-threaded, so it is fine.
- **Kotlin files are line-scanned, not AST-parsed** (JavaParser is Java-only). `GeneralAsset.kt` entries and `Bestiary.kt` references are matched by regex — deliberately loose, exactly like `asset-audit`; intentional exceptions ride the allowlist.
- **When in doubt about a finding, do not weaken a check** — either fix the content or allowlist it with a written reason. That discipline is why this repo's gates are trusted.
- If a real mob/item's localization key genuinely does not follow the derived pattern, the cleanest v1 handling is an allowlist entry for its `M2`/`I2`; a per-entity key-override map is a fast-follow if such cases are common.
