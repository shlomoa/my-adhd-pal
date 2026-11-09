# Tooling freshness (2026-10-17)

Question from the user: are we on the latest tool and package versions? This page is a check, not a change: nothing was
upgraded. Every "latest" value was read on **2026-10-17** by script from the source named in the table (Maven
`maven-metadata.xml` files, the Gradle versions API, the npm registry, the PyPI JSON API, `git ls-remote` on the action
repositories, nodejs.org and endoflife.date). "Stable" means a version without an alpha, beta, RC, milestone or preview
tag. Upgrades need the user's yes.

## Result in one paragraph

Every pinned Gradle, npm and PyPI version equals the latest stable release on 2026-10-17. The one pin that is not a
stable release is detekt `2.0.0-alpha.6`, because all published 2.x versions are alphas. Behind are only the five GitHub
Actions (three to four major versions) and the local Node version (22 against LTS 24). **No newer stable release of
AGP, Kotlin or Gradle exists, so none can contain a fix for the lint crash** (see the last two sections).

## Gradle build (`gradle/libs.versions.toml`, wrapper)

| Pin | Current | Latest stable (source) | Newer release fixes the lint crash? | Recommendation |
|---|---|---|---|---|
| Gradle wrapper | 9.8.1 | 9.8.1 ([versions API](https://services.gradle.org/versions/current)) | no newer release | pin; AGP 9.4 needs 9.6 or newer |
| `agp` | 9.4.1 | 9.4.1; 9.5.0 exists only as alpha01 to alpha10 ([Google Maven](https://dl.google.com/android/maven2/com/android/tools/build/gradle/maven-metadata.xml)) | 9.5.0 alpha notes list no lint crash fix | pin; revisit when 9.5.0 is stable |
| `kotlin` | 2.4.21 | 2.4.21; 2.5.0-Beta1 exists ([Maven Central](https://repo1.maven.org/maven2/org/jetbrains/kotlin/kotlin-gradle-plugin/maven-metadata.xml)) | none stable | pin; later for 2.5 |
| `ksp` | 2.3.12 | 2.3.12 ([Maven Central](https://repo1.maven.org/maven2/com/google/devtools/ksp/symbol-processing-api/maven-metadata.xml)) | n/a | pin |
| `composeBom` | 2026.09.00 | 2026.09.00 ([Google Maven](https://dl.google.com/android/maven2/androidx/compose/compose-bom/maven-metadata.xml)) | n/a | pin |
| `wearCompose` | 1.7.1 | 1.7.1 ([Google Maven](https://dl.google.com/android/maven2/androidx/wear/compose/compose-material3/maven-metadata.xml)) | n/a | pin |
| `activityCompose` | 1.13.0 | 1.13.0 ([Google Maven](https://dl.google.com/android/maven2/androidx/activity/activity-compose/maven-metadata.xml)) | n/a | pin |
| `room` | 2.8.5 | 2.8.5 ([Google Maven](https://dl.google.com/android/maven2/androidx/room/room-runtime/maven-metadata.xml)) | n/a | pin |
| `datastore` | 1.2.1 | 1.2.1 ([Google Maven](https://dl.google.com/android/maven2/androidx/datastore/datastore-preferences/maven-metadata.xml)) | n/a | pin |
| `androidxTestCore` | 1.7.0 | 1.7.0 ([Google Maven](https://dl.google.com/android/maven2/androidx/test/core-ktx/maven-metadata.xml)) | n/a | pin |
| `fragment`, `tiles`, `protolayout`, `healthServices` | 1.9.1, 1.6.2, 1.4.2, 1.1.0 | the same ([Google Maven](https://dl.google.com/android/maven2/androidx/fragment/fragment/maven-metadata.xml), [tiles](https://dl.google.com/android/maven2/androidx/wear/tiles/tiles/maven-metadata.xml), [protolayout](https://dl.google.com/android/maven2/androidx/wear/protolayout/protolayout/maven-metadata.xml), [health](https://dl.google.com/android/maven2/androidx/health/health-services-client/maven-metadata.xml)) | n/a | pin |
| `playServicesWearable` | 20.0.1 | 20.0.1 ([Google Maven](https://dl.google.com/android/maven2/com/google/android/gms/play-services-wearable/maven-metadata.xml)) | n/a | pin |
| `hilt` | 2.60.1 | 2.60.1 ([Maven Central](https://repo1.maven.org/maven2/com/google/dagger/hilt-android/maven-metadata.xml)) | n/a | pin |
| `robolectric` | 4.17 | 4.17 ([Maven Central](https://repo1.maven.org/maven2/org/robolectric/robolectric/maven-metadata.xml)) | n/a | pin |
| `kotlinxSerialization`, `kotlinxCoroutines` | 1.11.0, 1.11.0 | the same ([serialization](https://repo1.maven.org/maven2/org/jetbrains/kotlinx/kotlinx-serialization-json/maven-metadata.xml), [coroutines](https://repo1.maven.org/maven2/org/jetbrains/kotlinx/kotlinx-coroutines-core/maven-metadata.xml)) | n/a | pin |
| `junit` | 4.13.2 | 4.13.2 ([Maven Central](https://repo1.maven.org/maven2/junit/junit/maven-metadata.xml)) | n/a | pin (JUnit 5 is a different artifact, not an upgrade of this one) |
| `spotless` | 8.10.4 | 8.10.4 ([Maven Central](https://repo1.maven.org/maven2/com/diffplug/spotless/spotless-plugin-gradle/maven-metadata.xml)) | n/a | pin |
| `ktlint` | 1.8.0 | 1.8.0 ([Maven Central](https://repo1.maven.org/maven2/com/pinterest/ktlint/ktlint-cli/maven-metadata.xml)) | n/a | pin |
| `detekt` | 2.0.0-alpha.6 | none: all 7 published versions of the 2.x plugin are alphas ([Maven Central](https://repo1.maven.org/maven2/dev/detekt/detekt-gradle-plugin/maven-metadata.xml)) | n/a | pin; move to the first 2.0 stable |

## Documentation tools

| Pin | Current | Latest stable (source) | Recommendation |
|---|---|---|---|
| `markdownlint-cli2` | 0.23.3 | 0.23.3 ([npm](https://registry.npmjs.org/markdownlint-cli2/latest)) | pin |
| `markdown-link-check` | 3.15.0 | 3.15.0 ([npm](https://registry.npmjs.org/markdown-link-check/latest)) | pin |
| `remark-cli`, `remark-validate-links` | 12.0.1, 13.1.0 | the same ([npm](https://registry.npmjs.org/remark-cli/latest), [npm](https://registry.npmjs.org/remark-validate-links/latest)) | pin |
| `tools/docs-site/requirements.txt` (31 packages, `mkdocs` 1.6.1, `mkdocs-material` 9.7.7) | as listed | every package equals `info.version` of its [PyPI JSON](https://pypi.org/pypi/mkdocs-material/json) page on the read date | pin |
| Node.js (local; `docs-lint.sh` needs 20 or newer) | 22.22.0 | LTS 24.22.0; 26 becomes LTS on 2026-10-28 ([nodejs.org](https://nodejs.org/dist/index.json), [endoflife.date](https://endoflife.date/api/nodejs.json)) | later: 24 LTS at the next tooling change; the CI Node version is the runner image's, not pinned here (**not verified**) |
| Python in CI (`setup-python`) | 3.13 | 3.14.8 stable, 3.15.0 just released ([endoflife.date](https://endoflife.date/api/python.json)) | pin; 3.13.16 is current for its line |

## CI (`.github/workflows/*.yml`)

| Pin | Current | Latest release tag (read with `git ls-remote --tags`) | Recommendation |
|---|---|---|---|
| `actions/checkout` | v4 | v7.0.1 ([repo](https://github.com/actions/checkout)) | upgrade later, one PR for all five actions |
| `actions/setup-java` | v4 | v6.1.0 ([repo](https://github.com/actions/setup-java)) | upgrade later |
| `actions/setup-python` | v5 | v7.0.0 ([repo](https://github.com/actions/setup-python)) | upgrade later |
| `actions/upload-artifact` | v4 | v7.0.2 ([repo](https://github.com/actions/upload-artifact)) | upgrade later |
| `gradle/actions` (`setup-gradle`) | v4 | v6.4.0 ([repo](https://github.com/gradle/actions)) | upgrade later |
| Java for CI (`temurin`) | 21 | LTS 25.0.4.1; 27 is not LTS ([endoflife.date](https://endoflife.date/api/temurin.json)) | pin; AGP 9.4 needs JDK 17 or newer |

The action majors are three to four behind. Their release notes were **not** read, so breaking changes are
**not verified**; the old majors keep working until GitHub stops supporting the Node version they run on, which was not
checked either. Changing them is low value for the demo and is best done in one PR with a CI run as the test.

## Does anything newer fix the lint crash?

The crash is `Unexpected failure during lint analysis` with a `KotlinIllegalArgumentExceptionWithAttachments` from
`LLFirModuleLazyDeclarationResolver.handleExceptionFromResolve` (see
[`../conventions.md`](../conventions.md), "When lint itself crashes").

- **AGP.** 9.4.1 is the latest stable. The [AGP 9.4 release notes](https://developer.android.com/build/releases/gradle-plugin)
  (page last updated 2026-10-16) list no lint crash fix and no 9.4.x patch beyond 9.4.1. The
  [AGP 9.5 preview notes](https://developer.android.com/build/releases/agp-preview) cover alpha01 to alpha10; the only
  lint item is a SARIF JSON fix in alpha02. No Analysis API or Kotlin bump for lint is mentioned.
- **Kotlin.** 2.4.21 is the latest stable; the next is 2.5.0-Beta1. No stable release newer than ours exists.
- **Same report elsewhere.** [bluecard issue 227](https://github.com/bryancassell/bluecard/issues/227) (AGP 9.4.1, Kotlin
  2.4.20) shows the same stack; no fix is named there. A web search found no Google issue tracker entry or Kotlin
  YouTrack issue for this exact stack.

**Conclusion:** nothing newer than what we pin fixes it. Upgrading would not help today; the avoidance is a clean tree
per Gradle run (lane V's per-worktree runner).

## Other bugs we hit

None of the bugs we hit in this repository (the DataStore reopen race, the lint crash, the ktlint trailing comma
difference between machines) has a newer stable release to try, because we are already on the latest stable of every
tool involved.
