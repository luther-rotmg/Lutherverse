# Lutherverse — Build-Craft Spine & Vision Decomposition

*Design captured 2026-08-13. Decomposes the "Lutherverse ultimate vision" into an
ordered map, then designs the build-craft ARPG spine (the chosen primary) and records
what the walking-skeleton prototype on `worktree-game-dev` has proven. Written after the
prototype so it reflects what actually worked, not a pre-prototype guess.*

---

## 1. Vision decomposition — the ordered map

The north-star ("sphere grid, keyblades + Keybearer, turn-based toggle, towns/quests/
narrative, 200-floor generator + biomes, cosmic-horror pack, cameo framework, turn-based
coop, leaderboards + labeled seeds, story/cutscene engine") is ~10 independent subsystems.
Sequenced by hard dependency, they fall into six layers:

**Layer F — Foundations** (pure prerequisites)
- **F1 Headless test bootstrap** (`gdx-backend-headless`) — lets us test real gameplay at
  all. Confirmed to be a genuine multi-step spike: game-runtime code reads assets through
  `ModManager` + `Gdx.files` (asset-root config) and pulls in `SPDSettings`/`Dungeon`
  static state. Unblocks every runtime test below.
- **F2 Save-roundtrip harness** — safety net before mutating serialized state. Needs F1.
- **F3 Run-determinism finish** (ordered `Level.mobs`) — needs F1+F2. Unblocks seeds,
  leaderboards, coop.
- **F4 Region/depth scaling** (`Dungeon.region()` past floor 25) — needs F1. Unblocks the
  200-floor world. (The `depth/5` *crashes* are already clamped; this is the gameplay-
  scaling generalization, an open product/API call.)

**Layer S — Build-craft spine** (the primary; see §2)
- **S1 Dual-wield combat engine** · **S2 Keyblade weapon family** · **S3 Hybrid sphere
  grid** · **S4 Keybearer class**.

**Layer C — Combat model:** C1 turn-based toggle (needs S1).

**Layer W — World/canvas:** W1 200-floor generator + biomes (needs F4) · W2 cosmic-horror
pack (needs W1) · W3 towns/save-zones/quests/narrative state (needs W1+F2).

**Layer N — Narrative:** N1 story spine + cutscene/dialogue engine (needs W3).

**Layer P — Platform/community** (last, matches the Sub-C+ roadmap): P1 modding API (needs
stable S+W) · P2 cameo framework (needs P1+S4) · P3 labeled seeds + leaderboards (needs F3)
· P4 turn-based coop (needs F3+C1).

**Critical path to a playable build-craft demo:** F1 → F2 → S1 → S3 → S4, with S2 alongside.
Everything in W/N/P hangs off that trunk.

## 2. The build-craft spine (chosen primary)

**Pillar:** progression + build variety. Hook: *"every run I craft a different powerful
build."* Combat/items/progression lead; world & platform hang off.

**Sphere grid — hybrid, Keybearer-only to start.** Two layers: a persistent "Insight"
unlock backbone (what nodes are *available*, earned across runs — deferred past the
prototype) plus per-run allocation (how you spec *this* descent, points from level-ups).
Keybearer-only so it doesn't disturb existing classes; generalizes later.

**Keyblade build axis — element + ability (hybrid).** Each keyblade carries an element
(its strikes deal/apply it) and a signature ability; the grid lets you lean either way.
Dual-wielding two elements becomes a build choice.

**Grid topology — a branching web:** a central start radiating interconnected clusters
(Element, Ability, Core), joined at junctions so hybrid builds are reachable, with
build-defining keystones. Node effects flow through one small API combat reads, keeping the
grid a clean, testable engine.

**Dual-wield** reuses the Champion's existing `secondWep` slot + serialization + equip flow
(hard-gated to `HeroSubClass.CHAMPION` today); real work is widening those gates and adding
simultaneous-strike logic, not building a slot.

**First slice = the full spine** (F1, F2, S1, S2, S3, S4) — the most faithful bite, built
as a walking skeleton first (below) then filled out.

## 3. What the prototype proved (`worktree-game-dev`)

A walking skeleton of the spine, run-scoped grid only, built + committed + compiling:

- **Keybearer class** registers and boots into a run (mirrors DUELIST; `HeroClass` enum +
  `initHero` + `keybearer.json` kit; placeholder Warrior sprite). Confirmed: the built jar
  reaches a windowed title screen with no startup crash.
- **Fire keyblade** — a `MeleeWeapon` whose damaging strikes reignite the target.
- **Hybrid sphere grid, run-scoped layer** — `SphereNode` (Ember/Might/Vigor clusters, tiny
  branching web via string prerequisites) + `SphereGrid` (points, activations, aggregate
  levels). Attached to `Hero`, serialized run-scoped in the hero bundle, 1 point per level.
- **Combat reads the grid:** Might → +melee damage; Ember → +fire damage on the burn;
  Vigor → +max HP. Three distinct build directions exist today.
- **Grid screen** (`WndSphereGrid`) reachable from the Keybearer's hero-panel talent tab,
  guarded so the construction-time `select(lastIdx)` never stacks a window early.
- **Tested:** 7 unit tests on the grid logic incl. the `Bundle.write/read` save roundtrip,
  all green. The grid mechanics + persistence are proven; GUI render and in-combat feel are
  not (no display / no F1 bootstrap yet).

## 4. Deferred / next

- **Persistent Insight layer** (the other half of the hybrid grid) · **dual-wield** ·
  **signature-ability axis** · **real art** (Keybearer sprite, keyblade icon) · the
  **branching-web visual** grid UI · **balance**.
- **F1 headless bootstrap** is the gating foundation for self-testing the runtime
  (init/combat/window) and for all of F2–F4; a dedicated spike, not a quick task.

## 5. Two open product decisions (LO's call)

- **Asset strategy** (`cpdu-15l` + sprite sheets) — unblocks the parked Sub-B batches; and
  the deeper question of whether to finish upstream-sync to a stable checkpoint before
  diverging hard into custom content (divergence makes remaining upstream merges harder).
- **`Dungeon.region()` 200-floor gameplay scaling** (Layer F4).

## 6. Progress since writing (2026-08-13, later same day)

Built out on `worktree-game-dev` after the initial walking skeleton (append-only per the
historical-record convention):

- **F1 — headless test bootstrap: DONE.** `HeadlessGdx` boots a libGDX HeadlessApplication once
  per JVM (Gdx.files + temp storage + `Badges.loadGlobal()` + seeded item-appearance handlers),
  so tests run real game code with no display. Contrary to §1's "multi-step spike" caution it
  landed cleanly; the only static state runtime paths needed was `Dungeon.hero`. **This unblocks
  F2–F4.**
- **F2 — save-roundtrip harness: DONE (first bricks).** `SaveRoundtrip.of/writeRead` round-trips
  state through the real `Bundle.write/read` path — the "no save-roundtrip harness exists" gap.
- **F3 — run-determinism: already handled.** Investigated `Level.mobs`; it is a `LinkedHashSet`
  at both construction sites and the store/restore preserves order, so the §1 note is stale.
  Left untouched (no redundant core change).
- **Vigor wired** to bonus max HP — all three original grid branches now have real effects.
- **Second element added — frost keyblade + Frost grid branch.** The element axis is now real:
  builds diverge across fire (burn-DoT) vs frost (chill/control) vs Might vs Vigor, and the
  Keybearer carries both keyblades to swap between.
- **Runtime coverage:** 35 core tests green, including headless integration tests that verify the
  class boots (`initHero`), all four grid effects in real combat (Might/Ember/Frost/Vigor), and a
  full `Hero` save roundtrip. Only `WndSphereGrid` **rendering** remains untestable headless
  (needs a GL context / a playthrough).

Still deferred: persistent Insight layer · dual-wield (invasive combat-core, wants supervision) ·
signature-ability axis · real art · the branching-web grid visual · balance.
