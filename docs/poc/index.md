# POC goals and status

Goals:

1. Direct access to pulse sensor data, no more than 0.5 s from recording.
2. Control of power, sensor enabling and modes.
3. Live pulse data over Bluetooth to an Android app, sub-second delay.

Status: built and tested against simulated devices; waiting for a run on the real watch and phone. No result yet.

- [Setup checklist](setup.md)
- [Requirements, tests and pass rules](requirements.md)
- [Watch commands and how latency is measured](commands.md)

Run with one command: `scripts/run-poc.sh` (about 7 minutes; `--smoke` for a 2-minute first check). Prepare the phone first: unlocked, screen on.
