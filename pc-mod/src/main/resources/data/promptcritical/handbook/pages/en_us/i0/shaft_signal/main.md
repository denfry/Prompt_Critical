A shaft with several landings needs a way to call the cage without walking to the hoist house. Real mines use a signal box at every landing: the onsetter rings the winding engineman, and a lamp shows that the cage has arrived.

## Placing it

- Put the [shaft signal box](item:promptcritical:shaft_signal) next to the cage at a landing, at floor level or up to two blocks above it, within three blocks of the cage.
- The box finds its hoist by itself. If nothing serves the landing, a click says so: check that the hoist has a headframe, a cage and a rope.

## What it does

- An empty-hand click rings the bell and calls the cage to this landing. The hoist's own interlocks still apply: an open cage door, a rope below its safety factor or a missing work permit stop the call, and the box tells you why.
- A redstone pulse into the box does the same. One box per landing and a lever or a button is all a lift needs.
- The box gives a strong redstone signal while the cage stands at its landing, and its lamp lights up.
- A comparator reads the cage at this landing: 0 when it is elsewhere, 1 for an empty cage, up to 15 at full load.

## Automating the shaft

The hoist is also a ComputerCraft device of type `pc_hoist`: `call(n)` sends the cage to landing n (1 is the top), `getState()` returns depth, landing, door and load, `getLandings()` lists landing depths. Events `pc_hoist_arrived` and `pc_hoist_departed` tell a program when the cage moves.

A skip cycle needs no program at all: a box at the bottom landing called by a detector rail under a loaded cart, a box at the top wired to call the cage back down when the comparator shows it empty again.
