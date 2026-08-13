# content-audit — Design

*A completeness gate for Lutherverse core content. The first deliverable of the
content-pipeline foundation (Sub-C). Written 2026-08-12.*

---

## 1. Context and why this is first

Lutherverse's vision — 200 floors, new content the whole way up, a story with cutscenes
and dialogue, and eventually a platform for mod creators — has been blocked on a
"vision-decomposition pass" that `project_progress.md` records as never having happened.
This spec is the first concrete output of that pass.

**The decomposition.** The vision splits into four largely independent pillars:

1. **200-floor generation** — generalize depth/region/layout past vanilla's 25 floors.
2. **Content pipeline** — author new mobs/items/bosses/biomes at scale.
3. **Story/narrative engine** — cutscenes, dialogue, story beats (does not exist yet).
4. **Mod-creator platform** — SDK, validation, docs, examples around the pack system.

**The chosen entry point (LO, 2026-08-12): the content pipeline, team-code-first.** A
JSON pack-modding system already exists (29 files under `core/…/modding/`), but it is
configuration-only: `CustomMobScheme` defines a mob as stats + sprite + loot + a fixed
vocabulary of `properties`/`enchantments`, reusing the engine's built-in AI. It cannot
express new mechanics. Creator-facing no-code authoring of new mechanics is already
planned as **DSL v2 in Slice 7** (spells, vault rooms, investigating AI, thrown-weapon
sets). The 200-floor content, however, will be overwhelmingly **team-authored in
Java/Kotlin**, so the highest-leverage foundation now is a fast, consistent, testable
**team authoring workflow** — not a widened DSL.

**Within that, the first piece: the "make it correct" half — a completeness validator.**
Adding one mob today touches six-plus disconnected places (the `Mob` class, a sprite
class, a `GeneralAsset` entry + PNG, localization in `actors.properties`, spawn tables in
`Bestiary.kt`, often `Catalog`/`Generator`). Miss one and you get exactly the bug classes
this repo keeps hitting: orphan sprites, missing localization, content that never spawns.
There is no authoring-time completeness check. `content-audit` is that check. Its
touchpoint checklist doubles as the authoring guide, turning "complete content" from
tribal knowledge into an executable spec. The scaffold/generator (the "make it fast" half)
builds on this next; it is out of scope here.

## 2. What content-audit is

A new gate, `services/tools/content-audit/` (Java 17, alongside `asset-audit`,
`deletion-audit`, `manifest-audit`). It answers one question: **is every content entity
fully wired across all its touchpoints?**

It introspects by **source-scanning** — reading core's source, assets, and `.properties`
files — **not** by running the game.

- *Why not runtime/reflection.* The point is to find entities **missing** from the
  registries, so the registries cannot be the enumeration source; and running game code
  standalone drags in libGDX/Android stubs — heavy and brittle. Source-scanning is fast,
  robust, needs no runtime, and matches how `asset-audit` already works.
- *The cost.* Loose matching (occasional false positive/negative), paid down by the
  ratchet (§4) — the same trade-off `asset-audit` documents and accepts.
- *Alternative considered and rejected.* A reflection-driven `core:test` like
  `TerrainIdTest` is more precise per-entity but cannot cleanly enumerate un-registered
  classes and couples the check to game-runtime stubs.

## 3. The checks

**Enumeration.** A content entity is any concrete (non-`abstract`) class whose `extends`
ancestry (built from source) reaches `Mob` (under `actors/mobs/`) or `Item` (under
`items/`). Known base types (`Mob`, `Item`, `MeleeWeapon`, `MissileWeapon`, `Armor`, …)
are skipped via a small base-class allowlist. Touchpoints satisfied by an ancestor count:
the walker checks the whole ancestry, so `Albino extends Rat` inherits `RatSprite` and
passes.

**Per mob (three checks):**

- **M1 · Sprite wired.** The class or an ancestor sets `spriteClass = XSprite`; `XSprite`
  references a `GeneralAsset.Y`; `Y`'s path resolves to a PNG that exists under
  `core/src/main/assets/`. Fails on a broken link at any hop.
- **M2 · Localization.** The derived key `actors.mobs.<name>.name` **and** `.desc` exist
  in `actors.properties`.
- **M3 · Registered.** The class is referenced in `Bestiary.kt` **or** on the allowlist
  (bosses, summoned minions, quest/ability-only mobs legitimately do not spawn there).

**Per item (same shape):**

- **I1 · Sprite wired.** The class or an ancestor sets `image = GeneralAsset.Y`; `Y` → PNG
  exists.
- **I2 · Localization.** The derived key `items.<subpath>.<name>.name`/`.desc` exists
  (e.g. `items.food.supplyration`, `items.remains.bowfragment`).
- **I3 · Registered.** The class is referenced in a `Generator` `Category` **or**
  allowlisted (quest items, boss drops, ability-created, non-generated).

**Two honest nuances, both absorbed by the ratchet:**

- *Registration is a proxy for "spawns."* "Appears in `Bestiary`/`Generator`" is not a
  perfect signal, but it is the cheap robust one; intentional exceptions go on the
  allowlist rather than weakening the check.
- *Localization-key derivation has edge cases.* A few items use non-standard key prefixes.
  Those get a per-entity key override in a small map, or ride the allowlist.

**Finding format.** Each finding names the entity, the failed check, and the missing
artifact, e.g. `Mob SewerCrab: M1 sprite GeneralAsset.CRAB → sprites/mobs/crab.png NOT
FOUND`.

## 4. Ratchet and negative control

Both mechanisms are lifted from the repo's existing patterns; nothing novel to learn.

**Allowlist — `reviewed-exceptions.txt` (permanent, correct exemptions).** Same shape as
`deletion-audit/reviewed-removals.txt`: one `EntityType Name#Check` key per line, with a
comment block above each group explaining why. These are correct **forever**, not gaps:

```
# Bosses never spawn via Bestiary — they're placed by their boss level.
Mob YogDzewa#M3
Mob Goo#M3
# Quest rewards / ability-created items are not Generator-generated.
Item Amulet#I3
Item SpiritBow#I3
```

**Ceiling — `--max-findings N` (the un-triaged backlog).** Per CLAUDE.md's "ratchet, not a
baseline you stop reading": the first run will surface real incomplete content. Rather than
block on fixing all of it up front, park the count behind an explicit ceiling — new gaps
push over it and **fail**. Every finding prints (`TRACKED, NOT ACCEPTED`); ratchet `N` down
as content is fixed or moved to the allowlist. Distinction: allowlist = "this is right";
ceiling = "this is a known gap we will burn down."

**Negative control — `-Canary`.** CLAUDE.md requires every gate be provably able to fail.
`-Canary` runs the checks against a synthetic entity fixture with a deliberately missing
localization key and a dangling sprite reference, and asserts the tool **flags both**. If
the canary is not caught, the tool exits non-zero — so a green result is never one that has
never been seen to go red.

**Exit codes** match the family: `0` within ceiling, `1` over ceiling or canary failed, `2`
the scan itself broke (fewer than a sanity floor of source files read → refuse to declare
everything complete, like `asset-audit`'s under-read guard).

## 5. Scope and non-goals

**v1 ships:** Mobs + Items; the three checks each; the source-scan engine; allowlist +
`--max-findings` ratchet; `-Canary`; a Java 17 test suite; a README whose touchpoint
checklist is the authoring guide; and gate/CI wiring — with the allowlist triaged and the
ceiling set to the real current backlog.

**Explicit non-goals (deferred, not forgotten):**

- **Bosses, biomes, levels, talents, traps, plants as first-class entities.** Bosses and
  quest mobs ride the allowlist for now; auditing them on their own terms (arena, music,
  phases) is a follow-up.
- **The scaffold/generator** (the "make it fast" half). Separate next step; it builds on the
  touchpoint definition this establishes.
- **Pack/marketplace JSON content.** v1 audits *core* content; auditing the 29-file modding
  packs is a later extension.
- **"Is it good?"** This checks that content is *wired*, never that it is *balanced or fun*.
  It catches a mob that will not render / has no name / never spawns — not a boring mob or a
  broken mechanic. Completeness, not correctness-of-behavior.

## 6. Testing and integration

**Testing** — its own Java 17 test suite, matching the other tools: unit tests for the
extends-graph walker, localization-key derivation, the sprite-resolution chain, allowlist
parsing, and ceiling logic; the `-Canary` fixture as the integration-level negative control.
Fixtures include one fully-wired entity and one missing each touchpoint, so every check is
proven to both fire and pass.

**Integration** — a gradle Java task like `deletion-audit`:

```
:services:tools:content-audit:run --args="--max-findings N --allowlist services/tools/content-audit/reviewed-exceptions.txt"
```

Added to the CPDU gate command in `CLAUDE.md` (the "anything touching `core/`" section) and
to CI, asserting non-vacuity (scanned > 0 entities). The README's touchpoint checklist is
the first "how to add a mob/item in Lutherverse" doc.

## 7. Definition of done

- Tool builds and runs as `:services:tools:content-audit:run`.
- Enumerates all concrete core Mob and Item entities; reports each failed check with the
  missing artifact named.
- `reviewed-exceptions.txt` triaged for the legitimate permanent exceptions.
- `--max-findings` ceiling set to the current real backlog; findings printed.
- `-Canary` proven to catch an injected missing-localization and dangling-sprite entity;
  demonstrated red then green in the commit that adds it.
- Test suite green; the tool proven able to fail before it is trusted.
- Wired into the CPDU gate command and CI, asserting non-vacuity.
- README authoring guide written.
