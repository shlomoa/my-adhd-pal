# Windows setup: build, emulators and the Data Layer spike

For Windows 11 with PowerShell and Android Studio. It takes you from a fresh machine to the two paired emulators and
the spike 0b.2a run (see [`plan.md`](plan.md), steps 0b.2a and 0b.2b). Linux and macOS differ only in the shell
commands (`./gradlew` instead of `.\gradlew.bat`, forward slashes).

**Status:** written from the Android Studio, Gradle and Wear OS documentation and not yet followed end to end on a
Windows machine. Anything that does not match what you see is a defect in this guide: report the step number and the
exact message, and the guide is fixed.

## 1. What you need

| Item | Notes |
|---|---|
| Windows 11, 16 GB RAM or more | Two emulators run at the same time. |
| About 30 GB free disk | Android Studio, SDK, two system images. |
| Virtualization on | Turned on in the BIOS or UEFI, and the Windows feature *Windows Hypervisor Platform* (or Hyper-V) enabled. See section 2. |
| Git | `git --version` works in PowerShell. |
| Android Studio, current stable | Includes the SDK Manager, Device Manager and `adb`. |
| JDK 21 | The build requires Java 21. See section 3. |
| A Google account | Needed once to pair the emulators (section 5). A throwaway account is fine. |

## 2. Check that the emulator can run accelerated

1. Open *Turn Windows features on or off*, enable **Windows Hypervisor Platform** (and **Virtual Machine Platform**),
   and restart if asked.
2. After Android Studio is installed, check in PowerShell:

   ```powershell
   & "$env:LOCALAPPDATA\Android\Sdk\emulator\emulator.exe" -accel-check
   ```

   It should report that acceleration can be used. If it reports that virtualization is disabled, enable it in the
   BIOS or UEFI (names vary: Intel VT-x, SVM Mode, Virtualization Technology) and run the check again.

## 3. Install JDK 21 and open the project

The `:protocol` module requires a Java 21 toolchain. Without one, the Gradle sync fails with *Cannot find a Java
installation on your machine ... matching: {languageVersion=21 ...}*.

1. In PowerShell:

   ```powershell
   winget install EclipseAdoptium.Temurin.21.JDK
   ```

   If `winget` is not available, download the **Temurin 21 JDK** `.msi` from adoptium.net and enable the options that
   set `JAVA_HOME` and add Java to `PATH`.
2. Close PowerShell, open a new one, and check:

   ```powershell
   java -version
   ```

   It must report version 21. `where java` shows where it is installed.
3. Clone and open the repository:

   ```powershell
   git clone https://github.com/shlomoa/my-adhd-pal.git
   ```

   In Android Studio choose **Open** and select the `my-adhd-pal` folder. If you cloned it earlier, stay on whichever
   branch is checked out: sections 3 to 5 do not depend on the branch.
4. Open **File > Settings > Build, Execution, Deployment > Build Tools > Gradle**. Set **Gradle JDK** to the Temurin
   21 entry. If it is not listed, choose **Add JDK from disk** and select the install folder, usually
   `C:\Program Files\Eclipse Adoptium\jdk-21...`. Click OK.
5. If Android Studio offers to install missing SDK pieces (the project needs **Android SDK platform 37**), accept.
   You can also install it in **Tools > SDK Manager > SDK Platforms**.
6. Run **File > Sync Project with Gradle Files**.
7. If the sync still reports no matching Java installation, open the **Terminal** tab, run `.\gradlew.bat --stop`,
   restart Android Studio and sync again.
8. Check the setup in the Terminal tab:

   ```powershell
   .\gradlew.bat :protocol:test
   ```

   It must end with `BUILD SUCCESSFUL`.

## 4. Create the two emulators

You can do this even while the Gradle sync is failing: Device Manager does not use Gradle.

1. Open **Device Manager** (right-hand toolbar). Click **+** and choose **Create Virtual Device**.
2. **Phone.** Category **Phone**, a **Pixel** model, **Next**. On the system image screen pick the newest API
   available (36 if offered) whose **Play Store** column shows the Play Store icon. Download it if needed, then
   **Finish**. An image without the Play Store icon cannot be paired.
3. **Watch.** **+** again, category **Wear OS**, the **Large Round** profile, **Next**. Pick a Wear OS image (API 34
   or 36), download it and **Finish**.
4. Start both with their play buttons. Wait until both show a home screen.

## 5. Pair them

1. In Device Manager open the menu (three dots) of the **phone** row and choose **Pair Wearable** (in some Android
   Studio versions it is a button on the row, or under **View > Tool Windows > Device Manager**).
2. In the assistant select the watch emulator.
3. The phone emulator needs the **Wear OS** companion app set up and signed in with your Google account. You may
   have to open the Play Store on the phone emulator, sign in, and update the *Wear OS* app and *Google Play
   services*. Follow the assistant to the end.
4. Success: the Wear OS app on the phone shows the watch as connected.
5. If pairing does not complete, restart both emulators and run the assistant again. Wear OS emulators are
   sensitive here. Report the step and, if you can, a screenshot.

## 6. Run the spike (0b.2a)

The spike apps are the normal `:phone` and `:wear` apps plus an extra launcher icon named **Spike**. Its screen has
buttons that send small test items between the phone and the watch through the Wearable Data Layer and log what
arrives. The code is on the throwaway branch `claude/shared-0b2a-spike` and is never merged. The question it
answers: do items cross the pairing, survive a disconnect, wake the phone's listener with its app closed, and stay
deleted once deleted.

### 6.1 Build and install

1. In the Terminal tab (before switching branches run `git status`; if it lists changes, run `git stash`):

   ```powershell
   git fetch origin
   git checkout claude/shared-0b2a-spike
   ```

   Android Studio asks to sync Gradle again: choose yes.
2. Build both apps:

   ```powershell
   .\gradlew.bat :phone:assembleDebug :wear:assembleDebug
   ```

3. Make `adb` available in this PowerShell window:

   ```powershell
   $env:Path += ";$env:LOCALAPPDATA\Android\Sdk\platform-tools"
   adb devices -l
   ```

   Two lines such as `emulator-5554` and `emulator-5556` appear. The watch is the one whose model mentions a round or
   Wear device. Below, `PHONE` and `WATCH` stand for those two names.
4. Install. Do not use `installDebug`: it targets every connected device.

   ```powershell
   adb -s PHONE install -r phone\build\outputs\apk\debug\phone-debug.apk
   adb -s WATCH install -r wear\build\outputs\apk\debug\wear-debug.apk
   ```

5. Open two more PowerShell windows (each needs the `$env:Path` line from step 3) and leave them running:

   ```powershell
   adb -s PHONE logcat -s SPIKE
   adb -s WATCH logcat -s SPIKE
   ```

6. On each emulator open the app list and tap **Spike** once. This matters: a freshly installed app is in the
   "stopped" state until it has been opened once.

### 6.2 The buttons

| Button | Effect |
|---|---|
| Send DEL urgent / non-urgent | Puts an item; the receiver deletes it after reading it. |
| Send KEEP urgent | Puts an item; the receiver leaves it. |
| List data items | Logs every item stored on this device. |
| List connected nodes | Logs the connected devices and whether each is nearby. |
| Refresh log / Clear log | Shows or clears the last lines of this device's log. |

Every action writes one log line starting with a millisecond timestamp. Fields: `role` (phone or watch),
`processUptimeMs` (a few hundred means the system just started the process for this event), `fg` (app in the
foreground), then the event: `SEND`, `SEND-OK`, `RECV ... delayMs=`, `DELETE-OK`, `EVENT-DELETED`, `ITEMS`, `NODES`,
`SERVICE-CREATED`.

### 6.3 The five cases

Mark each case PASS or FAIL and copy the `SPIKE` log lines. Report anything unexpected: that is the point of the
spike.

**C1, items cross both ways.**

1. On the watch tap **Send DEL urgent**.
2. Phone log: `RECV path=/spike/del/... from=watch ... delayMs=<n>` then `DELETE-OK`. Watch log: `SEND-OK` then
   `EVENT-DELETED` for the same path.
3. On the phone tap **Send DEL urgent**; expect the mirror image on the watch.
4. Tap **List data items** on both: the deleted items must not be listed.

**C2, deletion sticks.**

1. On the watch tap **Send KEEP urgent**. On the phone tap **List data items**: the `/spike/keep/...` item is listed.
2. After C5, tap **List data items** on both: the C1 items must not have reappeared, and the KEEP item is still there.

**C3, the phone listener wakes with its app closed.**

1. On the phone emulator open Recents (the square button, or swipe up from the bottom and hold) and swipe the Spike
   app away. Check it is gone:

   ```powershell
   adb -s PHONE shell pidof com.shlomoa.myadhdpal
   ```

   It prints nothing.
2. On the watch tap **Send DEL urgent**. Without touching the phone, expect `SERVICE-CREATED` and then
   `RECV ... fg=false` in the phone log.
3. Stricter variant: `adb -s PHONE shell am force-stop com.shlomoa.myadhdpal`, check `pidof` prints nothing, send again.
   Record whether the item arrives without you opening the app. If not, open the Spike app on the phone and record
   whether it arrives then.

**C4, urgent versus non-urgent delay.**

1. From the watch tap **Send DEL urgent** three times and **Send DEL non-urgent** three times, waiting for each
   `DELETE-OK` first.
2. Copy the six `delayMs` values from the phone log. Repeat from the phone to the watch.

**C5, disconnect and reconnect.**

1. In Device Manager stop the **watch** emulator (a normal stop, no wipe).
2. On the phone tap **Send DEL urgent** and **Send KEEP urgent**; expect `SEND-OK`. Tap **List connected nodes** and
   note the result.
3. Start the watch emulator again. Without opening the Spike app on the watch, wait up to 3 minutes. Expect `RECV`
   for both items with a large `delayMs`, and `DELETE-OK` for the DEL one.
4. Note whether the devices reconnected by themselves or needed a step (reopening the Wear OS app, the Pair Wearable
   assistant, an adb command).
5. Swap roles: stop the **phone** emulator, send from the watch, start the phone and do not open its app. Expect
   `SERVICE-CREATED` and `RECV ... fg=false`.

### 6.4 Save the logs and report

Windows PowerShell 5.1 writes UTF-16 with `>`, so save with `Out-File`:

```powershell
adb -s PHONE logcat -s SPIKE -d | Out-File -Encoding utf8 phone-spike.log
adb -s WATCH logcat -s SPIKE -d | Out-File -Encoding utf8 watch-spike.log
```

Send the results in this shape:

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

### 6.5 Afterwards

Switch back with `git checkout main` (and `git stash pop` if you stashed). The spike branch stays unmerged; its code is
discarded once the results are recorded in the protocol document.

## 7. Troubleshooting

| Symptom | Cause and fix |
|---|---|
| Sync fails: *Cannot find a Java installation ... languageVersion=21 ... Toolchain download repositories have not been configured* | No JDK 21 found. Install Temurin 21 and set Gradle JDK (section 3). |
| `java -version` is not 21 in the terminal | Open a new PowerShell after the install; check `where java`; make sure the Temurin entry is first in `PATH`. |
| `adb` is not recognized | Add the SDK `platform-tools` folder to `PATH` for this window (section 6.1, step 3). |
| Emulator is very slow or refuses to start | Virtualization is off. Section 2. |
| No Spike icon on the emulators | You built a branch other than `claude/shared-0b2a-spike`. Check out the spike branch, rebuild, reinstall. |
| `INSTALL_FAILED_...` on a reinstall | Uninstall first: `adb -s PHONE uninstall com.shlomoa.myadhdpal`, then install again. |
