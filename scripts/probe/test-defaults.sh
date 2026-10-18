#!/usr/bin/env bash
# Tests the default durations of the probe run scripts (the user's budget, 2026-10-18: no script over 10 minutes of attended watch time,
# all scripts together at most 60 minutes). E2 (the burst period of about 10 minutes) is measured and is not repeated. The durations stay
# parameters (-PhaseSec, -BlockSec, ...); this test reads the defaults from the param blocks. Needs PowerShell 7 as `pwsh` (or set PWSH).
# Run: scripts/probe/test-defaults.sh
set -u
here="$(cd "$(dirname "$0")" && pwd)"
PWSH="${PWSH:-pwsh}"
fail=0
check() { if [ "$2" = "$3" ]; then echo "PASS $1"; else echo "FAIL $1 (expected $2, got $3)"; fail=1; fi; }
# def SCRIPT PARAM -> the default value
def() {
  SCRIPT_FILE="$here/$1" PARAM="$2" "$PWSH" -NoProfile -Command '
    $ast = [System.Management.Automation.Language.Parser]::ParseFile($env:SCRIPT_FILE, [ref]$null, [ref]$null)
    $p = $ast.ParamBlock.Parameters | Where-Object { $_.Name.VariablePath.UserPath -eq $env:PARAM }
    $p.DefaultValue.Extent.Text'
}
check "TC-W-227 E1 phase default is 120 s" 120 "$(def run-e1.ps1 PhaseSec)"
check "TC-W-227 screen phase default is 120 s" 120 "$(def run-screen.ps1 PhaseSec)"
check "TC-W-227 E3 block default is 120 s (3 blocks worn)" 120 "$(def run-e3.ps1 BlockSec)"
check "TC-W-227 E3 Sleep mode / Power saving default is 90 s" 90 "$(def run-e3.ps1 StateSec)"
check "TC-W-227 E4 run default is 60 s" 60 "$(def run-e4.ps1 RunSec)"
check "TC-W-227 E6 alarm wait default is 15 s" 15 "$(def run-e6.ps1 AlarmWaitSec)"
e1=$(( 3 * $(def run-e1.ps1 PhaseSec) )); sc=$(( 2 * $(def run-screen.ps1 PhaseSec) ))
e3=$(( 3 * $(def run-e3.ps1 BlockSec) + 2 * $(def run-e3.ps1 StateSec) )); e4=$(( 2 * $(def run-e4.ps1 RunSec) ))
[ "$e1" -le 600 ] && [ "$sc" -le 600 ] && [ "$e3" -le 600 ] && [ "$e4" -le 600 ]; check "TC-W-227 no script has more than 10 minutes of waiting" 0 $?
[ $((e1 + sc + e3 + e4)) -le 1800 ]; check "TC-W-227 the waiting of all scripts together is at most 30 minutes (the rest is questions and set-up)" 0 $?
exit $fail
