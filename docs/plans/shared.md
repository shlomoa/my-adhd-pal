# Lane S: Shared and data level

Overview, gates, stop rule and ownership: [README](README.md).


| Step | Work | Validation (test first) | Requires | Produces |
|---|---|---|---|---|
| **S0** | Bootstrap, by the orchestrator, on this session's branch before launching threads: commit the three plan docs, `gates.json`, `require-gate.sh`, `check-test-cases.sh`, the `gates` CI job | tests for both scripts written and shown red, then green | none | G-BOOT |
| **S1a** | Catalogue `docs/test-plans/shared.md`: tooling negative fixtures (unformatted Kotlin, detekt violation, markdown/link error, applicationId mismatch, debug class in release), protocol cases (envelope round-trip, 100 KB payload assertion, `HELLO`/`HELLO_ACK`, incompatible major -> `ERROR(UNSUPPORTED_VERSION)`, idempotent ack by id, schedule LWW by version, retry backoff, request and link state-machine transitions), data cases (DAO, DataStore, repository + fake link) | docs only | G-BOOT | none |
| **S1b** | Research note `docs/research/tooling.md`: AGP 9.4/Kotlin 2.4 compatibility and latest versions for Spotless/ktlint, detekt, markdownlint-cli2, link check, Hilt/KSP (decides Hilt vs manual DI for A), Room, DataStore, kotlinx.serialization; Data Layer persistence/readability/deletion semantics | n/a (research; cites sources) | G-BOOT | none |
| **S2** | Tooling tests-first: negative-fixture scripts under `scripts/verify/` run, recorded red. Then restructure: `:app` -> `:wear`, add `:protocol`, `:phone` skeletons, version catalog, `./gradlew check`, CI update | baseline `shared-baseline.md` (tooling part red) -> fixtures now fail the build as intended; `./gradlew check assembleDebug` green | S1a, S1b | **G0** |
| **S3** | Guardrails: shared `applicationId`/signing check and release-has-no-debug check | each check demonstrated failing on a bad fixture, then passing | G0 | **G4** |
| **S4** | `docs/protocol.md` + compile-only `:protocol` declarations + `WatchLinkDataSource` interface; transport = DataItems only | markdownlint + link check green; module compiles; protocol-level test cases (S1a) written against the declarations and recorded red | G0 | **G1** |
| **S5** | `:protocol` implementation (kotlinx.serialization codec isolated, handshake, idempotency, LWW, backoff, state machines), `FakeTransport`, `FakeWatchLinkDataSource` | `./gradlew :protocol:test` red -> green (JVM only) | G1 | **G2** |
| **S6** | Phone data layer: tests first against the fake, then Room/DataStore/repositories and real Data Layer `WatchLinkDataSource` (`CapabilityClient` listener, `getCapability(FILTER_REACHABLE)`, periodic re-read) | `:phone:testDebugUnitTest` data cases red -> green | G2, G4 | **G3** |
| **S7 (V-last)** | Run everything on merged `main`: `./gradlew check assembleDebug assembleRelease`, markdownlint, link check, all `TC-S-*` green, final report; confirm `:wear` and `:phone` still compile against the final contract | `shared-final.md` via `check-test-cases.sh shared final` | G3, G5, G6 | none |

