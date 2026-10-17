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

## Cleanup

Cleanup is part of every step. If a file or file content is no longer used, delete it in the same pull request: code,
resources, tests, fixtures, scripts, documents, images, style rules, constants, imports and settings. No dead code, no unused
files. Before opening a pull request, search for what your change made unused and remove it, and say in the pull request body
what you deleted ("Cleanup: ..."), or "Cleanup: nothing became unused". Clean what your step touched or obsoleted; report older
leftovers you notice to the coordinator instead of widening the change.

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
| Every pull request | `./gradlew check assembleDebug` and the release-has-no-debug script (`build`), the documentation site build and index check (`docs-site`), the non-blocking external link check, and the gate and convention checks (`gates`, `conventions`) | `build.yml`, `gates.yml`, `conventions.yml` |
| After each merge to `main` | all script tests of `scripts/test/` that the former `script-tests` job ran (structure, pairing, checks, ci, protocol-spec, gates, spike, design-docs, doc-checks, docs-site, design-style) and `test-post-merge.sh`, listed in `scripts/ci-post-merge/run-tests.sh` | the validation lane agent, in its own container; no workflow |

The post-merge run is not a pull request check and not a workflow, so a pull request never waits for it. The validation
lane (V) checks out the merged `main` sha and runs `scripts/ci-post-merge/run-tests.sh` itself (several merges in a row:
the latest sha, bisecting only when red). The runner runs three groups of tests at the same time, each group in its own
git worktree of that sha (some tests edit and restore files or run Gradle in the checkout), and reruns a test once when
its log shows the known lint crash. When it fails, or cannot run, lane V opens one issue labelled `main-red` with
its GitHub issue tool. The issue body names the failing test ids, the sha, the owning lane and the sub-agent. Lane V
starts one sub-agent per `main-red` issue; the sub-agent fixes the failure in its own PR. Lane V updates the issue, and
closes it with the sub-agent once a later run is green. A new `scripts/test/test-*.sh` must be added to
`scripts/ci-post-merge/run-tests.sh` or to a pull request workflow; `test-post-merge.sh` fails if one runs nowhere.

### When lint itself crashes

Android lint sometimes stops with `Unexpected failure during lint analysis of <file>.kt`, a
`KotlinIllegalArgumentExceptionWithAttachments` from `LLFirModuleLazyDeclarationResolver.handleExceptionFromResolve`
(stack through `SymbolLightClassForClassOrObject.isRecord` and `UElementVisitor.visitFile`). That is a crash of lint's
Kotlin Analysis API, not a lint finding about the code. It was seen with AGP 9.4.1, Kotlin 2.4.x and Gradle 9.8.1. The
same signature is reported in [bluecard issue 227](https://github.com/bryancassell/bluecard/issues/227); no fix is
named there, none is in the AGP 9.4 or 9.5 alpha release notes, and no newer stable AGP, Kotlin or Gradle exists
(see [Tooling freshness](research/tooling.md)).

**How to recognise it.** The failed task is `:phone:lintAnalyzeDebugUnitTest` (lint of the phone's unit-test sources,
for example `AlertActivityTest.kt`), the output has three of these messages, and there is no lint finding with a rule id
and a line. A real finding always names both.

**When it happens.** Not deterministic. It was seen:

- on the first `check` after a merge of main that rewrote Kotlin or resource files, after `spotlessApply`, or after a
  failed build (Android lane), and an identical rerun passed;
- in `scripts/test/test-checks.sh` at TC-S-017 (`./gradlew check --rerun-tasks`) after `test-structure.sh` and
  `test-pairing.sh` had run in the same checkout, twice in a row, while the same script passed in a clean git worktree
  (lane V);
- in one of four consecutive runs of `./gradlew --console=plain check --rerun-tasks` on main in one checkout, with no
  source change in between (lane S): run 1 passed, run 2 failed, runs 3 and 4 were not completed.

It did not happen in three runs of `clean` and `:phone:lintAnalyzeDebugUnitTest` alone, nor in `check` at one commit
followed by `check` after switching the same tree to main. So a full lint analysis (`--rerun-tasks`) in a tree and
daemon that already ran other Gradle work raises the odds; the necessary condition is not known. The full recipe and the
open question are in the "Recipe (lane S)" section of issue #205.

**Failing recipe** (may need several tries; use a checkout of main):

```sh
for i in 1 2 3 4; do ./gradlew --console=plain check --rerun-tasks; done
```

**What clears it.** Running the same command again, or a clean git worktree. Not yet tested on its own:
`./gradlew --stop` and removing `phone/build` and `.gradle`.

What to do:

1. Run the same command again once. If it passes, the code is fine; do not change code or baselines to chase it.
2. If it fails a second time, or lint reports a finding with a file and line, it is a real failure.
3. When it happens, save the first 40 lines of the task output and `build/reports/lint-results-*` so a report to
   Google (the [AGP bug-report guide](https://developer.android.com/studio/report-bugs)) has the full trace.
4. Do not add a retry to CI: a repeat that hides a real second failure is worse than one manual rerun.

When the Android Gradle plugin or Kotlin version in `gradle/libs.versions.toml` changes, check whether this note is
still needed.
