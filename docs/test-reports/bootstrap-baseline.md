# Bootstrap baseline (red)

Command: `scripts/test/test-scripts.sh` run before the scripts existed (exit 127 = script not found), recorded before implementation. TC-B-013..017 were added with check-pr-gates.sh and were red (127) the same way. TC-B-019 was added after the first CI run and was red (got exit 0, expected 4) before the fix: a base without `gates.json` passed only by accident.

```
FAIL TC-B-001 merged gate passes (expected exit 0, got 127)
FAIL TC-B-002 open gate blocks (expected exit 3, got 127)
FAIL TC-B-003 one open of many blocks (expected exit 3, got 127)
FAIL TC-B-004 unknown gate is an error (expected exit 2, got 127)
FAIL TC-B-005 no args is an error (expected exit 2, got 127)
FAIL TC-B-006 output names blocking gate and owner (expected exit 0, got 1)
FAIL TC-B-007 baseline fails when an id has no test (expected exit 1, got 127)
FAIL TC-B-008 baseline passes when all ids have tests (expected exit 0, got 127)
FAIL TC-B-009 final fails without report (expected exit 1, got 127)
FAIL TC-B-010 final passes with all PASS (expected exit 0, got 127)
FAIL TC-B-011 final fails on a FAIL row (expected exit 1, got 127)
FAIL TC-B-012 final fails on @Ignore (expected exit 1, got 127)
```
