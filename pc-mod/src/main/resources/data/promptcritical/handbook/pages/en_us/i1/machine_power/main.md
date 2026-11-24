Electric machines of the plant take their power from the 0.4 kV grid. Each one asks the nearest [socket](item:promptcritical:power_socket), [power board](item:promptcritical:power_board) or [UPS](item:promptcritical:ups_cabinet) within its reach for the power it needs. Feed the board with a cable to the wall behind it, from a distribution transformer.

| Supply | Rating | Reach |
|---|---|---|
| Socket 16 A | 3.7 kW | 4 blocks |
| Three-phase socket 32 A | 22 kW | 6 blocks |
| Power board | 40 to 250 kW | 16 blocks |
| UPS cabinet | 9 kW | 16 blocks |

## Slowing down

A board never gives more than its rating. If its machines ask for more, each gets the same share and works that much slower. Motors also follow the grid frequency: at 49 Hz they run 2 % slower. With no board in reach, or no voltage, a machine stands.

## Undervoltage

If the voltage at the board stays below 85 % for half a second, the contactor drops out, as the undervoltage protection of real motor starters does. When the voltage comes back the machines do not start on their own: press the board, Start. A board placed before the station runs does not drop out; the protection arms once it has seen voltage.

Right click switches the contactor, Start or Stop. Shift + right click shows the group; on a power board it also moves the incomer to the next rating.

## UPS

The UPS feeds its group from the grid and charges its battery. When the grid fails it carries on from the battery, about twenty minutes at full load, and does not drop out. Put the instruments and the controls that must see a blackout through on a UPS.

## Large machines: 6.3 kV

A power board at 0.4 kV cannot carry plants above 200 kW (catalytic reforming, delayed coking, the alumina calciner): they need their own 6.3 kV feed. Run a 6 kV cable to any face of the plant. It asks the grid for its rated power while it is switched on and follows the grid frequency like the rest. If the voltage stays below 85 % for half a second, the incomer breaker drops out and the plant stops; it will not start by itself, so switch it on again (Shift + right click).

Electrolysis cells take direct current from their rectifier, and machines on a Create shaft take their power from the motor or steam engine on that shaft. Boilers, furnaces that burn their own fuel and passive blocks (tanks, manways, needle gauges) take nothing from the grid.

The server key `energy.machines_need_power` turns the whole rule off.
