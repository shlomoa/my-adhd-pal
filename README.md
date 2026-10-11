# my-adhd-pal

My ADHD companion: a Wear OS project custom designed to help me manage my ADHD.

## Stack

- Kotlin + Jetpack Compose for Wear OS (Wear Compose Material 3)
- Single `:app` module, standalone watch app (no paired phone needed)
- `minSdk 30` (Wear OS 3+), `targetSdk 36`, `compileSdk 37`
- Gradle 9.8 (wrapper), Android Gradle Plugin 9.4, JDK 21

## Target test device

Samsung Galaxy Watch6 Classic 47mm (SM-R965F): round 480x480 display, Wear OS 5 (API 34) or Wear OS 6 (API 36) after update.

## Build

```sh
echo "sdk.dir=/path/to/Android/sdk" > local.properties   # needs platform android-37.0
./gradlew assembleDebug testDebugUnitTest lintDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

## Run on the Galaxy Watch6 Classic

1. On the watch: Settings > About watch > Software information, tap *Software version* 7 times to enable Developer options.
2. Settings > Developer options: turn on *ADB debugging* and *Debug over Wi-Fi* (watch and computer on the same Wi-Fi).
3. Note the IP:port shown, then:

```sh
adb pair <ip>:<pairing-port>      # only the first time, using the pairing code shown on the watch
adb connect <ip>:<port>
./gradlew installDebug
```

## Roadmap ideas

- Focus timer with haptic nudges
- Quick-capture reminders
- Medication check-ins
- Tiles and complications for one-tap access
