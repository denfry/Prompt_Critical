Machines of other mods run on Forge Energy, FE. FE has no voltage, no frequency and no inertia, so the plant grid is not built on it: FE is a border. The [grid FE interface](item:promptcritical:grid_fe_interface) is a converter cabinet on that border. Run a cable to any face except the front; FE cables and machines of other mods connect on any face.

The cabinet comes in three sizes: 100 kW on a 0.4 kV bus, 1 MW and 10 MW on 6.3 kV. It holds one second of its rated power in a buffer and converts at 97 % efficiency.

## Modes

- **Import**, grid to FE. The cabinet is a constant power load. It fills the buffer and hands FE to its neighbours. With the plain rates, 1 MW from the grid is 19,400 FE a tick.
- **Export**, FE to grid. The cabinet follows the grid, like a solar inverter. It feeds the set power only while the bus voltage is within 0.9 to 1.1 of nominal and the frequency within 49 to 51 Hz. It gives no inertia and cannot start a dead grid. If the voltage drops out it trips in 0.2 s, and it comes back only after the bus has been healthy for five seconds, then raises its power over ten seconds rather than all at once. FE generators of other mods can help your plant, but cannot run it on their own.
- **Auto** keeps the buffer half full: it imports when the buffer is low and exports when it is high.

The ±10 % keys set the power limit.

## The price of FE

The plant counts energy in watts. On a Create shaft it is 190 W per stress unit, and at the FE border 2.5 J per FE. Some mods, such as Crafts & Additions and New Age, turn shaft rotation into FE at their own rate. With the plain rates, their motor turning our generator would make energy out of nothing, about 120 times more than it takes. So while such a mod is loaded, the cabinet prices FE at 380 J, and both loops lose energy. The Parity lamp on the panel shows that this rule is on. The server operator can turn it off and align the bridge mod instead (key `energy.fe_su_parity`).
