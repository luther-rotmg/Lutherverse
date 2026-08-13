# content-scaffold — Design

*The "make it fast" half of the content pipeline: a generator that emits a compilable,
fully-wired Mob/Item skeleton so an author fills in the mechanic, not the wiring. The
inverse of content-audit. Written 2026-08-12.*

---

## 1. Context

The content pipeline (Sub-C, team-code-first) has two halves. `content-audit` (merged
2026-08-12) is the "make it correct" half: it defines the touchpoint model and reports any
core Mob/Item not fully wired across sprite, localization, and registration. This spec is
the "make it fast" half: for each touchpoint the auditor checks, the scaffold **emits** it.

Adding one mob today touches six-plus disconnected places (the `Mob` class, a sprite class,
a `GeneralAsset` entry + PNG, `actors.properties` keys, and a `bestiary` registration in
`dungeon.json`). The friction is the wiring, not the mechanic. `content-scaffold` removes the
wiring: one command produces a compilable, runnable, fully-wired skeleton with the mechanic
and the art left as clearly-marked TODOs.

## 2. What it is

A new Java 17 tool `services/tools/content-scaffold/` (sibling to `content-audit`, same
Gradle `application` module shape, reusing JavaParser where Java parsing is needed).

CLI:
```
content-scaffold mob  <Name> --depth <n>
content-scaffold item <Name> --category <cat> --tier <n>
```

`<Name>` is the PascalCase class name. `--depth` (mob) and `--category`/`--tier` (item) are
the **placement** decisions — which floors the mob spawns on, which `Generator.Category` and
tier the item generates in. Placement is a real design choice, so it is a required arg, never
a default.

## 3. Architecture — marker-anchored insertion

The risk in auto-wiring is corrupting `GeneralAsset.kt`, `*.properties`, `dungeon.json`, or
`Generator.java` by parse-and-reserialize (which reflows formatting and drops comments). The
scaffold therefore does **targeted text insertion at explicit anchor markers**, never a full
reserialize.

**One-time prep (a setup step in the plan):** add an insertion marker to each registry at the
point new entries belong:
- `GeneralAsset.kt`: `// @content-scaffold:mobs` / `// @content-scaffold:items` comment lines.
- `actors.properties` / `items.properties`: a `###scaffold` section header per file.
- `Generator.java`: a `// @content-scaffold:items` marker inside the item-generation area.
- `dungeon.json`: no marker — the scaffold inserts into the `bestiary` array of the level
  whose depth matches `--depth`, located structurally.

**Per invocation** the scaffold inserts each new entry immediately above (or into) its anchor:
- Minimal diff — surrounding formatting untouched.
- **Idempotent** — if an entry for `<Name>` already exists in a registry, that insertion is a
  no-op with a warning, not a duplicate. Re-running the whole command on an existing name is a
  warned no-op across every touchpoint.
- Fail-safe — if any anchor is missing, the tool exits non-zero naming the missing marker
  rather than guessing an insertion point.

## 4. What it produces (per mob, e.g. `Wisp` at `--depth 3`)

- **`core/.../actors/mobs/Wisp.java`** — a compilable skeleton: `extends Mob`, an init block
  (`spriteClass = WispSprite.class`, placeholder `HP/HT`, `defenseSkill`, `maxLvl`), and
  `act()` / `damageRoll()` / `attackSkill()` overrides carrying `// TODO: mechanic`.
- **`core/.../sprites/WispSprite.java`** — a minimal single-frame `MobSprite` stub texturing
  `GeneralAsset.WISP`, with `// TODO: real frames + art`.
- **`GeneralAsset.kt`** — `WISP("sprites/mobs/wisp.png")` inserted at its marker.
- **`core/.../assets/sprites/mobs/wisp.png`** — the shipped placeholder PNG copied in.
- **`actors.properties`** — `actors.mobs.wisp.name` / `.desc` inserted at the `###scaffold`
  section.
- **`dungeon.json`** — `"Wisp"` added to the depth-3 `bestiary` list.

Items are analogous: `Item` subclass (`image = GeneralAsset.<NAME>`), sprite via `GeneralAsset`
directly (no separate sprite class — items render from `image`), the PNG, `items.properties`
keys, and registration into the named `Generator.Category`.

The result **compiles and runs**; the author replaces the placeholder art and writes the
mechanic.

## 5. The placeholder sprite

No art generation. The tool ships one tiny, unmistakable placeholder PNG (a 16×16 magenta
box) under its own resources and copies it to the new asset path. The generated content
renders (a magenta box) and passes the sprite check; the `// TODO: real frames + art` note and
the obvious magenta signal the art is unfinished.

## 6. Correctness contract — scaffold and audit are inverses

After generating, the scaffold runs `content-audit` and reports whether the new entity is
fully wired — its built-in acceptance is *generate → audit-green*. One honest caveat, from
`content-audit`'s own final review:

- **M1/M2/I1/I2** (sprite, localization): scaffold output passes `content-audit` immediately.
- **M3/I3** (registration): `content-audit`'s registration checks currently read `Bestiary.kt`
  / `Generator`, but mobs actually register in `dungeon.json`. The scaffold registers
  *correctly* (verified by construction — it inserts into `dungeon.json` / the named
  `Generator.Category`), but `content-audit` cannot *confirm* the mob's registration until its
  heuristic bead re-teaches it to read `dungeon.json`. This is a documented interaction, not a
  blocker: the scaffold's registration is correct regardless, and the two tools close the loop
  together once the heuristic lands.

## 7. Scope & non-goals

**v1 ships:** Mobs + Items; marker-anchored auto-wiring of all six touchpoints; the placeholder
PNG; CLI with required placement args; idempotency; the post-generate `content-audit` check; a
JUnit test suite; a README.

**Non-goals (deferred):**
- **Art generation** — placeholder only, always.
- **The mechanic** — the author writes it; that is the entire point.
- **Bosses, biomes, talents, traps** — v1 is Mobs + Items, matching `content-audit`.
- **Interactive prompts** — CLI args only.
- **Editing anything but the six touchpoint registries.**
- **Un-scaffolding / delete** — out of scope; removing generated content is manual.

## 8. Testing

Fixture-based JUnit (mirroring `content-audit`), generating into a `@TempDir` fixture repo
seeded with marker-bearing registries:
- The new files exist with the expected class/package shape.
- Each registry received exactly the right insertion at its marker (GeneralAsset entry,
  properties keys, `dungeon.json` bestiary entry, `Generator` category entry).
- **Idempotency** — re-running the same command is a warned no-op, not a duplicate; a test
  proves the guard actually fires (the standing negative-control discipline).
- **Missing-anchor** — a registry missing its marker makes the tool exit non-zero naming the
  marker, not guess.
- A round-trip check that the generated entity satisfies the sprite/localization touchpoints.

## 9. Definition of done

- `content-scaffold mob <Name> --depth <n>` and `content-scaffold item <Name> --category <cat>
  --tier <n>` produce compilable, fully-wired skeletons.
- All six touchpoints are inserted at their anchors with minimal diffs; idempotent on re-run;
  fail-safe on a missing anchor.
- The prep step adds the anchor markers to the registries.
- The placeholder PNG ships with the tool and lands at the new asset path.
- Post-generate `content-audit` reports the sprite/localization checks green (M3/I3 per the
  documented caveat).
- Test suite green; idempotency and missing-anchor guards proven able to fail.
- README written (how to scaffold a mob/item; the placement args; the art/mechanic TODOs).
