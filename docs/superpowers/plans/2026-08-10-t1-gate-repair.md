# T1 Gate Repair Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the three non-functional verification gates identified in the 2026-08-10 audit with gates that actually run, so the remaining Sub-B integration work lands on measurement rather than assertion.

**Architecture:** A new standalone `services/tools/deletion-audit` Gradle module uses JavaParser to inventory every callable in `core/` at two git refs — including private members and method *body* sizes, which the existing api-diff tool structurally cannot see — and reports declarations that vanished or bodies that shrank. A runtime test in `SPD-classes` proves `Bundle.addAlias` resolution actually works, which nothing has ever verified. A desktop boot smoke replaces the Android emulator smoke that has never once executed the APK.

**Tech Stack:** Java 17, Gradle, JavaParser 3.25.5, JUnit Jupiter 5.10.0, PowerShell 7 (`pwsh`), libGDX/LWJGL3.

## Global Constraints

- Java source/target level: 17 (`appJavaCompatibility`). Records and switch expressions are available.
- New tool modules mirror `services/tools/api-diff`: `plugins { id 'application'; id 'java' }`, `repositories { mavenCentral() }`, JavaParser + JUnit 5 deps, `test { useJUnitPlatform() }`, a `*Cli` main class, and registration in `settings.gradle`.
- **All git subprocess invocations MUST be anchored to the repository root** via `git rev-parse --show-toplevel`. This is not optional style: api-diff silently scanned zero files and printed PASS for weeks because `git ls-tree` inherited Gradle's subproject CWD.
- **`git show` reports an absent path two ways**: `does not exist in` and `exists on disk, but not in`. Both must be handled. The second is what a file ADDED by the audited range produces.
- Tool modules are standalone. Do not add cross-dependencies between `services/tools/*` modules; the ~40 lines of shared git plumbing are duplicated deliberately, matching the existing api-diff / pack-smoke / namespace-transform pattern.
- `core` has no test source set (`core:test` is `NO-SOURCE`). Never cite `core:test` as a gate.
- `core` depends on `SPD-classes`; the reverse is forbidden. No test in `SPD-classes` may reference a `core` class.
- No AI attribution in commit messages.
- Slice 1 base ref for audit runs: `7d9c139c8`.

---

## File Structure

| Path | Responsibility |
|---|---|
| `services/tools/deletion-audit/build.gradle` | Module definition, mirrors api-diff |
| `.../deletionaudit/GitCommands.java` | Repo-root-anchored `git show` and `git ls-tree` |
| `.../deletionaudit/CallableInventory.java` | Immutable inventory of callables in one source file |
| `.../deletionaudit/InventoryExtractor.java` | JavaParser → `CallableInventory` |
| `.../deletionaudit/InventoryDiff.java` | Compares two inventories → deletions and body shrinks |
| `.../deletionaudit/Allowlist.java` | Reads reviewed-and-accepted removal keys |
| `.../deletionaudit/DeletionAuditCli.java` | Arg parsing, orchestration, report, exit code |
| `SPD-classes/src/test/java/com/watabou/utils/BundleAliasRoundtripTest.java` | Proves `addAlias` + write/read resolution works |
| `desktop/src/main/java/.../DesktopLauncher.java` (modify) | `--smoke-frames N` clean-exit hook |
| `services/tools/desktop-smoke/desktop-smoke.ps1` | Boot smoke driver |
| `settings.gradle` (modify) | Register `:services:tools:deletion-audit` |

---

### Task 1: deletion-audit module skeleton and git plumbing

**Files:**
- Create: `services/tools/deletion-audit/build.gradle`
- Create: `services/tools/deletion-audit/src/main/java/com/qsr/customspd/tools/deletionaudit/GitCommands.java`
- Test: `services/tools/deletion-audit/src/test/java/com/qsr/customspd/tools/deletionaudit/GitCommandsTest.java`
- Modify: `settings.gradle`

**Interfaces:**
- Consumes: nothing.
- Produces: `GitCommands.readBlob(String ref, String path) -> String` (throws `IOException`); `GitCommands.isPathNotFound(IOException) -> boolean`; `GitCommands.listTree(String ref) -> List<String>`; `GitCommands.repoRoot() -> java.io.File` (nullable).

- [ ] **Step 1: Create the module build file**

Create `services/tools/deletion-audit/build.gradle`:

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
    mainClass = 'com.qsr.customspd.tools.deletionaudit.DeletionAuditCli'
}

test {
    useJUnitPlatform()
}
```

- [ ] **Step 2: Register the module**

In `settings.gradle`, add the new include directly after the `pack-smoke` line so the tools block stays alphabetical-ish and grouped:

```groovy
    //tools
    include ':services:tools:api-diff'
    include ':services:tools:deletion-audit'
    include ':services:tools:namespace-transform'
    include ':services:tools:pack-smoke'
```

- [ ] **Step 3: Write the failing test**

Create `services/tools/deletion-audit/src/test/java/com/qsr/customspd/tools/deletionaudit/GitCommandsTest.java`:

```java
package com.qsr.customspd.tools.deletionaudit;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GitCommandsTest {

    @Test
    void repoRootResolvesFromSubprojectWorkingDirectory() throws IOException {
        // The JVM running this test has its CWD set to the subproject by Gradle.
        // repoRoot() must still resolve the repository root, or every other
        // command in this tool silently scans the wrong tree.
        assertNotNull(GitCommands.repoRoot(), "repository root must resolve");
    }

    @Test
    void listTreeReturnsPathsOutsideThisSubproject() throws IOException {
        List<String> paths = GitCommands.listTree("HEAD");
        assertFalse(paths.isEmpty(), "HEAD tree must not be empty");
        assertTrue(paths.stream().anyMatch(p -> p.startsWith("core/")),
                "listTree must return repo-root-relative paths, including core/");
    }

    @Test
    void readBlobReturnsFileContentAtRef() throws IOException {
        String content = GitCommands.readBlob("HEAD", "settings.gradle");
        assertTrue(content.contains("deletion-audit"),
                "settings.gradle at HEAD must register this module");
    }

    @Test
    void missingPathIsRecognisedRatherThanCrashing() {
        IOException thrown = assertThrows(IOException.class,
                () -> GitCommands.readBlob("HEAD", "no/such/file/anywhere.java"));
        assertTrue(GitCommands.isPathNotFound(thrown),
                "an absent path must be classified as not-found, not as a git failure");
    }
}
```

- [ ] **Step 4: Run the test to verify it fails**

Run: `.\gradlew.bat :services:tools:deletion-audit:test`
Expected: FAIL — compilation error, `GitCommands` does not exist.

- [ ] **Step 5: Implement GitCommands**

Create `services/tools/deletion-audit/src/main/java/com/qsr/customspd/tools/deletionaudit/GitCommands.java`:

```java
package com.qsr.customspd.tools.deletionaudit;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Repo-root-anchored git invocations.
 *
 * <p>Every command here sets its working directory to the repository root.
 * The api-diff tool omitted this and inherited Gradle's subproject CWD, so
 * {@code git ls-tree} listed only that subproject, matched zero of the paths
 * its glob targeted, and printed PASS while auditing nothing. Do not remove
 * the {@code directory(repoRoot())} calls.
 */
public final class GitCommands {

    private GitCommands() {
    }

    /**
     * Resolves the repository root so git invocations are independent of the
     * JVM's working directory. Returns {@code null} (meaning "inherit the
     * current directory") if the root cannot be determined.
     */
    public static File repoRoot() throws IOException {
        ProcessBuilder builder = new ProcessBuilder("git", "rev-parse", "--show-toplevel");
        builder.redirectErrorStream(true);
        Process process = builder.start();
        String out = readStream(process.getInputStream()).trim();
        try {
            if (process.waitFor() != 0 || out.isEmpty()) {
                return null;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted resolving repository root", e);
        }
        return new File(out);
    }

    /** Reads a file's content at {@code ref} via {@code git show ref:path}. */
    public static String readBlob(String ref, String path) throws IOException {
        String blobSpec = ref + ":" + path;
        ProcessBuilder builder = new ProcessBuilder("git", "show", blobSpec);
        builder.directory(repoRoot());
        return runCapturingStdout(builder, "git show " + blobSpec);
    }

    /** Lists every path in the tree at {@code ref}, repo-root-relative. */
    public static List<String> listTree(String ref) throws IOException {
        ProcessBuilder builder = new ProcessBuilder("git", "ls-tree", "-r", "--name-only", ref);
        builder.directory(repoRoot());
        String stdout = runCapturingStdout(builder, "git ls-tree " + ref);

        List<String> paths = new ArrayList<>();
        for (String line : stdout.split("\n")) {
            String trimmed = line.strip();
            if (!trimmed.isEmpty()) {
                paths.add(trimmed);
            }
        }
        return paths;
    }

    /**
     * Returns {@code true} if the exception indicates a genuinely absent path
     * rather than a process or ref failure.
     *
     * <p>git reports an absent path two different ways depending on whether the
     * file is present in the working tree:
     * <pre>
     *   fatal: path 'X' does not exist in 'REF'
     *   fatal: path 'X' exists on disk, but not in 'REF'
     * </pre>
     * The second form is what a file ADDED by the audited range produces, so
     * missing it makes the tool crash on exactly the commits it exists to audit.
     */
    public static boolean isPathNotFound(IOException e) {
        String message = e.getMessage();
        if (message == null) {
            return false;
        }
        return message.contains("does not exist in")
                || message.contains("exists on disk, but not in");
    }

    private static String runCapturingStdout(ProcessBuilder builder, String description)
            throws IOException {
        Process process = builder.start();

        StringBuilder stderrBuffer = new StringBuilder();
        Thread stderrDrain = new Thread(() -> {
            try {
                stderrBuffer.append(readStream(process.getErrorStream()));
            } catch (IOException ignored) {
                // best-effort: stderr is only used for the error message
            }
        });
        stderrDrain.start();

        String stdout = readStream(process.getInputStream());

        int exitCode;
        try {
            exitCode = process.waitFor();
            stderrDrain.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while running: " + description, e);
        }

        if (exitCode != 0) {
            throw new IOException(description + " failed with exit code " + exitCode
                    + ": " + stderrBuffer.toString().trim());
        }
        return stdout;
    }

    private static String readStream(InputStream inputStream) throws IOException {
        StringBuilder builder = new StringBuilder();
        try (BufferedReader reader =
                     new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            char[] buffer = new char[8192];
            int read;
            while ((read = reader.read(buffer)) != -1) {
                builder.append(buffer, 0, read);
            }
        }
        return builder.toString();
    }
}
```

- [ ] **Step 6: Run the test to verify it passes**

Run: `.\gradlew.bat :services:tools:deletion-audit:test`
Expected: PASS, 4 tests.

- [ ] **Step 7: Commit**

```bash
git add settings.gradle services/tools/deletion-audit
git commit -m "feat(tools): add deletion-audit module with repo-root-anchored git plumbing"
```

---

### Task 2: Callable inventory extraction

**Files:**
- Create: `services/tools/deletion-audit/src/main/java/com/qsr/customspd/tools/deletionaudit/CallableInventory.java`
- Create: `services/tools/deletion-audit/src/main/java/com/qsr/customspd/tools/deletionaudit/InventoryExtractor.java`
- Test: `services/tools/deletion-audit/src/test/java/com/qsr/customspd/tools/deletionaudit/InventoryExtractorTest.java`

**Interfaces:**
- Consumes: nothing from Task 1.
- Produces:
  - `record CallableInventory.Entry(String key, String visibility, int statementCount)`
  - `CallableInventory(List<Entry> entries)` with `Map<String, Entry> byKey()`
  - `InventoryExtractor.extract(String path, String source) -> CallableInventory`
  - Key format is exactly `TypeName#signature`, e.g. `CorpseDust#actions(Hero)`.

**Why this exists:** api-diff inventories *public and protected declarations only*. It cannot see private members, and it cannot see method bodies at all. The `CorpseDust.actions()` regression was caught only because it happened to be a visible override. A statement dropped from inside a body passes every gate this project currently has.

- [ ] **Step 1: Write the failing test**

Create `services/tools/deletion-audit/src/test/java/com/qsr/customspd/tools/deletionaudit/InventoryExtractorTest.java`:

```java
package com.qsr.customspd.tools.deletionaudit;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InventoryExtractorTest {

    @Test
    void capturesPrivateMethodsThatApiDiffIgnores() {
        String source = """
                package p;
                class Sample {
                    private void hidden() { int a = 1; }
                }
                """;
        Map<String, CallableInventory.Entry> byKey =
                InventoryExtractor.extract("Sample.java", source).byKey();

        assertTrue(byKey.containsKey("Sample#hidden()"),
                "private methods must be inventoried; api-diff cannot see them");
        assertEquals("private", byKey.get("Sample#hidden()").visibility());
    }

    @Test
    void countsStatementsIncludingNestedOnes() {
        String source = """
                package p;
                class Sample {
                    void body() {
                        int a = 1;
                        if (a > 0) {
                            a++;
                        }
                    }
                }
                """;
        CallableInventory.Entry entry =
                InventoryExtractor.extract("Sample.java", source).byKey().get("Sample#body()");

        // local-var decl, if-stmt, its block, and the a++ expression statement
        assertEquals(4, entry.statementCount());
    }

    @Test
    void keysIncludeParameterTypesSoOverloadsStaySeparate() {
        String source = """
                package p;
                class Sample {
                    void go(int a) {}
                    void go(String a) {}
                }
                """;
        Map<String, CallableInventory.Entry> byKey =
                InventoryExtractor.extract("Sample.java", source).byKey();

        assertTrue(byKey.containsKey("Sample#go(int)"));
        assertTrue(byKey.containsKey("Sample#go(String)"));
    }

    @Test
    void nestedTypesAreQualifiedByTheirOuterType() {
        String source = """
                package p;
                class Outer {
                    static class Inner {
                        void run() {}
                    }
                }
                """;
        assertTrue(InventoryExtractor.extract("Outer.java", source).byKey()
                        .containsKey("Outer.Inner#run()"),
                "nested type callables must not collide with outer-type ones");
    }

    @Test
    void constructorsAreInventoried() {
        String source = """
                package p;
                class Sample {
                    Sample(int a) {}
                }
                """;
        assertTrue(InventoryExtractor.extract("Sample.java", source).byKey()
                .containsKey("Sample#Sample(int)"));
    }

    @Test
    void abstractMethodHasZeroStatementsRatherThanFailing() {
        String source = """
                package p;
                abstract class Sample {
                    abstract void todo();
                }
                """;
        assertEquals(0, InventoryExtractor.extract("Sample.java", source)
                .byKey().get("Sample#todo()").statementCount());
    }

    @Test
    void unparseableSourceYieldsEmptyInventoryRatherThanThrowing() {
        CallableInventory inventory = InventoryExtractor.extract("Broken.java", "this is not java {{{");
        assertTrue(inventory.entries().isEmpty(),
                "a parse failure must degrade to empty, not abort a whole-repo audit");
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `.\gradlew.bat :services:tools:deletion-audit:test --tests '*InventoryExtractorTest*'`
Expected: FAIL — compilation error, `CallableInventory` and `InventoryExtractor` do not exist.

- [ ] **Step 3: Implement CallableInventory**

Create `services/tools/deletion-audit/src/main/java/com/qsr/customspd/tools/deletionaudit/CallableInventory.java`:

```java
package com.qsr.customspd.tools.deletionaudit;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Every callable declared in one source file, at one git ref.
 *
 * <p>Unlike api-diff's {@code JavaSurface}, this includes private members and
 * records each body's statement count, so a deletion inside an unchanged
 * signature is visible.
 */
public record CallableInventory(List<Entry> entries) {

    /**
     * @param key            {@code TypeName#signature}, e.g. {@code CorpseDust#actions(Hero)}
     * @param visibility     {@code public}, {@code protected}, {@code private}, or {@code package}
     * @param statementCount total statements in the body, nested included; 0 when there is no body
     */
    public record Entry(String key, String visibility, int statementCount) {
    }

    public Map<String, Entry> byKey() {
        Map<String, Entry> map = new LinkedHashMap<>();
        for (Entry entry : entries) {
            map.put(entry.key(), entry);
        }
        return map;
    }
}
```

- [ ] **Step 4: Implement InventoryExtractor**

Create `services/tools/deletion-audit/src/main/java/com/qsr/customspd/tools/deletionaudit/InventoryExtractor.java`:

```java
package com.qsr.customspd.tools.deletionaudit;

import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.CallableDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.stmt.Statement;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Parses Java source into a {@link CallableInventory}. */
public final class InventoryExtractor {

    static {
        StaticJavaParser.getParserConfiguration()
                .setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_17);
    }

    private InventoryExtractor() {
    }

    /**
     * @param path   repo-root-relative path, used only for diagnostics
     * @param source the file's content at some ref
     * @return the inventory; empty if the source cannot be parsed, so one bad
     *         file cannot abort a whole-repository audit
     */
    public static CallableInventory extract(String path, String source) {
        CompilationUnit unit;
        try {
            unit = StaticJavaParser.parse(source);
        } catch (RuntimeException e) {
            System.err.println("deletion-audit: could not parse " + path + ": " + e.getMessage());
            return new CallableInventory(List.of());
        }

        List<CallableInventory.Entry> entries = new ArrayList<>();
        for (CallableDeclaration<?> callable : unit.findAll(CallableDeclaration.class)) {
            entries.add(new CallableInventory.Entry(
                    enclosingTypeName(callable) + "#" + callable.getSignature().asString(),
                    visibilityOf(callable),
                    statementCountOf(callable)));
        }
        return new CallableInventory(entries);
    }

    /**
     * Builds a dotted type name from the callable's ancestor types, so a nested
     * {@code Outer.Inner#run()} never collides with {@code Outer#run()}.
     */
    private static String enclosingTypeName(CallableDeclaration<?> callable) {
        List<String> names = new ArrayList<>();
        Optional<TypeDeclaration> ancestor = callable.findAncestor(TypeDeclaration.class);
        while (ancestor.isPresent()) {
            TypeDeclaration<?> type = ancestor.get();
            names.add(0, type.getNameAsString());
            ancestor = type.findAncestor(TypeDeclaration.class);
        }
        return names.isEmpty() ? "<anonymous>" : String.join(".", names);
    }

    private static String visibilityOf(CallableDeclaration<?> callable) {
        if (callable.isPublic()) return "public";
        if (callable.isProtected()) return "protected";
        if (callable.isPrivate()) return "private";
        return "package";
    }

    /**
     * Counts every statement in the body, nested statements included. An
     * abstract or interface method has no body and counts 0.
     */
    private static int statementCountOf(CallableDeclaration<?> callable) {
        if (callable instanceof MethodDeclaration method) {
            return method.getBody().map(body -> body.findAll(Statement.class).size()).orElse(0);
        }
        if (callable instanceof ConstructorDeclaration constructor) {
            return constructor.getBody().findAll(Statement.class).size();
        }
        return 0;
    }
}
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `.\gradlew.bat :services:tools:deletion-audit:test --tests '*InventoryExtractorTest*'`
Expected: PASS, 7 tests.

If `countsStatementsIncludingNestedOnes` fails on the expected value, do NOT change the production code to match — read the actual count JavaParser reports, confirm by hand which nodes it counted, and correct the test's expectation. The invariant that matters is that the count is deterministic and decreases when statements are removed, not its exact absolute value.

- [ ] **Step 6: Commit**

```bash
git add services/tools/deletion-audit
git commit -m "feat(tools): inventory callables including private members and body statement counts"
```

---

### Task 3: Inventory diff

**Files:**
- Create: `services/tools/deletion-audit/src/main/java/com/qsr/customspd/tools/deletionaudit/InventoryDiff.java`
- Test: `services/tools/deletion-audit/src/test/java/com/qsr/customspd/tools/deletionaudit/InventoryDiffTest.java`

**Interfaces:**
- Consumes: `CallableInventory`, `CallableInventory.Entry` from Task 2.
- Produces:
  - `record InventoryDiff.Deleted(String key, String visibility, int statementsLost)`
  - `record InventoryDiff.Shrunk(String key, int before, int after)`
  - `record InventoryDiff.Report(List<Deleted> deleted, List<Shrunk> shrunk)` with `boolean isEmpty()`
  - `InventoryDiff.compare(CallableInventory before, CallableInventory after, int minShrink) -> Report`

- [ ] **Step 1: Write the failing test**

Create `services/tools/deletion-audit/src/test/java/com/qsr/customspd/tools/deletionaudit/InventoryDiffTest.java`:

```java
package com.qsr.customspd.tools.deletionaudit;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InventoryDiffTest {

    private static CallableInventory inventoryOf(CallableInventory.Entry... entries) {
        return new CallableInventory(List.of(entries));
    }

    @Test
    void reportsACallablePresentBeforeAndAbsentAfter() {
        CallableInventory before = inventoryOf(
                new CallableInventory.Entry("CorpseDust#actions(Hero)", "public", 5));
        CallableInventory after = inventoryOf();

        InventoryDiff.Report report = InventoryDiff.compare(before, after, 1);

        assertEquals(1, report.deleted().size());
        assertEquals("CorpseDust#actions(Hero)", report.deleted().get(0).key());
        assertEquals(5, report.deleted().get(0).statementsLost());
    }

    @Test
    void reportsABodyThatLostStatementsWhileKeepingItsSignature() {
        CallableInventory before = inventoryOf(
                new CallableInventory.Entry("Mob#die(Object)", "public", 12));
        CallableInventory after = inventoryOf(
                new CallableInventory.Entry("Mob#die(Object)", "public", 3));

        InventoryDiff.Report report = InventoryDiff.compare(before, after, 1);

        assertTrue(report.deleted().isEmpty());
        assertEquals(1, report.shrunk().size());
        assertEquals(12, report.shrunk().get(0).before());
        assertEquals(3, report.shrunk().get(0).after());
    }

    @Test
    void ignoresShrinkageBelowTheThreshold() {
        CallableInventory before = inventoryOf(
                new CallableInventory.Entry("Mob#die(Object)", "public", 12));
        CallableInventory after = inventoryOf(
                new CallableInventory.Entry("Mob#die(Object)", "public", 10));

        assertTrue(InventoryDiff.compare(before, after, 3).shrunk().isEmpty(),
                "a 2-statement drop must not fire when minShrink is 3");
    }

    @Test
    void ignoresAdditionsAndGrowth() {
        CallableInventory before = inventoryOf(
                new CallableInventory.Entry("Mob#die(Object)", "public", 3));
        CallableInventory after = inventoryOf(
                new CallableInventory.Entry("Mob#die(Object)", "public", 12),
                new CallableInventory.Entry("Mob#extra()", "private", 4));

        assertTrue(InventoryDiff.compare(before, after, 1).isEmpty(),
                "this tool audits removals only; additions are api-diff's job");
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `.\gradlew.bat :services:tools:deletion-audit:test --tests '*InventoryDiffTest*'`
Expected: FAIL — compilation error, `InventoryDiff` does not exist.

- [ ] **Step 3: Implement InventoryDiff**

Create `services/tools/deletion-audit/src/main/java/com/qsr/customspd/tools/deletionaudit/InventoryDiff.java`:

```java
package com.qsr.customspd.tools.deletionaudit;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Compares two {@link CallableInventory} snapshots and reports removals.
 *
 * <p>Deliberately one-directional: additions and growth are api-diff's
 * concern. This tool exists to answer one question — did anything quietly
 * disappear?
 */
public final class InventoryDiff {

    private InventoryDiff() {
    }

    /** A callable that existed at the base ref and does not exist at the head ref. */
    public record Deleted(String key, String visibility, int statementsLost) {
    }

    /** A callable that kept its signature but whose body lost statements. */
    public record Shrunk(String key, int before, int after) {
    }

    public record Report(List<Deleted> deleted, List<Shrunk> shrunk) {
        public boolean isEmpty() {
            return deleted.isEmpty() && shrunk.isEmpty();
        }
    }

    /**
     * @param minShrink minimum statement drop before a surviving callable is
     *                  reported; filters formatting-level noise out of a
     *                  whole-repository run
     */
    public static Report compare(CallableInventory before, CallableInventory after, int minShrink) {
        Map<String, CallableInventory.Entry> beforeByKey = before.byKey();
        Map<String, CallableInventory.Entry> afterByKey = after.byKey();

        List<Deleted> deleted = new ArrayList<>();
        List<Shrunk> shrunk = new ArrayList<>();

        for (Map.Entry<String, CallableInventory.Entry> entry : beforeByKey.entrySet()) {
            CallableInventory.Entry priorEntry = entry.getValue();
            CallableInventory.Entry currentEntry = afterByKey.get(entry.getKey());

            if (currentEntry == null) {
                deleted.add(new Deleted(
                        entry.getKey(), priorEntry.visibility(), priorEntry.statementCount()));
            } else if (priorEntry.statementCount() - currentEntry.statementCount() >= minShrink) {
                shrunk.add(new Shrunk(
                        entry.getKey(), priorEntry.statementCount(), currentEntry.statementCount()));
            }
        }
        return new Report(deleted, shrunk);
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `.\gradlew.bat :services:tools:deletion-audit:test --tests '*InventoryDiffTest*'`
Expected: PASS, 4 tests.

- [ ] **Step 5: Commit**

```bash
git add services/tools/deletion-audit
git commit -m "feat(tools): compare callable inventories for deletions and body shrinkage"
```

---

### Task 4: Reviewed-removal allowlist

**Files:**
- Create: `services/tools/deletion-audit/src/main/java/com/qsr/customspd/tools/deletionaudit/Allowlist.java`
- Test: `services/tools/deletion-audit/src/test/java/com/qsr/customspd/tools/deletionaudit/AllowlistTest.java`

**Interfaces:**
- Consumes: nothing.
- Produces: `Allowlist.load(java.nio.file.Path) -> Allowlist` (empty allowlist when the path is `null` or absent); `allowlist.permits(String key) -> boolean`.

**Why this exists:** most removals during upstream integration are legitimate — CPDU code is replaced by upstream code on purpose. Without an allowlist the tool reports the same reviewed removals on every run and gets ignored, which is how the previous gates died.

- [ ] **Step 1: Write the failing test**

Create `services/tools/deletion-audit/src/test/java/com/qsr/customspd/tools/deletionaudit/AllowlistTest.java`.

Note `@TempDir` is a *parameter* annotation on each test method — JUnit injects a fresh temporary directory per test:

```java
package com.qsr.customspd.tools.deletionaudit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AllowlistTest {

    @Test
    void permitsKeysListedInTheFile(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("allow.txt");
        Files.writeString(file, """
                # reviewed 2026-08-10: superseded by CPDU's pack-config music routing
                CorpseDust#actions(Hero)

                Mob#die(Object)
                """);

        Allowlist allowlist = Allowlist.load(file);

        assertTrue(allowlist.permits("CorpseDust#actions(Hero)"));
        assertTrue(allowlist.permits("Mob#die(Object)"));
    }

    @Test
    void rejectsKeysNotListed(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("allow.txt");
        Files.writeString(file, "CorpseDust#actions(Hero)\n");

        assertFalse(Allowlist.load(file).permits("Hero#something()"));
    }

    @Test
    void commentsAndBlankLinesAreNotKeys(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("allow.txt");
        Files.writeString(file, "# a comment\n\n");

        assertFalse(Allowlist.load(file).permits("# a comment"));
        assertFalse(Allowlist.load(file).permits(""));
    }

    @Test
    void aNullPathYieldsAnAllowlistThatPermitsNothing() throws IOException {
        assertFalse(Allowlist.load(null).permits("Anything#at(All)"));
    }

    @Test
    void anAbsentFileYieldsAnAllowlistThatPermitsNothing(@TempDir Path dir) throws IOException {
        assertFalse(Allowlist.load(dir.resolve("missing.txt")).permits("Anything#at(All)"));
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `.\gradlew.bat :services:tools:deletion-audit:test --tests '*AllowlistTest*'`
Expected: FAIL — compilation error, `Allowlist` does not exist.

- [ ] **Step 3: Implement Allowlist**

Create `services/tools/deletion-audit/src/main/java/com/qsr/customspd/tools/deletionaudit/Allowlist.java`:

```java
package com.qsr.customspd.tools.deletionaudit;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Removal keys that a human has reviewed and accepted.
 *
 * <p>File format: one {@code TypeName#signature} key per line. Blank lines and
 * lines starting with {@code #} are ignored, so each entry can carry a comment
 * above it recording who accepted it and why.
 */
public final class Allowlist {

    private final Set<String> permitted;

    private Allowlist(Set<String> permitted) {
        this.permitted = permitted;
    }

    /** Loads the allowlist, treating a null or absent path as "permit nothing". */
    public static Allowlist load(Path path) throws IOException {
        if (path == null || !Files.exists(path)) {
            return new Allowlist(Set.of());
        }
        Set<String> keys = new HashSet<>();
        List<String> lines = Files.readAllLines(path);
        for (String line : lines) {
            String trimmed = line.strip();
            if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
                keys.add(trimmed);
            }
        }
        return new Allowlist(keys);
    }

    public boolean permits(String key) {
        return permitted.contains(key);
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `.\gradlew.bat :services:tools:deletion-audit:test --tests '*AllowlistTest*'`
Expected: PASS, 5 tests.

- [ ] **Step 5: Commit**

```bash
git add services/tools/deletion-audit
git commit -m "feat(tools): add reviewed-removal allowlist to deletion-audit"
```

---

### Task 5: CLI wiring

**Files:**
- Create: `services/tools/deletion-audit/src/main/java/com/qsr/customspd/tools/deletionaudit/DeletionAuditCli.java`
- Test: `services/tools/deletion-audit/src/test/java/com/qsr/customspd/tools/deletionaudit/DeletionAuditCliTest.java`

**Interfaces:**
- Consumes: `GitCommands` (Task 1), `InventoryExtractor` / `CallableInventory` (Task 2), `InventoryDiff` (Task 3), `Allowlist` (Task 4).
- Produces: `DeletionAuditCli.run(String base, String head, String filesGlob, int minShrink, Allowlist allowlist) -> Result`, where `record Result(int filesScanned, int deleted, int shrunk, List<String> detailLines)` has `boolean hasFindings()`.

- [ ] **Step 1: Write the failing test**

Create `services/tools/deletion-audit/src/test/java/com/qsr/customspd/tools/deletionaudit/DeletionAuditCliTest.java`:

```java
package com.qsr.customspd.tools.deletionaudit;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeletionAuditCliTest {

    @Test
    void identicalRefsProduceNoFindings() throws IOException {
        DeletionAuditCli.Result result = DeletionAuditCli.run(
                "HEAD", "HEAD", "core/src/main/java/**/*.java", 3, Allowlist.load(null));

        assertTrue(result.filesScanned() > 0,
                "scanning HEAD against itself must still walk the tree; a zero here is "
                        + "the api-diff failure mode repeating");
        assertFalse(result.hasFindings(), "HEAD vs HEAD cannot have removals");
    }

    @Test
    void globIsAppliedAgainstRepoRootRelativePaths() throws IOException {
        DeletionAuditCli.Result result = DeletionAuditCli.run(
                "HEAD", "HEAD", "SPD-classes/src/main/java/**/*.java", 3, Allowlist.load(null));

        assertTrue(result.filesScanned() > 0, "SPD-classes sources must match the glob");
    }

    @Test
    void aGlobMatchingNothingScansZeroFiles() throws IOException {
        DeletionAuditCli.Result result = DeletionAuditCli.run(
                "HEAD", "HEAD", "no/such/dir/**/*.java", 3, Allowlist.load(null));

        assertEquals(0, result.filesScanned());
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `.\gradlew.bat :services:tools:deletion-audit:test --tests '*DeletionAuditCliTest*'`
Expected: FAIL — compilation error, `DeletionAuditCli` does not exist.

- [ ] **Step 3: Implement DeletionAuditCli**

Create `services/tools/deletion-audit/src/main/java/com/qsr/customspd/tools/deletionaudit/DeletionAuditCli.java`:

```java
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
```

- [ ] **Step 4: Run the whole module's tests**

Run: `.\gradlew.bat :services:tools:deletion-audit:test`
Expected: PASS, 23 tests across five test classes (`GitCommandsTest` 4, `InventoryExtractorTest` 7, `InventoryDiffTest` 4, `AllowlistTest` 5, `DeletionAuditCliTest` 3).

- [ ] **Step 5: Commit**

```bash
git add services/tools/deletion-audit
git commit -m "feat(tools): wire deletion-audit CLI with glob, min-shrink, and allowlist"
```

---

### Task 6: First real audit run against Slice 1

**Files:**
- Create: `services/tools/deletion-audit/reviewed-removals.txt`
- Create: `services/tools/deletion-audit/README.md`

**Interfaces:**
- Consumes: the CLI from Task 5.
- Produces: a triaged allowlist file and a recorded finding count for PROJECT-STATUS.

**Expect a large number of findings.** Upstream integration legitimately removes CPDU code and replaces it with upstream code, so this is a *review queue*, not a pass/fail oracle on first run. The job is to triage it once, allowlist what is legitimate with a reason, and fix what is not.

- [ ] **Step 1: Run the audit over the Slice 1 range**

```bash
./gradlew :services:tools:deletion-audit:run --args="--base 7d9c139c8 --head HEAD --min-shrink 3" --quiet
```

Expected: a `Files scanned:` count in the high hundreds (Phase 4's api-diff run reported 1,021 files), a non-zero Deleted/Shrunk count, and `RESULT: FAIL`.

**If `Files scanned: 0`, stop.** That is the exact api-diff failure mode reproducing; fix `GitCommands.repoRoot()` before going further. A zero here means the tool is lying, not that the code is clean.

- [ ] **Step 2: Capture the report for triage**

```bash
./gradlew :services:tools:deletion-audit:run --args="--base 7d9c139c8 --head HEAD --min-shrink 3" --quiet > deletion-audit-report.txt
```

- [ ] **Step 3: Triage every DELETED entry**

For each `DELETED` line, decide one of three outcomes and record it:

1. **Legitimate** — the upstream commit being ported removed it too, or CPDU superseded it. Add the key to `reviewed-removals.txt` with a comment line above giving the reason and the date.
2. **Regression** — CPDU behavior dropped with no upstream justification. This is the `CorpseDust.actions()` class. Restore it in a separate `fix:` commit, exactly as `874e49851` did.
3. **Uncertain** — file a bead rather than guessing.

Prioritise `public` and `protected` entries first: those are the ones with external callers, so a wrong call there breaks the most.

- [ ] **Step 4: Triage SHRUNK entries**

Same three outcomes. A body that lost 3+ statements while keeping its signature is the failure class no existing gate can see, so do not skim these. Read the actual diff for each:

```bash
git diff 7d9c139c8..HEAD -- <path>
```

- [ ] **Step 5: Write the allowlist file**

Create `services/tools/deletion-audit/reviewed-removals.txt` in this format:

```
# Reviewed removals for the Slice 1 integration range (7d9c139c8..HEAD).
# One TypeName#signature key per line. A comment above each entry records why
# the removal is legitimate and when it was reviewed.
#
# Reviewed 2026-08-10 during T1 gate repair.

# superseded: CPDU drives this from pack config, upstream hardcodes it
# <replace this placeholder block with real triaged entries>
```

Replace the placeholder block with the real entries from Steps 3 and 4. Do not ship the placeholder.

- [ ] **Step 6: Verify the gate now passes**

```bash
./gradlew :services:tools:deletion-audit:run --args="--base 7d9c139c8 --head HEAD --min-shrink 3 --allowlist services/tools/deletion-audit/reviewed-removals.txt" --quiet
```

Expected: `RESULT: PASS`, exit 0. If any entry remains, it is either an un-triaged removal or a genuine regression still to fix.

- [ ] **Step 7: Write the tool README**

Create `services/tools/deletion-audit/README.md`:

```markdown
# deletion-audit

Answers the question api-diff structurally cannot: **did any code quietly disappear?**

api-diff compares the public/protected *declaration surface* between two refs. It is
blind to private members, and blind to statements removed from inside a method whose
signature never changed. The `CorpseDust.actions()` regression was caught only because
it happened to be a visible override; the same deletion one line inside the body would
have passed every gate this project had.

## Usage

```
./gradlew :services:tools:deletion-audit:run --args="\
  --base <ref> --head <ref> \
  [--files 'core/src/main/java/**/*.java'] \
  [--min-shrink 3] \
  [--allowlist services/tools/deletion-audit/reviewed-removals.txt]"
```

Exits 0 when clean, 1 when unreviewed removals remain, 2 on bad arguments.

## Reading the output

- `DELETED` — a callable present at base and absent at head.
- `SHRUNK` — a callable that kept its signature but whose body lost `--min-shrink`
  or more statements.

Both are a **review queue**, not a verdict. Integration work legitimately removes
CPDU code. Triage each entry, then either restore the behavior or record the key in
`reviewed-removals.txt` with a reason.

## Gotcha

If `Files scanned:` is 0, the tool is lying, not reporting clean code. That is the
api-diff failure mode: git invocations must be anchored to the repository root via
`GitCommands.repoRoot()`, or they inherit Gradle's subproject working directory and
scan nothing.
```

- [ ] **Step 8: Commit**

```bash
git add services/tools/deletion-audit/reviewed-removals.txt services/tools/deletion-audit/README.md
git commit -m "chore(tools): triage Slice 1 deletion-audit findings and document the tool"
```

Any regressions found in Steps 3–4 are committed separately, one `fix:` commit each, before this commit.

---

### Task 7: Prove Bundle alias resolution actually works

**Files:**
- Create: `SPD-classes/src/test/java/com/watabou/utils/BundleAliasRoundtripTest.java`

**Interfaces:**
- Consumes: `com.watabou.utils.Bundle` — `Bundle.addAlias(Class<?>, String)`, `Bundle.write(Bundle, OutputStream)`, `Bundle.read(InputStream)`, `bundle.put(String, Bundlable)`, `bundle.get(String)`.
- Produces: nothing consumed by later tasks.

**Why this exists and what it does NOT cover.** Slice 1 moved `EntranceRoom`/`ExitRoom` between packages and relied on `Bundle.addAlias` to keep old saves loading. Nothing has ever tested that `addAlias` works. This task tests the *mechanism* with a test-local class.

It cannot test the *registrations*: `core` depends on `SPD-classes`, never the reverse, so no test here may reference `EntranceRoom`, `ExitRoom`, or core's terrain constants. Auditing the actual registrations and the `CUSTOM_DECO`-takes-`SIGN`-id-23 terrain reuse needs either a test source set on `core` or a static checker, and is filed as a follow-up bead in Task 9 rather than bodged in here.

- [ ] **Step 1: Write the failing test**

Create `SPD-classes/src/test/java/com/watabou/utils/BundleAliasRoundtripTest.java`:

```java
package com.watabou.utils;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Slice 1 moved EntranceRoom/ExitRoom between packages and depended on
 * Bundle.addAlias to keep old saves loading. Nothing had ever verified that
 * addAlias resolves. These tests pin the mechanism using a test-local class,
 * because SPD-classes cannot reference core types.
 */
class BundleAliasRoundtripTest {

    /** Stands in for a game class that was moved between packages. */
    public static class MovedThing implements Bundlable {
        int value;

        @Override
        public void storeInBundle(Bundle bundle) {
            bundle.put("value", value);
        }

        @Override
        public void restoreFromBundle(Bundle bundle) {
            value = bundle.getInt("value");
        }
    }

    private static Bundle roundtrip(Bundle source) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        assertTrue(Bundle.write(source, out), "write must succeed");
        return Bundle.read(new ByteArrayInputStream(out.toByteArray()));
    }

    @Test
    void aBundlableSurvivesAWriteReadRoundtrip() throws IOException {
        MovedThing thing = new MovedThing();
        thing.value = 42;

        Bundle source = new Bundle();
        source.put("thing", thing);

        Bundle restored = roundtrip(source);
        Bundlable result = restored.get("thing");

        assertNotNull(result, "the bundled object must come back");
        assertEquals(42, ((MovedThing) result).value);
    }

    @Test
    void anAliasedLegacyClassNameResolvesToTheCurrentClass() throws IOException {
        // Simulate a save written before the class moved: the persisted
        // __className is the OLD fully-qualified name, which no longer exists.
        String legacyName = "com.watabou.utils.legacy.OldMovedThing";
        Bundle.addAlias(MovedThing.class, legacyName);

        String json = "{\"thing\":{\"__className\":\"" + legacyName + "\",\"value\":7}}";
        Bundle restored = Bundle.read(
                new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));

        Bundlable result = restored.get("thing");

        assertNotNull(result,
                "an aliased legacy class name must resolve; if this is null, every "
                        + "addAlias registration Slice 1 added is silently dead and old "
                        + "saves lose these objects");
        assertEquals(7, ((MovedThing) result).value);
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `.\gradlew.bat :SPD-classes:test --tests '*BundleAliasRoundtripTest*'`
Expected: FAIL — the class does not exist yet, then once compiled, the assertions exercise real behavior.

- [ ] **Step 3: Run and interpret**

Run: `.\gradlew.bat :SPD-classes:test --tests '*BundleAliasRoundtripTest*'`

Two legitimate outcomes:

- **PASS** — the alias mechanism works. The gate now exists and guards it.
- **FAIL** — `Bundle.read` cannot resolve the alias. **Do not weaken the test to make it green.** This is a real finding: it means Slice 1's `addAlias` registrations do not do what the integration assumed, and every affected old save silently loses those objects. Read `Bundle.java` around the `aliases` map (declared at line 56) and the class-resolution path in `get(String)`, identify why, and file a bead. Report the finding rather than deleting the test.

If the failure is instead a `Game.reportException` NPE from `Bundle`'s error path, that means an earlier assertion already failed; fix the reading of the test, not the test's intent.

- [ ] **Step 4: Commit**

```bash
git add SPD-classes/src/test/java/com/watabou/utils/BundleAliasRoundtripTest.java
git commit -m "test: pin Bundle alias resolution that Slice 1's package moves depend on"
```

---

### Task 8: Desktop boot smoke

**Files:**
- Modify: `desktop/src/main/java/com/qsr/customspd/desktop/DesktopLauncher.java`
- Create: `services/tools/desktop-smoke/desktop-smoke.ps1`
- Create: `services/tools/desktop-smoke/README.md`

**Interfaces:**
- Consumes: the `desktop:release` Gradle task, which produces `desktop/build/libs/desktop-<version>.jar`.
- Produces: a smoke script returning exit 0 on a clean boot, non-zero otherwise.

**Why this replaces the Android smoke.** `smoke-boot.ps1` has never executed the APK: the emulator never reaches `sys.boot_completed`, so the run dies before `adb install`. Even when it did run it was only a PID-alive check. The desktop path exercises the same `core` module and actually runs.

- [ ] **Step 1: Confirm the insertion point**

Run: `grep -n "void main\|new Lwjgl3Application" desktop/src/main/java/com/qsr/customspd/desktop/DesktopLauncher.java`

Expected: `main` at line 49, and the application constructed on line 189:

```java
		new Lwjgl3Application(new ShatteredPixelDungeon(new DesktopPlatformSupport(), new DesktopTileMapCompiler()), config);
```

**This constructor blocks.** LWJGL3 runs the whole render loop inside it and does not return until the application exits. The watchdog must therefore be started on the line *before* it — a watchdog installed "after the app is created" would never run at all.

- [ ] **Step 2: Add the smoke-exit hook**

In `DesktopLauncher.java`, add the method below to the class, and call it on the line immediately **before** `new Lwjgl3Application(...)`:

```java
		installSmokeWatchdog();
		new Lwjgl3Application(new ShatteredPixelDungeon(new DesktopPlatformSupport(), new DesktopTileMapCompiler()), config);
```

The method itself:

```java
	/**
	 * Boot-smoke hook. When -Dsmoke.frames=N is set, the launcher exits with
	 * status 0 once the game has been alive long enough to render N frames,
	 * and with status 1 if it never gets there.
	 *
	 * This exists because the Android emulator smoke has never once executed
	 * the APK -- it dies before adb install -- so the desktop path is the only
	 * automated proof that core actually boots.
	 */
	private static void installSmokeWatchdog() {
		String frames = System.getProperty("smoke.frames");
		if (frames == null) {
			return;
		}
		int targetFrames = Integer.parseInt(frames);
		Thread watchdog = new Thread(() -> {
			// 60fps nominal, with a 4x grace factor for slow first-frame asset loads
			long budgetMillis = Math.max(5_000L, (targetFrames * 1000L / 60L) * 4L);
			long deadline = System.currentTimeMillis() + budgetMillis;
			while (System.currentTimeMillis() < deadline) {
				if (com.badlogic.gdx.Gdx.graphics != null
						&& com.badlogic.gdx.Gdx.graphics.getFrameId() >= targetFrames) {
					System.out.println("SMOKE: reached frame "
							+ com.badlogic.gdx.Gdx.graphics.getFrameId() + ", exiting clean");
					Runtime.getRuntime().halt(0);
				}
				try {
					Thread.sleep(100L);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					return;
				}
			}
			System.err.println("SMOKE: never reached frame " + targetFrames);
			Runtime.getRuntime().halt(1);
		}, "smoke-watchdog");
		watchdog.setDaemon(true);
		watchdog.start();
	}
```

Two deliberate choices, do not "simplify" either:

- `Runtime.halt` rather than `System.exit` — `exit` runs shutdown hooks that may block on the GL thread and hang the smoke.
- The watchdog is a **daemon** thread started before the blocking constructor, so a normal (non-smoke) launch is completely unaffected: with `smoke.frames` unset the method returns immediately and no thread is created.

- [ ] **Step 3: Write the smoke script**

Create `services/tools/desktop-smoke/desktop-smoke.ps1`:

```powershell
#requires -Version 7
<#
.SYNOPSIS
    Boot smoke for the desktop build: proves core actually starts and renders.
.DESCRIPTION
    Replaces the Android emulator smoke, which has never executed the APK because
    the emulator never reaches sys.boot_completed. Builds the desktop jar, runs it
    with -Dsmoke.frames, and asserts a clean exit.
.PARAMETER Frames
    How many rendered frames count as a successful boot.
.PARAMETER TimeoutSeconds
    Hard ceiling on the whole run.
#>
param(
    [int]$Frames = 120,
    [int]$TimeoutSeconds = 180
)

$ErrorActionPreference = 'Stop'
$repoRoot = (git rev-parse --show-toplevel).Trim()
Set-Location $repoRoot

Write-Host "desktop-smoke: building the release jar"
& "$repoRoot\gradlew.bat" desktop:release --quiet
if ($LASTEXITCODE -ne 0) {
    Write-Error "desktop-smoke: desktop:release failed"
    exit 1
}

$jar = Get-ChildItem "$repoRoot\desktop\build\libs\*.jar" |
    Sort-Object LastWriteTime -Descending |
    Select-Object -First 1
if (-not $jar) {
    Write-Error "desktop-smoke: no jar produced under desktop/build/libs"
    exit 1
}
Write-Host "desktop-smoke: launching $($jar.Name), target $Frames frames"

$stdout = New-TemporaryFile
$stderr = New-TemporaryFile
$process = Start-Process -FilePath 'java' `
    -ArgumentList @("-Dsmoke.frames=$Frames", '-jar', $jar.FullName) `
    -PassThru -NoNewWindow `
    -RedirectStandardOutput $stdout -RedirectStandardError $stderr

if (-not $process.WaitForExit($TimeoutSeconds * 1000)) {
    Write-Host "desktop-smoke: timed out after ${TimeoutSeconds}s, killing"
    $process | Stop-Process -Force
    Get-Content $stderr | Select-Object -Last 40
    exit 1
}

$out = Get-Content $stdout -Raw
$err = Get-Content $stderr -Raw
Remove-Item $stdout, $stderr -Force

if ($process.ExitCode -ne 0) {
    Write-Host "desktop-smoke: FAIL (exit $($process.ExitCode))"
    Write-Host $err
    exit 1
}
if ($out -notmatch 'SMOKE: reached frame') {
    Write-Host "desktop-smoke: FAIL (exited 0 but never confirmed a rendered frame)"
    Write-Host $out
    Write-Host $err
    exit 1
}

Write-Host "desktop-smoke: PASS"
exit 0
```

- [ ] **Step 4: Run the smoke**

Run: `pwsh services/tools/desktop-smoke/desktop-smoke.ps1`
Expected: `desktop-smoke: PASS`, exit 0.

This needs a display. It will fail on a headless machine, which is a real constraint to document, not to work around today.

- [ ] **Step 5: Write the README**

Create `services/tools/desktop-smoke/README.md`:

```markdown
# desktop-smoke

Proves the `core` module actually boots and renders.

## Why this exists

`services/tools/smoke-boot/` targets Android and has **never executed the APK**. The
emulator never reaches `sys.boot_completed`, so the run dies before `adb install`.
Even when it did run it was a PID-alive check, which never verified gameplay.

This script runs the real desktop jar, waits for the game to render a configurable
number of frames, and asserts a clean exit. Rendered frames mean assets loaded, the
GL context came up, and the initial scene constructed.

## Usage

```
pwsh services/tools/desktop-smoke/desktop-smoke.ps1 [-Frames 120] [-TimeoutSeconds 180]
```

Exit 0 on a confirmed boot, 1 otherwise.

## Constraints

- **Requires a display.** Headless CI cannot run this as written.
- Depends on the `-Dsmoke.frames` hook in `DesktopLauncher`. Removing that hook
  silently turns this script into a PID-alive check, which is the failure this
  tool was built to end.
- Android remains a **manual** pre-release check. It is not an automated gate and
  must not be cited as one.
```

- [ ] **Step 6: Commit**

```bash
git add desktop/src/main/java/com/qsr/customspd/desktop/DesktopLauncher.java services/tools/desktop-smoke
git commit -m "feat(tools): add desktop boot smoke to replace the never-running Android smoke"
```

---

### Task 9: Record the new gate set and file follow-ups

**Files:**
- Modify: `PROJECT-STATUS.md`
- Modify: `CHANGELOG.md`
- Modify: `docs/superpowers/specs/2026-08-10-lutherverse-push-design.md`

**Interfaces:**
- Consumes: findings from Tasks 6, 7, and 8.
- Produces: the recorded gate set that T2 and T5 run against.

- [ ] **Step 1: File follow-up beads**

```bash
bd create --title "Add a test source set to core so core:test stops being vacuous" --labels worker,size:small
bd create --title "Static-check core terrain ID uniqueness and Bundle.addAlias registrations" --labels worker,size:medium
bd create --title "Audit Task 9 asset batches for the assets-landed/code-deferred pipeline seam" --labels worker,size:small
```

The second one covers what Task 7 could not: `core` depends on `SPD-classes`, so no test there can reference `EntranceRoom`, `ExitRoom`, or the `CUSTOM_DECO`-takes-`SIGN`-id-23 terrain reuse. It needs a static checker or a `core` test source set.

- [ ] **Step 2: Update PROJECT-STATUS.md**

Replace the "Known tool limitations found while running these gates" section's stale claims with the current state:

- deletion-audit now covers private members and method bodies; record the Slice 1 triage counts from Task 6.
- `Bundle.addAlias` resolution is now pinned by a test; record whether it passed or exposed a defect.
- Android runtime smoke is superseded by desktop-smoke for automation and downgraded to a documented manual pre-release check.
- Leave the `core:test` is `NO-SOURCE` note in place — it is still true — and add the follow-up bead id.

- [ ] **Step 3: Update the spec's verification table**

In `docs/superpowers/specs/2026-08-10-lutherverse-push-design.md`, the verification table lists the desktop boot smoke as "reaches title screen, exits clean". Replace with the real command:

```
| Desktop boot smoke *(new)* | `pwsh services/tools/desktop-smoke/desktop-smoke.ps1` |
```

And replace the save-roundtrip row, which claimed more coverage than Task 7 delivers:

```
| Bundle alias resolution *(new)* | `gradlew SPD-classes:test` — mechanism only; core registrations tracked separately |
```

- [ ] **Step 4: Add a CHANGELOG entry**

Under an `### Added` heading in the current unreleased section:

```markdown
- `services/tools/deletion-audit` — audits removals api-diff cannot see (private
  members and method bodies), with a reviewed-removal allowlist.
- `services/tools/desktop-smoke` — boot smoke that actually executes the game,
  replacing the Android emulator smoke that never ran.
- `BundleAliasRoundtripTest` — pins the `Bundle.addAlias` resolution that Slice 1's
  package moves depend on.
```

- [ ] **Step 5: Run the full gate set**

```bash
./gradlew core:compileJava --rerun-tasks SPD-classes:test :services:tools:api-diff:test :services:tools:pack-smoke:test :services:tools:namespace-transform:test :services:tools:deletion-audit:test desktop:release android:assembleDebug
```

Expected: all tasks PASS. Then:

```bash
./gradlew :services:tools:deletion-audit:run --args="--base 7d9c139c8 --head HEAD --min-shrink 3 --allowlist services/tools/deletion-audit/reviewed-removals.txt" --quiet
```

Expected: `RESULT: PASS`.

- [ ] **Step 6: Commit**

```bash
git add PROJECT-STATUS.md CHANGELOG.md docs/superpowers/specs/2026-08-10-lutherverse-push-design.md
git commit -m "docs: record the repaired gate set and T1 findings"
```

---

## What T1 does not cover

Stated so no later reader mistakes silence for coverage:

- **`core` still has no tests.** `core:test` remains `NO-SOURCE` and must never be cited as a gate. Follow-up bead filed in Task 9.
- **Core-specific save invariants are unverified.** Task 7 proves the alias *mechanism*; it cannot reach `EntranceRoom`/`ExitRoom` or the terrain-ID reuse because of the `core` → `SPD-classes` dependency direction. Follow-up bead filed in Task 9.
- **Android runtime is manual.** desktop-smoke exercises the same `core` module but not the Android platform layer.
- **desktop-smoke needs a display.** It cannot run headless as written.
- **deletion-audit is a review queue, not an oracle.** It reports what vanished; a human decides whether that was correct.

## Sequencing after T1

| Track | Gate to start |
|---|---|
| T2 ready-bead burndown | T1 complete; every bead runs the new gate set, starting with `cpdu-0a7`, already half-landed in the working tree |
| T3 vision decomposition | none — runs concurrently, its own brainstorm |
| T4 Sub-C API design | T3 delivers the consumer-demand list |
| T5 Sub-B completion grind | T1 complete, T2 drained |
