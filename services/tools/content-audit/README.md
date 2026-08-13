# content-audit

Fails the build when a core Mob or Item is not fully wired. The check list below
IS the "how to add content" checklist — satisfy every row and the gate passes.

```
./gradlew.bat :services:tools:content-audit:run \
  --args="--allowlist services/tools/content-audit/reviewed-exceptions.txt --max-findings 189"
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
