# POC goals and status

Goals:

1. Direct access to pulse sensor data, no more than 0.5 s from recording.
2. Control of power, sensor enabling and modes.
3. Live pulse data over Bluetooth to an Android app, sub-second delay.

Status: **done**. All three goals achieved on the real watch and phone: see [the summary](summary.md).

- [Setup checklist](setup.md)
- [Summary: the POC is done](summary.md)
- [Requirements, tests and pass rules](requirements.md)
- [Research, findings and issues](findings.md)
- [Watch commands and how latency is measured](commands.md)

Run with one command: `scripts/run-poc.sh` (about 7 minutes; `--smoke` for a 2-minute first check). Prepare the phone first: unlocked, screen on.
