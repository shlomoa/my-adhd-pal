# ADHD Pal: pulse-sensor POC

Proof of concept on a Galaxy Watch6 Classic and a Galaxy A55 phone: direct pulse-sensor access with at most 0.5 s
delay, control of sensor power, enabling and modes, and live delivery to the phone over Bluetooth with sub-second delay.

- Documentation: [docs/index.md](docs/index.md)
- Modules: `:wear` (watch app), `:phone` (phone receiver), `:core` (shared pure Kotlin).
- The previous implementation is in [`archive/`](archive/) (copy its contents back to the repository root to restore
  it) and at the tag `v0.1.0-pre-poc`.
