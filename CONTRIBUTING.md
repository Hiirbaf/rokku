# Contributing

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
