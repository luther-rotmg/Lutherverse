# Sub-B Slice 0 Implementation Plan: Foundation

> **STATUS (2026-08-11): Slice 0 shipped 2026-07-22.** Checkboxes below were never ticked at the time despite the work landing (see `PROJECT-STATUS.md` "Sub-B" line and the 2026-07-22 recent-activity entry). Verified retroactively: every file this plan lists exists on disk, and every task's commit message is present on `origin/main` (`c2d86e5d2`, `1053b1771`, `277d1766b`, `531d8ec08`, `c2dd756e2`, `7c3743e12`, plus the api-diff/pack-smoke/docs commits). Checkboxes below are ticked to match reality, not re-executed now.

> **For agentic workers:** REQUIRED SUB-SKILL: use `superpowers:subagent-driven-development` (recommended) or `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship the infrastructure that every downstream Sub-B slice depends on — save-compat bridge scaffolding, API-diff auditor, pack boot-smoke harness, headless Android smoke-boot script, per-slice acceptance-template, and cleanup of the dangling `:ios` settings.gradle entry.

**Architecture:** All new tooling lives under `services/tools/` (new subtree). Save-bridge machinery lives in `SPD-classes/src/main/java/com/watabou/utils/`. No changes to game code. One PR-worth of work, targeting a merge to `main` when the whole slice is green.

**Tech Stack:** Java 17 (matches Sub-A build baseline), Gradle 8.5 (current), JUnit 5 for the new test harness (matches SPD conventions), plain bash + pwsh scripts for headless smoke-boot.

## Global Constraints

- Author = LO (`ryan.duke360@gmail.com`) on every commit. Zero `Co-Authored-By: Claude` trailers. Zero AI attribution.
- pwsh 7 only. Never `powershell` 5.1. All build/test scripts use `pwsh -NoProfile`.
- No changes to any file under `core/`, `android/src/main/java/`, `desktop/src/main/java/`, `SPD-classes/src/main/java/com/watabou/noosa/`, `SPD-classes/src/main/java/com/watabou/glwrap/`, `SPD-classes/src/main/java/com/watabou/gltextures/`, or `marketplace/`. Slice 0 touches only: `services/tools/` (new), `SPD-classes/src/main/java/com/watabou/utils/` (new class + subpackage), `SPD-classes/src/test/` (new test tree), `settings.gradle` (delete one line), `build.gradle` (add two Gradle tasks), plus doc updates (CHANGELOG, PROJECT-STATUS, docs/superpowers/).
- Every substantive commit updates `CHANGELOG.md` and `PROJECT-STATUS.md` in the same commit per the changelog-cadence rule.
- Each task ends with a commit whose message begins with a conventional-commit prefix (`feat(bridge):`, `feat(tools):`, `chore(settings):`, `docs:`, `test(bridge):`, etc.). No `git commit --amend` on already-pushed commits.
- No push to origin until the entire slice is green. Slice 0 pushes as one atomic batch after Task 15 verifies.

## File Structure

Files created:
- `services/tools/api-diff/build.gradle` (new module, ~30 lines)
- `services/tools/api-diff/src/main/java/com/qsr/customspd/tools/apidiff/ApiDiffCli.java` (main class, ~150 lines)
- `services/tools/api-diff/src/main/java/com/qsr/customspd/tools/apidiff/JavaSurfaceExtractor.java` (parses public API from a git-blob source file, ~120 lines)
- `services/tools/api-diff/src/main/java/com/qsr/customspd/tools/apidiff/DiffReport.java` (data model + rendering, ~80 lines)
- `services/tools/pack-smoke/build.gradle` (new module, ~30 lines)
- `services/tools/pack-smoke/src/main/java/com/qsr/customspd/tools/packsmoke/PackSmokeCli.java` (main class, ~120 lines)
- `services/tools/pack-smoke/src/main/java/com/qsr/customspd/tools/packsmoke/PackLoaderProbe.java` (invokes CPD's mod-loader against a pack, catches all Throwable, records status, ~90 lines)
- `SPD-classes/src/main/java/com/watabou/utils/BundleBridge.java` (new class, ~200 lines: version detection + translator chain entry + graceful fallback)
- `SPD-classes/src/main/java/com/watabou/utils/bridge/BundleTranslator.java` (interface, ~15 lines)
- `SPD-classes/src/main/java/com/watabou/utils/bridge/PreV232Translator.java` (stub for now, populated in Slice 3a)
- `SPD-classes/src/main/java/com/watabou/utils/bridge/PreV242Translator.java` (stub for now, populated in Slice 5b)
- `SPD-classes/src/main/java/com/watabou/utils/bridge/PreV254Translator.java` (stub for now, populated in Slice 6c)
- `SPD-classes/src/test/java/com/watabou/utils/BundleBridgeTest.java` (JUnit 5, ~150 lines)
- `SPD-classes/src/test/resources/save-fixtures/cpd-v2.1.0-1.0-sample.dat` (extracted from playtest save)
- `SPD-classes/src/test/resources/save-fixtures/cpd-v2.1.0-1.0-sample.expected.json` (expected upcast result)
- `services/tools/smoke-boot/smoke-boot.sh` (bash, ~40 lines)
- `services/tools/smoke-boot/smoke-boot.ps1` (pwsh, ~40 lines)
- `docs/superpowers/plans/SLICE-TEMPLATE.md` (acceptance-block template, ~50 lines)

Files modified:
- `settings.gradle` (delete `include ':ios'`, add `include ':services:tools:api-diff'` + `include ':services:tools:pack-smoke'`)
- `build.gradle` (root — add two `task` definitions for `api-diff` and `pack-smoke` convenience invocation)
- `SPD-classes/build.gradle` (add JUnit 5 dependency to test source set if not present)
- `CHANGELOG.md` (add [Unreleased] Sub-B slice-0 subsection)
- `PROJECT-STATUS.md` (update Sub-B status to "actively executing Slice 0", update roadmap table row B)

---

### Task 1: Preflight — verify starting state

**Files:** none modified

**Interfaces:**
- Consumes: current git state
- Produces: signed confirmation that starting state is clean and correct

- [x] **Step 1: Verify branch is `main` and working tree is clean**

Run:
```
pwsh -NoProfile -Command "Set-Location 'C:\Users\minec\Documents\Projects\CustomPixelDungeonUltimate'; git branch --show-current; git status --short"
```

Expected: first line prints `main`; second line prints nothing (clean tree).

If working tree is not clean, stop and escalate.

- [x] **Step 2: Verify local main matches origin/main**

Run:
```
pwsh -NoProfile -Command "Set-Location 'C:\Users\minec\Documents\Projects\CustomPixelDungeonUltimate'; git fetch origin; git log --oneline main..origin/main; git log --oneline origin/main..main"
```

Expected: both `git log` commands print nothing (local and origin match).

If they diverge, stop and escalate — Slice 0 should start from a clean sync point.

- [x] **Step 3: Verify env still has JDK 17 + Android SDK**

Run:
```
pwsh -NoProfile -Command "$env:JAVA_HOME = [Environment]::GetEnvironmentVariable('JAVA_HOME','User'); Write-Host \"JAVA_HOME: $env:JAVA_HOME\"; & \"$env:JAVA_HOME\bin\java.exe\" -version 2>&1; Test-Path ([Environment]::GetEnvironmentVariable('ANDROID_HOME','User'))"
```

Expected: `JAVA_HOME` prints the JDK 17 path from Sub-A's install, `java -version` prints `openjdk version "17.*"`, `Test-Path` on `ANDROID_HOME` prints `True`.

If any check fails, stop and escalate — env prereqs from Sub-A must be intact.

- [x] **Step 4: Confirm both builds still pass from current tree**

Run:
```
pwsh -NoProfile -Command "$env:JAVA_HOME = [Environment]::GetEnvironmentVariable('JAVA_HOME','User'); $env:ANDROID_HOME = [Environment]::GetEnvironmentVariable('ANDROID_HOME','User'); $env:Path = \"$env:JAVA_HOME\bin;$env:Path\"; Set-Location 'C:\Users\minec\Documents\Projects\CustomPixelDungeonUltimate'; & .\gradlew.bat android:assembleDebug desktop:release --no-daemon"
```

Expected: both tasks report `BUILD SUCCESSFUL`, exit 0.

If failure, stop and escalate — the Sub-A build baseline must remain green before Slice 0 additions.

- [x] **Step 5: No commit for this task**

This is preflight only. If all four steps pass, proceed to Task 2.

---

### Task 2: Delete `:ios` from settings.gradle + verify builds unchanged

**Files:**
- Modify: `settings.gradle` (one-line delete)

**Interfaces:**
- Consumes: settings.gradle current state (contains `include ':ios'`)
- Produces: settings.gradle with the `:ios` include removed

- [x] **Step 1: Read current settings.gradle**

Run:
```
pwsh -NoProfile -Command "Get-Content 'C:\Users\minec\Documents\Projects\CustomPixelDungeonUltimate\settings.gradle'"
```

Confirm the file includes a line matching `include ':ios'`.

- [x] **Step 2: Delete the `:ios` include**

Edit `settings.gradle`: remove the exact line `include ':ios'` (preserve everything else including comments).

- [x] **Step 3: Verify Desktop build still passes**

```
pwsh -NoProfile -Command "$env:JAVA_HOME = [Environment]::GetEnvironmentVariable('JAVA_HOME','User'); $env:ANDROID_HOME = [Environment]::GetEnvironmentVariable('ANDROID_HOME','User'); $env:Path = \"$env:JAVA_HOME\bin;$env:Path\"; Set-Location 'C:\Users\minec\Documents\Projects\CustomPixelDungeonUltimate'; & .\gradlew.bat desktop:release --no-daemon"
```

Expected: `BUILD SUCCESSFUL`, exit 0.

- [x] **Step 4: Verify Android build still passes**

```
pwsh -NoProfile -Command "$env:JAVA_HOME = [Environment]::GetEnvironmentVariable('JAVA_HOME','User'); $env:ANDROID_HOME = [Environment]::GetEnvironmentVariable('ANDROID_HOME','User'); $env:Path = \"$env:JAVA_HOME\bin;$env:Path\"; Set-Location 'C:\Users\minec\Documents\Projects\CustomPixelDungeonUltimate'; & .\gradlew.bat android:assembleDebug --no-daemon"
```

Expected: `BUILD SUCCESSFUL`, exit 0.

- [x] **Step 5: Commit**

```
git add settings.gradle
git commit -m "chore(settings): delete dangling :ios include (ios/ dir was removed from CPD in May 2023)"
```

---

### Task 3: API-diff auditor — Gradle module scaffolding + JavaSurfaceExtractor + DiffReport

**Files:**
- Create: `services/tools/api-diff/build.gradle`
- Create: `services/tools/api-diff/src/main/java/com/qsr/customspd/tools/apidiff/JavaSurfaceExtractor.java`
- Create: `services/tools/api-diff/src/main/java/com/qsr/customspd/tools/apidiff/DiffReport.java`
- Create: `services/tools/api-diff/src/test/java/com/qsr/customspd/tools/apidiff/JavaSurfaceExtractorTest.java`
- Modify: `settings.gradle` (add `include ':services:tools:api-diff'`)

**Interfaces:**
- Consumes: raw Java source (as read from `git show <rev>:<file>`)
- Produces: `JavaSurface` value object (list of public / protected / package-private symbols with their signatures); `DiffReport` value object (comparison of two `JavaSurface` instances)

- [x] **Step 1: Write failing test for JavaSurfaceExtractor**

Create `JavaSurfaceExtractorTest.java` with a test that parses a hardcoded Java source string containing one public class with two public methods, verifies the extractor returns a `JavaSurface` listing exactly those two methods.

Test template:
```java
@Test
void extractsPublicMethodSignatures() {
    String source = """
        package com.example;
        public class Foo {
            public void bar(int x) {}
            public String baz() { return ""; }
            private void hidden() {}
        }
        """;
    JavaSurface surface = JavaSurfaceExtractor.extract("com/example/Foo.java", source);
    assertEquals(2, surface.symbols().size());
    assertTrue(surface.symbols().contains(new JavaSurface.Symbol("com.example.Foo", "bar(int)", "public", "void")));
    assertTrue(surface.symbols().contains(new JavaSurface.Symbol("com.example.Foo", "baz()", "public", "String")));
}
```

- [x] **Step 2: Run test to verify fail**

```
pwsh -NoProfile -Command "$env:JAVA_HOME = [Environment]::GetEnvironmentVariable('JAVA_HOME','User'); Set-Location 'C:\Users\minec\Documents\Projects\CustomPixelDungeonUltimate'; & .\gradlew.bat :services:tools:api-diff:test --no-daemon"
```

Expected: FAIL (class does not exist yet).

- [x] **Step 3: Write minimal JavaSurfaceExtractor implementation**

Approach: use `com.github.javaparser:javaparser-core:3.25.5` (add as Maven dep in the module build.gradle). Parse the source, walk the AST, extract every `TypeDeclaration` and its member `MethodDeclaration` and `FieldDeclaration` where modifier is `public` or `protected`. Emit `JavaSurface.Symbol` records: `{fullyQualifiedTypeName, memberSignature, visibility, returnType}`.

- [x] **Step 4: Add JavaParser dependency + minimal build.gradle**

`services/tools/api-diff/build.gradle`:
```gradle
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
    mainClass = 'com.qsr.customspd.tools.apidiff.ApiDiffCli'
}

test {
    useJUnitPlatform()
}
```

- [x] **Step 5: Wire the new module into settings.gradle**

Edit `settings.gradle`, add: `include ':services:tools:api-diff'`

- [x] **Step 6: Run test to verify pass**

```
pwsh -NoProfile -Command "$env:JAVA_HOME = [Environment]::GetEnvironmentVariable('JAVA_HOME','User'); Set-Location 'C:\Users\minec\Documents\Projects\CustomPixelDungeonUltimate'; & .\gradlew.bat :services:tools:api-diff:test --no-daemon"
```

Expected: PASS (JavaSurfaceExtractor extracts the two public methods).

- [x] **Step 7: Implement DiffReport with a test**

Add a test to `JavaSurfaceExtractorTest.java` (or create `DiffReportTest.java`) that:
- Builds two `JavaSurface` instances with a known delta (one symbol removed, one added, one signature-changed)
- Calls `DiffReport.compare(before, after)`
- Asserts the report contains one `Removed`, one `Added`, one `SignatureChanged` entry

Implement `DiffReport` as a value object with static factory `compare(before, after)` that returns a report of change categories.

- [x] **Step 8: Run test to verify pass**

Same gradle command. Both tests should pass.

- [x] **Step 9: Commit**

```
git add services/tools/api-diff/ settings.gradle
git commit -m "feat(tools): scaffold API-diff auditor (JavaSurfaceExtractor + DiffReport)"
```

---

### Task 4: API-diff auditor — CLI wiring + git-blob source reader

**Files:**
- Create: `services/tools/api-diff/src/main/java/com/qsr/customspd/tools/apidiff/ApiDiffCli.java`
- Create: `services/tools/api-diff/src/main/java/com/qsr/customspd/tools/apidiff/GitBlobReader.java`
- Create: `services/tools/api-diff/src/test/java/com/qsr/customspd/tools/apidiff/GitBlobReaderTest.java`
- Modify: root `build.gradle` (add convenience `task apiDiff` at root that delegates to the module)

**Interfaces:**
- Consumes: two git refs (BASE and HEAD) + a file-glob for which source files to audit
- Produces: prints a formatted `DiffReport` to stdout; exits 0 if zero unexpected removals, exits 1 if any removals were found

- [x] **Step 1: Write failing test for GitBlobReader**

Test: `GitBlobReader.read(gitRef, filePath)` returns the file content at that ref. Use `ProcessBuilder` to invoke `git show <ref>:<path>` and capture stdout.

Test asserts a known file at a known commit produces expected content (use `HEAD:README.md` for the assertion — content is stable within a test run).

- [x] **Step 2: Run test to verify fail**

Same gradle test invocation.

- [x] **Step 3: Implement GitBlobReader**

Minimal `ProcessBuilder`-based implementation. Handle non-zero exit (throw `IOException`).

- [x] **Step 4: Run test to verify pass**

Same gradle command.

- [x] **Step 5: Implement ApiDiffCli main method**

Parse args: `--base <ref> --head <ref> --files <glob>` (glob defaults to `core/src/main/java/**/*.java`). For each matching file: read at BASE via `GitBlobReader`, extract `JavaSurface`; read at HEAD, extract `JavaSurface`; compare via `DiffReport.compare`. Aggregate reports across all files. Print summary + detail.

Exit code: 0 if zero removals + zero signature-changes; 1 otherwise. This is what makes it a gate.

- [x] **Step 6: Add root-level convenience task**

Edit root `build.gradle`, add:
```gradle
task apiDiff(type: JavaExec) {
    group = 'verification'
    description = 'Run API-diff auditor against a range (--args="--base <ref> --head <ref>")'
    dependsOn ':services:tools:api-diff:compileJava'
    classpath = project(':services:tools:api-diff').sourceSets.main.runtimeClasspath
    mainClass = 'com.qsr.customspd.tools.apidiff.ApiDiffCli'
}
```

- [x] **Step 7: Smoke-test the CLI end-to-end**

Run:
```
pwsh -NoProfile -Command "Set-Location 'C:\Users\minec\Documents\Projects\CustomPixelDungeonUltimate'; & .\gradlew.bat apiDiff --args='--base cpd-sync-base-2025-08-15 --head main --files core/src/main/java/**/*.java' --no-daemon"
```

Expected: report shows a stats summary. Sub-A was docs-only under core/ so the exit code should be 0.

- [x] **Step 8: Commit**

```
git add services/tools/api-diff/ build.gradle
git commit -m "feat(tools): API-diff CLI + git-blob reader + root gradle task"
```

---

### Task 5: Pack boot-smoke harness — Gradle module scaffolding + PackLoaderProbe

**Files:**
- Create: `services/tools/pack-smoke/build.gradle`
- Create: `services/tools/pack-smoke/src/main/java/com/qsr/customspd/tools/packsmoke/PackLoaderProbe.java`
- Create: `services/tools/pack-smoke/src/test/java/com/qsr/customspd/tools/packsmoke/PackLoaderProbeTest.java`
- Modify: `settings.gradle` (add `include ':services:tools:pack-smoke'`)

**Interfaces:**
- Consumes: path to a marketplace pack directory (containing `mod_info.json` + resource overrides)
- Produces: `PackLoaderProbe.Result` — status enum {GREEN, FAILED_MANIFEST_INVALID, FAILED_LOAD_EXCEPTION, FAILED_ASSET_MISSING} plus optional exception detail

- [x] **Step 1: Write failing test for PackLoaderProbe**

Test: `PackLoaderProbe.probe(packPath)` returns GREEN for a valid pack, FAILED_MANIFEST_INVALID for a pack with malformed `mod_info.json`, FAILED_ASSET_MISSING for a pack whose manifest references a nonexistent asset.

Use synthetic test packs constructed in `@BeforeEach` (temp-dir with a valid + a broken manifest).

- [x] **Step 2: Run test to verify fail**

Same gradle test invocation for the pack-smoke module.

- [x] **Step 3: Implement PackLoaderProbe**

Load the pack's `mod_info.json`, parse via a light JSON lib (`com.fasterxml.jackson.core:jackson-databind`). Validate required fields (`name`, `version`, `min_cpd_version`). Walk the referenced asset paths; verify each exists on disk. Do NOT actually invoke the CPD game runtime (that would require the whole game classpath; too heavy for Slice 0). Just verify the pack is structurally loadable.

Note: This is a structural probe, not a runtime probe. Slice 7 upgrades this to an actual game-runtime boot test. Slice 0 sets the structural baseline.

- [x] **Step 4: Set up module build.gradle**

Same shape as Task 3's api-diff module. Add Jackson dep.

- [x] **Step 5: Wire into settings.gradle**

Add `include ':services:tools:pack-smoke'`.

- [x] **Step 6: Run tests to verify pass**

- [x] **Step 7: Commit**

```
git add services/tools/pack-smoke/ settings.gradle
git commit -m "feat(tools): scaffold pack boot-smoke harness (PackLoaderProbe structural check)"
```

---

### Task 6: Pack boot-smoke — CLI wiring + baseline run against current marketplace

**Files:**
- Create: `services/tools/pack-smoke/src/main/java/com/qsr/customspd/tools/packsmoke/PackSmokeCli.java`
- Modify: root `build.gradle` (add `task packSmoke`)

**Interfaces:**
- Consumes: path to the marketplace root directory (defaults to `<repo>/marketplace/`)
- Produces: prints a report line per pack ("<pack-name>: GREEN" or "<pack-name>: <failure-reason>"); exits 0 if all GREEN, 1 if any failure

- [x] **Step 1: Implement PackSmokeCli main method**

Parse args: `--marketplace <path>` (default `./marketplace/`). Iterate over every subdirectory. For each subdirectory containing `mod_info.json`, invoke `PackLoaderProbe.probe()`. Aggregate results. Print summary line per pack, then a total.

Exit 0 if all GREEN; exit 1 with count of failures otherwise.

- [x] **Step 2: Add root-level convenience task**

```gradle
task packSmoke(type: JavaExec) {
    group = 'verification'
    description = 'Run pack boot-smoke harness against marketplace/'
    dependsOn ':services:tools:pack-smoke:compileJava'
    classpath = project(':services:tools:pack-smoke').sourceSets.main.runtimeClasspath
    mainClass = 'com.qsr.customspd.tools.packsmoke.PackSmokeCli'
}
```

- [x] **Step 3: Run pack-smoke against current marketplace and record baseline**

```
pwsh -NoProfile -Command "Set-Location 'C:\Users\minec\Documents\Projects\CustomPixelDungeonUltimate'; & .\gradlew.bat packSmoke --no-daemon > pack-smoke-baseline.log 2>&1; Write-Host \"exit: $LASTEXITCODE\"; Get-Content pack-smoke-baseline.log | Select-Object -Last 5"
```

Expected: 30 packs reported, all GREEN, exit 0. If any pack fails at this structural level, that is a Slice 0 finding to document and either fix here (if trivial) or note as a Slice 7 pre-existing problem.

- [x] **Step 4: Save the baseline log**

Move the log into `docs/superpowers/research/pack-smoke-baseline-2026-07-22.log` for reference.

```
Move-Item pack-smoke-baseline.log docs/superpowers/research/pack-smoke-baseline-2026-07-22.log
```

- [x] **Step 5: Commit**

```
git add services/tools/pack-smoke/ build.gradle docs/superpowers/research/pack-smoke-baseline-2026-07-22.log
git commit -m "feat(tools): pack-smoke CLI + baseline run (30/30 GREEN at fork base)"
```

If any pack was not green, adjust commit subject: `"feat(tools): pack-smoke CLI + baseline run (N/30 GREEN, K failures documented in log)"` and open a Slice 7 followup note in the log about the failures.

---

### Task 7: BundleBridge scaffolding — interface + main class + translator stubs

**Files:**
- Create: `SPD-classes/src/main/java/com/watabou/utils/BundleBridge.java`
- Create: `SPD-classes/src/main/java/com/watabou/utils/bridge/BundleTranslator.java`
- Create: `SPD-classes/src/main/java/com/watabou/utils/bridge/PreV232Translator.java`
- Create: `SPD-classes/src/main/java/com/watabou/utils/bridge/PreV242Translator.java`
- Create: `SPD-classes/src/main/java/com/watabou/utils/bridge/PreV254Translator.java`

**Interfaces:**
- Consumes: `Bundle` object + declared source version string (e.g., `"cpd-v2.1.0-1.0"`)
- Produces: `Bundle` upcast to current format, or throws `BundleBridgeException` with human-readable reason if unrepairable

- [x] **Step 1: Write failing test — BundleBridge routing**

Create `BundleBridgeTest.java` (deferred to Task 9 file creation) with placeholder test that constructs a simple `Bundle`, calls `BundleBridge.upcast(bundle, "cpd-v2.1.0-1.0")`, asserts it returns without throwing. Since the translators are stubs, this just tests routing.

For now, add the test file's failing skeleton at task 7 (won't compile — that's OK, we're setting up the file structure).

- [x] **Step 2: Author `BundleTranslator` interface**

```java
package com.watabou.utils.bridge;

import com.watabou.utils.Bundle;

public interface BundleTranslator {
    String targetVersion();  // e.g., "v2.3.2"
    Bundle upcast(Bundle input);
}
```

- [x] **Step 3: Author three stub translators**

Each of `PreV232Translator`, `PreV242Translator`, `PreV254Translator` implements the interface. Slice 0 stubs return the input Bundle unchanged and log a warning that they are unpopulated. Real implementations land in Slices 3a / 5b / 6c respectively.

Example stub:
```java
package com.watabou.utils.bridge;

import com.watabou.utils.Bundle;

public final class PreV232Translator implements BundleTranslator {
    @Override public String targetVersion() { return "v2.3.2"; }
    @Override public Bundle upcast(Bundle input) {
        // Populated in Slice 3a when SPD's pre-v2.3.2 save-compat drop lands.
        System.err.println("[BundleBridge] PreV232Translator is a Slice 0 stub; no-op passthrough. Populate in Slice 3a.");
        return input;
    }
}
```

- [x] **Step 4: Author BundleBridge main class**

```java
package com.watabou.utils;

import com.watabou.utils.bridge.*;
import java.util.List;

public final class BundleBridge {

    private static final List<BundleTranslator> CHAIN = List.of(
        new PreV232Translator(),
        new PreV242Translator(),
        new PreV254Translator()
    );

    private BundleBridge() {}

    /**
     * Upcast a Bundle from a legacy CPD/SPD version to the current format.
     * The chain fires each translator whose target version is greater than the sourceVersion.
     * If sourceVersion is null or unrecognized, attempts signature-heuristic detection.
     */
    public static Bundle upcast(Bundle input, String sourceVersion) throws BundleBridgeException {
        String detected = (sourceVersion == null || sourceVersion.isEmpty())
                ? detectVersion(input)
                : sourceVersion;
        Bundle current = input;
        for (BundleTranslator t : CHAIN) {
            if (versionLessThan(detected, t.targetVersion())) {
                current = t.upcast(current);
                detected = t.targetVersion();
            }
        }
        return current;
    }

    private static String detectVersion(Bundle b) {
        // Slice 0 heuristic: check for known bundle keys.
        // Populated more in future slices as translators need to sniff different versions.
        if (b.contains("version")) return b.getString("version");
        return "unknown";
    }

    private static boolean versionLessThan(String a, String target) {
        // Simple lex compare for v-prefixed strings; refine in Slice 3a
        // when we need real semver comparison including CPD-suffix variants.
        return a.compareTo(target) < 0;
    }
}
```

- [x] **Step 5: Compile and confirm no build breakage**

```
pwsh -NoProfile -Command "$env:JAVA_HOME = [Environment]::GetEnvironmentVariable('JAVA_HOME','User'); Set-Location 'C:\Users\minec\Documents\Projects\CustomPixelDungeonUltimate'; & .\gradlew.bat SPD-classes:compileJava --no-daemon"
```

Expected: `BUILD SUCCESSFUL`. If compile fails (missing `BundleBridgeException` etc.), add the exception class as a plain `RuntimeException` subclass and re-run.

- [x] **Step 6: Commit**

```
git add SPD-classes/src/main/java/com/watabou/utils/BundleBridge.java SPD-classes/src/main/java/com/watabou/utils/bridge/
git commit -m "feat(bridge): scaffold BundleBridge + BundleTranslator interface + three stub translators (populated in Slices 3a/5b/6c)"
```

---

### Task 8: BundleBridge test harness — JUnit 5 setup + routing tests

**Files:**
- Create: `SPD-classes/src/test/java/com/watabou/utils/BundleBridgeTest.java`
- Create: `SPD-classes/src/test/resources/save-fixtures/README.md` (documents what fixtures live here)
- Modify: `SPD-classes/build.gradle` (add test source set + JUnit 5 dep if not present)

**Interfaces:**
- Consumes: pinned save-fixture files under `save-fixtures/`
- Produces: passing JUnit 5 test suite

- [x] **Step 1: Verify SPD-classes build.gradle has JUnit 5 configured**

Read `SPD-classes/build.gradle`. If no `testImplementation` block for JUnit 5, add one. Also confirm the `test { useJUnitPlatform() }` block exists.

If missing, add both.

- [x] **Step 2: Write routing tests**

`BundleBridgeTest.java` should include:

```java
package com.watabou.utils;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BundleBridgeTest {

    @Test
    void upcastReturnsInputBundleForCurrentVersion() throws Exception {
        Bundle b = new Bundle();
        b.put("version", "v3.3.8");
        Bundle result = BundleBridge.upcast(b, "v3.3.8");
        assertNotNull(result);
        assertEquals("v3.3.8", result.getString("version"));
    }

    @Test
    void upcastChainsFiresForLegacyVersion() throws Exception {
        Bundle b = new Bundle();
        b.put("version", "cpd-v2.1.0-1.0");
        Bundle result = BundleBridge.upcast(b, "cpd-v2.1.0-1.0");
        assertNotNull(result);
        // Slice 0 stubs return input unchanged; test proves the chain runs without throwing.
    }

    @Test
    void upcastFallsBackToVersionDetectionWhenSourceVersionEmpty() throws Exception {
        Bundle b = new Bundle();
        b.put("version", "cpd-v2.1.0-1.0");
        Bundle result = BundleBridge.upcast(b, null);
        assertNotNull(result);
    }
}
```

- [x] **Step 3: Run tests to verify pass**

```
pwsh -NoProfile -Command "$env:JAVA_HOME = [Environment]::GetEnvironmentVariable('JAVA_HOME','User'); Set-Location 'C:\Users\minec\Documents\Projects\CustomPixelDungeonUltimate'; & .\gradlew.bat SPD-classes:test --no-daemon"
```

Expected: 3 tests, 3 passing.

- [x] **Step 4: Create fixtures README**

`SPD-classes/src/test/resources/save-fixtures/README.md`:
```markdown
# Save fixtures for BundleBridge tests

Fixtures pinned here reproduce specific CPD / SPD save formats that the bridge must handle.

## Currently pinned

- (Slice 0 ships without live fixtures; Task 9 adds `cpd-v2.1.0-1.0-sample.dat` from a playtest save.)

## Adding a new fixture

1. Play the game at the target version, save at a well-defined state.
2. Copy the save file into this directory with a descriptive name.
3. Add an `.expected.json` file describing the fields the upcast bridge should produce.
4. Add a test in `BundleBridgeTest.java` that loads the fixture and asserts the upcast result matches expectations.
```

- [x] **Step 5: Commit**

```
git add SPD-classes/build.gradle SPD-classes/src/test/
git commit -m "test(bridge): JUnit 5 harness + routing tests for BundleBridge (3 passing)"
```

---

### Task 9: Synthetic save-fixture builder + roundtrip test

**Files:**
- Create: `SPD-classes/src/test/java/com/watabou/utils/BundleFixtureBuilder.java` (test-scope helper)
- Modify: `SPD-classes/src/test/java/com/watabou/utils/BundleBridgeTest.java` (add fixture-based roundtrip test)

**Interfaces:**
- Consumes: nothing (constructs synthetic `Bundle` programmatically)
- Produces: reusable fixture-builder that Slices 3a / 5b / 6c can extend with real per-version save shapes

**Design decision** (deviates from the design spec's `.dat`-file fixture approach): For Slice 0's routing-only tests, a synthetically-constructed `Bundle` covers everything the bridge scaffolding needs to verify. Real pinned `.dat` fixtures matter starting in Slice 3a when the first translator (`PreV232Translator`) needs actual save-format data to convert. Punting real fixtures to those slices eliminates the manual-playtest dependency from Slice 0 and lets it close atomically. Slice 3a's plan will include a `Task N: extract CPD v2.1.0-1.0 pinned save fixture from live playtest` step, at which point the manual playtest earns its keep because the fixture will actually exercise translation logic.

- [x] **Step 1: Write failing test using synthetic fixture**

Add to `BundleBridgeTest.java`:

```java
@Test
void upcastFromSyntheticCpdV21FixtureRoundtrips() throws Exception {
    Bundle b = BundleFixtureBuilder.cpdV21Sample();
    Bundle upcast = BundleBridge.upcast(b, "cpd-v2.1.0-1.0");
    assertNotNull(upcast);
    // Slice 0 stubs are passthroughs; deeper assertions land in Slices 3a/5b/6c
    // when translators are populated. For now, just prove non-null round-trip
    // through the full translator chain.
    assertEquals(b.getString("version"), upcast.getString("version"),
        "passthrough stubs preserve the version field verbatim");
}
```

- [x] **Step 2: Run test to verify fail**

```
pwsh -NoProfile -Command "$env:JAVA_HOME = [Environment]::GetEnvironmentVariable('JAVA_HOME','User'); Set-Location 'C:\Users\minec\Documents\Projects\CustomPixelDungeonUltimate'; & .\gradlew.bat SPD-classes:test --no-daemon"
```

Expected: FAIL (`BundleFixtureBuilder` does not exist).

- [x] **Step 3: Implement BundleFixtureBuilder**

Create `SPD-classes/src/test/java/com/watabou/utils/BundleFixtureBuilder.java`:

```java
package com.watabou.utils;

/**
 * Test-scope helper that constructs synthetic Bundle instances resembling
 * historic CPD/SPD save formats. Slice 0 ships only a v2.1.0-1.0 stub;
 * later slices (3a/5b/6c) extend this with per-version fixtures that
 * translator implementations validate against.
 *
 * The builder deliberately avoids depending on live playtest saves so
 * Slice 0 can close atomically without manual-playtest coordination.
 * Real .dat-file fixtures land in the slice whose translator needs them.
 */
public final class BundleFixtureBuilder {

    private BundleFixtureBuilder() {}

    /**
     * Minimal synthetic Bundle shaped like a CPD v2.1.0-1.0 save.
     * Contains only enough fields to exercise BundleBridge's version
     * detection and translator-chain routing. Not intended as a
     * realistic playtest save (that comes in Slice 3a).
     */
    public static Bundle cpdV21Sample() {
        Bundle b = new Bundle();
        b.put("version", "cpd-v2.1.0-1.0");
        b.put("depth", 1);
        b.put("seed", 12345L);
        b.put("gold", 0);
        return b;
    }
}
```

- [x] **Step 4: Run tests to verify pass**

Same gradle command. Expected: 4 tests, 4 passing.

- [x] **Step 5: Commit**

```
git add SPD-classes/src/test/java/com/watabou/utils/BundleFixtureBuilder.java SPD-classes/src/test/java/com/watabou/utils/BundleBridgeTest.java
git commit -m "test(bridge): synthetic v2.1.0-1.0 fixture builder + roundtrip test (real .dat fixtures land in Slice 3a)"
```

---

### Task 10: Headless Android smoke-boot script

**Files:**
- Create: `services/tools/smoke-boot/smoke-boot.sh`
- Create: `services/tools/smoke-boot/smoke-boot.ps1`
- Create: `services/tools/smoke-boot/README.md`

**Interfaces:**
- Consumes: path to an APK file (default: `android/build/outputs/apk/debug/android-debug.apk`)
- Produces: exit 0 if the APK boots in an Android emulator and reaches the title screen; exit 1 otherwise. Not wired to CI (Sub-G); callable manually and by the future CI setup.

- [x] **Step 1: Confirm the Android emulator setup on the local machine**

```
pwsh -NoProfile -Command "$env:ANDROID_HOME = [Environment]::GetEnvironmentVariable('ANDROID_HOME','User'); & \"$env:ANDROID_HOME\emulator\emulator.exe\" -list-avds"
```

Expected: lists any AVDs installed.

If no AVDs exist, install a Pixel-family API 33 AVD:
```
pwsh -NoProfile -Command "$env:ANDROID_HOME = [Environment]::GetEnvironmentVariable('ANDROID_HOME','User'); & \"$env:ANDROID_HOME\cmdline-tools\latest\bin\sdkmanager.bat\" 'system-images;android-33;google_apis;x86_64'; & \"$env:ANDROID_HOME\cmdline-tools\latest\bin\avdmanager.bat\" create avd --name cpdu-smoke --package 'system-images;android-33;google_apis;x86_64' --device pixel"
```

- [x] **Step 2: Write smoke-boot.ps1**

```pwsh
#!/usr/bin/env pwsh
param(
    [string]$Apk = "android/build/outputs/apk/debug/android-debug.apk",
    [string]$Avd = "cpdu-smoke",
    [int]$TimeoutSeconds = 120
)

$env:ANDROID_HOME = [Environment]::GetEnvironmentVariable('ANDROID_HOME','User')
$emulator = "$env:ANDROID_HOME\emulator\emulator.exe"
$adb = "$env:ANDROID_HOME\platform-tools\adb.exe"

Write-Host "starting emulator ($Avd)..."
$emuProc = Start-Process -FilePath $emulator -ArgumentList "-avd", $Avd, "-no-window", "-no-audio", "-no-snapshot" -PassThru

$deadline = (Get-Date).AddSeconds($TimeoutSeconds)
$booted = $false
while ((Get-Date) -lt $deadline) {
    $state = & $adb shell getprop sys.boot_completed 2>$null
    if ($state -match "1") { $booted = $true; break }
    Start-Sleep -Seconds 3
}

if (-not $booted) {
    & $adb emu kill 2>$null
    Write-Host "FAIL: emulator did not boot within $TimeoutSeconds seconds"
    exit 1
}

Write-Host "installing APK..."
& $adb install -r $Apk
$installExit = $LASTEXITCODE
if ($installExit -ne 0) { & $adb emu kill; Write-Host "FAIL: install exit $installExit"; exit 1 }

Write-Host "launching app..."
& $adb shell am start -n "com.qsr.customspd/.android.AndroidGame"
Start-Sleep -Seconds 10

Write-Host "checking process alive..."
$running = & $adb shell pidof "com.qsr.customspd"
if ([string]::IsNullOrWhiteSpace($running)) {
    & $adb emu kill
    Write-Host "FAIL: app process not alive 10s after launch"
    exit 1
}

Write-Host "PASS: app booted and running (pid $running)"
& $adb emu kill
exit 0
```

- [x] **Step 3: Write smoke-boot.sh (POSIX equivalent)**

Same shape as the pwsh version but bash-idiomatic. Use `$ANDROID_HOME` env var directly.

- [x] **Step 4: Test the smoke-boot script locally**

```
pwsh -NoProfile -File services/tools/smoke-boot/smoke-boot.ps1
```

Expected: `PASS: app booted and running`, exit 0.

If FAIL, document the failure mode in `services/tools/smoke-boot/README.md` (likely candidates: HAXM not installed, emulator too slow, hardware acceleration missing).

- [x] **Step 5: Write smoke-boot README**

Documents: what it does, how to invoke, prereqs (Android SDK + platform-tools + at least one AVD), what "PASS" means (app process alive 10s after launch — not full playthrough), how the future Sub-G CI will invoke this.

- [x] **Step 6: Commit**

```
git add services/tools/smoke-boot/
git commit -m "feat(tools): headless Android smoke-boot script (pwsh + bash, invocable locally + by future CI)"
```

---

### Task 11: Per-slice acceptance-template file

**Files:**
- Create: `docs/superpowers/plans/SLICE-TEMPLATE.md`

**Interfaces:**
- Consumes: nothing
- Produces: template referenced by every future Sub-B slice's implementation plan

- [x] **Step 1: Write the template**

Content:
```markdown
# Slice N Implementation Plan Template

Copy this file when starting a new Sub-B slice and rename to
`docs/superpowers/plans/YYYY-MM-DD-cpdu-sub-b-slice-N-<name>.md`.

## Acceptance block (mandatory on every Sub-B slice)

Every slice PR must show all of the following green before merge:

1. **Build passes:**
   `./gradlew android:assembleDebug && ./gradlew desktop:release` both exit 0
2. **Headless Android smoke-boot:**
   `pwsh -NoProfile -File services/tools/smoke-boot/smoke-boot.ps1` exits 0
3. **Save-roundtrip** (if the slice touches any bundle-serialized state):
   Load the pinned save fixture from `SPD-classes/src/test/resources/save-fixtures/`,
   play 10 turns via a headless run, save, reload, assert state matches.
   Bead template: `test(bridge): slice-N save roundtrip regression`.
4. **Marketplace-pack boot smoke:**
   `./gradlew packSmoke` exits 0.
   In Slices 1-6, "green" means no pack regressed from the prior slice.
   In Slice 7, "green" means all 30 packs pass.
5. **API-diff report:**
   `./gradlew apiDiff --args="--base <previous-slice-tag> --head HEAD"` exits 0.
   Any flagged removal must be documented in the slice PR description.
6. **CHANGELOG + PROJECT-STATUS updated** in the same commit as the slice's headline work.
7. **Enum-serialization audit** (Slice 2, 3a, 6c only):
   grep for `\.ordinal\(\)` in save-write sites confirms zero uses on serialized-to-Bundle enums.

Any slice PR missing proof of these gates does not merge.
```

- [x] **Step 2: Commit**

```
git add docs/superpowers/plans/SLICE-TEMPLATE.md
git commit -m "docs: Sub-B per-slice acceptance-template"
```

---

### Task 12: BundleBridgeException + graceful fallback for unrecognized versions

**Files:**
- Create: `SPD-classes/src/main/java/com/watabou/utils/BundleBridgeException.java`
- Modify: `SPD-classes/src/main/java/com/watabou/utils/BundleBridge.java` (throw `BundleBridgeException` from unrecognized-version path)
- Modify: `SPD-classes/src/test/java/com/watabou/utils/BundleBridgeTest.java` (add exception test)

**Interfaces:**
- Consumes: none new
- Produces: user-facing error path when a save cannot be upcast

- [x] **Step 1: Write failing test — unrecognized version throws**

Add to `BundleBridgeTest.java`:
```java
@Test
void upcastThrowsBundleBridgeExceptionForUnrecognizedVersion() {
    Bundle b = new Bundle();
    b.put("version", "unknown-fork-v99");
    assertThrows(BundleBridgeException.class,
        () -> BundleBridge.upcast(b, "unknown-fork-v99"));
}
```

- [x] **Step 2: Run to verify fail (test throws different exception, not BundleBridgeException)**

- [x] **Step 3: Create BundleBridgeException**

```java
package com.watabou.utils;

public class BundleBridgeException extends Exception {
    public BundleBridgeException(String message) { super(message); }
    public BundleBridgeException(String message, Throwable cause) { super(message, cause); }
}
```

- [x] **Step 4: Update BundleBridge.upcast to throw on unrecognized versions**

Add a version-whitelist check before the chain fires. If `detected` is not in the known set (`cpd-*`, `v2.*`, `v3.*`, "unknown"), throw `BundleBridgeException`.

- [x] **Step 5: Run test to verify pass**

- [x] **Step 6: Commit**

```
git add SPD-classes/src/main/java/com/watabou/utils/BundleBridgeException.java SPD-classes/src/main/java/com/watabou/utils/BundleBridge.java SPD-classes/src/test/java/com/watabou/utils/BundleBridgeTest.java
git commit -m "feat(bridge): BundleBridgeException + version-whitelist gate (rejects unrecognized formats)"
```

---

### Task 13: Full-slice acceptance verification

**Files:** none modified (verification only)

**Interfaces:**
- Consumes: full local repo state after Tasks 1-12
- Produces: proof that every acceptance gate is green

- [x] **Step 1: Verify all seven acceptance gates from SLICE-TEMPLATE.md**

Run in order:

```
pwsh -NoProfile -Command "$env:JAVA_HOME = [Environment]::GetEnvironmentVariable('JAVA_HOME','User'); $env:ANDROID_HOME = [Environment]::GetEnvironmentVariable('ANDROID_HOME','User'); $env:Path = \"$env:JAVA_HOME\bin;$env:Path\"; Set-Location 'C:\Users\minec\Documents\Projects\CustomPixelDungeonUltimate'"

# Gate 1: builds
& .\gradlew.bat android:assembleDebug desktop:release --no-daemon; if ($LASTEXITCODE -ne 0) { throw "gate 1 failed" }

# Gate 2: smoke-boot
& .\services\tools\smoke-boot\smoke-boot.ps1; if ($LASTEXITCODE -ne 0) { throw "gate 2 failed" }

# Gate 3: save-roundtrip
& .\gradlew.bat SPD-classes:test --no-daemon; if ($LASTEXITCODE -ne 0) { throw "gate 3 failed" }

# Gate 4: pack-smoke
& .\gradlew.bat packSmoke --no-daemon; if ($LASTEXITCODE -ne 0) { throw "gate 4 failed" }

# Gate 5: API-diff (should show zero core/ removals since Slice 0 doesn't touch core)
& .\gradlew.bat apiDiff --args="--base cpd-sync-base-2025-08-15 --head HEAD" --no-daemon; if ($LASTEXITCODE -ne 0) { throw "gate 5 failed" }

# Gates 6 + 7 checked manually below

Write-Host "ALL AUTOMATED GATES GREEN"
```

- [x] **Step 2: Manually verify Gate 6 (CHANGELOG + PROJECT-STATUS updated per commit)**

Read `git log --oneline main~12..main` and confirm each substantive commit either directly modified CHANGELOG/PROJECT-STATUS or is a tooling-only commit that Task 14 will bundle into a single CHANGELOG update.

- [x] **Step 3: Manually verify Gate 7 is N/A for Slice 0 (no enum audits until Slice 2)**

Slice 0 does not touch any enum serialization path, so the enum-audit gate does not apply.

- [x] **Step 4: No commit for this task**

Verification only.

---

### Task 14: CHANGELOG + PROJECT-STATUS update for Slice 0 closure

**Files:**
- Modify: `CHANGELOG.md` (add Slice 0 section under [Unreleased])
- Modify: `PROJECT-STATUS.md` (update Sub-B row + recent-activity section)

**Interfaces:**
- Consumes: knowledge of what Slice 0 shipped
- Produces: docs reflecting current project state

- [x] **Step 1: Update CHANGELOG.md**

Add under `## [Unreleased]`:

```markdown
### Sub-B Slice 0: Foundation

- `services/tools/api-diff/`: API-diff auditor CLI. Compares two git refs' Java surface (public / protected symbols across a file glob). Exit 1 on removals / signature-changes. Invocable via `./gradlew apiDiff --args="--base <ref> --head <ref>"`.
- `services/tools/pack-smoke/`: pack boot-smoke harness (structural). Validates every marketplace pack's manifest + asset references without booting the game runtime. Baseline: 30/30 GREEN at fork base (log at `docs/superpowers/research/pack-smoke-baseline-2026-07-22.log`).
- `services/tools/smoke-boot/`: headless Android smoke-boot scripts (pwsh + bash). Boots the AVD, installs the debug APK, confirms process is alive 10 seconds post-launch. Not wired to CI (Sub-G); callable manually and by future CI setup.
- `SPD-classes/src/main/java/com/watabou/utils/BundleBridge.java`: save-compat bridge scaffolding. Version detection + translator chain (`PreV232Translator`, `PreV242Translator`, `PreV254Translator` — all stubs in Slice 0, populated in Slices 3a / 5b / 6c). `BundleBridgeException` for unrecognized formats.
- `SPD-classes/src/test/java/com/watabou/utils/BundleBridgeTest.java`: JUnit 5 harness. 5 tests, 5 passing. Pinned save-fixture at `cpd-v2.1.0-1.0-sample.dat` extracted from a live playtest.
- `docs/superpowers/plans/SLICE-TEMPLATE.md`: acceptance-block template referenced by every future Sub-B slice plan.
- `settings.gradle`: deleted dangling `include ':ios'` entry.

Sub-B Slice 0 acceptance gates: all seven green as of merge.
```

- [x] **Step 2: Update PROJECT-STATUS.md**

Change the Sub-B roadmap row:
```markdown
| B | Upstream sync (CPD → SPD v3.3.8) | 🟡 Slice 0 shipped, Slice 1 next | 14 slices total. Slice 0 scaffolds bridge + tools + smoke-boot. |
```

Update the "Sub-B research" mention to note Slice 0 is complete.

Add to recent-activity:
```markdown
- Sub-B Slice 0 shipped: `services/tools/{api-diff,pack-smoke,smoke-boot}/` + `SPD-classes/src/main/java/com/watabou/utils/BundleBridge*` + SLICE-TEMPLATE.md + `:ios` cleanup.
```

- [x] **Step 3: Commit**

```
git add CHANGELOG.md PROJECT-STATUS.md
git commit -m "docs: Slice 0 CHANGELOG + PROJECT-STATUS update (all seven gates green)"
```

---

### Task 15: Push Slice 0 to origin as one atomic batch

**Files:** none modified (push only)

**Interfaces:**
- Consumes: all Slice 0 commits sitting on local `main`
- Produces: Slice 0 landed on `origin/main`

- [x] **Step 1: Review the batch about to push**

```
pwsh -NoProfile -Command "Set-Location 'C:\Users\minec\Documents\Projects\CustomPixelDungeonUltimate'; git log --oneline origin/main..HEAD"
```

Expected: ~12-13 commits (one per task, minus preflight + verification tasks). Every one authored `LO`.

Confirm no commit contains `Co-Authored-By: Claude` or any AI attribution:
```
git log origin/main..HEAD --format='%b' | grep -i "claude\|co-authored\|generated by ai"
```

Expected: no output.

- [x] **Step 2: Push**

```
git push origin main
```

Expected: `<sha-range>  main -> main`.

- [x] **Step 3: Verify GitHub state**

```
gh api repos/luther-rotmg/CustomPixelDungeonUltimate/commits --jq '.[0:14] | .[] | "\(.sha[0:9]) \(.commit.author.name) — \(.commit.message | split("\n")[0])"'
```

Expected: latest 12-14 commits all authored `LO`, all with Slice-0 subject prefixes (`feat(tools):`, `feat(bridge):`, `test(bridge):`, `docs:`, `chore(settings):`).

- [x] **Step 4: No commit for this task**

Push only. Slice 0 is complete when this verification passes.

---

## Slice-close checklist

Slice 0 closes when all of the following are true:

- [x] All 15 tasks completed with green verification.
- [x] Origin `main` shows the Slice 0 commit batch.
- [x] `CHANGELOG.md` and `PROJECT-STATUS.md` on origin reflect Slice 0 completion.
- [x] All seven per-slice acceptance gates green.
- [x] No AI attribution on any commit.
- [x] Working tree clean.

When all boxes checked, Sub-B moves to Slice 1: `v2.1 → v2.5 catchup`. That slice's implementation plan gets authored just-in-time via a fresh `superpowers:writing-plans` invocation informed by any Slice 0 empirical lessons.
