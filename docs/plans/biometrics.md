# Biometric alerts: decisions (step 6.2, gate H1)

Decision document for step 6.2 ([issue #55](https://github.com/shlomoa/my-adhd-pal/issues/55)). The user's answers to the
proposal are in the body of the issue (section "Decisions"). **Confirmed by the user on 2026-10-16, with the last two items:**
"the two remaing items: "I'm OK" and the cap of 6 repeats is approved". Nothing in section 8 is open. Gate H1 flips in a
separate one-line PR. Research notes: [`../research/watch.md`](../research/watch.md) sections 3 to 5. Steps 6.3 to 6.8 in
[`watch.md`](watch.md) follow once H1 and G2b are merged.

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
| D6 | Latency | Decided | Best effort. Readings are batched by the system at an interval the app is not told (section 6). No maximum delay is promised. |
| D7 | Permission | Decided, feasibility checked | Requested from the **phone** app (onboarding). Section 5. |
| D8 | Privacy | Decided | Raw readings are never logged in release builds; raw feed only in `wear/src/debug/`. `/alert` carries the numeric value (already accepted). |

## 2. The resting baseline (D3)

**What the platform offers on the watch.**

- Verified: Health Services passive monitoring delivers `HEART_RATE_BPM` samples; sampling frequency and batching vary by
  device and are not told to the app
  ([compatibility](https://developer.android.com/health-and-fitness/health-services/compatibility)).
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
It is derived on the watch from passive samples (no new permission, works without Samsung Health):

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
two minutes). It needs a second passive data type and permission and is a source of false alerts until then: walking fast or
climbing stairs can exceed resting + 30.

## 3. Cooldown and escalation (D4)

Decided by the user:

- **Level 1** at the first reading above the threshold.
- **Cooldown 5 minutes** after it, counted between reading times (batching may deliver late).
- If the **next reading is still above** the threshold when the cooldown ends: **level 2**, a stronger alert. After that, level 2
  repeats **every 2 minutes** while the rate stays above the threshold.
- The episode ends when a reading is at or below the threshold. The next breach starts again at level 1.
- Each alert (level 1 and every level 2 repeat) sends `/alert/<id>` with its `level` (protocol change, section 7); the **phone
  raises its own alert for each one** (section 4a).
- Batching can make the 2-minute rhythm irregular: an alert can only follow a reading, and readings may arrive late or in
  groups (section 6). The rhythm is "at most one alert per 2 minutes, as soon as a reading above the threshold is evaluated".

Decided by the user (2026-10-16: "the two remaing items: "I'm OK" and the cap of 6 repeats is approved"): level 2 stops after **6
repeats** per episode (12 minutes at 2-minute intervals), and the full-screen alert on the watch and on the phone has an
**"I'm OK" button** that ends the episode early. Without these a high rate during exercise would alarm the user for as long
as it lasts.

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

A short probe (debug-only, 6.4 first task) on the Galaxy Watch6 Classic:

1. `getCapabilitiesAsync()` reports passive `HEART_RATE_BPM`.
2. Register a passive listener; record arrival times and sample counts with the screen on, off and while the watch sits on a
   desk, for at least 24 hours. This sets what "best effort" means (D6) and whether the 24-hourly-values baseline in section 2
   is reachable. If readings arrive only every several minutes, or not at all in the background, the user decides whether
   biometric alerts are still worth building (Samsung may restrict third-party background heart rate:
   [forum thread](https://forum.developer.samsung.com/t/read-heart-rate-every-5-minutes-in-background-service-on-wear-os/34328),
   secondhand).
3. The vibration/DND/Bedtime/intensity checks of section 4, and the remote permission flow of section 5.

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
| Passive listener, baseline store (hourly values), alert activity with full-screen intent, vibration patterns | Watch lane (6.3, 6.4, 6.6) | D5 |
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

**Nothing is open.** This is the user's confirmed document for step 6.2 (confirmed 2026-10-16). The remaining "Proposal" marks in
section 4 (exact vibration patterns, screen and sound details) are implementation details that tests in 6.6 pin down, not
questions for the user. The one-line PR that flips H1 follows.
