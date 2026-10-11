# Bootstrap test plan (S0)

Run: `scripts/test/test-scripts.sh` (also the `Gates` CI workflow). Pass criterion: every case PASS.

| Id | Case |
|---|---|
| TC-B-001 | merged gate passes |
| TC-B-002 | open gate blocks |
| TC-B-003 | one open of many blocks |
| TC-B-004 | unknown gate is an error |
| TC-B-005 | no args is an error |
| TC-B-006 | output names blocking gate and owner |
| TC-B-006 | output names blocking gate and owner |
| TC-B-007 | baseline fails when an id has no test |
| TC-B-008 | baseline passes when all ids have tests |
| TC-B-009 | final fails without report |
| TC-B-010 | final passes with all PASS |
| TC-B-011 | final fails on a FAIL row |
| TC-B-012 | final fails on @Ignore |
| TC-B-013 | met requires and own produces pass |
| TC-B-014 | unmet requires fail |
| TC-B-015 | flipping a gate not produced fails |
| TC-B-016 | no flips and no produces pass |
| TC-B-017 | missing Requires line fails |
| TC-B-018 | missing base file: gate merged in head must be produced |
| TC-B-019 | missing base file: unproduced merged gate fails |
