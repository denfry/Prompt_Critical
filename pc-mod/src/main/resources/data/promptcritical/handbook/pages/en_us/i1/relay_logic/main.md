Before microprocessors, plants were run by relays. A relay is an electromagnet that throws contacts. Current in the coil pulls the armature in and the contacts change over. Relays, pushbuttons, switches and lamps make logic: AND is contacts in series, OR is contacts in parallel, NOT is a normally closed contact. The circuit is drawn as a ladder: the +24 V rail on the left, 0 V on the right, and each rung a chain of contacts to one coil or lamp.

## Components

All of these mount on a wall. Terminals are marked as on real devices:

- the [control relay](item:promptcritical:relay): coil A1-A2, two changeover groups: 11 common, 12 normally closed, 14 normally open; likewise 21-22-24. It comes as general purpose or with force-guided contacts;
- the [latching relay](item:promptcritical:relay_latching) remembers its position without current: B1-B2 resets it, and reset wins over set;
- the [time relay](item:promptcritical:time_relay): delayed contacts 15-16-18 and instantaneous 25-26-28. Modes: on-delay, off-delay, pulse, flasher. A pneumatic timer is off by ±10 %, a motor timer by ±1 %, an electronic one by ±0.1 %;
- the [stepping switch](item:promptcritical:stepping_switch): 25 positions, three wipers, one step per rising edge on the coil. Contact bounce makes extra steps; the RC filter on its panel suppresses them;
- the [cam controller](item:promptcritical:cam_controller): a drum of cams on a Create shaft, each contact closing and opening at set angles. A program written in hardware;
- the [process switch](item:promptcritical:process_switch): pressure, level, temperature or flow of whatever it hangs on: a boiler, an engine, a Create tank, a pump. It trips on rising or falling value, with a reset band;
- the [pushbutton](item:promptcritical:pushbutton) (13-14 make, 11-12 break), the [control switch](item:promptcritical:key_switch), the [indicator lamp](item:promptcritical:indicator_lamp).

The control switch works select-before-operate: a click selects, Shift + click is ON, a click is OFF.

## Supply and wiring

The circuit is fed from a [24 V power supply](item:promptcritical:relay_power_supply): a rectifier off the 0.4 kV bus, cable to any face but the wall and the front. No voltage on the bus, and every relay drops out. Each coil draws current, and on a large circuit the supply voltage sags: relays with margin hold in, relays without it let go. Overload shows on the supply's panel.

Wires are run with the [wiring tool](item:promptcritical:wiring_tool): click one terminal, then another, no more than 64 m apart and only between terminals of the same type. Shift + click on a terminal removes its wires; Shift + right-click in the air cancels a wire in progress. With the tool in hand you see wires, terminals and their labels. A [relay rack](item:promptcritical:relay_rack) holds 16 relays neatly.

## Reaching the world

- Switchgear cell and 0.4 kV switchboard: control power terminals "+" and "−", CLOSE and TRIP, auxiliary contacts 13-14 and 21-22.
- Electric motor: contactor coil A1-A2 and auxiliary contact 13-14.
- Settlement load: contact 13-14.
- [Relay–redstone interface](item:promptcritical:redstone_interface): with coil A1-A2 energized it sends a strong signal into the block it hangs on. That is how relays drive Create machinery. The other way round, redstone at its input closes contact 13-14.

## Seal-in

The classic motor starter: one rung "STOP (NC), START (NO), contactor coil KM1", with KM1's auxiliary contact 13-14 in parallel with START. Press START and the contactor pulls in and holds its own coil. Press STOP or lose the supply and it drops out and stays out. Interlocks go in series with STOP: a pressure switch, a thermostat, a time relay contact for "no discharge pressure within 10 s".

## Failures

Relays wear out with operations. A coil can open, a contact can weld, an armature can stick. A welded contact keeps a circuit made after the coil has let go, the circuit behaves "impossibly", and you find the bad relay by working through the ladder rung by rung. The relay's state shows on its panel; the "Replace" button swaps it. Safety circuits use force-guided relays: if one contact welds, its normally closed contacts will not close after the coil drops out, and a monitoring circuit sees it.
