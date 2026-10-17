# Validation lane test-case catalogue (V)

Lane V owns the post-merge workflow and its scripts ([`../plans/validation.md`](../plans/validation.md)). Case ids are
`TC-V-nnn`. The cases of issue #190 keep the ids `TC-S-170` to `TC-S-175` that lane S wrote first; they are catalogued in
[`shared.md`](shared.md) and run by `scripts/test/test-post-merge.sh`, which runs on every pull request.

| Id | Kind | Case | Pass criteria |
|---|---|---|---|
| TC-V-001 | script | Lane set up | `docs/plans/validation.md`, this catalogue, `docs/work-log/validation.md`, gate `G-VAL` (owner V, requires G-BOOT) and the README ownership row exist (`test-post-merge.sh`) |
| TC-V-002 | script | Lane label and log | `scripts/check-issues.sh` accepts `lane:V` and `scripts/check-work-log.sh` accepts the validation work log (`test-conventions.sh`) |
| TC-V-003 | script | Main-red procedure written down | the validation plan and `docs/conventions.md` describe one sub-agent per `main-red` issue and archiving it when resolved (`test-post-merge.sh`) |
