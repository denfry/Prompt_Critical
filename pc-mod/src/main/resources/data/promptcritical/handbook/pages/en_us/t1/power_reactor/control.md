## Control and protection

There are three rod groups:

- **AZ**, the scram rods — about 4 % of reactivity. Cocked, they hang above the core and fall in 2 seconds when they trip.
- **RR**, the shim rods — about 12 %. They hold down the excess reactivity of fresh fuel and the xenon. The ▲ ■ ▼ keys drive them up, stop them and drive them down.
- **AR**, the automatic regulator — about 0.6 %. It holds the power setpoint when switched on.

A cold clean core with all rods in is subcritical with margin.

The period is the time over which power grows e-fold. The inhour equation links it to reactivity. At +0.1β the period is about a minute; at β the reactor runs away on prompt neutrons.

The scram trips by itself on:

- a period shorter than 10 s;
- power above 120 %;
- flow below 70 % at power;
- outlet water above 300 °C;
- the scram key (or Shift + use on the reactor);
- an external signal.

The scram can be cocked only once the cause is gone. The Protections key bypasses the process trips. That was done during tests, and that is how accidents happened.

## Terminals

The reactor's front carries the control cabinet terminals:

- **N** — ion chamber power, 4–20 mA for 0–120 %, for a PLC or a safety logic unit;
- **13–14** — a "scram tripped" contact;
- **SCRAM** — the external scram coil: 24 V on it shuts the reactor down like the key;
- **+/−** — circuit monitoring.

This way a safety system can protect the reactor from its own sensors.

## Reactivity margin and the iodine pit

The **operational reactivity margin** (ORM) is the worth of the shim and auto rods still in the core, counted in equivalent rods: all of them in is 180, all out is 0. Hover over the RR bar to read it. Below **15 rods** the amber **ORM** lamp lights: the reactor no longer has enough rods in hand to stop a rise of reactivity quickly.

After a drop in power xenon keeps growing for hours from the decay of iodine, the **iodine pit**. A reactor that fell to a few percent cannot be brought back at once: the operator withdraws the rods, the margin shrinks, and if it goes below the minimum the rods are no longer a defence. The safe course is to wait until xenon decays (20–30 real minutes) or to rise with the margin kept above 15 rods. A chat warning says when the margin was broken.
