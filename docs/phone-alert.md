# Phone alert for biometric alerts: design note

Design for the Android lane's part of the biometric decisions
([issue #55](https://github.com/shlomoa/my-adhd-pal/issues/55), decision document
[PR #147](https://github.com/shlomoa/my-adhd-pal/pull/147), section 4a): when the watch sends `/alert/<id>`, the **phone**
also vibrates hard and shows a full-screen alert, and the phone asks for the watch's heart-rate permission and holds the
offset setting. This note is written before any code. Status words: **Verified** (an official page read for this note),
**From the decision document** (its sources, not re-read), **Unverified** (general knowledge or untested; must be checked on
the Galaxy A55 5G before anyone relies on it).

## 1. Where it runs

- The watch's alert arrives like any other item: `PhoneLinkListenerService` wakes the process (even with the app closed),
  and the existing event processing stores it as a history row. **Nothing under `data/` or `link/` changes.**
- A singleton `AlertRaiser` is started from `MyAdhdPalApp.onCreate`. It collects `HistoryRepository.history(ALERT)` and
  raises an alert for every row it has not raised yet. Because it reads the stored rows, an alert stored just before the
  process died is still raised at the next start, and an alert delivered late (items queue while the watch is away)
  is shown with its original time.
- "Not raised yet" is a watermark (the `at` and id of the last raised alert) in the Android lane's own small DataStore file,
  outside `data/`. A history row is never edited.
- One alert at a time: a new one replaces the one showing (same notification id).

## 2. What the alert is

| Part | Design | Status |
|---|---|---|
| Notification | Own channel `alerts`, importance high, category alarm, with a full-screen intent to `AlertActivity` | Channel and category: standard API. Whether category alarm lets it through Do Not Disturb: **Unverified** |
| Full screen | `AlertActivity` with show-when-locked and turn-screen-on, a large word and number ("Heart rate 138"), one big button "I'm OK" that stops the vibration and sound on the phone and ends the episode on the watch (section 2a) | Mechanism **Verified**: see section 3 |
| Vibration | Waveform at amplitude 255 when `Vibrator.hasAmplitudeControl()`, else on/off timing; alarm usage attributes. Level 1: about 15 s with short pauses. Level 2: about 30 s, no pause over 0.3 s | Amplitude range and `hasAmplitudeControl`: general API knowledge, **Unverified** here (the reference page did not load). Numbers are from the decision document |
| Flashing | The screen alternates two high-contrast colours **at most 3 times per second** (the usual photosensitivity limit); a setting turns flashing off | **Decided (user, 2026-10-15)**; the limit itself is standard accessibility guidance, **Unverified** here |
| Sound | **On by default**; a setting turns it off. Played with alarm audio attributes at the alarm volume | **Decided (user, 2026-10-15)** |
| Level | `Alert.level` (1 or 2) once protocol 1.2 has it; until then every alert is level 1. The phone only reacts to what arrives: **no repeat count and no timer on the phone** | Protocol change owed by the Shared lane; **Decided (user, 2026-10-15)** |

## 2a. "I'm OK" ends the episode on the watch too (decided)

**Decided (user, 2026-10-15):** tapping "I'm OK" on the phone also ends the episode on the watch. That needs a new item from
the phone to the watch (protocol 1.2, Shared lane: it names the alert it answers, so it is idempotent by id like the
others; the watch handling is the Watch lane's). The phone side:

- "I'm OK" stops the phone's vibration and sound at once, whatever the link does, and marks that alert as handled in the
  watermark so it is not raised again.
- It then hands the item to the data source (a new `WatchLinkDataSource` method, `data/`, Shared lane). If the watch is away
  the item is held by the Data Layer and delivered on reconnect; the screen says "sent" only when the data source accepted it.
- Until protocol 1.2 exists the button does the first point only, and the work log says so.

The pure part (level to vibration timings and amplitudes, the colour schedule, the text) is a plain Kotlin function and
unit-tested. Android calls are a thin layer.

## 3. What can suppress it

| Cause | Effect | Status |
|---|---|---|
| Notification permission refused (Android 13 and later) | No notification at all, so no full-screen alert | **Verified**: `POST_NOTIFICATIONS` is a runtime permission from Android 13 ([notifications guide](https://developer.android.com/develop/ui/views/notifications/time-sensitive)) |
| Full-screen-intent permission missing | The alert shows as a heads-up notification for 60 s instead of taking the screen | **Verified** ([AOSP](https://source.android.com/docs/core/permissions/fsi-limits)): on Android 14 the permission is granted by default only to apps with calling or alarm functionality, Google Play revokes it for others, and the user grants it with `ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT` (check with `NotificationManager.canUseFullScreenIntent()`) |
| Phone in active use | Heads-up notification instead of the full screen | **Verified** (same notifications guide): "the system UI displays a heads-up notification instead while the user actively uses the device" |
| Do Not Disturb, silent or vibrate mode, Samsung modes | May silence sound or vibration; alarms may pass | **From the decision document** (Samsung's pages): alarms pass DND and Bedtime mode. Whether this alert counts as an alarm: **Unverified**. Ordinary apps cannot force vibration past the interruption policy |
| Notification channel turned off by the user in Settings | No alert | Standard behaviour; the onboarding checks it |
| Battery restrictions on the app | The process may not wake in time | The hardware spike (G-DEC) passed with default settings; **Unverified** for a long idle period |
| Watch away from the phone | Alert arrives late | By design; shown with its original time |

The onboarding (section 4) checks what it can (notification permission, channel, full-screen-intent permission) and says
plainly what it cannot (DND, silent mode). The manual test on the devices tries each of them once and records the result.

## 4. Onboarding screen on the phone

1. **Notifications**: request `POST_NOTIFICATIONS`.
2. **Full-screen alerts**: if `canUseFullScreenIntent()` is false, a button opens `ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT`.
3. **Heart rate on the watch**: a button calls `RemoteActivityHelper.startRemoteActivity` with `adhdpal://permission/heart-rate`
   (the intent needs the view action, a data URI and the browsable category; unavailable while the devices are not connected;
   **From the decision document**). The permission prompt appears **on the watch** and is answered there. The phone shows the
   result when the watch reports it.
4. Each step shows done, not done, or "needs the watch nearby".

## 5. The offset setting

The offset (20 to 50 bpm, default 30) is a field on the settings screen (step 4.3) once protocol 1.2 carries it and
`SettingsRepository` validates it. Step 4.3 is built first without it; adding the field is a small follow-up. The screen also
shows the resulting threshold once the watch reports its baseline.

## 6. Steps, in order, each a small pull request with tests first

| Step | Work | Needs from others |
|---|---|---|
| A-alert-1 | Pure alert plan (level to vibration, sound and colours), `AlertRaiser` and the watermark, channel, `AlertActivity`; "I'm OK" stops the phone's own alert; tests with the in-memory history and a fake notifier; manual on the A55 | Nothing for level 1. `Alert.level`: Shared lane (protocol 1.2) |
| A-alert-1b | "I'm OK" also sends the episode-end item to the watch | The item and the `WatchLinkDataSource` method: Shared lane (protocol 1.2); handling on the watch: Watch lane |
| A-alert-2 | Onboarding screen (sections 4.1 and 4.2 first, then 4.3) | `androidx.wear:wear-remote-interactions` in `phone/build.gradle.kts` (Shared lane); the watch's permission activity with the `adhdpal://permission/heart-rate` filter and a granted-state report (Watch and Shared lanes) |
| A-alert-3 | Offset field on the settings screen | `offsetBpm` in the schedule and settings (Shared lane, protocol 1.2) |
| 7.x | Play declaration for the full-screen intent | Part of the release checklist |

## 7. Questions for the user: answered 2026-10-15

1. Phone alert sound: **on by default**, a setting turns it off (changed from the proposal).
2. Flashing: at most 3 per second, with an off switch: **yes**.
3. "I'm OK" on the phone also ends the episode on the watch: **yes** (section 2a; protocol 1.2, Shared lane; handling on the
   watch, Watch lane).
4. The cap of 6 level-2 repeats: **the phone only reacts to what arrives**, with no repeat count on the phone. The cap, if it
   stays, is the watch's.
