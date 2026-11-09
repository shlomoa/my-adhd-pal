# Setup checklist

You do these once. Nothing here changes code.

## Watch (Galaxy Watch6 Classic)

1. Charge it and wear it on the wrist (the heart-rate sensor reads 0 when the watch is not worn).
2. Enable developer options (Settings, About watch, Software, tap Software version several times).
3. In Developer options turn on ADB debugging and Debug over Wi-Fi, then pair and connect from your computer
   (`adb pair <ip>:<port>`, then `adb connect <ip>:<port>`).

## Phone (Galaxy A55)

1. Pair it with the watch through the Wear OS companion app; Google Play services must be present.
2. Enable developer options and USB debugging (or wireless debugging), and accept the authorisation prompt.

## Check

With both connected, run on your computer from the repository root:

```bash
setup/check-devices.sh --push
```

It prints model, Android version and the heart-rate sensor lines for each device, writes a file under
`setup/results/`, and commits and pushes only that file to the current branch. The step is done when it reports one
watch, one phone and a heart-rate sensor on the watch.
