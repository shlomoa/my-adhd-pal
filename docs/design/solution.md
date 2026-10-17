# Solution (proposed design)

Part of the [UI/UX design](README.md), a **proposed design** until the user approves it. This page shows how the screens
are fed: the data flow (what is stored where and which way items travel) and the event flow (what happens, in order, when
the user or the heart rate does something). Both are derived from [`../protocol.md`](../protocol.md),
[`../data-layer.md`](../data-layer.md) and [`../plans/biometrics.md`](../plans/biometrics.md); they add no behaviour.

## Data flow

```mermaid
flowchart LR
    subgraph Phone
        UI["Screens P1-P13"] --> VM["ViewModels"]
        VM --> Repos["Repositories: tasks, history, settings, schedule, prompt, link status"]
        Repos --> Room[("Room: tasks, reminders, prompts, history")]
        Repos --> DS[("DataStore: settings, last schedule version")]
        Repos --> Src["WatchLinkDataSource"]
        Src --> Proc["Event storer (WatchEventProcessor)"]
        Proc --> Room
    end
    Src -- "put /schedule, /prompt/id, /hello/phone" --> DL(["Data Layer items"])
    DL -- "/ack/id, /selection/id, /alert/id, /hello/watch" --> Src
    DL -- "/schedule, /prompt/id, /hello/phone" --> WApp
    subgraph Watch
        WApp["Watch app: schedule store, scheduler, link client"] --> WUI["Screens W1-W9 and tile"]
        HS["Health Services heart rate"] --> Eval["Alert evaluator (thresholds, cooldown)"]
        Eval --> WApp
    end
    WApp -- "put /ack/id, /selection/id, /alert/id, /hello/watch" --> DL
```

The phone is the master. The user's tasks, reminders, prompts and history live in Room and the settings and the last
published schedule version in DataStore; the screens read them only through ViewModels and repositories, and the Data
Layer is reached only through `WatchLinkDataSource` (the rules of [`../data-layer.md`](../data-layer.md)). The schedule
builder turns tasks, reminders and settings into a versioned `/schedule` item (P5) and prompts become `/prompt/<id>`
items (P8). The watch keeps the last schedule it received and applies only a newer version, so it keeps working while it is
away. It never edits the phone's master data: what it sends back (`/ack`, `/selection`, `/alert`) becomes history rows only
(P6). Each node deletes only the items it wrote, except that the phone deletes a watch-written event after storing it.
The link state shown on P1, P13, W1 and W2 is not stored: it is derived from the connected nodes, the app capability and
the peer's hello item.

## Event flow

```mermaid
sequenceDiagram
    actor U as User
    participant P as Phone
    participant D as Data Layer
    participant W as Watch
    participant H as Health Services
    U->>P: edit tasks and reminders, tap Send to watch (P3-P5)
    P->>D: put /schedule (version n)
    D-->>W: item changed (queued while Away)
    W->>W: apply if n is newer, recompute the next reminder
    Note over W: the reminder time arrives
    W->>U: reminder with haptic (W3)
    U->>W: Done, Snooze, Start 2 min or Dismiss
    W->>D: put /ack/id (Snooze is applied on the watch first)
    D-->>P: item changed
    P->>P: store once as history (P6)
    P->>D: delete /ack/id
    U->>P: ask a question, tap Send to watch (P8)
    P->>D: put /prompt/id
    D-->>W: item changed
    W->>U: choice (W4)
    U->>W: pick an option
    W->>D: put /selection/id
    D-->>P: item changed
    P->>P: store as history, delete the prompt and the selection
    H->>W: heart rate reading
    W->>W: compare with the threshold, check the cooldown
    W->>U: heart-rate alert level 1 (W7), later level 2 (W8)
    W->>D: put /alert/id with its level
    D-->>P: item changed
    P->>U: phone alert (P10, P11), stored in history
    U->>W: I'm OK ends the episode
```

The flow reads from top to bottom and shows the three kinds of event. A **schedule** goes phone to watch when the user
sends it (P5); the watch fires reminders from its stored schedule by itself, so the phone need not be nearby, and each
action on the reminder (W3) goes back as an acknowledgement that the phone stores as history. A **prompt** goes phone to
watch (P8) and the answer comes back as a selection (W4). A **heart-rate alert** starts on the watch: the readings come from
Health Services, the thresholds and the cooldown are evaluated on the watch, level 1 shows first, level 2 follows if the
rate is still above the threshold after the cooldown and repeats every 2 minutes at most 6 times, and each alert also
reaches the phone, which raises its own alert and stores it ([`../plans/biometrics.md`](../plans/biometrics.md) sections 3
and 4a). While the link is Away, items wait in the Data Layer and arrive when it is back; the event diagrams in
[`../protocol.md`](../protocol.md), "Diagrams", give the detail of each exchange.
