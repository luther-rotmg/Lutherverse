# Merge and diff tooling

Two external binaries that make the upstream sync cheaper. Neither is a build dependency —
they are developer tools, and the repo builds fine without them.

## Why

~541 upstream commits remain to port from Shattered PD. The conflicts that dominate that
work are ones a **line-based** merge cannot resolve but a **structural** one can: two sides
adding different methods to the same class, reordered imports, independent keys in
`messages/*.properties`, sibling entries in the JSON mod manifests.

## Install

```
winget install --id Mergiraf.Mergiraf
winget install --id Wilfred.difftastic
```

Verified 2026-08-10: Mergiraf 0.16.3, Difftastic 0.69.0.

## Configure (per clone — `.gitattributes` alone is not enough)

```
git config merge.mergiraf.name "mergiraf syntax-aware merge"
git config merge.mergiraf.driver "mergiraf merge --git %O %A %B -s %S -x %X -y %Y -p %P"
git config diff.difftastic.command difft
```

`.gitattributes` already maps `*.java`, `*.kt`, `*.kts`, `*.json`, `*.xml` and `*.properties`
to `merge=mergiraf`. **Without the local `git config` above, git silently falls back to the
default merge** — a fresh clone gets ordinary behaviour rather than an error, which is the
safe failure but also an easy one to not notice.

Mergiraf works with `merge`, `rebase`, `cherry-pick` and `revert` — all four porting verbs.

## Validation performed before adopting

Three-way merge, both sides adding a different method to the same class:

| Tool | Result |
|---|---|
| `git merge-file` | exit 1, one conflict, `<<<<<<<` markers |
| `mergiraf merge` | exit 0, clean, both methods present and correctly placed |

Do the same A/B on a real already-ported batch before trusting it on new work — re-run a
finished integration and compare conflict counts. A structural merge that resolves
*incorrectly* is worse than a conflict you can see.

`mergiraf review <id>` shows how a resolution differs from what a line merge would have
produced. Use it whenever a merge resolves suspiciously cleanly.

## difftastic

Structural diff, for reviewing upstream commits where reformatting and import churn wrap a
few real lines:

```
git -c diff.external=difft show <sha>
git -c diff.external=difft diff <range>
```

Left unset as the default `diff.external` deliberately — it changes every `git diff` in the
repo, and scripts that parse diff output would break. Opt in per command.

## Also enabled

`git rerere` (`rerere.enabled`, `rerere.autoupdate`), so the same conflict resolved once in a
batch is replayed automatically the next time it appears. It was off, which meant the same
namespace-rename conflicts were being resolved by hand on every batch.
