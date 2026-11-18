A plant big enough for several annunciators needs one place where the operator sees all of them: the main control room. It is a room you build, and its quality is part of the plant's safety.

## The control desk

The [control desk](item:promptcritical:control_desk) is the operator's overview. It lists the alarms of every annunciator within 32 blocks: unacknowledged first, then by priority, newest on top, eight to a page. Below the list it counts alarms by priority, unacknowledged, shelved and suppressed ones, and the alarm rate over 10 minutes, which is the rate per operator. A shelved alarm is hidden, but the desk always counts it: a shelf must never be invisible.

ACK ALL acknowledges every annunciator at once and SILENCE stops every horn. Sneak and use the desk to acknowledge all.

## Habitability

Put a [control room core](item:promptcritical:control_room_core) inside the room. It fills the air around it and finds the closed volume, up to 4096 blocks (a hall of 16 by 16 by 16). A hall that leaks to the outside is not a room: close the walls and use the core to measure again.

Once a second the core checks:

- the dose rate, averaged over 9 points of the room: degraded above 69 µSv/h, which is 50 mSv over a 30-day accident, the design limit for control room staff; uninhabitable above 10 mSv/h;
- smoke: one fire in the room or on its walls degrades it, four make it uninhabitable.

The core has an amber DEGRADED lamp and a red UNINHABITABLE lamp, so an annunciator in the room alarms on both by itself. The desk shows the room state and whether the desk itself stands inside the room.

A degraded room still works: the instruments do not care. The people in it take the dose and the heat. That is why real plants build a second, remote shutdown panel far from the first.
