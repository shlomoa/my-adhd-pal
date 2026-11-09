## Pulse-sensor POC: done

All three goals achieved on a Galaxy Watch6 Classic and a Galaxy A55 (final run `poc-results/20261109-174743`):

1. **Pulse data within 0.5 s of recording**: achieved with the wake-up heart-rate sensor (p95 12 ms screen on, 20 ms screen off). Health Services (`MeasureClient`) is up to 41 s late when the display is dim or off.
2. **Control of power, sensor enabling and modes**: achieved (enable, disable, sensor power seen by the OS, wake lock, route selection). Batching mode has no effect on this sensor.
3. **Live pulse to the phone over Bluetooth, sub-second**: achieved over direct Bluetooth (p95 44 ms screen on, 66 ms screen off).

Also: a live pulse indicator and graph in the phone app, and the watch's raw heart sensor is readable by an ordinary app (24.8 Hz), from which beat intervals and HRV can be computed (indicative, not validated).

Limits: one run, one wrist, about 40 s per phase; the dim (always-on) display state was not reached.

Details: `docs/poc/summary.md`. The project before the POC is the tag `v0.1.0-pre-poc` and the `archive/` folder.
