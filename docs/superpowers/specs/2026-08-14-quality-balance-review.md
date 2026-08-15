# Friday-night quality + balance review — 2026-08-14 (multi-lens, adversarially verified)

**Range reviewed:** `c5dae6fee..9d6092902` (everything shipped to `main` today).

## 1. Verdict

Today's push lands the Keybearer spine — sphere grid window rework, Insight Crystal faucet, Nova activation test on the F3 fixture, and the adversarial-review fixes from 30cdd293c — and the model-level work is solidly tested. But it is **not shippable as-is**: the ScrollPane rework in `WndSphereGrid` kills every enabled node button, which means the entire unlock/activate loop is dead in the actual UI (a regression against 30cdd293c that no test covers), and five shipped `.properties` descriptions silently lose the paragraph that explains their mechanic. Beyond the two confirmed defects, the balance numbers on the new content run hot across the board — the keyblade burst and Nova gridBonus in particular. Every finding below was adversarially verified in source before inclusion; two claims that did not survive verification are listed at the end for honesty.

## 2. Confirmed code findings

| # | Severity | File:line | Defect |
|---|----------|-----------|--------|
| 1 | HIGH | `core/src/main/java/com/qsr/customspd/windows/WndSphereGrid.java:100` | Every enabled sphere-grid node button is unclickable — unlock/activate UI loop is dead (regression from 30cdd293c) |
| 2 | MEDIUM | `core/src/main/assets/messages/items/items.properties:2044` | `items.insightcrystal.desc` (plus four mob descs in `actors.properties`) truncated by broken `.properties` line continuation; junk keys injected — independently confirmed by both the correctness and invariants lenses |

### 2.1 — Dead node buttons in WndSphereGrid (HIGH, correctness)

The RedButtons at `WndSphereGrid.java:100` have no `onClick` override; clicks were meant to arrive via the ScrollPane's forwarding loop (lines 78–89), justified by the comment "Buttons inside a ScrollPane don't receive raw touches." That comment is false in this engine. Each button's `hotArea` (`ui/Button.java:49-114`) registers on the stack-mode pointer signal (`PointerEvent.java`: `new Signal<>(true)`; `Signal.dispatch` stops at the first listener returning true) *after* the pane's PointerController, so the enabled button is notified first, hit-tests correctly through the content camera, consumes both DOWN and UP with default `blockLevel = BLOCK_WHEN_ACTIVE`, and fires the empty default `Button.onClick()` — the pane's forwarding never runs. Disabled buttons do pass through, but the loop's `btn.active` guard skips them. Net: no node can be unlocked or activated from the window. The engine's own convention for buttons in scroll areas is `hotArea.blockLevel = PointerArea.NEVER_BLOCK` (`PointerArea.java:38`, "handy for buttons in scroll areas" — see `TalentButton.java:68`, `QuickRecipe.java:111/214`); the imitated `WndKeyBindings` idiom only works because its rows are plain Components, not Buttons.

**Fix:** follow the TalentButton idiom — give each node button a real `onClick` override calling `nodeClicked(node)` and set its `hotArea.blockLevel = PointerArea.NEVER_BLOCK` (needs a small RedButton subclass or accessor; `hotArea` is protected), then drop the pane-side forwarding loop. Alternatively keep the loop but still set NEVER_BLOCK on every node button. Verify by clicking a `canUnlock` node in a live run: Insight must decrement and the window must rebuild.

### 2.2 — Truncated `.properties` descriptions, junk keys injected (MEDIUM, correctness + invariants)

`items.insightcrystal.desc` at `items.properties:2044` ends after `_permanently_.`, followed by a literal blank line and a bare continuation line ("Grants _+1 Insight_ …"). Messages loads these via libGDX I18NBundle (`Messages.java:104-112`), which follows java `.properties` logical-line rules: no trailing backslash means the bare line parses as a *new* property keyed `Grants`, and the paragraph explaining the +1 persistent-Insight mechanic is silently dropped from the in-game info window (`setExceptionOnMissingKey(false)` at `Messages.java:87` suppresses any signal, and content-audit only checks key existence). The identical malformation shipped in the same-day scaffold commit 4885605ad for `tumblerwisp`, `hexwardmoth`, `wardstonesentinel`, and `gaolshade` descs (`actors.properties:1392-1406`, orphan keys `When`, `Its`, `Slow,`, `Every`). Every correct multi-paragraph neighbour (e.g. `keywraith.desc`) uses literal `\n\n` on one line.

**Fix:** rejoin all five descriptions into single physical lines with `\n\n` escapes, matching house style. Then add a tiny gate that scans the `.properties` files for non-comment lines lacking `=` — it would have caught all five — and/or teach content-scaffold to reject bare continuation lines after a key it just added.

## 3. Balance & tuning recommendations

### Keyblades / sphere grid

- **HIGH — `Keyblade.java:80`** — The element×ability burst (identical in all three blades) is multiplicative, armor-bypassing, and fires every hit. A mid-game 8-point fire build deals ~30/swing on a tier-2 (base avg 8.5) vs the tier-3 Sword's 11.5; full grid the flat rider is ~38, and the quadratic term grows as points²/4 while upstream weapons grow linearly. **Change:** make the burst additive — `burst = elementLevel + abilityLevel` (mid-game 7, full grid 10) — or halve the product and route it through `drRoll`. Also route the flat element damage (`defender.damage(emberLevel)`) through the main damage value instead of a separate armor-bypassing hit.
- **MEDIUM — `heroes/keybearer.json:13`** — Thermal Shock / Conduction's +2 is permanent, not a build choice: the Keybearer starts with all three blades, so "carrying another elemental keyblade" is true from turn 1 — an unconditional armor-bypassing rider worth ~+24% at depths 1–5, dominant early, dead later, never a decision. **Change:** `bonus = level of the highest OTHER element branch` (0 unspecced, 5–6 late), or require at least one node in the other blade's element before the +2 fires.
- **MEDIUM — `StormKeyblade.java:93`** — The storm arc at `(storm+1)/2` to a single adjacent enemy (2–3 damage) underdelivers vs fire's guaranteed Burning reignite (~20 expected) and frost's up-to-8-turn chill. **Change:** arc the full storm level to one neighbour, or keep half damage but arc to *all* adjacent enemies — the "punishes clustering" identity the class comment already claims.
- **LOW — `SphereNode.java:49`** — Vigor is strictly dominated: 4 points buy +10 max HP while one free level-up grants +5, and the same 4 points in Might/Ember buy ~+16/hit. **Change:** raise magnitudes to 5/6/8 (+19 HP total), or give VIGOR_III a defensive rider (e.g. 2-shield Barrier when struck below half HP).
- **LOW — `WndSphereGrid.java:132`** — First tap silently spends persistent Insight and leaves the node inactive; the y-only hit-test (line 83) means any horizontal position on a row spends currency. Tolerable at cost 1, a real sting if unlock costs rise. **Change:** when the hero can afford both, one tap unlocks AND activates; add a confirm prompt for nodes costing more than 1 Insight once costs are retuned.

### Nova

- **HIGH — `KeybladeNova.java:78`** — gridBonus (`ember+frost+storm+might+2*ability`, conflux counted 3×) reaches +33 flat per target at full grid on a 360°, radius-4 AoE at 35 charge — avg 51 pre-armor per target vs Shockwave's ~19.5–31 in a 60° cone at the same cost. **Change:** `gridBonus = emberLevel + frostLevel + stormLevel + abilityLevel` (full grid +22, mid-game +10), or halve the current sum; alternatively keep the number and shrink to radius 3 so it stops strictly dominating Shockwave.
- **MEDIUM — `KeybladeNova.java:132`** — Storm-dominant Nova applies guaranteed, no-roll AoE Paralysis of `1 + 0.5*storm` turns (4 at full grid); two back-to-back Novas at 100 charge lock a room for ~8 turns. Upstream gates AoE paralysis behind talents, traps, or rare consumables. **Change:** cap at 2 turns (`1 + min(2, storm*0.5)`), or make the base status Cripple/Vertigo with Paralysis only under TEMPEST, mirroring frost's soft-control Chill.

### Insight economy

- **MEDIUM — `SphereGridProgress.java:45`** — Flat `UNLOCK_COST = 1` across all 20 nodes vs 1 Insight per level-up means the full grid, keystone included, unlocks by hero level ~21 of the first decent run; the persistent layer never matters again, and the keystone costs the same as a root. **Change:** price unlocks at `2 × node.cost` (roots 2, tier-IIIs/keystones 4) — total 52 Insight, a 2–3 run horizon. For a longer arc, tier 1/2/4 by web depth (~35 total).
- **LOW — `InsightCrystal.java:36`** — The shatter message is a hardcoded English string to `GLog.p`, bypassing Messages and invisible to content-audit's localization checks. **Change:** add `items.insightcrystal.shatter` to `items.properties` and call `GLog.p(Messages.get(InsightCrystal.class, "shatter", INSIGHT_PER_CRYSTAL))`.

### New mobs

- **MEDIUM — `WardstoneSentinel.java:17` (and GaolShade)** — The depth-6/7 wave stubs are live in `dungeon.json` spawn decks with sewers-tier stats: HP 10, dmg 1–4, attackSkill 10, default EXP 1, maxLvl 10 — next to Skeleton (HP 25, EXP 5) and Guard (HP 40, EXP 7). A depth-6/7 hero (level 11–14) is above maxLvl 10, so they die in one hit for zero XP while diluting the prison tables. TumblerWisp at depth 2 is fine; HexwardMoth at depth 4 is thin but survivable. **Change:** move stat bands to depth — HexwardMoth ~HP 18 / def 7 / dmg 2–6 / EXP 4 / maxLvl 9; WardstoneSentinel ~HP 30 / def 8 / dmg 3–9 / dr 0–4 / EXP 6 / maxLvl 13; GaolShade ~HP 30 / def 11 / dmg 4–10 / EXP 6 / maxLvl 14 — or pull the depth-6/7 entries from `dungeon.json` until the fill-in pass.

### Drop weights

- **LOW — `InsightCrystal.java:27`** — At STONE-deck weight 2 (deck total 54, ~2–3 stones per run) the faucet fires roughly once every ten runs at `INSIGHT_PER_CRYSTAL = 1` — noise next to ~25 Insight from level-ups, which cannot deliver the stated "exploration pays into the build-craft spine" goal. **Change:** raise deck weight to 5 and/or `INSIGHT_PER_CRYSTAL` to 2; if the economy is repriced per the SphereGridProgress item, a guaranteed one-per-chapter spawn is the cleaner faucet.
- **LOW — `WardensSigil.java:9`** — Note: the "inert stub in the live deck" version of this claim was refuted (see below — the mechanic shipped in 654e8113c and is test-pinned). The residual tuning point stands: at weight 2 next to the common 5s, the aimed lock-stone is a reasonable rarity — no change needed now that the mechanic is live. Minor flavor nit: Cripple slows rather than roots, so "locked in place" in `items.properties:2048` slightly overstates the effect.

## 4. Refuted claims

- **"WardensSigil (a plain Item, not a Runestone) in the STONE pool makes ScrollOfTransmutation crash with ClassCastException"** → the premise is factually wrong: `WardensSigil.java:36` declares `extends Runestone`, so the cast at `ScrollOfTransmutation.java:319` (and `RingOfWealth:212`, `Recycle:78`) succeeds; no crash is possible on this path.
- **"WardensSigil is an inert TODO stub registered in the live drop deck"** → stale-snapshot review: the stub existed only in 4885605ad; same-day follow-up 654e8113c (ancestor of HEAD) implemented the full `activate(int cell)` mechanic (Cripple 5t direct, 2t enemy-only splash), pinned by 5 tests in `WardensSigilTest.java` on the real HeadlessLevel fixture.