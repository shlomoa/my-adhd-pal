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

   `winget` installs without asking anything. If it is not available, download the **Temurin 21 JDK** `.msi` from
   adoptium.net and run it with the defaults. Do not count on the installer setting `JAVA_HOME` or `PATH` for you:
   step 2 checks, and step 3 sets them by hand if they are missing.
2. Find the install folder. In PowerShell:

   ```powershell
   Get-ChildItem "C:\Program Files\Eclipse Adoptium"
   ```

   There is a folder such as `jdk-21.0.5.11-hotspot`. Use its full path below as `JDK21`. (If the folder does not
   exist, look under `C:\Program Files\Java` or `C:\Program Files\Microsoft` and tell me what you find.)
3. Make Java 21 available to the command line:

   ```powershell
   $env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-21.0.5.11-hotspot"   # use your folder
   $env:Path = "$env:JAVA_HOME\bin;$env:Path"
   java -version
   ```

   `java -version` must report version 21. To see which `java` is being used, run `Get-Command java` (or
   `where.exe java`; plain `where java` prints nothing in PowerShell because `where` is an alias for
   `Where-Object`).

   **`JAVA_HOME` matters more than `PATH`.** `.\gradlew.bat` uses `JAVA_HOME` first and `PATH` only when
   `JAVA_HOME` is not set. If another JDK is installed (for example `C:\Program Files\Java\jdk-23`) and `JAVA_HOME`
   points at it, `java -version` can report 21 while Gradle runs on the other JDK, and Android Studio warns
   *Multiple Gradle daemons might be spawned because the Gradle JDK and JAVA_HOME locations are different*. Check:

   ```powershell
   echo $env:JAVA_HOME
   ```

   It must be the Temurin 21 folder. The two lines above fix this for the current PowerShell window only. To make it
   permanent, open *Edit the system environment variables > Environment Variables*:

   - If `JAVA_HOME` is a **user** variable, edit it to the Temurin 21 folder.
   - If it is a **system** variable (changing it needs administrator rights), leave it and add a **user** variable
     named `JAVA_HOME` with the Temurin 21 folder: a user variable overrides the system variable of the same name.
   - Make sure `%JAVA_HOME%\bin` is in the user `Path` (or that the Temurin `bin` folder comes before any other JDK).

   Then close all PowerShell windows, restart Android Studio, and check in a new PowerShell: `echo $env:JAVA_HOME`
   shows the Temurin folder and `.\gradlew.bat --version` (run in the repository folder, step 4) shows
   `JVM: 21`.
4. Clone and open the repository:

   ```powershell
   git clone https://github.com/shlomoa/my-adhd-pal.git
   ```

   In Android Studio choose **Open** and select the `my-adhd-pal` folder. If you cloned it earlier, stay on whichever
   branch is checked out: sections 3 to 5 do not depend on the branch.
5. Optional (it only affects syncs and builds started inside Android Studio; the spike builds from the terminal in
   step 9 and section 6, and Device Manager does not use Gradle, so you can skip this step if the field is blank or
   locked). Make sure the project is open (not the Welcome screen) and no sync or indexing is running, then open
   **File > Settings > Build, Execution, Deployment > Build Tools > Gradle**. Set **Gradle JDK** to the Temurin
   21 entry. If the field shows nothing, close the Settings dialog and open it again: Android Studio sometimes
   displays the field blank until the dialog is reopened. If it is not listed, choose **Add JDK from disk** and select the install folder, usually
   `C:\Program Files\Eclipse Adoptium\jdk-21...`. Click OK.
6. If Android Studio offers to install missing SDK pieces (the project needs **Android SDK platform 37**), accept.
   You can also install it in **Tools > SDK Manager > SDK Platforms**.
7. Run **File > Sync Project with Gradle Files**.
8. If the sync still reports no matching Java installation, open the **Terminal** tab, run `.\gradlew.bat --stop`,
   restart Android Studio and sync again.
9. Check the setup in the Terminal tab:

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
3. The first page of the assistant is **Install Wear OS Companion Application**: *Google Pixel Watch is not installed on
   the companion device*. The companion app is called **Google Pixel Watch** (formerly *Wear OS by Google*) and is
   installed on the **phone emulator**, not on your PC:
   1. Click **Open Google Pixel Watch in Play Store**. The Play Store opens inside the phone emulator window.
   2. If the Play Store asks you to sign in, sign in with your Google account (a throwaway account is fine) and accept
      any terms. This is the only place the account is needed.
   3. On the **Google Pixel Watch** page tap **Install** (or **Update** if it offers that) and wait for it to finish.
   4. Back in the assistant, scroll down or enlarge the window: it has more steps below the first screen. When it says
      the install is done, **Next** becomes active. Click it and follow the remaining pages to the end. You may be
      asked to update *Google Play services* on the phone emulator as well.
4. Success: the Google Pixel Watch app on the phone shows the watch as connected.
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

   Android Studio may offer to sync Gradle again (say yes) or may not ask at all; the build below runs from the
   terminal and does not depend on it. Check that the checkout worked:

   ```powershell
   git branch --show-current
   Test-Path phone\src\main\java\com\shlomoa\myadhdpal\phone\spike\SpikeActivity.kt
   ```

   The first command prints `claude/shared-0b2a-spike` and the second prints `True`.
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
4. Note whether the devices reconnected by themselves or needed a step (reopening the Google Pixel Watch app, the Pair Wearable
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
| Sync fails: *Cannot find a Java installation ... languageVersion=21 ... Toolchain download repositories have not been configured* | No JDK 21 found. Install Temurin 21 and set Gradle JDK (section 3, steps 1 and 5). |
| `java -version` is not 21, or `java` is not found | Set `JAVA_HOME` and `Path` by hand (section 3, step 3). The installer does not always do it. |
| Android Studio warns *Multiple Gradle daemons might be spawned because the Gradle JDK and JAVA_HOME locations are different* | `JAVA_HOME` points at another JDK (such as JDK 23) while Studio uses Temurin 21. Set `JAVA_HOME` to the Temurin 21 folder (section 3, step 3), restart PowerShell and Android Studio. |
| `.\gradlew.bat` says *JAVA_HOME is not set and no java command could be found* | Same fix: section 3, step 3, in the PowerShell window you are using. |
| `adb` is not recognized | Add the SDK `platform-tools` folder to `PATH` for this window (section 6.1, step 3). |
| Emulator is very slow or refuses to start | Virtualization is off. Section 2. |
| No Spike icon on the emulators | You built a branch other than `claude/shared-0b2a-spike`. Check out the spike branch, rebuild, reinstall. |
| `INSTALL_FAILED_...` on a reinstall | Uninstall first: `adb -s PHONE uninstall com.shlomoa.myadhdpal`, then install again. |
