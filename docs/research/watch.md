# Watch research notes (W1b)

Research for [`../plan.md`](../plan.md) steps 3.4, 5.1 and 6.1. No code. Researched 2026-10-11. Each claim cites a
source; anything not confirmed by a primary source is marked **unverified**, and **open question** items need the
user or the real watch. Pages were summarised through a fetch tool, so exact version numbers and wording must be
re-read at implementation time.

## 1. Reminder mechanism and permissions (step 3.4)

Sources: [Schedule alarms](https://developer.android.com/training/scheduling/alarms),
[Exact alarms behaviour change, Android 14](https://developer.android.com/about/versions/14/changes/schedule-exact-alarms).
Neither page has Wear OS specific content.

Findings:

- Alarms do not fire in Doze and are deferred until Doze ends. `setExactAndAllowWhileIdle()` fires "at a nearly
  precise time ... even if battery-saving measures are in effect". `setAlarmClock()` is never adjusted, and the system
  leaves low-power modes to deliver it, at a battery cost.
- Exact alarms need `SCHEDULE_EXACT_ALARM` (user-granted, not pre-granted for fresh installs targeting API 33+, and
  revocation cancels all future exact alarms; check with `canScheduleExactAlarms()`) or `USE_EXACT_ALARM` (granted
  automatically, not revocable, limited to a user-facing function such as alarm clocks and calendars, subject to a
  Google Play policy).
- All alarms are cancelled at shutdown. Restoring them needs `RECEIVE_BOOT_COMPLETED` and a receiver for
  `ACTION_BOOT_COMPLETED` that re-sets them from stored data. This works only after the user has launched the app once.
- Exact alarms are exempt from foreground-service launch restrictions.

Recommendation for 3.4 (pending the open questions): persist the schedule (3.3), compute the single next reminder
with the pure schedule logic from `:protocol` (1.6), and set one exact alarm for it with `setExactAndAllowWhileIdle()`
(or `setAlarmClock()` if the 1-minute target is missed in testing). When it fires, re-compute and set the next one. A
boot receiver and every schedule update call the same re-arm function, so there is one place that sets alarms. One
alarm at a time keeps the logic testable with a fixed clock.

**Decision (coordinator, 2026-10-14, reversible, one manifest line; the user has not answered):** declare `USE_EXACT_ALARM`
(granted with the install from API 33, no settings step on the watch; the Galaxy Watch6 Classic runs Wear OS 5, API 34) and
`SCHEDULE_EXACT_ALARM` with `android:maxSdkVersion="32"` for API 31 and 32. When `canScheduleExactAlarms()` is false the
scheduler sets an inexact alarm and the home screen shows a warning. The Play policy limits `USE_EXACT_ALARM` to alarm and
calendar-type apps, so the choice is revisited at milestone 7 (Play readiness) and the user is told then; if Play declines it, the
fallback is `SCHEDULE_EXACT_ALARM` with a settings prompt.

Open questions:

1. **Which permission on Wear OS?** (decided provisionally, see above) Whether `USE_EXACT_ALARM` is acceptable for a reminder app under the Play policy,
   or `SCHEDULE_EXACT_ALARM` with a settings prompt on the watch, is **unverified**: no Wear OS specific documentation
   was found. Decide in 3.4 after checking the Play policy text and testing on the watch (user, with H2).
2. **Samsung One UI Watch battery management** may restrict background apps beyond stock Wear OS: **unverified**,
   covered by the 24-hour run (TC-W-025).
3. Whether Play requires a declaration for the chosen exact-alarm permission is part of 7.x.

## 2. Tile API (step 5.1)

Sources: [Get started with tiles](https://developer.android.com/training/wearables/tiles/get_started),
[Show periodic updates](https://developer.android.com/training/wearables/tiles/update),
[Tiles versioning](https://developer.android.com/training/wearables/tiles/versioning).

Findings:

- Current artifacts per the getting-started page: `androidx.wear.tiles:tiles:1.6.2` and
  `androidx.wear.protolayout:protolayout(-material, -expression):1.4.2`, with `tiles-renderer` (debug) and
  `tiles-testing` (test). A Material 3 artifact (`protolayout-material3`) holds the M3 components. The versioning page
  seen in a search snippet listed older 1.5 / 1.3 numbers, so the exact versions must be taken from the Google Maven
  index at 3.1/5.2 time.
- For Material 3 tiles the docs say to extend `Material3TileService` (tiles 1.6.0+) and implement
  `MaterialScope.tileResponse(requestParams)`. Do not mix M2.5 and M3 tiles in one app. Layouts are built inside one
  `materialScope()`, using `primaryLayout` with title, main and bottom (edge button) slots.
- Manifest: a `<service>` with `android:permission="com.google.android.wearable.permission.BIND_TILE_PROVIDER"`, the
  `androidx.wear.tiles.action.BIND_TILE_PROVIDER` intent filter, `exported="true"`, label, description, icon and a
  `androidx.wear.tiles.PREVIEW` meta-data.
- Updates: a freshness interval (`setFreshnessIntervalMillis`) makes the system call `onTileRequest()` shortly after it
  elapses (none set means no refresh); `TileService.getUpdater(context).requestUpdate(MyTileService::class.java)` forces
  a refresh from app code; timeline entries with validity periods switch layout at known times without a new push. No
  rate limit is documented on those pages (**unverified** that none exists).

Recommendation for 5.2 and 5.3: `Material3TileService`; one timeline entry per upcoming reminder using validity
periods, a fallback entry for the empty state, and `requestUpdate` on a schedule change, an acknowledgement and a
link-state change. Tile content logic goes in a plain Kotlin function so it can be unit tested, with `tiles-testing`
for the service. Tile layout and the click action for Done need a manual check (TC-W-066).

Open question: dependency versions are re-checked against Google Maven when 5.2 starts, because the notes above come
from pages that disagree with each other on version numbers.

## 3. Health Services passive monitoring (step 6.1)

Sources: [Monitor health data in the background](https://developer.android.com/health-and-fitness/health-services/monitor-background),
[Health Services compatibility](https://developer.android.com/health-and-fitness/health-services/compatibility).

Findings:

- Dependency: `androidx.health:health-services-client` (page showed 1.1.0; re-check the latest at 6.4).
- Check support first: `passiveMonitoringClient.getCapabilitiesAsync()` and `DataType.HEART_RATE_BPM in
  capabilities.supportedDataTypesPassiveMonitoring`. The compatibility page says heart rate is required for passive
  monitoring on all Wear OS devices but still recommends runtime checks.
- Receive data in the background with a `PassiveListenerService` registered by `setPassiveListenerServiceAsync(...)`
  with a `PassiveListenerConfig` (`setDataTypes(setOf(DataType.HEART_RATE_BPM))`). The service in the manifest carries
  `android:permission="com.google.android.wearable.healthservices.permission.PASSIVE_DATA_BINDING"`.
- Delivery is **batched** and the interval is chosen by the system; devices may report heart rate anywhere from every
  second to every ten minutes, and the app is not told. Order samples by their timestamps, converted with the boot
  instant. Registrations do **not** survive a reboot: re-register from a `BOOT_COMPLETED` receiver through WorkManager,
  because Health Services may take 10 s or more to answer at startup.
- Consequence for the evaluator (6.5): thresholds act on batched, possibly sparse samples, so alerts can be late by
  the batching interval. A "within N seconds" promise cannot be made. This must be in the 6.2 proposal.

## 4. API 36 permission names (step 6.1)

Source: [Health Services permissions](https://developer.android.com/health-and-fitness/health-services/permissions).

| Target / device | Foreground heart rate | Background (passive) |
|---|---|---|
| Wear OS 5.1 (API 35) and lower | `android.permission.BODY_SENSORS` | `BODY_SENSORS` plus `android.permission.BODY_SENSORS_BACKGROUND` (API 33 to 35) |
| Targeting Wear OS 6 (API 36) | `android.permission.health.READ_HEART_RATE` | additionally `android.permission.health.READ_HEALTH_DATA_IN_BACKGROUND` |

Migration per the page: keep `BODY_SENSORS` and `BODY_SENSORS_BACKGROUND` with `android:maxSdkVersion="35"`, add the
two `android.permission.health.*` permissions, and gate the runtime request on
`Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA`. The app targets 36, so both sets belong in the manifest. The
runtime request flow for the background permission (separate prompt or settings page) is **unverified** and is
confirmed in 6.3 on an API 34 and an API 36 emulator.

## 5. Heart rate support on the Galaxy Watch6 Classic (step 6.1)

Findings:

- The Google docs above say passive heart rate is mandatory on Wear OS devices. They say nothing about Samsung.
- A web search surfaced Samsung developer-forum replies saying third-party background heart rate on Galaxy Watch is
  restricted or sparse (timestamps discontinuous, or only at the interval set in Samsung Health), and one thread about a
  Watch 6 firmware quirk with the watch-face heart-rate tag. I could not open those pages (the fetch returned 404), so
  this is **unverified, secondhand** and might be outdated. No official Samsung statement was found.
- The Watch6 Classic ships with Wear OS 5 (One UI 6 Watch) per the plan, and may update to Wear OS 6; behaviour on each
  is unconfirmed.

**Decision needed from the user (open question):** biometric alerts may depend on Samsung's sparse background heart
rate, or may need the Samsung Health Sensor SDK, which requires a Samsung approval and is a different integration than
the one in `plan.md`. Do not choose in code. The first action in 6.1 follow-up is a capability and sampling-interval
probe on the real watch (H2): call `getCapabilitiesAsync()`, register a passive listener, record the arrival times of
samples with the screen on and off, and report. The result goes into the 6.2 proposal. If the probe shows readings only
every several minutes, 6.2 must say so and the user decides whether alerts are still worth building.
