#!/usr/bin/env bash
# Shared by the probe test suites (sourced). make_repo builds a throwaway git repository $tmp/repo that holds a COPY of
# scripts/probe, with an origin that cannot be reached, so a run script that publishes its result file can never push to the real
# origin. Sets $tmp, $tmp/state (the simulated adb), $tmp/out (result folder). Needs $here (the scripts/probe folder).
export PROBE_STABLE_SEC="${PROBE_STABLE_SEC:-1}"   # the pre-flight watches adb for 30 s by default; the tests do not wait that long
make_repo() {
  tmp="$(mktemp -d)"; mkdir -p "$tmp/state" "$tmp/out"
  git init -q -b main "$tmp/repo"
  mkdir -p "$tmp/repo/scripts" "$tmp/repo/docs"
  cp -r "$here" "$tmp/repo/scripts/probe"; echo base > "$tmp/repo/docs/base.md"
  git -C "$tmp/repo" add -A; git -C "$tmp/repo" -c user.name=probe-test -c user.email=probe-test@invalid commit -q -m base
  git -C "$tmp/repo" remote add origin "$tmp/no-origin.git"
}
