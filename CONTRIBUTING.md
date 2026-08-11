# Contributing to Lutherverse

*Thanks for looking. Lutherverse (repo: `CustomPixelDungeonUltimate`) is a pre-alpha cult-classic ambition — every star, watch, and thoughtful issue helps.*

## What we're currently accepting

| Type | Status | How |
|---|---|---|
| ⭐ Stars & watches | **Yes, please** | [Star the repo](https://github.com/luther-rotmg/Lutherverse) · [Watch → Custom → Releases](https://github.com/luther-rotmg/Lutherverse/subscription) |
| 🐛 Bug reports | Yes | [File an issue](https://github.com/luther-rotmg/Lutherverse/issues/new/choose) with the Bug Report template |
| 💡 Feature ideas | Yes | Feature Request template — especially for content you want to see (biomes, mechanics, addons) |
| 🎭 Cameo suggestions | **Yes, please** | Cameo Suggestion template — who should Lutherverse Easter-egg? |
| 🌍 Translation contributions | Not yet | Wait until Sub-B ships (upstream sync); string keys are unstable until then |
| 🔀 Pull requests | **Not currently accepted** | The modding-platform API is unstable; every hook is subject to change until Sub-C ships. See [PROJECT-STATUS.md](PROJECT-STATUS.md) for progress. |
| 🎨 Art / hero-image contributions | Contact first | The placeholder title card at `docs/assets/lutherverse-title.svg` is meant to be replaced — reach out via issue if you'd like to make the real one |

## Why PRs are gated right now

The core game engine is about to undergo a massive upstream sync (Sub-B: absorbing ~1,400+ SPD upstream commits from v2.1.0 to v3.3.8). Load-bearing files like `Hero.java`, `Bundle.java`, `GameScene.java` will be rewritten during that sync — any PR touching them right now would need to be re-based against a fundamentally different tree. Once Sub-B lands and Sub-C stabilizes the modding-platform API, PR gates open.

**Exceptions where a PR might be considered even during the freeze:**
- Documentation fixes (typo, broken link, factual correction to README/CHANGELOG/docs)
- Trademark-safety corrections (something we said that could get us in trouble)
- Attribution corrections (missing or wrong entry in `THIRD_PARTY_NOTICES.md`)

For these, open an issue first and we'll coordinate.

## What good issues look like

**Bugs:** version + platform + reproduction steps + what you expected vs what happened. Screenshots or short videos welcome. If it involves save-corruption or crash-on-launch, mark it **critical** in the title.

**Feature ideas:** describe the mechanic in one paragraph, then say who it's for (which player type would love this) and how it interacts with the existing feature list in [PROJECT-STATUS.md](PROJECT-STATUS.md). Ideas that would collide with the roadmap are just as useful as ones that fit — flag the collision.

**Cameo suggestions:** name the character, the source work, and one sentence on why they'd fit Lutherverse's tone (cosmic-horror + KH2 + FFX + Bloodborne). Legal notes: recognizable characters from copyrighted works are handled as fan-project Easter eggs, not as central content. We won't accept cameos of real living people.

## Style & tone

- **Have fun.** This is a cult-classic project by design. If your issue is enthusiastic, that's on-brand.
- **Assume good faith.** Everyone here is either running the game or thinking about running it, and that's a small enough set that we should treat each other well.
- **Skip the AI-slop.** If you asked ChatGPT to write your issue, please read it once and rewrite it in your own voice. LLM boilerplate ("Thank you for the wonderful project! I would like to respectfully suggest...") is friction, not signal.

## License

By opening an issue or (once open) a PR, you agree that any contribution you make will be licensed under GPL-3.0, consistent with the rest of Lutherverse. See [`LICENSE.txt`](LICENSE.txt).

## Attribution chain

Lutherverse is a GPLv3 fan project. Attribution flows: Watabou → Evan Debenham (Shattered Pixel Dungeon) → QuasiStellar (Custom Pixel Dungeon) → Lutherverse. See [`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md).

## Who to contact

Repo maintainer: [@luther-rotmg](https://github.com/luther-rotmg). Issues are the preferred channel — email is not.
