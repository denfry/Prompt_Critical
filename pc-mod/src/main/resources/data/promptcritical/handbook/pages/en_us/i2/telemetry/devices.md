## Machines as peripherals

A computer next to a machine, or on the same wired modem network, sees the machine itself as a peripheral, without a gateway. Each has the common type **pc_device** and its own: **pc_reactor**, **pc_pump**, **pc_plc**, **pc_sis**, **pc_well**, **pc_drilling**, **pc_boiler**, **pc_furnace**, **pc_column** and others. Type `help pc_device` on the computer.

- **read(tag)** — value, quality and simulation time; **readMany({...})** — up to 64 tags from one snapshot;
- **watch(tag, lo, hi, deadband)** — a subscription: the **pc_setpoint_crossed** event comes when the value leaves the band;
- **command(name)** and the named methods (**requestTrip()**, **withdraw()**, **chokeUp()**, **writeMarker(i, v)**) return a ticket at once. The command runs on the next tick, and the **pc_command_result** event brings DONE, REJECTED or FAILED.

Readings are the panel's instruments, and commands are its buttons, so a program still can't do more than a player at the panel. Bypasses, protection setpoints and the safety logic solver are hand work: those calls fail with **E_HARDWARE_ONLY**. A machine under a lockout padlock is in MAINTENANCE and refuses commands.

## Limits

Each computer has a call budget: 400 tokens a second, 800 at most. A read costs 1, a command 8. A device takes no more than 10 commands a second and sends no more than 20 events a second to each computer. When events are dropped, **pc_event_overflow** comes: read the state again. Errors come as text such as `E_RATE: ...`; catch them with `pcall`.

Example programs are in `/rom/programs/pc/examples`.
