# Biometric alerts: decisions (step 6.2, gate H1)

Decision document for step 6.2 ([issue #55](https://github.com/shlomoa/my-adhd-pal/issues/55)). The user's answers to the
proposal are in the body of the issue (section "Decisions"). **Confirmed by the user on 2026-10-16, with the last two items:**
"the two remaing items: "I'm OK" and the cap of 6 repeats is approved". Nothing in section 8 is open. Gate H1 flips in a
separate one-line PR. Research notes: [`../research/watch.md`](../research/watch.md) sections 3 to 5. Steps 6.3 to 6.8 in
[`watch.md`](watch.md) follow once H1 and G2b are merged.

**Latest findings and direction: [section 12](#12-findings-and-direction-so-far-2026-10-17).**

**Which route is used when - the user's words (2026-10-17), verbatim:**

> It is expected that when the app is executed it stays on, it continuously measures pulse and other biometrics (to be discussed) and transmits / receives urgent events immediately.
>
> The "batch" [...] is reserved to exceptions: disconnects, phone down, watch down, pairing down, network down, etc.
>
> "Stays on" means: I see it all the time, I can look at the time but it will remain the top app and top window.

| Route | Measured on the user's watch | Against the words above |
|---|---|---|
| Live `MeasureClient` while the app runs (section 11) | About 2 readings per second, screen on; dim, off and 30 minutes not measured yet (section 12.3) | The candidate; confirmed by the probe of section 11.5 |
| Passive background listener | A delivery about every 10 minutes (section 6, section 12.3) | Not "immediately" |

When the app is not running nothing is measured (D9).

Still to prove on the real watch with the live route: readings with the screen dim and off, 30 minutes, the battery cost, and what the user must do (section 11).
Sections 2 to 10 were written for the passive route; where they say "passive", they describe how the data was first measured, and the route above applies.

Every statement below is marked **Decided** (the user said so), **Verified** (a cited source says so), **Unverified** (not
confirmed, or secondhand) or **Proposal** (my suggestion, needs the user's yes). Nothing unverified is built before the probe
on the real watch (section 6).

## 1. Decisions

| # | Topic | Status | Decision |
|---|---|---|---|
| D1 | Metrics | Decided | Heart rate only. |
| D2 | Direction | Decided | Above the threshold only. The evaluator also supports below, unused in v1. |
| D3 | Threshold | Decided | Relative: **resting heart rate + offset**. Default offset **30 bpm**; the offset is a phone setting, range **20 to 50**. The baseline is the **20th percentile of the hourly lows of the last 7 days** (Decided, section 2); until a baseline exists the threshold is an absolute **110 bpm** (Decided). |
| D4 | Cooldown and escalation | Decided | Cooldown **5 minutes**. If the rate is still above the threshold when it ends, raise a **different, stronger** alert (escalation level), not the same one again; after that, **every 2 minutes** while the rate stays above, at most **6 repeats** per episode (Decided). Details: section 3. |
| D5 | The alert itself | Decided, details open | Strong enough to break the state: maximum-amplitude repeating vibration, a screen wake with a large full-screen alert, optional sound, stronger at the escalation level. **The phone raises the same kind of alert** (it vibrates and flashes a full-screen alert, not only the watch). The full-screen alert has an **"I'm OK"** button that ends the episode (Decided). Sections 4 and 4a. |
| D6 | Latency | Decided | **Measured latency** (section 12.3). Passive background route: readings delivered together about every 10 minutes (577 to 598 readings, window 580 to 599 s, arriving 0.4 to 11.5 s after the window closes; first run 318 readings after 7 min 41 s). Live `MeasureClient`, screen on: 734 readings in 5 minutes (about 2 per second), gap median under 1 s, 95th percentile 1 s, longest 60 s, AVAILABLE 4 s after start. Live with the display dim, off, and for 30 minutes: to be measured. Alert-to-phone latency: to be measured. |
| D7 | Permission | Decided, feasibility checked | Requested from the **phone** app (onboarding). Section 5. |
| D8 | Privacy | Decided | Raw readings are never logged in release builds; raw feed only in `wear/src/debug/`. `/alert` carries the numeric value (already accepted). |
| D9 | When it measures | Decided (2026-10-17) | Heart rate is measured **only while the watch app is running**, started by the user; not as an always-on background service. Consequences and open points: section 11. |

**User definition (2026-10-17), verbatim:**

1. Heart rate is measured continuously.
2. Heart rate is compared to the baseline continuously.
3. A stress event is reported immediately and the local response is activated.

This is separate from D9 (measure only while the watch app runs). "Immediately" is read as within seconds. The route that meets it is
open until the probe of section 11.5 has run; the route is decided then. The passive background route does **not** meet "immediately"
as measured: readings were delivered together, up to about 600 at a time spanning about 10 minutes, seconds after that span ended (section 6).

## 2. The resting baseline (D3)

**What the platform offers on the watch.**

- Verified: Health Services passive monitoring delivers `HEART_RATE_BPM` samples; ([compatibility](https://developer.android.com/health-and-fitness/health-services/compatibility)).
- Not found: the passive-monitoring data types that page requires on every Wear OS device are heart rate (`HEART_RATE_BPM`),
  steps and similar; I found no resting-heart-rate type in it or in any other Health Services page I could read (search
  excerpts only for the full type reference, which I could not open). I therefore treat "read resting HR directly from Health
  Services on the watch" as **not available**, **Unverified** against the full `DataType` reference.
  (Unverified: newer client versions may add an aggregate or daily heart-rate type; a search for `HEART_RATE_BPM_STATS` found
  nothing. Re-check the `DataType` reference when 6.4 starts.)
- Verified: Health Connect has `RestingHeartRateRecord` (API 34)
  ([reference](https://developer.android.com/reference/android/health/connect/datatypes/RestingHeartRateRecord)). That is a
  **phone** API.
- Unverified: whether Samsung Health writes resting heart rate into Health Connect for the Galaxy Watch6 Classic. Samsung
  developer-forum threads report that it did not sync, or had gaps, at least on older versions
  ([thread](https://forum.developer.samsung.com/t/health-is-not-syncing-the-resting-heart-rate-data-in-health-connect/25800),
  [thread](https://eu.community.samsung.com/t5/wearables/resting-hr-is-recording-but-missing-on-some-days-while-min-max/td-p/7829821)).
  These are secondhand and possibly outdated.

**Baseline (Decided).** The user accepted the baseline as defined here (2026-10-16): "The baseline as you defined it is accepted by me."
It is derived on the watch from the heart-rate readings of the running app (no new permission, works without Samsung Health):

1. For each hour with at least 3 samples, keep the **median of the lowest 3 consecutive samples**. Keep only these hourly
   numbers (and their timestamps) for the last 7 days: they are a derived statistic, not raw readings, and are never logged
   in release builds (D8).
2. The baseline is the **20th percentile of the hourly values of the last 7 days**. Sleep hours and quiet hours dominate it,
   which is what "resting" means here, and one active afternoon cannot move it much.
3. The baseline needs at least **24 hourly values** to count as known.

   *Worked example (the reading the user accepted):* the samples of one hour are 80, 60, 62, 61, 90 bpm in time order. The
   windows of 3 consecutive samples are (80, 60, 62), (60, 62, 61) and (62, 61, 90), with medians 62, 61 and 62, so the hourly
   low is **61**. With 24 hourly lows 51, 52, ..., 74, the 20th percentile by nearest rank is the 5th smallest (ceil(0.2 x 24)),
   **55**, so the threshold at the default offset of 30 is 85 bpm.
4. The alert threshold is `baseline + offset` (D3). The phone shows the current baseline and the resulting threshold
   (the watch sends the baseline in `/hello/watch` or a status item: a protocol change, section 7).

**Initial value until a baseline exists (Decided):** an absolute **110 bpm**, used from the first run
until the watch has the 24 hourly values above. Once the baseline exists the threshold becomes `baseline + offset` and 110 is
no longer used.

**Optional later improvement (Proposal, not in v1):** ignore a breach while the user is clearly exercising (steps in the last
two minutes). It needs a second data type and permission and is a source of false alerts until then: walking fast or
climbing stairs can exceed resting + 30.

## 3. Cooldown and escalation (D4)

Decided by the user:

- **Level 1** at the first reading above the threshold.
- **Cooldown 5 minutes** after it, counted between reading times (readings can arrive late).
- If the **next reading is still above** the threshold when the cooldown ends: **level 2**, a stronger alert. After that, level 2
  repeats **every 2 minutes** while the rate stays above the threshold.
- The episode ends when a reading is at or below the threshold. The next breach starts again at level 1.
- Each alert (level 1 and every level 2 repeat) sends `/alert/<id>` with its `level` (protocol change, section 7); the **phone
  raises its own alert for each one** (section 4a).
- An alert can only follow a reading, so the 2-minute rhythm depends on when readings arrive (section 6). The rhythm is "at most one alert per 2 minutes, as soon as a reading above the threshold is evaluated".

Decided by the user (2026-10-16: "the two remaing items: "I'm OK" and the cap of 6 repeats is approved"): level 2 stops after **6
repeats** per episode (12 minutes at 2-minute intervals), and the full-screen alert on the watch and on the phone has an
**"I'm OK" button** that ends the episode early. Without these a high rate during exercise would alarm the user for as long
as it lasts.

### 3a. Open question (user): after "I'm OK"

As built, the episode ends and the very next reading above the
threshold starts level 1 again at once (within seconds). Options: (a) as built; (b) after "I'm OK", stay quiet for N
minutes (for example 5) before a new episode may start, then start at level 1 if still above. The user decides, and N if (b).

## 4. How strong an alert can be (D5)

**Plainly: the hardware offers vibration, sound and the screen, and nothing else.** A watch cannot shock, heat or physically
move the wearer. The strongest safe alert is the combination below at its limits.

| Part | Level 1 | Level 2 (escalation) | Status |
|---|---|---|---|
| Vibration | maximum-amplitude pattern (255), about 3 s on, short pauses, repeated for about 15 s | same at 255, continuous for about 30 s with no pauses longer than 0.3 s | Proposal for the shape. Whether the watch honours amplitude is **Unverified**: the app must check `Vibrator.hasAmplitudeControl()` and fall back to an on-off pattern |
| Screen | wake the screen and show a large full-screen alert (colour, one word, a big button) | brighter and flashing, button stays | Proposal; the mechanism is below |
| Sound | optional setting, off by default | on if the setting is on | Proposal |

**What can suppress it (checked as far as sources allow):**

- Do Not Disturb: Samsung says it silences notifications but lets **alarms** through
  ([Samsung](https://www.samsung.com/us/support/answer/ANS00080310/)). Verified for Samsung's description. Whether an
  alert from this app counts as an alarm is **Unverified**: the Android DND policy has an alarm category for notifications
  of category `ALARM` ([ZenPolicy](https://developer.android.com/reference/android/service/notification/ZenPolicy)), so the
  alert should be posted with category alarm and alarm audio/vibration attributes. Ordinary apps cannot use the flag that
  forces vibration through interruption limits (`FLAG_BYPASS_INTERRUPTION_POLICY` is for privileged apps,
  [VibrationAttributes](https://developer.android.com/reference/android/os/VibrationAttributes)).
- **Measured on the Galaxy Watch6 Classic (Wear OS 6, 2026-10-17, probe `vibrate`: 3 pulses of 800 ms at amplitude 255, alarm usage, called from a
  broadcast receiver; `hasAmplitudeControl=true`):** normal mode, felt, "between weak and strong"; Do Not Disturb set with
  `adb shell cmd notification set_dnd priority`, **felt nothing** (an earlier `set_dnd on` gave `zen_mode 2`, total silence). So a plain
  alarm-usage vibration from the background is suppressed under Do Not Disturb on this watch, although the Google Wear OS help says alarms
  always vibrate ([Wear OS help](https://support.google.com/wearos/answer/6056897)); Samsung forum reports of alarms silenced in Do Not
  Disturb or sleep mode agree with the measurement
  ([thread](https://eu.community.samsung.com/t5/wearables/alarm-only-vibrates-sounds-once-when-triggered-from-sleep-mode/td-p/10674677);
  secondhand). Bedtime, Theater mode and the intensity settings are still to be measured.
- **Routes that might get through Do Not Disturb (research, all Unverified until tested):**
  1. *Alarm-category notification on a channel with bypass-DND and a full-screen intent* (probe command `notify-alarm`). The channel flag
     only works when the user's policy allows it, and the Wear OS help lists a per-app "Override Do Not Disturb for" setting on the watch, so
     the user may have to switch the app on there once ([NotificationChannel](https://developer.android.com/reference/android/app/NotificationChannel),
     [Wear OS help](https://support.google.com/wearos/answer/6056897)). The probe prints `channelBypassDnd`, `policyAccess` and
     `canUseFullScreenIntent` and the user notes whether the watch vibrated and the screen woke, with and without that setting.
  2. *An alarm clock*: `AlarmManager.setAlarmClock`, which the system treats as a real alarm; its receiver vibrates with alarm usage (probe
     command `alarm-clock`, fires 15 s later). The Android DND exception for alarms is meant for exactly this.
  3. *Do Not Disturb access* (`ACCESS_NOTIFICATION_POLICY`, granted by the user in settings) so that the app can lift the filter while an alert
     is raised. Heavy-handed (it changes the user's setting), and whether the watch offers the settings screen is unknown; not probed, a fallback.
  4. *None works*: then under Do Not Disturb the alert may be silent. That would need the user's decision (for example: turn Do Not Disturb off
     when this app matters, or accept it). `FLAG_BYPASS_INTERRUPTION_POLICY` is for privileged apps and is not an option.
- Bedtime mode: Samsung says alarms still sound
  ([Samsung](https://www.samsung.com/us/support/answer/ANS00080310/)). Unverified for this alert (same question as above).
- **Theater mode: Samsung says it turns off all alerts, alarms and wake-up gestures. Expect no alert at all in theater mode;
  nothing in an app overrides it.**
- The user's vibration-intensity setting: the system keeps separate intensity settings, including one for alarms
  ([AOSP change](https://gitlab.e.foundation/e/os/android_frameworks_base/-/commit/095b3d31dbe1bb5614880a801b7c31c2facb3645)).
  Whether the Galaxy Watch's own "vibration intensity" setting scales an app's vibration down, and whether alarm usage is
  exempt, is **Unverified**. The probe (section 6) measures it by sending the same pattern at each setting.
- Do not rely on the full-screen alert alone: the screen can be covered by a dismissed or restricted notification, so the
  vibration and the `/alert` history entry are the minimum that always happens when the watch can run the code.

**Full-screen alert mechanism.** An app cannot just start an activity from the background (Android restricts it); the
supported way is a notification with a **full-screen intent**. Verified: from Android 14 the full-screen-intent permission
(`USE_FULL_SCREEN_INTENT`) is granted by default only to apps with calling or alarm functionality, others must ask the user,
and Play Console needs a declaration ([Play policy](https://support.google.com/googleplay/android-developer/answer/16965181),
[AOSP](https://source.android.com/docs/core/permissions/fsi-limits)). Unverified: whether Wear OS applies the same rules and how
the screen is woken on the Galaxy Watch6 Classic. Consequence: this alert may need a manual permission step on the watch and
a Play declaration (step 7.x) that a health-alert app might not get by default. For a private build that is acceptable; it is a
Play-readiness item to decide with the user.

## 4a. The phone raises the alert too (D5, Decided)

The user wants the **phone** to shake and flash, not only the watch. Consequences:

- The watch's `/alert/<id>` item (urgent, already in the protocol) is the trigger. The phone app's listener service receives it,
  stores it in the history as today and **also raises the alert**: maximum-importance alarm-category notification channel, a
  repeating maximum-amplitude vibration, a full-screen alert that flashes, and optional sound. Level 2 is stronger than level 1,
  as on the watch.
- This only works while the link delivers the item. With the watch away from the phone the phone shows nothing until the item
  arrives (items queue and are delivered on reconnect; a late alert is shown with its original time).
- The phone has the same limits as the watch (section 4): Do Not Disturb and silent modes, the full-screen-intent permission on
  Android 14 and later (Unverified for the Galaxy A55 5G here), and the vibration-intensity setting. The phone also needs the
  full-screen-intent step, asked during the same onboarding as the watch permission (D7).
- **"I'm OK" on the phone also ends the episode on the watch** (user decision, recorded in the body of issue #55 on 2026-10-16). The
  phone sends an end-episode item to the watch (new item, protocol 1.2, Shared lane); when the watch receives it, it stops
  its repeats, closes its alert and starts the next breach again at level 1. Watch lane: plan and test it with 6.5 and 6.6
  (TC-W-084, TC-W-085), tests first, once the item's path and body are defined in `docs/protocol.md`. Nothing is built before that.
- Owner: Android lane (phone alert and its "I'm OK"), after the protocol change (`level`, end-episode item). Watch lane: the watch alert and stopping on the end-episode item.

## 5. Permission requested from the phone (D7)

- Verified: the heart-rate permission is a runtime permission of the **watch app** (`BODY_SENSORS` up to API 35,
  `android.permission.health.READ_HEART_RATE` targeting API 36; background: `BODY_SENSORS_BACKGROUND`, or
  `READ_HEALTH_DATA_IN_BACKGROUND` on API 36)
  ([permissions](https://developer.android.com/health-and-fitness/health-services/permissions)). A phone app cannot grant it.
- Verified: a phone app can open an activity on the watch with `RemoteActivityHelper.startRemoteActivity(intent, watchNodeId)`;
  the intent must be `ACTION_VIEW`, carry a data URI and the `BROWSABLE` category
  ([reference](https://developer.android.com/reference/androidx/wear/remote/interactions/RemoteActivityHelper)), and it is
  unavailable while the devices are disconnected.
- **Proposed flow:** onboarding on the phone has a button "Allow heart rate on the watch". It calls `startRemoteActivity` with
  `adhdpal://permission/heart-rate`. The watch app has an activity with a matching `BROWSABLE` intent filter that requests
  the permissions. The system prompt appears **on the watch** and the user taps Allow there: this cannot be avoided.
  The watch then reports the granted state to the phone (new item or a field in `/hello/watch`: protocol change, section 7)
  and the phone shows it. If denied, no alerts, and both apps say why.
- Unverified (to test on the devices): that the remote activity starts with the watch screen off or locked (the user may need
  to wake the watch), that the background permission is a second prompt or a settings page on Wear OS 5, and the behaviour on
  Wear OS 6.

## 6. What must be verified on the real watch before building (H2)

The passive background listener steps and results in this section are **measured, not used for alerts**; the route in use is the live route (section 11).

A short probe (debug-only, 6.4 first task) on the Galaxy Watch6 Classic:

1. `getCapabilitiesAsync()` reports passive `HEART_RATE_BPM`.
2. Register a passive listener; record arrival times and sample counts with the screen on, off and while the watch sits on a
   desk, for at least 24 hours (done as far as needed: the results below show the passive route cannot give "immediately"; the live route replaces it, section 11). This gives the measured latency (D6) and shows whether the 24-hourly-values baseline in section 2
   is reachable. Result: readings arrive about every 10 minutes (section 12.3), which fails "immediately"; the live route is used instead
   (Samsung may restrict third-party background heart rate:
   [forum thread](https://forum.developer.samsung.com/t/read-heart-rate-every-5-minutes-in-background-service-on-wear-os/34328),
   secondhand).
3. The vibration/DND/Bedtime/intensity checks of section 4, and the remote permission flow of section 5.

### First result on the real watch (Galaxy Watch6 Classic, Wear OS 6, 2026-10-17)

Probe step 1 and 2, as reported by the user through the coordinator. Verified on the device, one run, not yet repeated:

- `getCapabilitiesAsync()` reports passive `HEART_RATE_BPM` supported (`heartRatePassive=true`).
- `pm grant` worked for both `android.permission.health.READ_HEART_RATE` and `READ_HEALTH_DATA_IN_BACKGROUND`; without the first, `start` fails
  with `SecurityException: Missing permissions: [android.permission.health.READ_HEART_RATE]` (section 5 therefore holds for Wear OS 6).
- The background app does receive heart-rate data, but **delayed and delivered together**: after `start` at 09:49:41 the only delivery in about ten
  minutes arrived at 09:57:22 (7 min 41 s later) with 318 samples (the longer run below gives the sample rate). Consequence for the alert: the delay from a high heart rate to the alert can be several
  minutes (D6, measured latency), and the hourly lows and the baseline have enough samples.
- The delivery was logged with the screen off although the user says the screen stays lit on the wrist: the first probe read `isInteractive`, which
  is false in the always-on display. The probe now records the display state (on, doze, off) and the age of the newest sample, and step 2 runs
  30 minutes to see several deliveries. Not yet known: the gap between deliveries and the delay with the screen on.
- **Off the wrist (step 3, old build, 2026-10-17):** the watch lay on a table with the screen off from 10:02:48 (after `clear`). One delivery of **1 sample** arrived at
  10:04:13, about 85 s after `clear` (probably while the watch was being taken off); the report at 10:12:51 showed `deliveries=1 samples=1`, no gap.
  So off the wrist the watch delivers essentially nothing, as expected: no alerts can come from a watch that is not worn (and the baseline gets no
  samples then).

### Longer run: delivery sizes and delay (user via the coordinator, 2026-10-17, same watch)

The first probe registration stayed active after step 2 and kept logging while the new build was installed. The wrist state between 10:29 and 11:25
is not known (the user did other things), so the gaps below are not a clean worn run. Raw `PROBE` lines, old build (its screen flag is unreliable):

| Time | Samples in the delivery |
|---|---|
| 09:57:22 (start 09:49:41) | 318 |
| 10:04:13 (off the wrist) | 1 |
| 10:29:52 | 574 |
| 10:32:20 | 4 |
| 10:37:08 | 371 |
| 10:40:03 | 83 |
| 10:48:14 | 565 |
| 10:55:21 | 404 |
| 11:05:08 | 600 |
| 11:15:00 | 590 (the line said 600) |

New build (installed about 11:24): 11:25:13 one delivery, 600 samples, `display=doze`, `newestAgeMs=7874`, `spanMs=599000`.

What this shows (Verified on the device, one watch, wrist state partly unknown):

- The watch records about **1 sample per second** (600 samples span 599 s). The earlier "0.7 per second" was a partial delivery.
- A delivery holds **up to about 600 samples spanning about 10 minutes**, and arrives **seconds after that span ends** (newest sample 7.9 s old).
  Smaller deliveries also occur (4, 83, 371, 404 samples), probably when the watch was taken off or the span was cut.
- Consequence for D6 (measured latency): a high heart rate can reach the app **up to about 10 minutes plus a few seconds late** (a rise at the start of a
  span is seen when the span closes), and sometimes sooner in a smaller delivery. No bound shorter than that is promised. The cooldown (5 minutes)
  and repeats (2 minutes) of section 3 count from the reading's own timestamp.
- The baseline of section 2 is reachable: one hour of wear gives thousands of samples.
- Still not measured: a clean worn run with the display on, and the delay when the watch is on the wrist but still. Checklist step 2 stays as it is for that run.

## 7. What changes elsewhere (not done in this document)

| Change | Owner | Why |
|---|---|---|
| `AlertSettings`: `offsetBpm` (20 to 50, default 30) and a fallback absolute threshold; `cooldownSeconds` default 300. Protocol 1.2 | Shared lane | D3, D4: a relative threshold is not expressible today (absolute `value` only) |
| `Alert`: add `level` (1 or 2) | Shared lane | D4 |
| Watch status (baseline, permission granted) to the phone: a field in `/hello/watch` or a new item | Shared lane | D3, D7 |
| Phone onboarding screen for the permission, the full-screen-intent step and the offset setting | Android lane | D3, D7 |
| End-episode item phone to watch (path, body, urgent), protocol 1.2 | Shared lane | "I'm OK" on the phone ends the episode on the watch (section 4a) |
| Phone raises its own alert when an `/alert` arrives: alarm-channel notification, repeating maximum vibration, flashing full-screen alert, level 2 stronger (section 4a) | Android lane | D5 |
| `EpisodeEvaluator` and `BaselineTracker` (in `:protocol`, `alerts/`): resting-relative threshold, escalation level, episode end (replace the first `AlertEvaluator` with absolute thresholds, deleted) | Watch lane (6.5), tests first (TC-W-086 to 089, 120 to 131, 138) | D3, D4 |
| Live measure service (section 11), baseline store (hourly values), alert activity with full-screen intent, vibration patterns | Watch lane (6.3, 6.4, 6.6) | D5 |
| Play declaration for the full-screen intent | Step 7.x | section 4 |

## 8. Answers and what is still open

Answered by the user (2026-10-15 in this lane's session; 2026-10-16 relayed by the coordinator with the user's exact words):

1. Initial value until a baseline exists: **110 bpm** (Decided, section 2).
2. Baseline rule: "The baseline as you defined it is accepted by me." (Decided, section 2: hourly low = median of the lowest 3
   consecutive samples per hour; baseline = 20th percentile of the hourly values of the last 7 days; known after 24 hourly
   values.) An earlier relayed wording, "the baseline is defined with taking the average", was withdrawn by the user.
3. Escalation: after the 5-minute cooldown, alerts every **2 minutes** while above the threshold (Decided, section 3). The
   alert must shake and flash the **phone** too and act like an alert there (Decided, section 4a).
4. Theater mode, and possibly the watch's vibration-intensity setting, can silence the alert, and nothing in an app overrides
   them: **accepted**.
5. The full-screen-intent permission step and the later Play declaration: **accepted**.

Approved by the user on 2026-10-16 (their words, relayed by the coordinator): "the two remaing items: "I'm OK" and the cap of 6
repeats is approved".

- Cap of **6 level-2 repeats** per episode: **Decided** (section 3).
- **"I'm OK" button** that ends an episode early, on the watch and the phone: **Decided** (sections 3 and 4a).

This was the user's confirmed document for step 6.2 (confirmed 2026-10-16). Section 11 stays research and proposal until E2 is recorded. The remaining "Proposal" marks in
section 4 (exact vibration patterns, screen and sound details) are implementation details that tests in 6.6 pin down, not
questions for the user. The one-line PR that flips H1 follows.

## 9. Could the Galaxy Watch's stress value be a trigger or a second condition? (research, 2026-10-17)

**In three lines:**

1. The watch's stress value is computed inside Samsung Health from heart-rate variability, and no route open to an ordinary app gives it to us: not Health
   Services, not Samsung's Sensor SDK, not the Samsung Health Data SDK, and (as far as I could find) not Health Connect. It is reachable only through Samsung's
   closed partner programs.
2. What we can get is heart rate and movement; the beat-to-beat intervals that make heart-rate variability come only through the Sensor SDK, which Samsung
   restricts to developer-mode testing unless Samsung accepts us as a partner.
3. Recommendation: **not possible as Samsung's stress value now**; use **movement (steps) as the second condition** to tell stress from exercise, and revisit
   the stress value only if Samsung approves a partnership.

Each statement is marked **Verified** (an official page I read, cited), **Secondhand** (forum, blog or search excerpt of a page I could not open) or
**Unverified**.

### 9.1 What Samsung's stress measure is

- HRV-based. **Secondhand** (Samsung support text seen as excerpts; the pages returned 403 to my fetch tool): the watch estimates stress from heart-rate
  variability ([Samsung](https://www.samsung.com/us/support/answer/ANS10001394/)).
- Continuous or on demand. **Secondhand**: the Stress setting offers "Measure continuously" and "Manual only". Continuously, it measures about once per
  second while heart rate is normal and there is no movement; a manual reading takes 1 to 2 minutes with the wrist still. A Samsung community answer says
  it does not measure automatically while you sleep or move a lot
  ([Samsung](https://www.samsung.com/ae/support/mobile-devices/how-can-i-use-stress-level-feature-on-samsung-watch-5/)). Exact intervals: **Unverified**.
- The algorithm is proprietary. **Secondhand**: a Samsung developer-relations reply on the developer forum says so and suggests building one's own estimate
  with Wear Health Services ([Samsung forum, via a search excerpt](https://forum.developer.samsung.com/)).

### 9.2 Every route an ordinary app has

| Route | Does it give the stress value? | What I found |
|---|---|---|
| Health Services on Wear OS (the route of section 11) | **No.** Verified | The data types required for passive monitoring are heart rate, steps, distance, speed, calories, elevation and floors; heart-rate variability, inter-beat interval and stress do not appear ([Google](https://developer.android.com/health-and-fitness/health-services/compatibility)). A given watch may add types; the capabilities call lists them (**Unverified** for this watch). |
| Samsung Health Sensor SDK (watch, Galaxy Watch4 and later) | **No stress tracker.** Verified | Continuous trackers: accelerometer, heart rate (its inter-beat interval "can be used to calculate HRV"), PPG, skin temperature; on demand: body composition, ECG. It does not share data with Samsung Health. **Developer mode is "intended only for testing or debugging your app. It is not intended for app users"; to distribute an app you must join the Samsung Partner Program** ([Samsung FAQ](https://developer.samsung.com/health/sensor/faq.html)). |
| Samsung Health Data SDK (phone) | **No stress, no HRV type.** Verified | The release notes list heart rate, sleep, sleep apnea, irregular rhythm notification, energy score, skin temperature, steps and others, with user consent for all reads; the access code for developer mode and distribution come through the partner program ([Samsung](https://developer.samsung.com/health/data/release-note.html)). Its energy score is derived from HRV (**Secondhand**) but is a different value. |
| Health Connect (phone) | **Probably not.** Mixed | A heart-rate-variability record type exists (`HeartRateVariabilityRmssdRecord`, API 34; Verified, [Android](https://developer.android.com/reference/kotlin/androidx/health/connect/client/records/HeartRateVariabilityRmssdRecord)); I saw no stress record type (**Unverified**: I did not read the whole type list). Whether Samsung Health writes HRV or stress there: **Secondhand and conflicting**, a sync vendor's page says overnight HRV and a stress score are written ([FitMesh](https://www.fitmesh.fit/en/sync/galaxy-watch/watch-6)); a Samsung forum thread suggests HRV may need the privileged partnership ([Samsung forum](https://forum.developer.samsung.com/t/hrv-heart-rate-variability-heart-rate/41364)). The user can check the Health Connect data-sources screen on the phone for what Samsung Health writes. |
| Samsung Privileged Health SDK / Samsung Health Platform server API | **Closed to us.** Secondhand | Samsung's developer blog presents the Privileged Health SDK as the route for processed sensor data such as SpO2, ECG and PPG ([Samsung](https://developer.samsung.com/sdp/blog/en/2022/05/25/check-which-sensor-you-can-use-in-galaxy-watch-running-wear-os-powered-by-samsung)); a developer-relations reply says only premium health partners have access to Samsung Health data; aggregators list a stress score behind a server-side program that needs Samsung's approval ([Open Wearables](https://openwearables.io/blog/samsung-health-api-galaxy-watch-data-for-developers), a vendor page, low trust). |

### 9.3 What it would cost

- **Permissions and approval.** Stress value: no open route at all. Own HRV from inter-beat intervals: the Sensor SDK in developer mode, a build installed only for testing; shipping needs the Samsung Partner Program (Verified). Whether a single-user private build is acceptable to Samsung: **Unverified**. Play: a health-data app has its own declarations (step 7.x), plus the partner terms.
- **Latency.** Heart rate through Health Services arrived about every 10 minutes (measured on this watch, section 6). The Sensor SDK's continuous trackers are described as continuous with finer-grained results ([Samsung FAQ](https://developer.samsung.com/health/sensor/faq.html)); real delivery delay on the Watch6 Classic: **Unverified**. Samsung's own stress values are produced only without movement and about once a second at best (Secondhand), and reach other apps (if at all) through Samsung Health on a sync schedule: **Unverified**.
- **Battery.** Continuous PPG or inter-beat tracking costs more battery than the live heart-rate route (section 11) and would likely need a foreground service; the amount is **Unverified** and would need a measurement.

### 9.4 Would it help tell stress from exercise?

- The Samsung stress value is, by its design, measured only while the wrist is still (**Secondhand**), so during exercise there is normally no value: its presence is a weak "not exercising" hint, and its absence says nothing.
- Heart-rate variability itself (low variability at a raised heart rate without movement) is the signal that separates stress from exercise, but it needs inter-beat intervals, which only the restricted Sensor SDK gives.
- A cheaper discriminator is open to us now: **movement from Health Services** (steps and calories are required passive types, Verified, [Google](https://developer.android.com/health-and-fitness/health-services/compatibility)). The optional improvement in section 2 (ignore a breach while the user is clearly moving) is exactly this second condition. It would cut walking and stair false alerts; it cannot tell a still, anxious state from a still, calm one, which the stress value would.

### 9.5 Recommendation

- **Samsung's stress value as a trigger or second condition: not possible now** (no open route).
- **Second condition from movement (steps): use it, as a later step.** It needs one more data type on the live route (experiment E5) and its permission (`ACTIVITY_RECOGNITION` for steps on the watch; **Unverified** which runtime permission the passive client asks for on Wear OS 6) and is within what the probe approach can measure. Proposed as a follow-up to 6.4/6.5, not part of the current decisions.
- **Own HRV-based stress: use later, only if wanted.** It needs Samsung's Sensor SDK, developer mode on the watch and, to ship, the partner program.

**What a probe on the real watch would need (not built; this section is research only):**

1. The `capabilities` command printing the whole list of passive types the watch supports (to see whether the Watch6 Classic offers anything beyond the required list); a few lines in the existing probe.
2. The user opening Health Connect on the phone (data and access, Samsung Health) to see which types Samsung Health writes: no code.
3. For the HRV route only: a debug-only app that asks the Sensor SDK for its tracking capability and an inter-beat-interval tracker in developer mode, printing counts and times only. Not recommended before the user decides to pursue Samsung's partner program.

## 10. Can the app tell a heart-rate rise from stress from one caused by exercise? (research, 2026-10-17)

**In three lines:**

1. Partly. The watch app can see movement in the background (steps and the system's "user activity state" through Health Services, or the raw step and
   accelerometer sensors), so it can hold back an alert while the user is moving and for a few minutes after; it cannot know the *reason* for a rise
   (it cannot tell a still, anxious body from a still, calm one without heart-rate variability, section 9).
2. The movement data is expected to arrive in the same deliveries as heart rate, with timestamps (Unverified), so the pairing would work and only the alert time would be late; it costs one more
   runtime permission (`ACTIVITY_RECOGNITION`). Exercise without steps (cycling, weights) is the main gap.
3. Recommendation: **build later, small** (steps + activity state as a "user is still" condition, with a configurable quiet time), after a short probe on the real watch
   that shows what these signals really do on this watch; it changes the decided design, so it needs the user's yes first.

Each statement is marked **Verified** (an official page I read, cited), **Secondhand** or **Unverified**.

### 10.1 Signals the watch app can get in the background

| Signal | What it gives | Facts |
|---|---|---|
| Health Services **user activity state** | `USER_ACTIVITY_PASSIVE` (awake, not exercising), `USER_ACTIVITY_EXERCISE`, `USER_ACTIVITY_ASLEEP`, `USER_ACTIVITY_UNKNOWN` | Verified ([state reference](https://developer.android.com/reference/kotlin/androidx/health/services/client/data/UserActivityState), seen as a search excerpt; the rest on the Google page I read): switched on with `setShouldUserActivityInfoBeRequested(true)` in the passive config, needs the `ACTIVITY_RECOGNITION` runtime permission, arrives in `onUserActivityInfoReceived` with a `stateChangeTime` that "may be in the past" ([Google](https://developer.android.com/health-and-fitness/health-services/monitor-background)). Whether the Watch6 Classic reports EXERCISE for brisk walking or only for tracked workouts: **Unverified**. |
| Health Services **steps, calories, distance** (passive data types) | Step counts and similar per interval | Verified: steps, daily steps, distance, speed, calories and elevation are required passive types on every Wear OS device ([Google](https://developer.android.com/health-and-fitness/health-services/compatibility)). Which of them arrive per interval and how fine: **Unverified**; the daily-goal feature (`PassiveGoal`) is a per-day counter and does not suit minutes ([Google](https://developer.android.com/health-and-fitness/health-services/monitor-background)). |
| Raw **step counter / step detector** (SensorManager) | Steps since boot / one event per step | Verified ([Android](https://developer.android.com/develop/sensors-and-location/sensors/sensors_motion)): both need `ACTIVITY_RECOGNITION`; step counter latency up to 10 s, step detector below 2 s. Hardware buffering and background delivery: **Unverified** (the page does not cover them); listening continuously in the background would likely need a foreground service. |
| Raw **accelerometer** | Movement intensity (including arm movement without steps) | Verified (same page): it "uses about 10 times less power than the other motion sensors"; from Android 12 it is rate-limited. A movement measure such as the moving average of the acceleration norm is the usual way to classify low and high activity (**Secondhand**, a patent excerpt). Continuous listening costs battery: **Unverified**, needs a measurement. |
| **Samsung's automatic workout detection** | A workout recorded by Samsung Health | **Secondhand** (Samsung support excerpts): it starts after about 10 minutes of activity and only for some activities (walking and running, cycling, elliptical, rowing, swimming, a "dynamic workout" option), can mistake driving for a workout, and must be switched on in Samsung Health ([Samsung](https://www.samsung.com/us/support/answer/ANS10003306/)). There is no API for an ordinary app to be told about it. |
| **The user starting a workout** | An exercise tracked by Health Services | Verified ([Google](https://developer.android.com/health-and-fitness/health-services/active-data)): only one exercise at a time on the device; `getCurrentExerciseInfoAsync()` returns `OTHER_APP_IN_PROGRESS`, `OWNED_EXERCISE_IN_PROGRESS` or `NO_EXERCISE_IN_PROGRESS` when asked, but there is no notification for exercises of other apps ("your listener only receives updates about exercises your app owns"), and the answer does not say which app or what exercise. Whether Samsung's own tracker answers `OTHER_APP_IN_PROGRESS`: **Unverified**. If a workout were started inside our app we would of course know. |

### 10.2 Permissions and latency compared with heart rate

- **Permission.** One more runtime permission, `ACTIVITY_RECOGNITION` (Verified, above), on top of the heart-rate ones; it fits the phone onboarding of D7 the same way (whether `pm grant` and a remote prompt work for it: **Unverified**, the heart-rate permissions did on this watch).
- **Latency.** Google ([monitor in the background](https://developer.android.com/health-and-fitness/health-services/monitor-background), Verified) says what "background" means: "A service lets your app receive data in the background when no part of your app is visible in the foreground." It says: "When you receive data in the background, it is delivered in batches." and "The data you receive from Health Services is batched, so you may receive data points of different types, or multiple data points of the same type, in the same batch." Google does **not** define "batch": the page gives no size, no interval and no trigger (read 2026-10-17). Our measurement for heart rate on the background route: readings delivered together, the first time 318 samples 7 min 41 s after start, later about every 10 minutes (section 6). Whether steps arrive together with heart rate on this watch, and how late steps are on the live route (section 11, app in the foreground): **Unverified**, not measured.
- **Battery.** Steps and activity state read in the background come from the same channel as the passive heart rate measured in section 6 (not used for alerts); the extra cost is **Unverified** and not measured for the live route; a raw accelerometer listener is the expensive option.

### 10.3 The usual rules, and their risks

The literature on cutting false heart-rate alarms uses the same idea: static thresholds ignore context, and an activity-dependent expectation or accelerometer-based suppression reduces false alarms (a 2015 body-sensor study cut false alarms from 9.58% to 1.43% with a rule-based filter on a chest ECG and an accelerometer; a Twente thesis predicts the expected heart rate from activity; both small and not for a wrist device, so a design direction, not proof)
([MDPI](https://www.mdpi.com/1424-8220/15/2/3952), [Twente](https://essay.utwente.nl/essays/90703); **Secondhand**, search excerpts).

| Rule | False alarms it removes | Missed alarms it causes |
|---|---|---|
| **R1** suppress while recent steps or movement are high | Walking, stairs, running | A stress episode while walking |
| **R2** keep suppressing for N minutes after movement stops (heart rate recovers slowly) | The drop-off after a run or stairs | Stress that starts right after exercise |
| **R3** suppress while the heart rate is falling | Recovery tail | Noisy wrist readings can look like falling; a real peak that is already falling is missed |
| **R4** compare with an activity-dependent expected heart rate (a model) | The most, in studies | Complex, needs training data and tuning; not for v1 |
| **R5** treat `USER_ACTIVITY_EXERCISE` as a hard suppress | Tracked workouts | Reliability on this watch **Unverified**; a workout the system does not detect is not covered |

The main gap for every rule that uses steps: exercise **without steps** (cycling, weights, rowing) does not count steps; the accelerometer or the activity state would catch more, at more battery cost. And none of them can say a still body is calm or anxious.

### 10.4 How it fits the decided design

Threshold (resting + offset), cooldown (5 min), level 2 every 2 min up to 6 repeats and "I'm OK" stay as decided. The new part would be a **gate on each reading before it can start or continue an episode**: a reading counts as a breach only if the user has been still for N minutes (no steps and not in the exercise state). Proposed handling (**Proposal**, needs the user's yes):

- a reading while the user is moving, or inside the quiet time after moving, is treated like a reading at or below the threshold: it ends a running episode (the rise has an explanation) and raises nothing;
- N is a phone setting next to the offset (default to be chosen, for example 10 minutes), and a switch to turn the gate off; both are protocol 1.3 fields (Shared lane), and the evaluator in `:protocol` takes the context as an input (Watch lane, tests first);
- the baseline is unaffected (hourly lows are low by construction).

### 10.5 Recommendation and what to probe

The steps and activity-state probe is run on the live route (experiment E5); it replaces the passive step probe.

**Build later, small:** steps + user activity state as the "user is still" gate (R1, R2, R5) after the probe confirms the signals work on this watch; no model (R4), no heart-rate slope (R3) in v1. Not part of the current decisions; the user decides.

**Probe on the real watch (not built; research only):**

1. The `capabilities` command listing every passive data type and user activity state the watch supports (a few lines in the existing probe).
2. A probe run that registers heart rate, steps and user activity info together (with `ACTIVITY_RECOGNITION` granted) and logs counts, times and the activity state per delivery, never values, while the user sits 5 minutes, walks 5 minutes, climbs stairs, then does one exercise without steps (cycling or weights): do steps arrive in the same delivery as heart rate, how late is the state change, and what does the state say for each activity.
3. With Samsung's automatic workout detection switched on and a detected workout running, `getCurrentExerciseInfoAsync()` from a probe command: does it answer `OTHER_APP_IN_PROGRESS`.
4. Optional, if steps miss too much: a short foreground accelerometer-variance measurement for the same activities, with the battery drop noted.

## 11. Measuring only while the app runs (D9, research and proposal, 2026-10-17)

Decided by the user (2026-10-17, via the coordinator): heart rate is measured **only while the watch app is running**, started by the user,
not as an always-on background service. Nothing below is built. Where something is open it is marked **Proposal**.

### 11.0 How each route meets the definition

| Route | Continuous | Immediate (seconds) | Local response, report to the phone | Status |
|---|---|---|---|---|
| Passive listener service (background) | Yes | **No**: measured delay up to about 10 minutes (section 6) | Yes | Measured; not used for alerts |
| Passive callback while the app runs | Yes | The page says "a slightly faster rate" than the service, not quantified | Yes | Unverified, not probed; not used |
| Live `MeasureClient` while the app runs (11.1) | Yes: about 2 readings per second, screen on (section 12.3) | Yes: within about a second, screen on | Yes | Measured with the screen on; dim, off and 30 minutes open (11.5) |
| `ExerciseClient` | Yes | See the Google text in 11.1 | Yes | Not chosen: it is for workouts |

### 11.1 The live route: `MeasureClient`

- Verified: `MeasureClient` is "meant for situations in which your app is in use and requires rapid data updates. If possible, create this with a foreground UI so
  that the user is aware" and is "not suitable for workout tracking"
  ([Google](https://developer.android.com/health-and-fitness/health-services/active-data/measure-client)). `ExerciseClient` is the API for workouts.
- Verified: capability is checked with `getCapabilitiesAsync()` and `DataType.HEART_RATE_BPM in capabilities.supportedDataTypesMeasure`; one callback per data type
  (`registerMeasureCallback(DataType.HEART_RATE_BPM, callback)`); `onAvailabilityChanged` reports, for example, that "heart rate data might not be available when the
  device is not properly attached to the wrist" (same page).
- Verified: "callbacks cause an increase in sensor sampling rates, which in turn increases power consumption", so the page advises keeping the callback registered as
  short as possible. The page gives **no** update rate, **no** figure for battery cost and says nothing about the app being in the background or the screen being off.
- Unverified: that `HEART_RATE_BPM` is in `supportedDataTypesMeasure` on the Galaxy Watch6 Classic (the passive capability was true, section 6; the measure
  capability is a separate list); the update interval (about 1 per second is expected from the passive route's own rate, not stated anywhere); whether readings
  continue with the screen dim or off.
- Google, for the other client ([`ExerciseClient`](https://developer.android.com/health-and-fitness/health-services/active-data), Verified):
  "Health Services batches workout data when the device screen is in ambient mode to save power, so the information displayed may not be recent."
  "When the user looks at the screen, any data in the process of being batched is immediately delivered to your app."
  "The processor delivers messages only when awake or when a maximum reporting period is reached, such as every 150 seconds."
  Google does not define "batches" or "batched" on that page either; the sentence above is the only figure it gives (read 2026-10-17).
  Whether `MeasureClient` behaves the same way in ambient mode: **Unverified**, the page does not say.
- Secondhand: Samsung's own developer blog says standard sensors "stop sending data when the screen turns off" and advises a foreground service plus a wake lock
  (it uses `SensorManager`, not Health Services, so it does not settle the question for `MeasureClient`)
  ([Samsung](https://developer.samsung.com/galaxy-watch/blog/en/2026/04/23/continuous-heart-rate-tracking-on-galaxy-watch-even-with-the-screen-off)). A Samsung
  forum thread reports `ExerciseUpdateListener` not updating with the screen off inside a foreground service (secondhand, not reproduced).

### 11.2 Keeping the app running

- Verified: a foreground service of type `health` needs `FOREGROUND_SERVICE` and `FOREGROUND_SERVICE_HEALTH` in the manifest and at least one of these granted at
  runtime: `BODY_SENSORS` (API 35 and lower) or `READ_HEART_RATE`, among others
  ([Android](https://developer.android.com/develop/background-work/services/fg-service-types)).
- Verified: those permissions are "while-in-use": a `health` foreground service that uses body sensors **cannot be created while the app is in the background**
  unless `BODY_SENSORS_BACKGROUND` (API 33 to 35) or `READ_HEALTH_DATA_IN_BACKGROUND` (API 36) is granted (same page). Because the user starts the app, the
  service is started from the visible app, so the background permission should **not** be needed. Unverified on the watch; the probe in 11.5 tests it by not
  granting the background permission.
- Verified: on Wear OS the long-running state is shown with an ongoing notification paired with an `OngoingActivity`; it gives a watch-face indicator, an entry in
  Recents and a one-tap way back, and "keeps your app visible for longer"
  ([Android](https://developer.android.com/training/wearables/ongoing-activity)). A static icon and a touch intent are required.
- Unverified: whether Wear OS 6 on this watch still stops or throttles a foreground service after the screen has been off for a while (Samsung's blog speaks of
  60 seconds of screen-off time for a service that was not promoted at once); whether a wake lock is needed; whether swiping the app out of Recents ends it;
  what Bedtime mode and battery saver do to it.
- **What the user must do (Proposal, to be confirmed by the probe):** start the app once from the watch app list; leave the ongoing notification in place; keep the
  watch worn; do not use the watch's "stop" or swipe-away on it; keep Bedtime mode and battery saver off if they stop it. The app shows one big state on its home
  screen: "Monitoring" or "Not monitoring".
- **When Android kills it:** the service does not restart by itself in a useful way, since D9 says the user starts it. **Proposal:** the watch writes a
  "monitoring" state with its time into `/status/watch` (Shared lane, protocol change) and renews it every few minutes; the phone shows "watch not monitoring" when
  the state is stale or off, so the user learns it stopped. No alert is raised for that on the watch.
- Effect on permissions (D7, section 5): only the foreground heart-rate permission is needed, so the second "background" prompt disappears. The notification
  permission (`POST_NOTIFICATIONS`, API 33 and up) is probably needed for the ongoing notification to show: Unverified.

### 11.3 What changes in the decided design

| Item | Before (passive route) | With D9 |
|---|---|---|
| Latency | Measured delay up to about 10 minutes (section 6) | Expected seconds (Unverified until probed) |
| Cooldown (5 min), level 2 every 2 min, cap 6, "I'm OK" | Decided | Unchanged |
| Baseline (20th percentile of hourly lows, 7 days, after 24 values) | Accumulates around the clock | Accumulates **only while the app runs** |
| Fallback 110 bpm | Until 24 hourly values | Until 24 hourly values exist; longer in calendar time |

**The baseline under D9.** An hourly value needs at least 3 consecutive samples in a clock hour. So the baseline is known once the app has run, with the watch worn,
in 24 distinct clock hours within the last 7 days. Running 8 hours a day takes three days; running 2 hours a day takes twelve days, and the 7-day window then
never holds 24 values (at most 14), so **the 110 bpm fallback would stay forever**. Daily use must therefore cover at least 4 hours a day for the baseline to be
reached at all.

**A bias (Deferred, low priority; user decision below).** Resting heart rate is lowest at night. If the app does not run at night, the hourly lows come from the day, the baseline is
higher, and the threshold (baseline plus 30) is higher, so fewer alerts. If the user starts the app only when they feel agitated, the lows are taken from
unusual hours and the baseline is higher still. Options: (a) accept this; (b) run it also while sleeping (battery cost, section 11.4); (c) keep the baseline from the last
good run and do not forget it when the app is stopped (the 7-day window is then measured in running hours, not calendar days). Option (c) is the extended flow.

**Decided (user, verbatim):** "Baseline will be defined by user as measured separately for now, the extended flow is deferred to later stages". The extended flow
(keep the last good baseline, count running hours instead of calendar days) is **deferred, low priority**.

### 11.4 Battery

Unverified, no figure from Google or Samsung was found. Google says only that a registered callback raises the sensor sampling rate and so the power use
(11.1). The probe records battery percentage at start and end of each run and the display state, so the cost per hour on this watch with the screen off, dim and on
can be stated from measurements. Until then the document makes no battery promise.

### 11.5 Probe on the real watch (Proposal; code follows in a separate debug-only change)

Debug-only (`wear/src/debug/`, package `debug`), counts and times only, never values in the report:

1. `measure-capabilities`: whether `HEART_RATE_BPM` is in `supportedDataTypesMeasure`.
2. `measure-start`: a `health` foreground service with an ongoing notification and `OngoingActivity` registers a `MeasureCallback` for `HEART_RATE_BPM`.
   Without `READ_HEALTH_DATA_IN_BACKGROUND` granted, to test 11.2.
3. `measure-report`: number of readings, the interval between them (median, 95th percentile, longest gap), counts per display state (on, doze, off), availability
   changes with their time, and the battery percentage at start and now.
4. `measure-stop`, `measure-clear`.

User run (30 minutes worn, started by the user; a checklist step 6 will be added to `watch-probe-checklist.md` with the code): 10 minutes screen on,
10 minutes screen dim or allowed to go always-on, 10 minutes with the wrist down so the screen goes off. Then `measure-report`. Pass: readings about once a second in all three
states, or the exact behaviour per state is recorded; the service survives 30 minutes; no background permission was needed.

### 11.6 What I need from the user

1. Run the 30-minute probe of 11.5 when its code is merged.

## 12. Findings and direction so far (2026-10-17)

Written from the user's runs on the Galaxy Watch6 Classic (Wear OS 6) and the user's decisions in [issue #55](https://github.com/shlomoa/my-adhd-pal/issues/55).
Facts are measurements or quotes; each says where it comes from.

### 12.1 What the user defined (Decided, 2026-10-17)

The acceptance test for every measurement route, in the user's words:

- heart rate is **measured continuously**;
- heart rate is **compared to the baseline continuously**;
- a **stress event is reported immediately** and the **local response is activated**.

Separate decision **D9** (Decided): heart rate is measured **only while the watch app is running** (started by the user), not as an always-on background
service. The route that meets the definition is not chosen yet; it is decided after the probes below.

### 12.2 What Google says about delivery (Verified, read 2026-10-17)

[Health Services compatibility](https://developer.android.com/health-and-fitness/health-services/compatibility), passive monitoring: "Devices may return heart rate readings at
different intervals. Some devices may take reading every second. Other devices may take a reading every ten minutes. These intervals are not made available to apps."
The [background monitoring page](https://developer.android.com/health-and-fitness/health-services/monitor-background) says the callback "receives data at a slightly faster rate,
but only while the app is running". Neither page gives an interval for a given watch; it is measured below.

### 12.3 Measured on the user's watch

| Route | Result | When |
|---|---|---|
| Passive background listener, worn | 318 readings delivered together 7 min 41 s after start | 09:49 to 09:57 |
| Passive background listener, off the wrist | 1 reading in 10 minutes | 10:02 to 10:12 |
| Passive background listener, long run | Readings delivered together about every **10 minutes**, 577 to 598 per delivery, window 580 to 599 s, arriving 0.4 to 11.5 s after the window closes (12:45, 12:55, 13:05, 13:15, 13:25, 13:35) | 12:45 to 13:35 |
| Live `MeasureClient` in a foreground service, screen on, worn | Capability true; readings AVAILABLE 4 s after start; **734 readings in 5 minutes** (about 2 per second); gap median under 1 s, 95th percentile 1 s, longest **60 s** (cause unknown); battery 68% to 67% (too coarse to give a rate, reported as 11.7 %/hour) | 13:44 to 13:49 |
| Live `MeasureClient`, first E1 run, phase A: screen on, 10 minutes, worn. Times are ARRIVAL times in the app (`e1-results/20261018-003737`) | 462 readings in 610 s (45.4 per minute); gap median 0 s, 95th percentile 0 s, longest **374 s**; all arrivals `on`; availability only `ACQUIRING`, `AVAILABLE` at second 0; battery 97 to 96; notification listed | 00:42 to 00:52 |
| Same run, phase B: display dim (Always On Display), 10 minutes | total 750 readings (37.0 per minute): `on` 462, `doze` 288; `doze` longest gap **290 s**; battery 95; notification listed | 00:52 to 01:02 |
| Same run, phase C: screen off, 10 minutes (the user touched the watch for 111 s before the sleep key) | total 1912 readings (59.3 per minute) in 1934 s: `on` 1026 (longest gap **562 s**), `doze` 288, `off` 598 (longest gap **606 s**, longer than the 602 s phase); median and 95th percentile 0 s in every state; battery 93 (7.4 per hour, whole-percent resolution); notification listed | 01:04 to 01:14 |

Conditions of the three E1 rows above, which the earlier rows did not have, so **they are not comparable to the earlier live runs (the 5-minute run and the manual run of 2026-10-17 18:46 to 18:53) until the run with the background permission granted exists**:

- the script had REVOKED `READ_HEALTH_DATA_IN_BACKGROUND` and `BODY_SENSORS_BACKGROUND` before the start; the earlier probe steps had granted them with `pm grant` and a grant persists, so the earlier live runs very probably had them granted (unverified: the permission state was not logged then, and the E1 file does not log it either);
- the service was started 4.5 minutes after the `measure-stop`, `measure-clear` and `measure-capabilities` commands (the script waited for the user's answer), by the same `am start` of the activity as in the manual runs;
- the screen timeout was set to 720000 ms and the log buffer to 4 MB by the script;
- the times are ARRIVAL times of the callbacks, which run on the main executor of the app (`ContextCompat.getMainExecutor` in `health-services-client` 1.1.0), so a delayed main thread or a suspended CPU delays them; the sample times of the readings are not recorded in this run;
- the watch was on the wrist the whole time and the user touched it only at the Always On Display prompt between phases B and C (the user's answers, relayed by the coordinator).

No verdict is written from these numbers (E2 waits for the next run; analysis: issue #228).

Consequence: the passive background route delivers readings about every 10 minutes on this watch, so it does **not** meet "reported immediately". The live route delivers within
about a second with the screen on. Not measured yet: the live route with the display dim (always-on) and with the screen off, 30 minutes worn, battery over a longer time,
and whether the "Probe: measuring heart rate" notification is visible (the user saw no pop-up; the notification list was not checked).

### 12.4 Other findings (from earlier sections)

- The Samsung stress value cannot be read by an ordinary app; Samsung's partner programme is the only route (section 9).
- Movement (steps, activity state) can gate alerts later, after a probe (section 10).
- A plain app vibration (alarm usage, maximum amplitude, amplitude control available) was felt normally and **not** under Do Not Disturb (priority mode); the routes to get through are
  in section 4, untested.
- Every Gradle, npm and PyPI pin was at its latest stable release on 2026-10-17 ([tooling research](../research/tooling.md)).

### 12.5 Direction

1. The live route in a foreground service is the candidate that meets the definition; prove it with the live probe (checklist step 6): screen dim, screen off, 30 minutes, battery,
   the notification, the 60 s gap.
2. Until the route is decided, nothing is built on the passive background route.
3. Deferred, low priority (notes only, user, 2026-10-17): other biometrics, and the "monitoring" status item.
4. The Watch, Android and Shared lanes are stopped by the user until the route is decided; their open pull requests are not merged.
