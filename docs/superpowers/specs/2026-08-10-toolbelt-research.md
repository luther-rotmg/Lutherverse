# Lutherverse toolbelt research — 2026-08-10

Wide-scope research into tooling that would raise quality and throughput on this project.
12 agents, 10 domains plus two adversarial passes, 94 candidates, ~29 minutes, 1.2M tokens.

**Read this first:** every claim below marked **VERIFIED** was checked against this repo by
hand after the research returned. Claims marked *reported* came from an agent and have not
been independently confirmed. Two agent claims were **wrong or overstated and are corrected
in place** — do not treat the raw agent output as ground truth.

---

## Two defects found while researching

### 1. `List.of` in an Android-shipped module — latent crash, armed for the save path

**VERIFIED.** [BundleBridge.java:43](../../../SPD-classes/src/main/java/com/watabou/utils/BundleBridge.java)
does `private static final List<BundleTranslator> CHAIN = List.of(...)`.
`java.util.List.of` requires **Android API 34**. `android/build.gradle` has no
`coreLibraryDesugaring` and no `desugar_jdk_libs` dependency, and minSdk is 19 / targetSdk 33.
Loading this class on any device below API 34 throws `NoSuchMethodError`.

**Correction to the agent's claim.** It reported this as crashing "every device you claim to
support." It does not, *today*: nothing in game code calls `BundleBridge`, so the class is
never loaded. It is **latent, not live**. But Slices 3a/5b/6c wire it into the save-load path,
so it is a landmine armed for exactly the moment saves start migrating. It is also the only
such site — a repo-wide sweep for `List.of` / `Set.of` / `Map.of` / `isBlank` / `strip` /
`repeat` / `Optional.or` across all four game modules found nothing else.

Fix: `Collections.unmodifiableList(Arrays.asList(...))`. Two lines.

### 2. Same-seed nondeterminism in `WandOfCorruption`

**VERIFIED, and narrower than reported.** The agent claimed `Random.chances` HashMap ordering
is broadly unsafe. That is too strong:

- [Random.java:197](../../../SPD-classes/src/main/java/com/watabou/utils/Random.java) —
  `chances(HashMap<K,Float>)` does `chances.keySet().toArray()`, so result selection follows
  **map iteration order**.
- [Generator.java:514-515](../../../core/src/main/java/com/qsr/customspd/items/Generator.java) —
  declared `HashMap` but **instantiated `LinkedHashMap`**. Insertion-ordered, deterministic.
  Upstream was careful here.
- [WandOfCorruption.java:198](../../../core/src/main/java/com/qsr/customspd/items/wands/WandOfCorruption.java) —
  `HashMap<Class<? extends Buff>, Float> debuffs = new HashMap<>(category)`. A **real HashMap
  keyed by `Class` objects**. `Class.hashCode()` is identity-based and varies per JVM run, so
  iteration order varies run to run.

Consequence: same seed, same actions, **different corruption debuff on different runs**. This
is not a levelgen divergence, but it breaks three things on the roadmap — labeled seed sharing,
deterministic lockstep coop, and any replay verifier. The right response is a *targeted* audit
of unordered-collection use in RNG paths, not a blanket ban.

---

## The toolbelt, in adoption order

Four groups. They are ordered by leverage-per-hour, and group C is the one nobody expected.

### Group A — make the gate you already run honest (hours, near-zero risk)

Nothing here adds a dependency to the shipped game.

| Item | What it buys | Cost |
|---|---|---|
| **`options.release = 8`** on `:core`, `:SPD-classes`, `:desktop`, `:services*` | Turns "compiles" into "compiles against the Java 8 API", closing the entire NoSuchMethodError class above. `-source/-target 8` constrains *language* level only; without `--release` javac 17 happily resolves Java 17 APIs into Java 8 bytecode. | 1–2h |
| **`.gitattributes`** | **VERIFIED absent**, with `core.autocrlf=true` and files stored LF / checked out CRLF. Every commit today printed "LF will be replaced by CRLF". This corrupts diffs, inflates merge conflicts on the 541-commit backlog, and risks mangling the 10k binary assets. Precondition for group C. | 1h |
| **Android Lint** (already in AGP 7.4.2) | `./gradlew :android:lint` — never run. NewApi alone guards compileSdk 33 against minSdk 19, which nothing does today. Covers Kotlin via UAST, so it reaches the 49 `.kt` files that `--release` cannot. | 5 min to first report; 4–8h to a ratcheted gate |
| **Canary fixtures for every gate** | The root-cause fix for lying gates, and *nobody in ten domains proposed it*. Every gate gets a deliberately-broken input it must fail on. This is the generalization of the negative control I added to `BundleAliasRoundtripTest`, and of the `Files scanned: 0` check in deletion-audit. | 2–4h |

Do **not** set `options.release` on `:android` — it conflicts with AGP's `android.jar`
bootclasspath. Lint's NewApi is the guard there.

Note the recurring shape: `--release`, lint baselines, and CI green checks are all gates that
*can* lie. Each needs an assertion that it actually analyzed something.

### Group B — give `core` a test suite (days, one spike first)

`core` has 1019 Java files and no test source set. This is the largest quality gap and the
correct sequence is strict:

1. **Add a test source set to `:core`** — 4 lines copied from `SPD-classes/build.gradle`. Blocks everything else here.
2. **ArchUnit 1.5.0** — bytecode rules over all 1019 classes with **no Gdx bootstrap needed**, so it delivers value before the hard part. Enforce: every `Bundlable` has a no-arg constructor (R8/reflection safety), RNG purity in generation paths, module layering. *Unverified:* archunit-junit5 1.5.0's minimum JUnit Platform vs the 5.10.0 currently pinned — check before writing a bead; it may force a JUnit bump first.
3. **`gdx-backend-headless` 1.11.0 + a ~50-line JUnit 5 bootstrap** — run game logic in tests without a display. **Do this as a timeboxed spike yourself, not as a bead.** The kill-check revised this from 4–8h to **1–3 days** because the depth of libGDX's static coupling (`Gdx.app`/`files`/`graphics`, `Messages.setup()`'s External branch, `Dungeon`/`Generator`/`Badges` statics) is unmeasured. `Gdx.gl` is never set — no GL-touching code can be tested this way, confirmed hard ceiling.
4. **Seeded levelgen invariants** — same seed twice → identical map; flood-fill reachability entrance→exit; key/door solvability. Hard-gated on step 3. Reset `Dungeon` statics in `@BeforeEach` and do **not** enable parallel execution.

Every one of these needs a negative control, or it becomes another gate that passes vacuously.

### Group C — industrialize the upstream sync (the finding I did not expect)

**Ten domain researchers proposed zero tooling for the 541-commit backlog** — the single
largest recurring cost in the project. The gap hunter filled it entirely with git-level tooling:

| Item | What it buys |
|---|---|
| **Mergiraf** | Syntax-aware merge driver (tree-sitter) for `.java`/`.kt`/`.json`/`.xml`/`.properties` — exactly this repo's file mix. Auto-resolves the conflict classes that dominate the backlog: two sides adding different methods to one class, reordered imports, independent `.properties` keys. External Rust binary, so no toolchain interaction at all. Windows binaries published. |
| **`git rerere`** | **VERIFIED off.** With batch porting you are re-resolving the same namespace-rename conflicts by hand, repeatedly. |
| **`git range-diff`** | Port-verification gate: compares your ported commit against the upstream original and catches silently dropped hunks. Your own history is the proof — `874e49851` "restore CorpseDust.actions() override dropped during Slice 1". |
| **`git patch-id` / `git cherry`** | Mechanizes the hand-maintained TSV manifest. That manifest is a gate that can lie *in exactly the way api-diff lied*, and it already did once (730 unreviewed `provisional:` rows). |
| **difftastic** | Structural diff for review — much of the 541 is reformatting and import churn wrapped around a few real lines. |

`git-imerge` and **Copybara** are the heavier options if the above is not enough. Copybara is
purpose-built for continuously importing upstream into a fork with systematic divergence,
which is precisely this situation — worth knowing it exists before Slices 2–7.

### Group D — CI (makes any of this run off your one Windows box)

Baseline workflow: `actions/checkout` + `actions/setup-java` (temurin 17) +
`gradle/actions/setup-gradle` + `android-actions/setup-android`. 4–8h to first green.

Two things the kill-check insists on, and they are the whole point:

- `:core:test` is `NO-SOURCE` and **will report green forever**. Assert `core/build/test-results/test/*.xml` exists with `tests > 0`.
- Assert api-diff / pack-smoke / deletion-audit print a **non-zero scanned count**.

*Reported:* `reactivecircus/android-emulator-runner` on ubuntu-latest with KVM may boot an
emulator where your local one cannot — which would resurrect the Android runtime smoke that
has never once executed the APK. Worth one experiment.

Also flagged: the root `allprojects` block pulls `https://oss.sonatype.org/content/repositories/snapshots`,
a mutable, supply-chain-relevant repo. It should get a `content{}` filter or be dropped.

---

## Deferred but decision-shaped

- **Android platform floor.** minSdk 19 → 21 (kills the multidex workaround; costs a fraction of a percent of devices). *Reported:* a Google Play targetSdk deadline of **2026-08-31** — three weeks out. **Verify this yourself before acting**; it only binds if you ship to Play, and I did not confirm it.
- **R8 full mode.** `android.enableR8.fullMode=false` is a standing "this crashes the game" workaround. Root cause is almost certainly missing keep rules for `Bundlable` constructors — the same reflection-safety invariant ArchUnit would enforce. Fix the rules; do not flip the flag yet.
- **`blade-ink`** — inkle's ink runtime in pure Java, for cutscenes/dialogue/story flags. This is a serious candidate for the narrative spine and it should inform the Sub-C API design (T4), not be bolted on after.
- **Mod content identity** — stable namespaced string IDs plus a Bundle alias registry, before the modding API freezes. Retrofitting identity after third parties ship mods is not possible.
- **GPLv3 × Steamworks.** A distribution trap nobody flagged: linking the proprietary Steamworks SDK into a GPLv3 binary is not straightforwardly permissible. `icculus/steamshim` (separate process, IPC) is the known escape hatch. Cheap to know now, expensive to discover after building achievements.
- **Aseprite CLI**, **Weblate**, **REUSE**, **CycloneDX SBOM**, **Dokka**, **PIT mutation testing**, **Jazzer fuzzing** for the save/mod parsers, **SDL_GameControllerDB** for controller mappings.

## Explicitly rejected (so nobody proposes them later)

**EvoSuite** — the obvious answer to 1019 untested files; generates assertions that pin current
behavior including current bugs, producing unreadable tests nobody maintains.
**Git LFS** for the 10,065 binary assets — looks like a textbook case, isn't for this repo.
**Gradle configuration cache** — incompatible with the current build's shape.
**LuaJava** for mod scripting. **Self-hosted Nakama** and **GlitchTip** — operational burden a
solo dev should not take. **SonarQube MCP** / **Sentry MCP** / **ccusage**.

---

## What this changes about the plan

T1 shipped three gates. This research says the *next* highest-leverage work is not more gates —
it is (a) making the compile gate honest for free via `--release 8` and lint, and (b) attacking
the 541-commit backlog with git tooling, which no one had proposed and which is the largest
recurring cost in the project.

Group A is hours and should land before T2 resumes, because it changes what "green" means.
Group C should land before the Sub-B grind resumes, because it changes the per-commit cost of
the thing that dominates the schedule.
