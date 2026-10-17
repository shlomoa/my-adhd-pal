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

### What runs when

| When | What | Where |
|---|---|---|
| Every pull request | `./gradlew check assembleDebug` and the release-has-no-debug script (`build`), the documentation site build and index check (`docs-site`), the non-blocking external link check, the gate and convention checks, and the fast setup test `scripts/test/test-post-merge.sh` | `build.yml`, `gates.yml`, `conventions.yml` |
| After each merge to `main` (and by hand: *Run workflow*) | the slow lane test scripts in `scripts/test/` (negative fixtures, fresh-clone formatter check, CI definition, spike runners, documentation checks), listed in `scripts/post-merge-tests.sh` | `post-merge.yml` |

The post-merge run is not a pull request check, so a pull request never waits for it. When it fails, the workflow opens
or updates one issue labelled `main-red` that names the failing script and cases and links the run; a later green run
closes it. The coordinator assigns the fix to the lane that owns the failing test. A new `scripts/test/test-*.sh` must be
added to `scripts/post-merge-tests.sh` or to a pull request workflow; `test-post-merge.sh` fails if one runs nowhere.

### When lint itself crashes

Android lint sometimes stops with `Unexpected failure during lint analysis of <file>.kt`, a
`KotlinIllegalArgumentExceptionWithAttachments` from `LLFirModuleLazyDeclarationResolver.handleExceptionFromResolve`
(stack through `SymbolLightClassForClassOrObject.isRecord` and `UElementVisitor.visitFile`). That is a crash of lint's
Kotlin Analysis API, not a lint finding about the code. It was seen with AGP 9.4.1 and Kotlin 2.4.x, mostly on the
first `check` after a merge or a failed build that changed Kotlin or resource files, and an identical second run
passes. We could not reproduce it from a clean state or from a merge in five full runs. The same signature is reported
in [bluecard issue 227](https://github.com/bryancassell/bluecard/issues/227) (AGP 9.4.1, Kotlin 2.4.20, also
intermittent after edits, no fix named); no fix is documented in the AGP release notes seen so far.

What to do:

1. Run the same command again once. If it passes, the code is fine; do not change code or baselines to chase it.
2. If it fails a second time, or lint reports a finding with a file and line, it is a real failure.
3. When it happens, save the first 40 lines of the task output and `build/reports/lint-results-*` so a report to
   Google (the [AGP bug-report guide](https://developer.android.com/studio/report-bugs)) has the full trace.
4. Do not add a retry to CI: a repeat that hides a real second failure is worse than one manual rerun.

When the Android Gradle plugin or Kotlin version in `gradle/libs.versions.toml` changes, check whether this note is
still needed.
