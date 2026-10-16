# Conventions

Rules every lane follows. The architecture and step ids are in [`plan.md`](plan.md); lanes, gates and ownership are in
[`plans/README.md`](plans/README.md).

## Issues and labels

- One GitHub issue per `plan.md` step, titled with the step id (`0a.8 Cloud bootstrap script`). Create it from the
  *Plan step* issue template, which starts with a **Validation plan** section: what is checked, how (test case ids), and
  the pass criteria.
- Labels: exactly one `milestone:<0a, 0b, 1, 2, 3, 4, 5, 6, 7>` and exactly one `lane:<S, A, W, D>`.
- Each pull request closes its issue and begins with `Requires:` and `Produces:` lines (see the stop rule).
- `scripts/check-issues.sh` verifies that every step has exactly one issue with these properties; the *Conventions*
  workflow runs it with the repository's issues.

## Work log

One log per lane in `docs/work-log/`. Entries follow the format in [`work-log.md`](work-log.md): the heading
`## YYYY-MM-DD - <step id> <title>` and the bullets *Issue and PR*, *Changed*, *Validation* and *Open items*.
`scripts/check-work-log.sh` verifies the format.

## Documentation

[`index.md`](index.md) is the single entry point of the documentation. Every new markdown file under `docs/` must be linked
from `docs/index.md` (directly, or through a page it links) in the same pull request; the `docs-site` CI job runs
`scripts/check-docs-index.sh` and builds the site in strict mode, so a document nobody links or a broken link fails the PR.
Other documents are linked, never copied. `scripts/docs-lint.sh lint` and `format` (and `fix` for the formatting rules)
check the markdown style.

## Debug code

Debug-only code (verbose logs, raw biometrics, injected-message receivers, debug log views) lives in
`src/debug/` of its module **and in a package named `debug`**, for example `com.shlomoa.myadhdpal.wear.debug`. Release
builds must contain no class from such a package; `./gradlew checkNoDebugInRelease` (part of `check`) fails otherwise.
Raw biometrics are health data and are never logged in release builds.

## Checks

`./gradlew check` is the single entry point and CI calls it: Spotless with ktlint, detekt, Android lint (any new issue
fails; each module's `lint-baseline.xml` holds the issues that existed before), the unit tests, the pairing check
(same `applicationId`, SDK levels and signing config in both apps), the documentation checks and the
release-has-no-debug check. `./gradlew spotlessApply` fixes formatting. Spotless can reuse stale results after `.editorconfig` changes; if CI disagrees with
your machine, run `./gradlew spotlessApply --rerun-tasks --no-build-cache`. External links are checked separately
(`./gradlew checkExternalLinks`) in a CI job that never blocks.

Shared configuration (`applicationId`, SDK levels) is defined once: the `[versions]` block of
`gradle/libs.versions.toml`, applied to both apps by the root `build.gradle.kts`. Module scripts must not redefine it.
