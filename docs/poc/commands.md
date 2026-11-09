# Watch commands

The watch app has no screen. Commands are sent from a computer with `adb` (the test script does this for you).
Every command starts the invisible `ControlActivity`, because a health foreground service may only start while the app
has a visible activity.

```bash
adb -s <watch> shell am start -n com.shlomoa.myadhdpal.poc/com.shlomoa.myadhdpal.poc.wear.ControlActivity \
  --es cmd start --es route r2 --es label my-run
```

| Extra | Values | Meaning |
|---|---|---|
| `cmd` | `start`, `stop`, `wakelock`, `status` | the command |
| `route` | `r1` (Health Services `MeasureClient`), `r2` (wake-up `TYPE_HEART_RATE` sensor) | sensor route for `start` |
| `label` | text without commas | names the result files of this run |
| `rate_us` | integer | sensor sampling period in microseconds (`r2`), default 0 (fastest) |
| `batch_us` | integer | maximum report latency in microseconds (`r2`), default 0 (deliver at once) |
| `hold` | `true`, `false` | for `start`: hold a partial wake lock; for `wakelock`: acquire or release it |

Files written in the watch app's private storage (read with `adb shell run-as com.shlomoa.myadhdpal.poc cat files/<name>`):

- `status.txt`: running state, counts, wake lock, offset estimate, last error; rewritten every second.
- `watch-<label>.csv`: one line per reading (`label,route,seq,bpm,accuracy,recordedNanos,arrivalNanos`).
- `watch-summary-<label>.txt`: written at `stop`; recorded-to-watch-app latency and gaps between readings.

The phone writes `phone-<label>.csv` and, after the broadcast `com.shlomoa.myadhdpal.poc.SUMMARIZE`,
`phone-summary-<label>.txt` with the end-to-end latency.

## How latency is measured

- Recorded time: the sensor event timestamp (`r2`) or the data point's time since boot (`r1`), both read as the
  watch's elapsed-realtime clock. That the Health Services value is on the same clock is assumed, not verified.
- Watch app arrival: elapsed realtime when the callback runs.
- Phone arrival: the phone's elapsed realtime when the message callback runs.
- Clock offset: the watch pings the phone every second; the phone answers with its clock. The offset (phone minus
  watch) is taken from the exchange with the shortest round trip, assuming equal travel time each way.
