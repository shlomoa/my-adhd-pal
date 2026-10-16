# Watch main flow (proposed design)

Part of the [UI/UX design](README.md), a **proposed design** until the user approves it. Screens W1-W4 are the watch app
itself: the home screen, its link states, a reminder and a choice. The watch face is round (300 px of 340 px board).

## W1 Home

![W1 Home](images/W1-home.png)

The link state ("Phone nearby"), the next reminder (time and title, "18:00 Water the plants") and the last sync. It has no
controls (step 3.2).

## W2 Link states

![W2 Link states](images/W2-link-states.png)

The watch shows four states: Phone nearby, Phone via internet, Phone away and Update the app. The phone's fifth state, No
watch app, does not exist on the watch: the watch calls the same function and shows No phone app as Away
([`../protocol.md`](../protocol.md), "Link states").

## W3 Reminder

![W3 Reminder](images/W3-reminder.png)

When a reminder fires: the time and the title with the four actions of the protocol's `Acknowledgement`: Done (the
highlighted button), Snooze, Start 2 min and Dismiss. Snooze is applied on the watch at once, within the cap and the
expiry rules, and the choice is reported to the phone as an acknowledgement (`/ack`).

## W4 Choice

![W4 Choice](images/W4-choice.png)

An option prompt from the phone (P8): the question and the options as buttons, and a line saying when it asks again
("Asks again in 10 min"). The tap is reported as a selection (`/selection`). A prompt past its expiry is hidden.
