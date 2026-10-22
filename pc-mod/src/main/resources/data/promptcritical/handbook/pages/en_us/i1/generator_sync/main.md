An alternator turns rotation into current at a frequency set by its speed. One machine on its own network simply sets the frequency. Two machines on common busbars must turn **in synchronism**: at one frequency and very nearly one angle. Closing a second machine in out of phase gives it a torque blow comparable to a short circuit.

## The shaft generator

The [shaft generator](item:promptcritical:shaft_generator) is a 500 kVA synchronous machine, 400 kW at 0.8 power factor. The Create shaft enters at the back; the terminals are the other faces. A [generator exciter](item:promptcritical:generator_exciter) goes right next to it: without field current the machine makes no voltage.

On the generator panel:

- **Gear**: the Create shaft speed at which the machine gives 50 Hz: 16, 32, 64, 128 or 256 RPM. Pick the one your shaft turns at.
- **kV**: stator voltage class, 0.4 or 6.3 kV. Changed only with the machine stopped.
- **Field**: the field breaker. Closed, the voltage regulator holds terminal voltage; open, the field dies away.
- **V −/+**: voltage setpoint, 0.5 % steps.
- **f −/+**: the speed-increaser trim, ±5 % in 0.1 % steps. It brings the frequency up to match and picks up load.

The machine draws its power from the shaft as a Create load. The drive is soft, with 4 % droop: at full load the frequency sags about 1.5 Hz, and the trim brings it back. The steam engine on the shaft needs power in reserve: when steam runs short, frequency falls.

The generator goes on line through a breaker: a [6 kV switchgear cell](item:promptcritical:switchgear_cell) or a 0.4 kV switchboard. The generator cable goes to the back face of the cell (the feeder side); a row of cells is the busbar. The first machine closes onto a dead bus without synchronizing.

## The synchroscope

A [synchroscope](item:promptcritical:instrument_synchroscope) hangs on the cell. It compares the feeder (the "generator") with the bus (the "grid"): voltages, frequencies, Δf and ΔV. The pointer turns at slip frequency: clockwise means the generator is fast. At twelve o'clock the two voltages are in phase. The synchroscope panel also carries the trim and voltage setpoint of that feeder's generator and the breaker close button, so the whole job is done from one instrument.

Conditions for accurate synchronizing:

- angle at the moment of closing: 10° at most;
- generator voltage 0–5 % above the grid;
- slip 0.067 Hz at most: one turn of the pointer in 15 seconds, or slower.

The pointer should creep **clockwise**: then the generator picks up load the moment it closes instead of running as a motor.

## Closing ahead

The cell's oil breaker closes 0.45 s after the command. In that time the pointer moves on by 360°·Δf·t. At 0.05 Hz slip that is 8°. So the button is pressed **before** the pointer reaches twelve o'clock, ahead by that angle. At large slip there is no closing accurately: at 1 Hz the lead is already 160°.

## The blow of a bad closure

At the instant of closing, only the machine's subtransient reactance limits the current. Peak shaft torque with equal voltages is about 2·sin(Δθ/2)/X″, in per unit of rated:

- 10°: 0.5; 30°: 1.5; 60°: 2.9; 120°: 5; 180°: 5.7.

Up to 1 pu is normal, a jolt. 1–1.5 is a heavy blow. Above 1.5 the shaft collects fatigue damage, above 3 the stator windings too. At 5 pu the coupling shears. When accumulated damage runs out, the machine is damaged and disconnected. The same blow reaches the Create kinetic network as a sudden load and nearly always overstresses all of it.

The cheap protection is a [shear-pin coupling](item:promptcritical:generator_coupling), fitted by right-clicking the generator. The pin breaks above 2 pu and keeps the blow out of the Create shaft. A sheared pin is replaced with a new coupling.

A clean closure is a torque blow of 0.1 pu or less: 1–2° at the moment of closing.

## Synchronism check relay

A [synchronism check relay](item:promptcritical:relay_sync_check) module in the relay cabinet next to the cell refuses to let the breaker close outside the window: the command comes back "interlock". It does not adjust slip or lead for you; that is still the operator's job.
