# Specification of the spike apps

Both apps contain the same four classes in the package `com.shlomoa.myadhdpal.<module>.debug.spike` (`phone` or
`wear`), in the debug source set of the module. Everything below was read from that code; the `ROLE` constant
(`phone` or `watch`) and the activity layout are the only differences between the two copies.

## Components and manifest entries

Declared in `phone/src/debug/AndroidManifest.xml` and `wear/src/debug/AndroidManifest.xml` (merged into debug builds
only; the main manifests do not mention the spike):

| Component | Class | Manifest | Purpose |
|---|---|---|---|
| Launcher activity | `.debug.spike.SpikeActivity` | `exported=true`, label `Spike`, `MAIN` + `LAUNCHER` | A second launcher icon named *Spike* with buttons and a log view, for a human |
| Command receiver | `.debug.spike.SpikeCommandReceiver` | `exported=true`, **no permission** | Lets `adb shell am broadcast` drive the spike without taps |
| Listener service | `.debug.spike.SpikeListenerService` (a `WearableListenerService`) | `exported=true`; intent filter action `com.google.android.gms.wearable.DATA_CHANGED`, data `scheme=wear`, `host=*`, `pathPrefix=/spike` | Receives Data Layer events; the system starts the app process for it when the app is closed |

Known limitation: unlike the other debug receivers of the watch app (`android:permission="android.permission.DUMP"`),
the spike receiver has no permission, so any app on a device with a debug build can send it commands. Kept as it was run
to leave the measured code unchanged; debug builds only. Adding the permission is a one-line change but would need one
hardware run to confirm that `adb shell` can still send the broadcasts (**not verified**).

## The `Spike` object

| Member | Behaviour |
|---|---|
| `ROLE` | `"phone"` or `"watch"` |
| `TAG` | `"SPIKE"` (the logcat tag) |
| `foreground` | set true in the activity's `onResume` and false in `onPause`; logged as `fg=` |
| `log(context, message)` | writes one line to logcat (`Log.i`) and appends it to `files/spike.log` of the app (synchronised); format below |
| `send(context, kind, urgent, onDone, deleteDelayMs)` | puts `/spike/<kind>/<uuid>` |
| `ping(context, urgent, onDone)` | puts `/spike/ping/<uuid>` |
| `pong(context, id, urgent, pingElapsed)` | puts `/spike/pong/<id>` |
| `listItems`, `listNodes` | `DataClient.getDataItems()` and `NodeClient.getConnectedNodes()` logged as one line |
| `readLog`, `clearLog` | read or delete `files/spike.log` |

## Item paths and keys

All items are `PutDataMapRequest` DataItems; `setUrgent()` is called only when `urgent` is true.

| Path | Written by | Keys (type) |
|---|---|---|
| `/spike/del/<uuid>` | `send` with kind `del` | `from` (string), `id`, `kind`, `sentTs` (long, `System.currentTimeMillis()`), `deleteDelayMs` (long) |
| `/spike/keep/<uuid>` | `send` with kind `keep` | the same keys |
| `/spike/ping/<uuid>` | `ping` | `from`, `id`, `kind="ping"`, `urgent` (boolean), `sentTs`, `pingElapsed` (long, the pinger's `SystemClock.elapsedRealtime()`) |
| `/spike/pong/<uuid>` | `pong` | `from`, `id`, `kind="pong"`, `urgent`, `sentTs`, `pingElapsed` (echoed unchanged) |

`kind` is whatever the `kind` extra says (default `del`); a kind other than `del`, `keep`, `ping` or `pong` is received
and logged but triggers no action. These paths are **not** protocol paths; the protocol catalog is in
[`../protocol.md`](../protocol.md).

## Command receiver: adb interface

```text
adb -s <serial> shell am broadcast -n <package>/<package>.<phone|wear>.debug.spike.SpikeCommandReceiver \
    -a <action> [--es kind del|keep] [--ez urgent true|false] [--es delayMs <ms>]
```

`<package>` is `com.shlomoa.myadhdpal` (the same on both apps). The receiver calls `goAsync()` and finishes the
broadcast when the Data Layer call completes. A successful call prints `Broadcast completed` (the runner requires it).

| Action (prefix `com.shlomoa.myadhdpal.spike.`) | Extras | Effect |
|---|---|---|
| `SEND` | `kind` (string, default `del`), `urgent` (boolean, default `true`), `delayMs` (**string**, parsed as long, default 0; it becomes `deleteDelayMs` in the item) | `Spike.send` |
| `PING` | `urgent` | `Spike.ping` |
| `LIST_ITEMS` | none | `Spike.listItems` |
| `LIST_NODES` | none | `Spike.listNodes` |
| `CLEAR_LOG` | none | `Spike.clearLog` |
| any other action | | logs `COMMAND-UNKNOWN` |

Every command first logs `COMMAND action=<action> kind=<kind or null> urgent=<bool>`.

## Listener service behaviour

For every `DataEvent` in `onDataChanged`:

1. `TYPE_DELETED`: logs `EVENT-DELETED path=<path> host=<node id>` and stops.
2. Otherwise reads the DataMap; if `from` equals this app's `ROLE`: logs `EVENT-OWN-WRITE ignored path=<path>` and stops.
3. Otherwise logs `RECV path=<path> from=<from> sentTs=<n> recvTs=<n> delayMs=<recvTs - sentTs>` (**two clocks**, skew
   contaminated) and acts on `kind`:
   - `del`: deletes the item with `DataClient.deleteDataItems(uri)`: at once, or, when `deleteDelayMs > 0`, logs
     `DELETE-SCHEDULED path=<path> afterMs=<n>` and deletes on a new thread after sleeping that long. Result logged as
     `DELETE-OK path=<path> count=<n>` or `DELETE-FAILED path=<path> error=<e>`.
   - `keep`: nothing; the item stays.
   - `ping`: writes the `pong` (same id, `urgent`, echoed `pingElapsed`), then deletes the ping.
   - `pong`: logs `RTT id=<id> urgent=<bool> rttMs=<SystemClock.elapsedRealtime() - pingElapsed>` (one clock, the
     pinger's), then deletes the pong.

`onCreate` logs `SERVICE-CREATED`. When the app is closed, the system starts the process for the event; the line then
shows `fg=false` and a small `processUptimeMs` (221 and 244 ms on the Galaxy A55 5G after a kill and a force-stop; 510 and
447 ms on the emulator).

The delayed delete uses a plain thread inside the service process; if the system ends the process during the delay the
delete does not happen. No case relied on that (the delay is 6 s).

## Log line format

```text
<epochMillis> role=<phone|watch> pid=<pid> processUptimeMs=<ms since process start> fg=<true|false> <message>
```

Messages (exact prefixes):

| Message | Written when |
|---|---|
| `COMMAND action=... kind=... urgent=...`, `COMMAND-UNKNOWN action=...` | a broadcast arrived |
| `SEND path=... urgent=...`, `SEND-OK path=... (putDataItem completed locally)`, `SEND-FAILED path=... error=...` | `send`; `SEND-OK` means the **local** write completed, not that the peer has it |
| `PING path=... urgent=...`, `PING-OK ...`, `PING-FAILED ...` | `ping` |
| `PONG path=... urgent=...`, `PONG-OK path=...`, `PONG-FAILED ...` | `pong` |
| `ITEMS count=<n> <uri>, <uri>...`, `ITEMS-FAILED error=...` | `LIST_ITEMS` |
| `NODES count=<n> <displayName>(nearby=<bool>), ...`, `NODES-FAILED error=...` | `LIST_NODES` |
| `SERVICE-CREATED` | listener `onCreate` |
| `EVENT-DELETED`, `EVENT-OWN-WRITE ignored`, `RECV`, `DELETE-SCHEDULED`, `DELETE-OK`, `DELETE-FAILED`, `RTT` | listener, as above |
| `ACTIVITY-RESUMED` | the activity came to the foreground |

## Patterns the runner searches for

The runner reads `adb logcat -d -s SPIKE`, keeps the text after the tag (the script's expression is: a space, `SPIKE`, optional spaces, a colon, a space, then the rest of the line) and
clears the buffer between cases with `logcat -c`. The expressions it matches (`<id>` is the item's UUID):

| Pattern | Used for |
|---|---|
| `SEND path=/spike/<kind>/(\S+) urgent` | learn the id of an item the sender just wrote |
| `RECV path=/spike/<kind>/<id> from=` and `fg=false RECV path=/spike/del/<id> from=watch` | the receiver got the item; with `fg=false`, with the app closed |
| `DELETE-OK path=/spike/del/<id>` | the receiver deleted it |
| `EVENT-DELETED path=/spike/del/<id>` | the sender saw the deletion |
| `ITEMS count=` | the list of stored items (searched for ids) |
| `NODES (count=\|-FAILED)`, `NODES count=(\d+)`, `nearby=true` | the node list and link state |
| `SERVICE-CREATED` | the service started (informational) |
| `PING path=/spike/ping/(\S+) urgent=` and `RTT id=<id> urgent=<true\|false> rttMs=(\d+)` | one round trip |
| `delayMs=(-?\d+)`, `processUptimeMs=(\d+)` | reported values |

Changing a message prefix in `Spike.kt` breaks the runner; the simulated adb (`scripts/spike/fake*/adb`) produces the
same prefixes, so the test suites fail first.

## UI (for a human)

`SpikeActivity` is a plain `Activity` (no Compose) with buttons **Send DEL urgent**, **Send DEL non-urgent**, **Send
KEEP urgent**, **List data items**, **List connected nodes**, **Refresh log**, **Clear log** and a text view with the last
25 lines of the log (refreshed 500 ms after a press and on resume). Insets: the content is padded for the system bars and
display cutout (targetSdk 36 draws edge-to-edge); on the watch, a round screen adds side and top/bottom padding (10 % and
18 % of the display) so the first and last buttons can be scrolled into the visible area.
