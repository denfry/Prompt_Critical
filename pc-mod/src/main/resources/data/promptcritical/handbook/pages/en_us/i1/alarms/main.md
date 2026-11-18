A lamp on a machine's own panel only helps the person standing next to it. An alarm system brings every lamp that needs action to one place, makes it flash until someone answers it and keeps it lit while the problem lasts. That is the [annunciator panel](item:promptcritical:annunciator_panel).

## What it watches

Hang the annunciator on a wall. Once a second it reads the local panels of all machines within 16 blocks, no cables needed. Every red lamp becomes a priority 2 alarm, every amber lamp a priority 3 alarm, and every dial with a red zone a high alarm at the start of the zone. Green and white lamps, and lamps that only show a position (fire lit, breaker closed, auto mode), are statuses, not alarms: an alarm is something the operator must act on.

An alarm must hold for 2 seconds before it comes in and stay away for 2 seconds before it goes, and an analog alarm only clears 2% back from its limit. This stops a value sitting on the line from flashing the window on and off.

## Reading the windows

The panel has 12 windows. A free window takes the next alarm by priority and keeps it, so the engraving does not move around.

- Dark: normal.
- Fast flashing in the priority colour: new and not acknowledged. The horn sounds.
- Flashing faster than the rest: first out, the alarm that started the cascade.
- Steady: acknowledged and still there.
- Slow white flashing: cleared but nobody acknowledged it.

Priority 1 is red with a triangle, 2 orange with a diamond, 3 yellow with a circle. The shape is there for players who cannot tell the colours apart.

ACK acknowledges every window, SILENCE stops the [alarm horn](item:promptcritical:alarm_horn) without acknowledging, and TEST flashes every window for 3 seconds so you can see that the lamps work. Sneak and use the panel to acknowledge everything.

While the horn sounds, the annunciator powers the block it hangs on, so a redstone lamp or bell can repeat it anywhere.

## Floods

More than 10 alarms in 10 minutes is an alarm flood: the operator can no longer follow them. The panel shows a FLOOD line until the rate drops below 5. A plant whose alarms are well chosen shows a few windows in an upset. A plant that alarms on everything buries the cause.
