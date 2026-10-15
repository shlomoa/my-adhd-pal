# Spike 0b.2a run checklist: Data Layer on paired emulators

Throwaway spike (never merged to `main`). Branch: `claude/shared-0b2a-spike`. It answers four questions from
[`plan.md`](../plan.md) section 3 on a **Wear OS emulator paired with a phone emulator**:

1. Items cross the pairing, in both directions.
2. Items written while the receiver is away arrive after it comes back.
3. The phone's listener service wakes with its app closed.
4. A consumed item can be deleted and does not reappear.

Real-hardware questions (Nearby/Relayed/Away, urgent-item delay, Samsung battery management) are step 0b.2b, not this
one. Delays measured on emulators are only indicative.

## 0. What the spike app does

Both apps (`:phone` and `:wear`, same `applicationId`) get a launcher entry **Spike** with buttons, and a manifest
listener `SpikeListenerService` for `wear://*/spike*`. Items are written to `/spike/<kind>/<uuid>` with the keys
`from`, `id`, `kind`, `sentTs`.

| Button | Effect |
|---|---|
| Send DEL urgent / non-urgent | puts an item; the **receiver deletes it** after reading it |
| Send KEEP urgent | puts an item; the receiver leaves it |
| List data items | logs every item currently stored on this device |
| List connected nodes | logs the connected nodes and whether each is nearby |
| Refresh log / Clear log | shows or clears the last lines of this device's log |

Every action writes one line starting with a millisecond timestamp, to `adb logcat` (tag `SPIKE`) and to the file
`files/spike.log` of the app. Fields to read: `role` (phone or watch), `pid`, `processUptimeMs` (a few hundred ms
means the system just started the process for this event), `fg` (app in the foreground), then the event:
`SEND`, `SEND-OK`, `RECV ... delayMs=`, `DELETE-OK`, `EVENT-DELETED`, `EVENT-OWN-WRITE ignored`, `ITEMS`, `NODES`,
`SERVICE-CREATED`.

## 1. Set up (once)

1. Android Studio (current stable), SDK Manager: install a **phone system image with Google Play** (a Pixel device
   definition, API 36 if available) and a **Wear OS system image** (Large Round; API 34 = Wear OS 5, the reference
   watch generation, or API 36).
2. Device Manager: create one phone AVD and one Wear OS AVD from those images and start both.
3. Pair them with Device Manager's **Pair Wearable** assistant (follow it to the end: the phone emulator needs the
   Wear OS app set up and signed in with a Google account). Confirm the phone's Wear OS app shows the watch as
   connected.
4. In a terminal in this repository: `git fetch origin && git checkout claude/shared-0b2a-spike`.
5. Build: `./gradlew :phone:assembleDebug :wear:assembleDebug` (needs the Android SDK; see the README).
6. Find the serials: `adb devices -l`. Below, `PHONE` and `WATCH` stand for the two serials (for example
   `emulator-5554` and `emulator-5556`).
7. Install (do **not** use `installDebug`, it targets every connected device):

   ```sh
   adb -s PHONE install -r phone/build/outputs/apk/debug/phone-debug.apk
   adb -s WATCH install -r wear/build/outputs/apk/debug/wear-debug.apk
   ```

8. Open two terminals and leave them running:

   ```sh
   adb -s PHONE logcat -s SPIKE
   adb -s WATCH logcat -s SPIKE
   ```

9. On each emulator open the **Spike** app once (it must be opened once because a freshly installed app is in the
   "stopped" state).

## 2. Cases

Mark each case PASS or FAIL and copy the **SPIKE log lines** of the case. If something looks odd, report it
anyway: unexpected behaviour is the point of the spike.

### C1 Items cross the pairing (question 1)

1. On the **watch**: tap *Send DEL urgent*.
2. Expect on PHONE logcat: `RECV path=/spike/del/... from=watch ... delayMs=<n>`, then `DELETE-OK`.
3. Expect on WATCH logcat: `SEND-OK`, then `EVENT-DELETED` for the same path.
4. On the **phone**: tap *Send DEL urgent*; expect the mirror image (RECV on the watch, `EVENT-DELETED` on the phone).
5. Tap *List data items* on both: the deleted items must not be listed.

### C2 Deletion sticks (question 4)

1. On the watch tap *Send KEEP urgent*. On the phone tap *List data items*: the `/spike/keep/...` item **is** listed
   (a stored item stays until someone deletes it).
2. Do C5 (disconnect and reconnect) and then *List data items* on both again: the DEL items from C1 must **not**
   have reappeared; the KEEP item should still be there.

### C3 Listener wakes with the phone app closed (question 3)

1. On the phone emulator open Recents and **swipe the Spike app away**. Check the process is gone:
   `adb -s PHONE shell pidof com.shlomoa.myadhdpal` prints nothing.
2. On the watch tap *Send DEL urgent*.
3. Expect on PHONE logcat, without touching the phone: `SERVICE-CREATED`, then `RECV ... fg=false`,
   with a small `processUptimeMs`. PASS = the item arrived while the app was closed.
4. Repeat the stricter variant: `adb -s PHONE shell am force-stop com.shlomoa.myadhdpal`, check `pidof` is empty,
   send from the watch again. Record whether `RECV` appears **without** opening the app. If it does not, open the
   Spike app on the phone and record whether the item then arrives.

### C4 Urgent versus non-urgent

1. From the watch send 3 × *DEL urgent* and 3 × *DEL non-urgent* (wait for each `DELETE-OK` first).
2. Copy the six `delayMs` values from PHONE logcat. Repeat from the phone to the watch.

### C5 Disconnect and reconnect (question 2)

1. **Watch away**: in Device Manager stop the **watch** emulator (a normal stop; do not wipe data).
2. On the phone tap *Send DEL urgent* and *Send KEEP urgent*. Expect `SEND-OK` on the phone; tap *List connected
   nodes* and record the result.
3. Start the watch emulator again (normal boot). Wait up to 3 minutes **without opening the Spike app on the
   watch**.
4. Expect on WATCH logcat: `RECV` for both items with a large `delayMs`, and `DELETE-OK` for the DEL one. Record how
   the devices reconnected: automatically, or only after a step (which one: reopening the Wear OS app, the Pair
   Wearable assistant, an adb command).
5. **Phone away**: repeat with the roles swapped (stop the phone emulator, send from the watch, start the phone, and
   do not open the phone app; expect `SERVICE-CREATED` and `RECV ... fg=false` on the phone).

## 3. Results to send back

Send everything through the coordinator, in this shape:

```text
Host OS / Android Studio version:
Phone image (name, API) / Watch image (name, API):
C1 PASS|FAIL  delayMs watch->phone / phone->watch:
C2 PASS|FAIL  (KEEP listed? DEL reappeared?):
C3 swipe-away PASS|FAIL, force-stop PASS|FAIL (RECV without opening the app? after opening?):
C4 delayMs urgent (x3) / non-urgent (x3), both directions:
C5 watch-away PASS|FAIL (items arrived? delayMs? how reconnected?), phone-away PASS|FAIL:
Anything unexpected (copy the SPIKE log lines):
```

The spike code is discarded in step 0b.3; nothing here is kept except the results table that goes into
`docs/protocol.md`.
