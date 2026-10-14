# Spike 0b.2b plan: Data Layer on the real watch and phone

Step 0b.2b of [`plan.md`](../plan.md) section 6. It answers what the emulators could not: Nearby, Relayed and Away,
urgent-item delay, Samsung battery management with default settings, and pairing through Samsung's Galaxy Wearable app.
It uses the same spike code as [0b.2a](../protocol.md) (already run on emulators), on the **Galaxy Watch6 Classic** and
the **Galaxy A55 5G**, and one command on a Windows 11 PC. Its result decides gate **G-DEC** (and therefore G2).

Everything the user does is in two lists below: **setup (about 15 minutes, once)** and **checklist (about 10
minutes)**. The script itself runs without taps for about 20 minutes. Target: **45 minutes in one sitting**. These
durations are estimates from the simulated run, not measurements on the devices.

## Decisions needed before the run

The plan says delay targets are agreed with the user first. Proposal (the script checks against these; change them with
the parameters in brackets if the user prefers other numbers):

| Situation | Proposed target | Parameter |
|---|---|---|
| Nearby (Bluetooth connected), urgent item | median round trip of **10 s or less** (about 5 s each way) | `-NearbyMaxRttMs 10000` |
| Relayed (phone Bluetooth off, both online), urgent item | median round trip of **60 s or less** (about 30 s each way) | `-RelayedMaxRttMs 60000` |
| Away, then the phone comes back | all four test items arrive on their own within **180 s** | `-AwayRecoverSec 180` |
| Phone app closed, forced Doze or restricted standby, urgent item | arrives within **120 s** | `-DozeWaitSec 120` |

Non-urgent round trips are measured and reported, but do not decide pass or fail. If a target is missed, the fallback
rule in `plan.md` section 3 applies (`MessageClient` for urgent alerts only); the numbers above are what makes "too
slow" concrete.

## What the user does, and what the script does

| The user does (and nothing else) | The script does |
|---|---|
| Setup part A to C below, once | Checks both devices and says which one is not ready and why |
| Keeps the PC awake and the watch on its charger during the run | Builds and installs both spike apps (`gradlew`, `adb install`) |
| Tells the script the watch's `ip:port` | Connects to the watch (`adb connect`) when it dropped off |
| Answers a prompt **only if** adb cannot switch a radio (see "When the script asks you to tap") | Starts the apps, sends every test item, reads the logs |
| Does the checklist (part E) | Switches the phone's Bluetooth, Wi-Fi and mobile data with adb; forces Doze; sets standby buckets |
| Tells the coordinator the results branch name | Closes the phone app by `am kill` and `am force-stop` |
| | Restores the phone's radios and settings at the end, also when a case fails |
| | Writes the results table and raw logs, pushes them as a branch |

## Part A. The phone (Galaxy A55 5G, over USB)

1. Settings > About phone > Software information > tap **Build number** 7 times, enter the PIN. Developer options now
   appear at the bottom of Settings.
2. Settings > Developer options > turn **USB debugging** on.
3. Settings > Security and privacy > **Auto Blocker**: if it is on, turn it off for the run. Auto Blocker can block
   commands over the USB cable, which would stop `adb`. (Off is the factory default.)
4. Plug the phone into the PC with the USB cable. Unlock it. If the USB notification asks, choose **File transfer**.
   Tap **Allow** on "Allow USB debugging?" and tick **Always allow from this computer**.
5. In PowerShell, in this repository (see [`../dev-setup-windows.md`](../dev-setup-windows.md) section 6.1 step 3 for
   putting `adb` on `PATH`):

   ```powershell
   adb devices -l
   ```

   Expected: one line ending in `device` for the phone, for example
   `R58W20ABCDE   device usb:1-3 product:a55xxx model:SM_A556E ...`. If it says `unauthorized`, unlock the phone and
   tap Allow. If the list is empty, try another cable or port.

## Part B. The watch (Galaxy Watch6 Classic, over Wi-Fi debugging)

The watch has no USB data port for adb, so it is driven over the Wi-Fi network. The watch and the PC must be on the same
Wi-Fi network (not a guest network that isolates devices).

1. On the watch: Settings > **Connections** > **Wi-Fi**: set it to **On** (not "Auto" and not "Off"), and connect it to
   the same network as the PC. With Auto the watch turns Wi-Fi off while it is connected to the phone, and adb is lost.
2. On the watch: Settings > **About watch** > **Software information** > tap **Software version** 5 times until
   "Developer mode turned on" appears. (The menu names can differ slightly between firmware versions; the path is
   under About watch.)
3. On the watch: Settings > **Developer options**: turn **ADB debugging** on (confirm the dialog), then open
   **Wireless debugging** and turn it on (confirm). The screen shows an **IP address and port** such as
   `192.168.1.57:41203`. Write it down; this port is used in part C step 2.
4. On the watch: in Wireless debugging tap **Pair new device**. It shows a **6-digit code** and a **pairing address and
   port** such as `192.168.1.57:37099` (a different port from step 3). Leave this screen open, it expires.
5. On the PC, within a minute or two:

   ```powershell
   adb pair 192.168.1.57:37099
   ```

   Type the 6-digit code when asked ("Enter pairing code:"). Expected: `Successfully paired to 192.168.1.57:37099
   [guid=adb-...]`. (You can instead pass `-PairWatch 192.168.1.57:37099 -PairCode 123456` to the script.)
6. Put the watch **on its charger** and keep the screen on (touch it). A sleeping watch drops its Wi-Fi connection and
   adb with it.

The pairing in step 5 is done once. Later runs only need part C step 2, because the connect port changes whenever
Wireless debugging is switched off and on (read it from the watch screen again).

## Part C. The PC and the code

1. Turn off sleep for the run (the emulator run was lost when the laptop went to sleep): plug in the laptop, then
   `powercfg /change standby-timeout-ac 0`. (Undo later with `powercfg /change standby-timeout-ac 30`.)
2. Connect to the watch and check both devices (replace the address with the one from part B step 3):

   ```powershell
   adb connect 192.168.1.57:41203
   adb devices -l
   ```

   Expected: `connected to 192.168.1.57:41203`, then two lines ending in `device`: the phone's USB serial and
   `192.168.1.57:41203`. If the watch line says `offline`, run `adb disconnect` and connect again.
3. Get the spike branch (it is never merged; it contains the apps and the runner). Before switching, run `git status`;
   if it lists changes run `git stash`:

   ```powershell
   git fetch origin
   git checkout claude/shared-0b2b-spike
   ```

   The build needs JDK 21 as in [`../dev-setup-windows.md`](../dev-setup-windows.md) section 3.

## Part D. Run it (one command)

```powershell
.\scripts\spike\run-0b2b.ps1 -WatchAddress 192.168.1.57:41203
```

Do not use the phone or the watch while it runs (a locked phone is fine; the script keeps its screen on).

What you see, in order (taken from the simulated run, so the numbers differ on real devices):

1. `Devices: phone = R58W..., watch = 192.168.1.57:41203`, then the **readiness table**, twice (before the build and
   after the install):

   ```text
   Readiness (before build and install):
     phone  adb:       device        boot:1   app:-          80%    READY
     watch  adb:       device        boot:1   app:-          80%    READY
   ```

   If a device is not ready the table says which one and why, then **"Do this:"** with the fix, and the script stops
   with exit code 2 **without having changed anything** on the devices. Typical reasons: `unauthorized` (tap Allow),
   `offline` (reconnect the watch), adb calls time out (wake the watch, put it on the charger), battery below 20%.
2. `Building the spike apps` with Gradle task lines (a few minutes the first time), then `Installed ... on the phone`
   and `... on the watch`.
3. `Link is up: phone sees 1 node(s), watch sees 1; nearby = True`. If the phone and watch do not see each other the
   script stops (exit 2) and tells you to open Galaxy Wearable and check that the watch shows **Connected**.
4. One line per case as it finishes, for example `C1: PASS ...`, `L2: PASS ...`. About 20 minutes in total.
5. The results table, `Results saved to ...`, and finally **`PUSHED results branch: spike-results/0b2b-<timestamp>`**.
   That branch name is all the coordinator needs from you. If the push fails, the results are on your PC and the
   script prints the one git command to retry.

### What each case does

| Case | What it does | Pass means |
|---|---|---|
| C1, C1b, C2a | Items cross both ways; immediate and delayed deletes both ways, repeated; a KEEP item stays | as in 0b.2a, now on hardware; tests the writer-owns-cleanup rule |
| C3a, C3b | Phone app killed, then force-stopped; the watch sends | the phone's listener receives with the app closed (`fg=false`) |
| C3c | Phone screen off and Doze forced (`dumpsys deviceidle force-idle`), app closed; the watch sends urgent and non-urgent | the urgent item arrives within `-DozeWaitSec` with the app closed |
| C3d | App in the `rare` and then the `restricted` standby bucket (`am set-standby-bucket`), app closed | the urgent item arrives within `-DozeWaitSec` |
| L1 Nearby | Round trips on the sender's own clock: 3 urgent and 3 non-urgent from each side, after 2 warm-ups | median urgent round trip within `-NearbyMaxRttMs` both ways |
| L2 Relayed | Phone Bluetooth off (`svc bluetooth disable`); waits until the watch lists the phone as a connected node that is not nearby; round trips | state seen within `-RelayedDetectSec`; median urgent within `-RelayedMaxRttMs` |
| L3 Away | Phone Bluetooth, Wi-Fi and mobile data off; waits until the watch sees no phone; the phone and the watch each send a DEL and a KEEP item (phone app then closed); the radios come back; the script does nothing else | Away seen within `-AwayDetectSec`; all four items arrive on their own within `-AwayRecoverSec`; the phone received with `fg=false` |
| C2b | After L3: deleted items did not reappear; the KEEP item is still there | both true on both devices |

A failure to **observe** a state (Relayed or Away never shows on the watch) is reported as FAIL with the last node
list: it is a finding about the link-state design, not a script error.

### When the script asks you to tap

adb switches the phone's Bluetooth, Wi-Fi and mobile data with `svc` and checks the setting changed. If it did not
change (some firmware ignores `svc bluetooth`), the script stops and prints, for example:

```text
MANUAL STEP: adb could not switch the phone's Bluetooth OFF. Swipe down on the phone and set Bluetooth to OFF.
Press Enter when done, or type s to skip this case
```

Do exactly that and press Enter. Typing `s` skips that case (reported NOT RUN, exit code 2). The script always
turns the radios on again at the end. If it is killed or you close the window mid-run, run:

```powershell
.\scripts\spike\run-0b2b.ps1 -RestorePhone
```

which turns Bluetooth, Wi-Fi and mobile data on, ends the forced Doze and puts the standby bucket back.

### Exit codes

| Code | Meaning |
|---|---|
| 0 | every selected case passed |
| 1 | at least one case FAILED (a finding; the results are still published) |
| 2 | a device is not ready (nothing changed), or a case could not run (NOT RUN) |
| 3 | setup problem: adb or git missing, device not found, pairing or build failed, unknown case name |

Useful options: `-OnlyCases L2,L3` runs just those cases, `-SkipBuild` reuses the installed apps, `-SkipPush` keeps the
results local, `-NonInteractive` never asks you to tap (the step is then NOT RUN).

## Part E. Checklist for the user (9 items, one sitting)

| # | When | Item | Pass criterion (write the answer down or tell the coordinator) |
|---|---|---|---|
| 1 | Before | Pairing went through the **Galaxy Wearable** app, not the Google Wear OS app | Galaxy Wearable on the phone shows the watch as **Connected**; say whether the pairing was fresh for this test or existing |
| 2 | Before | Software versions (the script reads them; fill in only what the report leaves empty) | Phone: Settings > About phone > Software information: Android and One UI version. Watch: Settings > About watch > Software information: Wear OS and One UI Watch version |
| 3 | Before | Phone battery management is at its **default** | Settings > Battery > Background usage limits: write down **Put unused apps to sleep**, **Auto disable unused apps** and **Adaptive battery** (On or Off). Nothing was changed by you for this test |
| 4 | Before | The spike app is **not exempted** | After the install, Settings > Apps > the app named **Spike** > Battery is **Optimized** (not Unrestricted), and the app is not in **Never sleeping apps** |
| 5 | Before | Laptop plugged in, sleep off, watch on its charger with the screen on | Part B step 6 and part C step 1 done |
| 6 | During | Only if the script prints MANUAL STEP: do what it says | The radio named is in the stated state; press Enter |
| 7 | After | Phone back to normal | Bluetooth, Wi-Fi and mobile data are on; Galaxy Wearable shows **Connected** again within 2 minutes |
| 8 | After | Send the results | Tell the coordinator the `spike-results/0b2b-...` branch name and the answers to items 1 to 4 |
| 9 | Later, optional, no time now | Samsung "sleeping apps" over days | Do not open the Spike app for 3 days, then run `.\scripts\spike\run-0b2b.ps1 -SkipBuild -OnlyCases C3`. The result says whether Samsung's own sleeping-apps list stops the listener |

Item 9 matters for the real risk row in `plan.md` section 8 ("Samsung battery management"): the sleeping-apps list
only acts on apps unused for several days, which cannot be forced in a 45-minute sitting. It needs no other work.

## What happens with the results

The script publishes the branch `spike-results/0b2b-<timestamp>` (only the folder `docs/spikes/results/0b2b-<timestamp>`:
`results.txt` plus the raw per-case logs, with e-mail addresses, tokens, device serials and IP addresses removed). The
shared lane then writes the hardware results table into [`../protocol.md`](../protocol.md) with the device and version
details, decides G-DEC with the coordinator (fallback rule if a criterion failed), records the decision in the work log
and discards the spike code (step 0b.3).

## What is not verified

The runner was tested against a **simulated adb** with hardware-style serials (a USB serial and `192.168.x.x:port`);
`scripts/spike/test-run-0b2b.sh` on the spike branch runs 65 checks of its flow, readiness gate, radio handling and
restore, report, exit codes and publishing. It has **not** been run on the Galaxy A55 5G, the Galaxy Watch6 Classic or
Windows PowerShell 5.1 (only PowerShell 7 on Linux). Unverified on the real devices:

- the watch click paths and menu names in part B, and the Auto Blocker setting, are from memory of Samsung and Wear OS
  documentation, not read from these devices;
- whether `svc bluetooth disable`, `svc wifi`, `svc data`, `dumpsys deviceidle force-idle`, `dumpsys battery unplug` and
  `am set-standby-bucket` take effect without root on One UI 7 (the script checks each and falls back or reports NOT RUN);
- whether Google Play services reports a Bluetooth-off phone as a connected, not-nearby node (Relayed), and how
  quickly it reports no node when the phone has no radio (Away); both are measured and reported, and may FAIL;
- the **watch cannot be made Away** while adb runs over its Wi-Fi, so only phone-side isolation is tested; the
  watch-away direction passed on emulators (0b.2a) and is not repeated on hardware;
- the Wi-Fi debugging connection can drop when the watch sleeps; the script reconnects at the start of a run only.

## Troubleshooting

| Symptom | Fix |
|---|---|
| `adb` is not recognized | Add the SDK `platform-tools` folder to `PATH` ([`../dev-setup-windows.md`](../dev-setup-windows.md) section 6.1) |
| `adb pair` says `Failed: Wrong password` | The code and port change every time you open Pair new device; read them again and run again quickly |
| `failed to connect` for the watch | Same Wi-Fi network? Watch Wi-Fi set to On? Wireless debugging still on? The port changed? |
| The phone asks "Scan app with Play Protect?" during install | Tap **Install anyway** or **Don't send** on the phone |
| `INSTALL_FAILED_UPDATE_INCOMPATIBLE` | Uninstall the old build: `adb -s <serial> uninstall com.shlomoa.myadhdpal`, then run again |
| Exit code 2 with "not ready" | Read the line after "Do this:" |
| The phone seems stuck with Wi-Fi or Bluetooth off after a crash | `.\scripts\spike\run-0b2b.ps1 -RestorePhone` |
