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

Researched 2026-10-11 and updated 2026-10-14 (step 5.1). Sources:
[Get started with tiles](https://developer.android.com/training/wearables/tiles/get_started),
[Show periodic updates](https://developer.android.com/training/wearables/tiles/update),
[Interactions](https://developer.android.com/training/wearables/tiles/interactions),
[Tiles versioning](https://developer.android.com/training/wearables/tiles/versioning), and the Google Maven index
(`dl.google.com/dl/android/maven2/.../maven-metadata.xml`, read 2026-10-14).

Versions (Google Maven, latest release on 2026-10-14; this settles the earlier disagreement between pages, which was the old
1.5 / 1.3 pair in a search snippet):

| Artifact | Version | Use |
|---|---|---|
| `androidx.wear.tiles:tiles` | 1.6.2 | `Material3TileService`, `TileService` |
| `androidx.wear.protolayout:protolayout` | 1.4.2 | layout builders |
| `androidx.wear.protolayout:protolayout-material3` | 1.4.2 | Material 3 components (`materialScope`, `primaryLayout`, buttons, edge button) |
| `androidx.wear.protolayout:protolayout-expression` | 1.4.2 | dynamic data |
| `androidx.wear.tiles:tiles-testing` | 1.6.2 | `testImplementation`, fetch a tile from a provider in tests |
| `androidx.wear.tiles:tiles-renderer` | 1.6.2 | `debugImplementation`, preview in the app |

The getting-started page lists `protolayout-material` (Material 2.5) and does not list `protolayout-material3` in its dependency
block; the artifact exists on Google Maven, so 5.2 uses `protolayout-material3` and not `protolayout-material` (the docs
advise against mixing the two in one app). This is **unverified against a build** until 5.2 compiles it with AGP 9.4 and Kotlin 2.4.

Findings:

- **Service.** For Material 3 tiles (tiles 1.6.0 and later) extend `Material3TileService` and implement
  `MaterialScope.tileResponse(requestParams)`; resources are registered automatically and `onTileResourcesRequest()` is final.
  Layouts are built inside one `materialScope`, with `primaryLayout` (title, main and bottom slots; the bottom slot is
  usually an edge button) and components such as `text`, `button`, `card`. Do not mix Material 2.5 and 3 tiles in one app.
- **Manifest.** A `<service>` with `android:permission="com.google.android.wearable.permission.BIND_TILE_PROVIDER"`,
  `android:exported="true"`, the `androidx.wear.tiles.action.BIND_TILE_PROVIDER` intent filter, a label, description, icon
  and an `androidx.wear.tiles.PREVIEW` meta-data (a 400 x 400 image).
- **Updates (5.3).** Three mechanisms, all from the pages above: (1) a freshness interval set on the `Tile`
  (`setFreshnessIntervalMillis`), after which the system calls `onTileRequest()`; with none set the system never refreshes; (2)
  `TileService.getUpdater(context).requestUpdate(MyTileService::class.java)` from app code on an external event; (3) a
  timeline of entries with validity periods, where the entry with the shortest time left is shown, so a known future change
  needs no push. No rate limit on `requestUpdate` is documented (**unverified** that none exists).
- **Taps.** An element responds to a tap through `clickable(...)`, which can carry a `launchAction` (opens an exported
  activity, with extras but no custom intent flags) or a `loadAction` (the system calls `onTileRequest()` again; the tapped
  element is identified by `requestParams.currentState.lastClickableId`, and a `loadAction` can carry state). The page says a
  tap area is at least 48 dp. A `loadAction` re-invokes the service, so logic such as an acknowledgement can run in
  `onTileRequest()`, but the docs do not say how long that work may run or whether it continues after the tile is returned
  (**unverified**; the "Lifecycle of a tile" page was not read).
- **Tests.** The tiles-testing artifact exists (1.6.2); its API was not documented in the pages read. 5.2 keeps the tile
  content in a plain Kotlin function (state in, layout description out) so that it is testable on the JVM, and uses
  tiles-testing only if its API is confirmed when it is added.

Recommendation for 5.2 and 5.3: `Material3TileService`; the tile shows the current reminder, a primary action (Done) and the
link state with last sync. For the Done tap prefer a `launchAction` to a tiny exported activity that calls the existing
`ReminderActionHandler` and finishes, because that path is documented to run code; use `loadAction` only if the lifecycle page
confirms background work is allowed. A timeline with one entry per upcoming reminder (validity until its time) and a fallback
entry for the empty state, plus `requestUpdate` when a schedule is applied, an acknowledgement is sent and the link state
changes.

Open questions: (a) whether work in `onTileRequest()` may outlive the response (decides `loadAction` vs `launchAction`);
(b) the `tiles-testing` API; (c) a clean build of `protolayout-material3` 1.4.2 under AGP 9.4 and Kotlin 2.4. All three are
settled by the first compile in 5.2; a failure of (c) is a "stop and ask" case.

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
- Google ([monitor in the background](https://developer.android.com/health-and-fitness/health-services/monitor-background)), verbatim:
  "When you receive data in the background, it is delivered in batches. The callback receives data at a slightly faster rate, but only while
  the app is running and the callback is successfully notified." and "Use the timestamps included within these objects rather than the time
  they were received by your app to determine the correct ordering of events." The page gives no interval.
- Measured on the Galaxy Watch6 Classic: readings delivered together, up to about 600 at a time spanning about 10 minutes
  (`docs/plans/biometrics.md` section 6).
- Order readings by their own timestamps, converted with the boot instant. Registrations do **not** survive a reboot: re-register from a
  `BOOT_COMPLETED` receiver through WorkManager, because Health Services may take 10 s or more to answer at startup.
- The evaluator (6.5) takes readings in any order and evaluates them in timestamp order.

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
