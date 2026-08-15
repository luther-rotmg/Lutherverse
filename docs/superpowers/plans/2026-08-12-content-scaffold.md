# content-scaffold Implementation Plan

> **Status (2026-08-14): DONE.** All 9 tasks' deliverables exist under `services/tools/content-scaffold/` (module registered in `settings.gradle`), and all 8 test classes pass with 0 failures/0 errors (see `build/test-results/test/*.xml`, timestamped 2026-08-13). Checkboxes below were backfilled to match reality — the work was already complete, just never marked. `build.gradle` diverges slightly from the Task 1 snippet (no `javaparser-core` dependency) per a later, deliberate removal.

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A generator that emits a compilable, fully-wired Mob/Item skeleton across all six content-audit touchpoints, so an author fills in the mechanic, not the wiring.

**Architecture:** A Java 17 Gradle `application` module `services/tools/content-scaffold`, mirroring `content-audit`. It writes new class/sprite files from string templates and inserts entries into shared registries by **targeted text insertion at anchor markers** (never parse-and-reserialize), plus a structural insertion into `dungeon.json` and a named `Generator.Category` array. It depends on the `content-audit` module to run a post-generate wiring check and to reuse `RepoRoot`.

**Tech Stack:** Java 17, JUnit 5, Gradle `application`. Depends on `:services:tools:content-audit`.

## Global Constraints

- Java 17 module; package root `com.qsr.customspd.tools.contentscaffold`.
- Working-tree writes only; resolve every path against the repo root (reuse `content-audit`'s `RepoRoot.find()`).
- **Never reserialize** a shared registry file — insert at an anchor / into a structural array with a minimal diff.
- **Idempotent:** re-running for an existing `<Name>` is a warned no-op per touchpoint, never a duplicate.
- **Fail-safe:** a missing anchor marker is a non-zero exit naming the marker, never a guessed insertion point.
- Mob sprite assets are `sprites/chars/<snake_name>.png`. Item sprite assets are `sprites/items/<snake_name>.png`.
- No AI attribution in commits.

---

### Task 1: Module scaffold + placeholder resource

**Files:**
- Create: `services/tools/content-scaffold/build.gradle`
- Modify: `settings.gradle`
- Create: `services/tools/content-scaffold/src/main/resources/placeholder.png` (16×16 magenta PNG)
- Test: `services/tools/content-scaffold/src/test/java/com/qsr/customspd/tools/contentscaffold/ScaffoldModuleTest.java`

**Interfaces:**
- Produces: buildable module `:services:tools:content-scaffold` with JUnit 5 and a dependency on `:services:tools:content-audit`; mainClass `com.qsr.customspd.tools.contentscaffold.ContentScaffoldCli` (created in Task 8).

- [x] **Step 1: Write build.gradle**

`services/tools/content-scaffold/build.gradle`:
```groovy
plugins {
    id 'application'
    id 'java'
}

repositories { mavenCentral() }

dependencies {
    implementation project(':services:tools:content-audit')
    implementation 'com.github.javaparser:javaparser-core:3.25.5'
    testImplementation 'org.junit.jupiter:junit-jupiter:5.10.0'
    testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
}

application {
    mainClass = 'com.qsr.customspd.tools.contentscaffold.ContentScaffoldCli'
}

test {
    useJUnitPlatform()
}
```

- [x] **Step 2: Register the module**

In `settings.gradle`, after the `':services:tools:content-audit'` line:
```groovy
    include ':services:tools:content-scaffold'
```

- [x] **Step 3: Create the placeholder PNG**

Create a 16×16 fully-magenta PNG at `services/tools/content-scaffold/src/main/resources/placeholder.png`. From the repo root run (Python is available):
```bash
python -c "from PIL import Image; Image.new('RGBA',(16,16),(255,0,255,255)).save('services/tools/content-scaffold/src/main/resources/placeholder.png')"
```
Verify it exists and is 16×16.

- [x] **Step 4: Write the scaffold test**

`ScaffoldModuleTest.java`:
```java
package com.qsr.customspd.tools.contentscaffold;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

class ScaffoldModuleTest {
    @Test
    void placeholderResourceIsOnTheClasspath() {
        assertNotNull(getClass().getResource("/placeholder.png"), "placeholder.png must be a bundled resource");
    }

    @Test
    void contentAuditDependencyResolves() {
        // Proves the cross-module dependency is wired: reference a content-audit type.
        assertNotNull(com.qsr.customspd.tools.contentaudit.RepoRoot.class);
    }
}
```

- [x] **Step 5: Run the test**

Run: `./gradlew.bat :services:tools:content-scaffold:test`
Expected: PASS (2 tests).

- [x] **Step 6: Commit**

```bash
git add services/tools/content-scaffold/build.gradle settings.gradle services/tools/content-scaffold/src
git commit -m "feat(content-scaffold): scaffold the module + placeholder resource"
```

---

### Task 2: Names — derivations

**Files:**
- Create: `.../contentscaffold/Names.java`
- Test: `.../contentscaffold/NamesTest.java`

**Interfaces:**
- Produces: `record Names(String className, String snake, String upperSnake)` and `Names.of(String pascal)`. Derived accessors: `mobAssetPath()` = `"sprites/chars/<snake>.png"`; `itemAssetPath()` = `"sprites/items/<snake>.png"`; `mobMessageBase()` = `"actors.mobs.<snake-no-underscores? no: lowercase className>"`. IMPORTANT: content-audit derives the mob localization key as `actors.mobs.<lowercase className>` (no underscores — e.g. `SewerCrab` → `actors.mobs.sewercrab`), and the item key as `items.<pkgSubpath>.<lowercase className>`. So `Names` exposes `lower()` = `className.toLowerCase()` for message keys, and `snake` (underscored) only for asset filenames and the GeneralAsset member.

- [x] **Step 1: Write the test**

`NamesTest.java`:
```java
package com.qsr.customspd.tools.contentscaffold;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class NamesTest {
    @Test
    void derivesFromPascalCase() {
        Names n = Names.of("SewerCrab");
        assertEquals("SewerCrab", n.className());
        assertEquals("sewer_crab", n.snake());       // asset filename + GeneralAsset base
        assertEquals("SEWER_CRAB", n.upperSnake());   // GeneralAsset member
        assertEquals("sewercrab", n.lower());          // message key leaf (matches content-audit)
        assertEquals("sprites/chars/sewer_crab.png", n.mobAssetPath());
        assertEquals("sprites/items/sewer_crab.png", n.itemAssetPath());
    }

    @Test
    void singleWordName() {
        Names n = Names.of("Wisp");
        assertEquals("wisp", n.snake());
        assertEquals("WISP", n.upperSnake());
        assertEquals("wisp", n.lower());
    }
}
```

- [x] **Step 2: Run to verify it fails**

Run: `./gradlew.bat :services:tools:content-scaffold:test --tests '*NamesTest'`
Expected: FAIL (compile error).

- [x] **Step 3: Implement Names**

`Names.java`:
```java
package com.qsr.customspd.tools.contentscaffold;

/** Derives the naming variants a content entity needs from its PascalCase class name. */
public record Names(String className, String snake, String upperSnake) {

    public static Names of(String pascal) {
        if (pascal == null || pascal.isBlank() || !Character.isUpperCase(pascal.charAt(0))) {
            throw new IllegalArgumentException("Name must be a non-empty PascalCase identifier: " + pascal);
        }
        StringBuilder snakeB = new StringBuilder();
        for (int i = 0; i < pascal.length(); i++) {
            char c = pascal.charAt(i);
            if (Character.isUpperCase(c) && i > 0) snakeB.append('_');
            snakeB.append(Character.toLowerCase(c));
        }
        String snake = snakeB.toString();
        return new Names(pascal, snake, snake.toUpperCase());
    }

    /** Message-key leaf, matching content-audit's derivation: the whole class name lowercased. */
    public String lower() { return className.toLowerCase(); }

    public String mobAssetPath() { return "sprites/chars/" + snake + ".png"; }

    public String itemAssetPath() { return "sprites/items/" + snake + ".png"; }
}
```

- [x] **Step 4: Run to verify it passes**

Run: `./gradlew.bat :services:tools:content-scaffold:test --tests '*NamesTest'`
Expected: PASS.

- [x] **Step 5: Commit**

```bash
git add services/tools/content-scaffold/src
git commit -m "feat(content-scaffold): name derivations"
```

---

### Task 3: AnchorInserter — marker-anchored idempotent insertion

**Files:**
- Create: `.../contentscaffold/AnchorInserter.java`
- Test: `.../contentscaffold/AnchorInserterTest.java`

**Interfaces:**
- Produces: `AnchorInserter.insertAbove(String fileContent, String marker, String lineToInsert, String idempotencyToken)` returns a `Result(String newContent, boolean inserted)`. If `idempotencyToken` already occurs in `fileContent`, returns `(fileContent, false)`. If `marker` is absent, throws `MissingAnchorException(marker)`. Otherwise inserts `lineToInsert` (with a trailing newline) on its own line immediately before the first line containing `marker`, preserving that line's indentation of `lineToInsert` is the caller's responsibility (pass it pre-indented). `class MissingAnchorException extends RuntimeException`.

- [x] **Step 1: Write the test**

`AnchorInserterTest.java`:
```java
package com.qsr.customspd.tools.contentscaffold;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AnchorInserterTest {
    private static final String FILE =
            "enum X {\n    ANKH(\"a.png\"),\n    // @content-scaffold:items\n}\n";

    @Test
    void insertsAboveTheMarker() {
        AnchorInserter.Result r = AnchorInserter.insertAbove(
                FILE, "// @content-scaffold:items", "    WISP(\"w.png\"),", "WISP(");
        assertTrue(r.inserted());
        assertEquals(
                "enum X {\n    ANKH(\"a.png\"),\n    WISP(\"w.png\"),\n    // @content-scaffold:items\n}\n",
                r.newContent());
    }

    @Test
    void idempotentWhenTokenAlreadyPresent() {
        String already = "enum X {\n    WISP(\"w.png\"),\n    // @content-scaffold:items\n}\n";
        AnchorInserter.Result r = AnchorInserter.insertAbove(
                already, "// @content-scaffold:items", "    WISP(\"w.png\"),", "WISP(");
        assertFalse(r.inserted());
        assertEquals(already, r.newContent());
    }

    @Test
    void missingMarkerThrows() {
        assertThrows(AnchorInserter.MissingAnchorException.class, () ->
                AnchorInserter.insertAbove("enum X {}\n", "// @content-scaffold:items", "x", "x"));
    }
}
```

- [x] **Step 2: Run to verify it fails**

Run: `./gradlew.bat :services:tools:content-scaffold:test --tests '*AnchorInserterTest'`
Expected: FAIL (compile error).

- [x] **Step 3: Implement AnchorInserter**

`AnchorInserter.java`:
```java
package com.qsr.customspd.tools.contentscaffold;

/** Inserts a line immediately above an anchor marker. Idempotent and fail-safe:
 *  never reserializes the file, never duplicates, never guesses when the marker is gone. */
public final class AnchorInserter {
    private AnchorInserter() {}

    public record Result(String newContent, boolean inserted) {}

    public static final class MissingAnchorException extends RuntimeException {
        public MissingAnchorException(String marker) { super("Anchor marker not found: " + marker); }
    }

    public static Result insertAbove(String fileContent, String marker, String lineToInsert,
                                     String idempotencyToken) {
        if (fileContent.contains(idempotencyToken)) {
            return new Result(fileContent, false);
        }
        int markerIdx = fileContent.indexOf(marker);
        if (markerIdx < 0) {
            throw new MissingAnchorException(marker);
        }
        // Find the start of the line the marker sits on.
        int lineStart = fileContent.lastIndexOf('\n', markerIdx) + 1; // 0 if marker on first line
        String before = fileContent.substring(0, lineStart);
        String after = fileContent.substring(lineStart);
        return new Result(before + lineToInsert + "\n" + after, true);
    }
}
```

- [x] **Step 4: Run to verify it passes**

Run: `./gradlew.bat :services:tools:content-scaffold:test --tests '*AnchorInserterTest'`
Expected: PASS.

- [x] **Step 5: Commit**

```bash
git add services/tools/content-scaffold/src
git commit -m "feat(content-scaffold): marker-anchored idempotent inserter"
```

---

### Task 4: JsonBestiaryInserter — dungeon.json registration

**Files:**
- Create: `.../contentscaffold/JsonBestiaryInserter.java`
- Test: `.../contentscaffold/JsonBestiaryInserterTest.java`

**Interfaces:**
- Produces: `JsonBestiaryInserter.addMob(String dungeonJson, int depth, String className)` returns `AnchorInserter.Result`. Adds a `"className"` element to the `bestiary` array of the level object whose `"depth": <depth>` matches, inserting it as the last element (minimal diff, preserves formatting). Idempotent: if that level's bestiary already contains `"className"`, returns `(unchanged, false)`. Throws `IllegalArgumentException` if no level with that depth, or that level has no `bestiary` array.

- [x] **Step 1: Write the test**

`JsonBestiaryInserterTest.java`:
```java
package com.qsr.customspd.tools.contentscaffold;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class JsonBestiaryInserterTest {
    private static final String JSON =
            "{\n  \"dungeon\": {\n    \"1\": {\n      \"depth\": 1,\n      \"bestiary\": [\n"
          + "        \"Rat\",\n        \"Snake\"\n      ]\n    },\n"
          + "    \"3\": {\n      \"depth\": 3,\n      \"bestiary\": [\n        \"Gnoll\"\n      ]\n    }\n  }\n}\n";

    @Test
    void addsToTheMatchingDepthAsLastElement() {
        AnchorInserter.Result r = JsonBestiaryInserter.addMob(JSON, 3, "Wisp");
        assertTrue(r.inserted());
        assertTrue(r.newContent().contains("\"Gnoll\",\n        \"Wisp\""),
                () -> "Wisp should follow Gnoll in the depth-3 bestiary:\n" + r.newContent());
        // depth-1 bestiary untouched
        assertTrue(r.newContent().contains("\"Rat\",\n        \"Snake\"\n      ]"));
    }

    @Test
    void idempotentWhenAlreadyInThatDepth() {
        AnchorInserter.Result first = JsonBestiaryInserter.addMob(JSON, 3, "Wisp");
        AnchorInserter.Result second = JsonBestiaryInserter.addMob(first.newContent(), 3, "Wisp");
        assertFalse(second.inserted());
    }

    @Test
    void unknownDepthThrows() {
        assertThrows(IllegalArgumentException.class, () -> JsonBestiaryInserter.addMob(JSON, 9, "Wisp"));
    }
}
```

- [x] **Step 2: Run to verify it fails**

Run: `./gradlew.bat :services:tools:content-scaffold:test --tests '*JsonBestiaryInserterTest'`
Expected: FAIL (compile error).

- [x] **Step 3: Implement JsonBestiaryInserter**

`JsonBestiaryInserter.java`:
```java
package com.qsr.customspd.tools.contentscaffold;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Adds a mob class name to the bestiary array of the dungeon.json level at a given depth.
 *  Text-targeted (not a JSON reserialize) so formatting and ordering are preserved. */
public final class JsonBestiaryInserter {
    private JsonBestiaryInserter() {}

    public static AnchorInserter.Result addMob(String dungeonJson, int depth, String className) {
        // Locate the level object whose "depth": <depth> appears, then its following "bestiary": [ ... ].
        Matcher depthM = Pattern.compile("\"depth\"\\s*:\\s*" + depth + "\\b").matcher(dungeonJson);
        if (!depthM.find()) {
            throw new IllegalArgumentException("No dungeon level with depth " + depth);
        }
        int bestiaryKey = dungeonJson.indexOf("\"bestiary\"", depthM.end());
        if (bestiaryKey < 0) {
            throw new IllegalArgumentException("Level at depth " + depth + " has no bestiary array");
        }
        int arrayOpen = dungeonJson.indexOf('[', bestiaryKey);
        int arrayClose = dungeonJson.indexOf(']', arrayOpen);
        if (arrayOpen < 0 || arrayClose < 0) {
            throw new IllegalArgumentException("Malformed bestiary array at depth " + depth);
        }
        String arrayBody = dungeonJson.substring(arrayOpen + 1, arrayClose);
        if (arrayBody.contains("\"" + className + "\"")) {
            return new AnchorInserter.Result(dungeonJson, false); // idempotent
        }
        // Find the last quoted element to copy its indentation and insert after it.
        Matcher last = Pattern.compile("(\\n(\\s*)\"[^\"]+\")(\\s*)$").matcher(arrayBody);
        String newBody;
        if (last.find()) {
            String indent = last.group(2);
            newBody = arrayBody.substring(0, last.end(1))
                    + ",\n" + indent + "\"" + className + "\""
                    + arrayBody.substring(last.end(1));
        } else {
            // empty array
            newBody = "\n        \"" + className + "\"\n      ";
        }
        String out = dungeonJson.substring(0, arrayOpen + 1) + newBody + dungeonJson.substring(arrayClose);
        return new AnchorInserter.Result(out, true);
    }
}
```

- [x] **Step 4: Run to verify it passes**

Run: `./gradlew.bat :services:tools:content-scaffold:test --tests '*JsonBestiaryInserterTest'`
Expected: PASS.

- [x] **Step 5: Commit**

```bash
git add services/tools/content-scaffold/src
git commit -m "feat(content-scaffold): dungeon.json bestiary inserter"
```

---

### Task 5: GeneratorCategoryInserter — item registration

**Files:**
- Create: `.../contentscaffold/GeneratorCategoryInserter.java`
- Test: `.../contentscaffold/GeneratorCategoryInserterTest.java`

**Interfaces:**
- Produces: `GeneratorCategoryInserter.addItem(String generatorJava, String category, String className)` returns `AnchorInserter.Result`. Finds the assignment `Category.<category>.classes = new Class<?>[]{ ... };` and inserts `<className>.class` as the last element before `}`. Idempotent (if `<className>.class` already in that array). Throws `IllegalArgumentException` if the category's `classes` assignment is not found.

- [x] **Step 1: Write the test**

`GeneratorCategoryInserterTest.java`:
```java
package com.qsr.customspd.tools.contentscaffold;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class GeneratorCategoryInserterTest {
    private static final String GEN =
            "static {\n  Category.FOOD.classes = new Class<?>[]{\n    Food.class,\n    Pasty.class\n  };\n}\n";

    @Test
    void insertsClassIntoNamedCategory() {
        AnchorInserter.Result r = GeneratorCategoryInserter.addItem(GEN, "FOOD", "Berry");
        assertTrue(r.inserted());
        assertTrue(r.newContent().contains("Pasty.class,\n    Berry.class"),
                () -> r.newContent());
    }

    @Test
    void idempotent() {
        AnchorInserter.Result first = GeneratorCategoryInserter.addItem(GEN, "FOOD", "Berry");
        assertFalse(GeneratorCategoryInserter.addItem(first.newContent(), "FOOD", "Berry").inserted());
    }

    @Test
    void unknownCategoryThrows() {
        assertThrows(IllegalArgumentException.class, () -> GeneratorCategoryInserter.addItem(GEN, "WAND", "Berry"));
    }
}
```

- [x] **Step 2: Run to verify it fails**

Run: `./gradlew.bat :services:tools:content-scaffold:test --tests '*GeneratorCategoryInserterTest'`
Expected: FAIL (compile error).

- [x] **Step 3: Implement GeneratorCategoryInserter**

`GeneratorCategoryInserter.java`:
```java
package com.qsr.customspd.tools.contentscaffold;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Inserts an item class into a named Generator.Category's classes array. Text-targeted. */
public final class GeneratorCategoryInserter {
    private GeneratorCategoryInserter() {}

    public static AnchorInserter.Result addItem(String generatorJava, String category, String className) {
        // Match: Category.<CAT>.classes = new Class<?>[]{  ...  };
        Matcher m = Pattern.compile(
                "Category\\." + Pattern.quote(category) + "\\.classes\\s*=\\s*new\\s+Class<\\?>\\[\\]\\{")
                .matcher(generatorJava);
        if (!m.find()) {
            throw new IllegalArgumentException("No Category." + category + ".classes assignment found");
        }
        int open = m.end();                    // just after the '{'
        int close = generatorJava.indexOf('}', open);
        if (close < 0) throw new IllegalArgumentException("Unterminated classes array for " + category);
        String body = generatorJava.substring(open, close);
        if (body.contains(className + ".class")) {
            return new AnchorInserter.Result(generatorJava, false);
        }
        // Insert after the last "X.class" entry, copying its indentation.
        Matcher last = Pattern.compile("(\\n(\\s*)[A-Za-z0-9_.]+\\.class)(\\s*,?\\s*)$").matcher(body);
        String newBody;
        if (last.find()) {
            String indent = last.group(2);
            newBody = body.substring(0, last.end(1)) + ",\n" + indent + className + ".class"
                    + body.substring(last.end(1));
        } else {
            newBody = "\n    " + className + ".class\n  ";
        }
        return new AnchorInserter.Result(
                generatorJava.substring(0, open) + newBody + generatorJava.substring(close), true);
    }
}
```

- [x] **Step 4: Run to verify it passes**

Run: `./gradlew.bat :services:tools:content-scaffold:test --tests '*GeneratorCategoryInserterTest'`
Expected: PASS.

- [x] **Step 5: Commit**

```bash
git add services/tools/content-scaffold/src
git commit -m "feat(content-scaffold): Generator category inserter"
```

---

### Task 6: Templates — mob class, mob sprite, item class

**Files:**
- Create: `.../contentscaffold/Templates.java`
- Test: `.../contentscaffold/TemplatesTest.java`

**Interfaces:**
- Consumes: `Names`.
- Produces: `Templates.mobClass(Names)`, `Templates.mobSprite(Names)`, `Templates.itemClass(Names)` — each returns the full Java source string. `Templates.generalAssetLine(Names, boolean mob)` returns the GeneralAsset enum line (pre-indented 4 spaces), e.g. `    WISP("sprites/chars/wisp.png"),`. `Templates.messageLines(Names, boolean mob)` returns the two `.properties` lines. All emit a `// TODO` marking the mechanic/art.

- [x] **Step 1: Write the test**

`TemplatesTest.java`:
```java
package com.qsr.customspd.tools.contentscaffold;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TemplatesTest {
    private final Names wisp = Names.of("Wisp");

    @Test
    void mobClassHasShapeAndTodo() {
        String s = Templates.mobClass(wisp);
        assertTrue(s.contains("package com.qsr.customspd.actors.mobs;"));
        assertTrue(s.contains("public class Wisp extends Mob {"));
        assertTrue(s.contains("spriteClass = WispSprite.class;"));
        assertTrue(s.contains("// TODO"));
    }

    @Test
    void mobSpriteTexturesTheAsset() {
        String s = Templates.mobSprite(wisp);
        assertTrue(s.contains("public class WispSprite extends MobSprite {"));
        assertTrue(s.contains("GeneralAsset.WISP"));
    }

    @Test
    void itemClassHasImageAndTodo() {
        String s = Templates.itemClass(wisp);
        assertTrue(s.contains("public class Wisp extends Item {"));
        assertTrue(s.contains("image = GeneralAsset.WISP;"));
        assertTrue(s.contains("// TODO"));
    }

    @Test
    void assetAndMessageLines() {
        assertTrue(Templates.generalAssetLine(wisp, true).equals("    WISP(\"sprites/chars/wisp.png\"),"));
        assertTrue(Templates.messageLines(wisp, true).contains("actors.mobs.wisp.name="));
        assertTrue(Templates.messageLines(wisp, false).contains("items.wisp.name="));
    }
}
```

- [x] **Step 2: Run to verify it fails**

Run: `./gradlew.bat :services:tools:content-scaffold:test --tests '*TemplatesTest'`
Expected: FAIL (compile error).

- [x] **Step 3: Implement Templates**

`Templates.java`:
```java
package com.qsr.customspd.tools.contentscaffold;

/** Java source templates for scaffolded content. The generated code compiles and runs;
 *  the mechanic and real art are left as TODOs. */
public final class Templates {
    private Templates() {}

    private static final String HEADER =
            "/*\n * Lutherverse -- scaffolded content stub.\n"
          + " * GPLv3; see the project license. Replace the TODOs with the real mechanic + art.\n */\n";

    public static String mobClass(Names n) {
        return HEADER
          + "package com.qsr.customspd.actors.mobs;\n\n"
          + "import com.qsr.customspd.actors.Char;\n"
          + "import com.qsr.customspd.sprites." + n.className() + "Sprite;\n"
          + "import com.watabou.utils.Random;\n\n"
          + "public class " + n.className() + " extends Mob {\n\n"
          + "\t{\n"
          + "\t\tspriteClass = " + n.className() + "Sprite.class;\n\n"
          + "\t\t// TODO: real stats\n"
          + "\t\tHP = HT = 10;\n"
          + "\t\tdefenseSkill = 5;\n"
          + "\t\tmaxLvl = 10;\n"
          + "\t}\n\n"
          + "\t@Override\n"
          + "\tpublic int damageRoll() {\n"
          + "\t\t// TODO: mechanic\n"
          + "\t\treturn Random.NormalIntRange(1, 4);\n"
          + "\t}\n\n"
          + "\t@Override\n"
          + "\tpublic int attackSkill(Char target) {\n"
          + "\t\treturn 10;\n"
          + "\t}\n\n"
          + "\t@Override\n"
          + "\tpublic int drRoll() {\n"
          + "\t\treturn super.drRoll();\n"
          + "\t}\n"
          + "}\n";
    }

    public static String mobSprite(Names n) {
        String a = "GeneralAsset." + n.upperSnake();
        return HEADER
          + "package com.qsr.customspd.sprites;\n\n"
          + "import com.qsr.customspd.assets.Asset;\n"
          + "import com.qsr.customspd.assets.GeneralAsset;\n"
          + "import com.watabou.noosa.TextureFilm;\n\n"
          + "public class " + n.className() + "Sprite extends MobSprite {\n"
          + "\tpublic " + n.className() + "Sprite() {\n"
          + "\t\tsuper();\n"
          + "\t\ttexture(Asset.getAssetFilePath(" + a + "));\n"
          + "\t\t// TODO: real frames + art. The placeholder is a single 16x16 frame.\n"
          + "\t\tTextureFilm frames = new TextureFilm(texture, 16, 16);\n"
          + "\t\tidle = new Animation(1, true);\n"
          + "\t\tidle.frames(frames, 0);\n"
          + "\t\trun = new Animation(1, true);\n"
          + "\t\trun.frames(frames, 0);\n"
          + "\t\tattack = new Animation(1, false);\n"
          + "\t\tattack.frames(frames, 0);\n"
          + "\t\tdie = new Animation(1, false);\n"
          + "\t\tdie.frames(frames, 0);\n"
          + "\t\tplay(idle);\n"
          + "\t}\n"
          + "}\n";
    }

    public static String itemClass(Names n) {
        return HEADER
          + "package com.qsr.customspd.items;\n\n"
          + "import com.qsr.customspd.assets.GeneralAsset;\n\n"
          + "public class " + n.className() + " extends Item {\n\n"
          + "\t{\n"
          + "\t\timage = GeneralAsset." + n.upperSnake() + ";\n"
          + "\t\t// TODO: stackable / defaultAction / mechanic\n"
          + "\t}\n\n"
          + "\t@Override\n"
          + "\tpublic boolean isUpgradable() {\n"
          + "\t\treturn false;\n"
          + "\t}\n\n"
          + "\t@Override\n"
          + "\tpublic boolean isIdentified() {\n"
          + "\t\treturn true;\n"
          + "\t}\n"
          + "}\n";
    }

    public static String generalAssetLine(Names n, boolean mob) {
        String path = mob ? n.mobAssetPath() : n.itemAssetPath();
        return "    " + n.upperSnake() + "(\"" + path + "\"),";
    }

    public static String messageLines(Names n, boolean mob) {
        String base = mob ? ("actors.mobs." + n.lower()) : ("items." + n.lower());
        return base + ".name=" + n.className().toLowerCase() + "\n"
             + base + ".desc=TODO: describe this " + (mob ? "creature" : "item") + ".\n";
    }
}
```

> Note for the implementer: the generated `drRoll()`/`isUpgradable()`/`isIdentified()` overrides are chosen to compile against CPDU's `Mob`/`Item` base classes without pulling in mechanic-specific APIs. If a base method signature differs, adjust the override to match the real base class — the goal is a compilable stub, and Task 8/9's post-generate compile is the check.

- [x] **Step 4: Run to verify it passes**

Run: `./gradlew.bat :services:tools:content-scaffold:test --tests '*TemplatesTest'`
Expected: PASS.

- [x] **Step 5: Commit**

```bash
git add services/tools/content-scaffold/src
git commit -m "feat(content-scaffold): source templates for mob/item stubs"
```

---

### Task 7: Prep — add anchor markers to the real registries

**Files:**
- Modify: `core/src/main/java/com/qsr/customspd/assets/GeneralAsset.kt`
- Modify: `core/src/main/assets/messages/actors/actors.properties`
- Modify: `core/src/main/assets/messages/items/items.properties`

**Interfaces:**
- Consumes: nothing.
- Produces: the anchor markers the CLI (Tasks 8/9) inserts against. `dungeon.json` and `Generator.java` need NO marker (structural/named insertion).

- [x] **Step 1: Add the GeneralAsset markers**

In `GeneralAsset.kt`, add a marker comment line inside the enum. Place `// @content-scaffold:mobs` on its own line immediately AFTER the last existing `sprites/chars/*.png` entry (so scaffolded mob sprites group with the char sprites), and `// @content-scaffold:items` immediately after the last existing `sprites/items/*.png` entry. Find those regions:
```bash
grep -nE "sprites/chars/.*\.png|sprites/items/.*\.png" core/src/main/java/com/qsr/customspd/assets/GeneralAsset.kt | tail
```
Add each marker as a line matching the enum's indentation (4 spaces). Do NOT remove or reorder any entry.

- [x] **Step 2: Add the properties markers**

Append a scaffold section to each properties file (these are `key=value` files; a bare comment line starting with `#` is safe and ignored by the loader). At the END of `core/src/main/assets/messages/actors/actors.properties` add:
```
### @content-scaffold:mobs
```
At the END of `core/src/main/assets/messages/items/items.properties` add:
```
### @content-scaffold:items
```

- [x] **Step 3: Verify the build still passes**

Run: `./gradlew.bat core:compileJava core:compileKotlin core:test --no-daemon`
Expected: BUILD SUCCESSFUL (markers are comments; no behavior change).

- [x] **Step 4: Commit**

```bash
git add core/src/main/java/com/qsr/customspd/assets/GeneralAsset.kt core/src/main/assets/messages/actors/actors.properties core/src/main/assets/messages/items/items.properties
git commit -m "chore(content-scaffold): add insertion anchor markers to the registries"
```

---

### Task 8: ContentScaffoldCli — mob generation

**Files:**
- Create: `.../contentscaffold/ContentScaffoldCli.java`
- Test: `.../contentscaffold/ContentScaffoldCliMobTest.java`

**Interfaces:**
- Consumes: `Names`, `Templates`, `AnchorInserter`, `JsonBestiaryInserter`, and `content-audit`'s `RepoRoot`.
- Produces: `main(String[])` handling `mob <Name> --depth <n>`; exit 0 success, 2 usage/anchor error. `ContentScaffoldCli.generateMob(File repoRoot, String name, int depth)` returns `record GenResult(List<String> created, List<String> modified, List<String> skipped)` for testability. Writes: the mob class, the sprite class, the GeneralAsset line (marker `// @content-scaffold:mobs`), the placeholder PNG at `sprites/chars/<snake>.png`, the actors.properties lines (marker `### @content-scaffold:mobs`), and the dungeon.json bestiary entry.

- [x] **Step 1: Write the fixture test**

`ContentScaffoldCliMobTest.java`:
```java
package com.qsr.customspd.tools.contentscaffold;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ContentScaffoldCliMobTest {
    private static void write(Path p, String s) throws Exception {
        Files.createDirectories(p.getParent());
        Files.writeString(p, s);
    }

    private static File repo(Path root) throws Exception {
        write(root.resolve("core/src/main/java/com/qsr/customspd/assets/GeneralAsset.kt"),
                "enum class GeneralAsset(val path: String) {\n    RAT(\"sprites/chars/rat.png\"),\n"
              + "    // @content-scaffold:mobs\n    BOMB(\"sprites/items/bomb.png\"),\n}\n");
        write(root.resolve("core/src/main/assets/messages/actors/actors.properties"),
                "actors.mobs.rat.name=rat\n### @content-scaffold:mobs\n");
        write(root.resolve("core/src/main/assets/dungeon/dungeon.json"),
                "{\n  \"dungeon\": {\n    \"3\": {\n      \"depth\": 3,\n      \"bestiary\": [\n        \"Gnoll\"\n      ]\n    }\n  }\n}\n");
        return root.toFile();
    }

    @Test
    void generatesAllMobTouchpoints(@TempDir Path root) throws Exception {
        File r = repo(root);
        ContentScaffoldCli.generateMob(r, "Wisp", 3);
        assertTrue(Files.exists(root.resolve("core/src/main/java/com/qsr/customspd/actors/mobs/Wisp.java")));
        assertTrue(Files.exists(root.resolve("core/src/main/java/com/qsr/customspd/sprites/WispSprite.java")));
        assertTrue(Files.exists(root.resolve("core/src/main/assets/sprites/chars/wisp.png")));
        assertTrue(Files.readString(root.resolve("core/src/main/java/com/qsr/customspd/assets/GeneralAsset.kt"))
                .contains("WISP(\"sprites/chars/wisp.png\"),"));
        assertTrue(Files.readString(root.resolve("core/src/main/assets/messages/actors/actors.properties"))
                .contains("actors.mobs.wisp.name="));
        assertTrue(Files.readString(root.resolve("core/src/main/assets/dungeon/dungeon.json"))
                .contains("\"Wisp\""));
    }

    @Test
    void idempotentReRunSkips(@TempDir Path root) throws Exception {
        File r = repo(root);
        ContentScaffoldCli.generateMob(r, "Wisp", 3);
        ContentScaffoldCli.GenResult second = ContentScaffoldCli.generateMob(r, "Wisp", 3);
        // GeneralAsset/properties/dungeon insertions are all no-ops on the second run
        assertTrue(second.skipped().size() >= 3, () -> "expected >=3 skipped, got " + second.skipped());
    }
}
```

- [x] **Step 2: Run to verify it fails**

Run: `./gradlew.bat :services:tools:content-scaffold:test --tests '*ContentScaffoldCliMobTest'`
Expected: FAIL (compile error).

- [x] **Step 3: Implement ContentScaffoldCli (mob path)**

`ContentScaffoldCli.java`:
```java
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

        // 3. GeneralAsset entry
        applyInsert(base.resolve("core/src/main/java/com/qsr/customspd/assets/GeneralAsset.kt"),
                c -> AnchorInserter.insertAbove(c, "// @content-scaffold:mobs",
                        Templates.generalAssetLine(n, true), n.upperSnake() + "("),
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
```

- [x] **Step 4: Run to verify it passes**

Run: `./gradlew.bat :services:tools:content-scaffold:test --tests '*ContentScaffoldCliMobTest'`
Expected: PASS.

- [x] **Step 5: Commit**

```bash
git add services/tools/content-scaffold/src
git commit -m "feat(content-scaffold): CLI mob generation, idempotent + fail-safe"
```

---

### Task 9: Item generation + post-generate audit + README

**Files:**
- Modify: `.../contentscaffold/ContentScaffoldCli.java`
- Test: `.../contentscaffold/ContentScaffoldCliItemTest.java`
- Create: `services/tools/content-scaffold/README.md`

**Interfaces:**
- Consumes: `GeneratorCategoryInserter`, `Templates`, `content-audit`'s `ContentAuditCli.run` + `Allowlist`.
- Produces: `generateItem(File repoRoot, String name, String category, String tier)` returning `GenResult`; `main` handling `item <Name> --category <cat> --tier <n>`; and a post-generate `content-audit` check (`ContentAuditCli.run(repoRoot, Allowlist.load(null))` filtered to findings whose key contains `<Name>`, printed as the wiring report with the M3/I3 caveat).

- [x] **Step 1: Write the item test**

`ContentScaffoldCliItemTest.java`:
```java
package com.qsr.customspd.tools.contentscaffold;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ContentScaffoldCliItemTest {
    private static void write(Path p, String s) throws Exception {
        Files.createDirectories(p.getParent());
        Files.writeString(p, s);
    }

    @Test
    void generatesAllItemTouchpoints(@TempDir Path root) throws Exception {
        write(root.resolve("core/src/main/java/com/qsr/customspd/assets/GeneralAsset.kt"),
                "enum class GeneralAsset(val path: String) {\n    ANKH(\"sprites/items/ankh.png\"),\n"
              + "    // @content-scaffold:items\n}\n");
        write(root.resolve("core/src/main/assets/messages/items/items.properties"),
                "items.ankh.name=ankh\n### @content-scaffold:items\n");
        write(root.resolve("core/src/main/java/com/qsr/customspd/items/Generator.java"),
                "class Generator {\n  static {\n    Category.FOOD.classes = new Class<?>[]{\n      Pasty.class\n    };\n  }\n}\n");
        File r = root.toFile();
        ContentScaffoldCli.generateItem(r, "Berry", "FOOD", "1");
        assertTrue(Files.exists(root.resolve("core/src/main/java/com/qsr/customspd/items/Berry.java")));
        assertTrue(Files.exists(root.resolve("core/src/main/assets/sprites/items/berry.png")));
        assertTrue(Files.readString(root.resolve("core/src/main/java/com/qsr/customspd/assets/GeneralAsset.kt"))
                .contains("BERRY(\"sprites/items/berry.png\"),"));
        assertTrue(Files.readString(root.resolve("core/src/main/assets/messages/items/items.properties"))
                .contains("items.berry.name="));
        assertTrue(Files.readString(root.resolve("core/src/main/java/com/qsr/customspd/items/Generator.java"))
                .contains("Berry.class"));
    }
}
```

- [x] **Step 2: Run to verify it fails**

Run: `./gradlew.bat :services:tools:content-scaffold:test --tests '*ContentScaffoldCliItemTest'`
Expected: FAIL (compile error).

- [x] **Step 3: Implement generateItem + wire main + post-generate audit**

Add to `ContentScaffoldCli.java` (new method + extend `main`):
```java
    static GenResult generateItem(File repoRoot, String name, String category, String tier) throws IOException {
        Names n = Names.of(name);
        Path base = repoRoot.toPath();
        List<String> created = new ArrayList<>(), modified = new ArrayList<>(), skipped = new ArrayList<>();

        writeIfAbsent(base.resolve("core/src/main/java/com/qsr/customspd/items/" + n.className() + ".java"),
                Templates.itemClass(n), created, skipped);

        Path png = base.resolve("core/src/main/assets/" + n.itemAssetPath());
        if (Files.exists(png)) { skipped.add(png.toString()); }
        else {
            Files.createDirectories(png.getParent());
            try (InputStream in = ContentScaffoldCli.class.getResourceAsStream("/placeholder.png")) {
                if (in == null) throw new IOException("placeholder.png resource missing");
                Files.copy(in, png);
            }
            created.add(png.toString());
        }

        applyInsert(base.resolve("core/src/main/java/com/qsr/customspd/assets/GeneralAsset.kt"),
                c -> AnchorInserter.insertAbove(c, "// @content-scaffold:items",
                        Templates.generalAssetLine(n, false), n.upperSnake() + "("),
                "GeneralAsset:" + n.upperSnake(), modified, skipped);

        applyInsert(base.resolve("core/src/main/assets/messages/items/items.properties"),
                c -> AnchorInserter.insertAbove(c, "### @content-scaffold:items",
                        Templates.messageLines(n, false).stripTrailing(), "items." + n.lower() + ".name="),
                "items." + n.lower(), modified, skipped);

        applyInsert(base.resolve("core/src/main/java/com/qsr/customspd/items/Generator.java"),
                c -> GeneratorCategoryInserter.addItem(c, category, n.className()),
                "Generator:" + category + ":" + n.className(), modified, skipped);

        return new GenResult(created, modified, skipped);
    }

    /** Post-generate wiring check: run content-audit and print the new entity's findings. */
    static void auditNewEntity(File repoRoot, String name) throws IOException {
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
    }
```
Extend `main` to handle the `item` verb (mirroring the `mob` branch: parse `item <Name> --category <cat> --tier <n>`, call `generateItem`, then `auditNewEntity`) and call `auditNewEntity` after a successful `generateMob` too. Update `usage()` to list both verbs.

- [x] **Step 4: Run to verify it passes**

Run: `./gradlew.bat :services:tools:content-scaffold:test`
Expected: PASS (all tests).

- [x] **Step 5: Compile-smoke the templates against the REAL core (then revert)**

The fixture tests prove the touchpoints land, but not that the generated Java compiles against CPDU's real `Mob`/`Item`/`MobSprite`. Verify once, on a throwaway name, and revert — leave NOTHING committed:
```bash
./gradlew.bat :services:tools:content-scaffold:run --args="mob ScaffoldSmoke --depth 1"
./gradlew.bat :services:tools:content-scaffold:run --args="item ScaffoldSmokeItem --category FOOD --tier 1"
./gradlew.bat core:compileJava core:compileKotlin --no-daemon
```
Expected: BUILD SUCCESSFUL. If a generated override (`drRoll`/`isUpgradable`/`isIdentified`/etc.) does not match the real base signature, fix `Templates` (Task 6) until `core:compileJava` passes, re-run the module tests, then revert ALL generated real-repo content and the registry insertions:
```bash
git checkout -- core/
git clean -fd core/src/main/java/com/qsr/customspd/actors/mobs/ScaffoldSmoke.java \
  core/src/main/java/com/qsr/customspd/sprites/ScaffoldSmokeSprite.java \
  core/src/main/java/com/qsr/customspd/items/ScaffoldSmokeItem.java \
  core/src/main/assets/sprites/chars/scaffold_smoke.png \
  core/src/main/assets/sprites/items/scaffold_smoke_item.png
git status --porcelain core/   # MUST be empty before proceeding
```
Confirm `git status` shows no residual `core/` changes. Any `Templates` fix is the only thing that stays (in `services/tools/content-scaffold/`).

- [x] **Step 6: Write the README**

`services/tools/content-scaffold/README.md`:
```markdown
# content-scaffold

Generates a compilable, fully-wired Mob or Item skeleton so you fill in the mechanic and
art, not the wiring. The inverse of `content-audit`.

```
./gradlew :services:tools:content-scaffold:run --args="mob Wisp --depth 3"
./gradlew :services:tools:content-scaffold:run --args="item Berry --category FOOD --tier 1"
```

## What it wires

- The class stub (`actors/mobs/<Name>.java` or `items/<Name>.java`) and, for mobs, a sprite
  class (`sprites/<Name>Sprite.java`) — with the mechanic left as `// TODO`.
- A `GeneralAsset` entry + a placeholder magenta PNG (mobs: `sprites/chars/`, items: `sprites/items/`).
- Localization keys in `actors.properties` / `items.properties`.
- Registration: mobs into `dungeon.json`'s `bestiary` at `--depth`; items into `Generator.Category.<cat>`.

## Safety

- Marker-anchored insertion: it never reserializes a shared file, only inserts at
  `// @content-scaffold:*` markers (or the structural `dungeon.json` array / named category).
- Idempotent: re-running for an existing name is a warned no-op, not a duplicate.
- Fail-safe: a missing anchor is a non-zero exit naming the marker.

## After scaffolding

Replace the magenta placeholder with real art, fill in the `// TODO` mechanic, and set real
stats. The tool prints a `content-audit` check for the new entity; sprite/localization
touchpoints pass immediately, and registration (M3/I3) is correct-by-construction (it edits
the real `dungeon.json`/`Generator`) but `content-audit` will only confirm it once its
registration-heuristic follow-up bead lands.

## Scope
v1 scaffolds core Mobs and Items. Art, the mechanic, and bosses/biomes/talents are out of scope.
```

- [x] **Step 7: Commit**

```bash
git add services/tools/content-scaffold/src services/tools/content-scaffold/README.md
git commit -m "feat(content-scaffold): item generation + post-generate audit + README"
```

---

## Notes for the implementer

- **The generated stubs must actually compile against CPDU's real `Mob`/`Item` base classes.** The templates in Task 6 target the common base API, but if a chosen override (`drRoll`, `isUpgradable`, `isIdentified`) does not match the real signature, adjust it — the deliverable is a *compilable* stub. A cheap way to check during Task 8/9: after generating into the real repo once (manually, on a throwaway name), run `core:compileJava` and fix any template mismatch, then delete the throwaway. Do NOT leave throwaway content committed.
- **Kotlin/properties/JSON files are edited as text, never reparsed** — that is deliberate (minimal diffs, no formatting churn), exactly like `content-audit` scans as text.
- **Do not commit generated game content** produced while testing against the real repo — the tests use `@TempDir` fixtures precisely so no real-repo content is created. The only real-repo change in this plan is Task 7's anchor markers.
- The post-generate `content-audit` check reuses the sibling module in-process; it does not shell out to Gradle.
