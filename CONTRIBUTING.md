# Contributing

## Commit messages

Commits follow [Conventional Commits](https://www.conventionalcommits.org/): `type(scope): summary`,
written in English, imperative mood (`fix: stop X from happening`, not `fixed`/`fixes`). The scope
is optional and usually names the area touched (`extensions`, `updater`, `reader`, `lint`, ...).

Types used in this repo:

- `feat` - a new user-facing feature
- `fix` - a bug fix
- `refactor` - a code change that doesn't fix a bug or add a feature
- `perf` - a performance improvement
- `docs` - documentation only (README, CHANGELOG, code comments)
- `style` - formatting-only changes (whitespace, lint autofixes) with no logic change
- `test` - adding or correcting tests
- `build` - build system or dependency changes (Gradle, version catalog, ...)
- `ci` - CI/CD workflow changes (`.github/workflows/`)
- `chore` - everything else (repo maintenance, tooling, translation syncs)

`CHANGELOG.md`'s `[Unreleased]` section is updated by hand alongside the commits it describes,
in the same user-facing, prose style as the rest of the file (not a raw commit log). If you want a
sanity check of what's landed since the last tag, `cliff.toml` configures
[git-cliff](https://git-cliff.org/) to draft one from Conventional Commits -
run `git-cliff --unreleased` locally; it's a manual spot-check tool, not something CI runs.

## Translations

Rokku's UI strings are translated via [Weblate](https://hosted.weblate.org/projects/rokku/).
Source strings live in `i18n/src/commonMain/moko-resources/base/` (`strings.xml` and
`plurals.xml`); translated locales live alongside them in the same module.

**Do not open PRs with hand-edited translation files.** Add or fix a translation on
Weblate instead — it keeps a single source of truth and avoids merge conflicts with
what Weblate pushes back automatically.

### How the sync works

- **GitHub → Weblate**: on every push to `master`, GitHub notifies Weblate so it can
  pull the latest source strings and update the translation base.
- **Weblate → GitHub**: translations made on Weblate are pushed back to the repository
  as a pull request for review before merging.

This is wired up via the [Hosted Weblate GitHub App](https://github.com/apps/hosted-weblate),
installed on the `thiago8rocha/rokku` repository. No webhook or deploy key needs to be
maintained by hand — the app manages both directions (pull notifications and translation
push-back) through its own installation token.
