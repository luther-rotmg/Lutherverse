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
