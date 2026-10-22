## Protective relaying

A short circuit drives current tens of times above normal. In fractions of a second it melts conductors and wrecks insulation, and voltage collapses across the grid. Protection must find the fault and open the breaker **nearest** to it, leaving the rest of the grid running.

A [relay protection cabinet](item:promptcritical:relay_cabinet), 1×2×1, stands right against a switchgear cell or a 0.4 kV switchboard. It has six module slots. The cabinet panel selects a module and turns its setting knobs; the "Tripped" flag stays up until reset. Settings are stored in the module itself and travel with it.

Generation A1 modules, electromechanical:

- **51/50 overcurrent**, the [overcurrent relay](item:promptcritical:relay_overcurrent): pickup current and time multiplier. The delay falls as current rises; the instantaneous element acts at 8 times pickup.
- **25**, [synchronism check](item:promptcritical:relay_sync_check): permits closing only inside the window of angle, voltage and slip.
- **32R**, [reverse power](item:promptcritical:relay_reverse_power): the generator is motoring, 2 % of rating, 3 s.
- **81U**, [underfrequency](item:promptcritical:relay_frequency): 47.5 Hz, 2 s by default; trips the generator.
- **27**, [undervoltage](item:promptcritical:relay_voltage): 0.7 of rated, 3 s.
- **49**, motor [thermal relay](item:promptcritical:relay_thermal): trips at 140 °C winding.
- **79**, [auto-reclose](item:promptcritical:relay_autoreclose) of a line after a dead time: most faults on overhead lines clear themselves.
- **50BF**, [breaker failure](item:promptcritical:relay_breaker_failure): if a breaker has not cleared the current in 0.25 s, the other breakers on its bus open.

**Discrimination.** The time step is 0.3–0.5 s. Protection nearer the source gets a longer delay than protection on the outgoing feeder. A mistake shows at once: a fault at the end of a feeder blacks out the whole substation.

**Control power.** A breaker needs direct current for its mechanism. While its "+" and "−" terminals are free, control power is taken as present. Once those terminals are wired, supply them from a [24 V power supply](item:promptcritical:relay_power_supply). Without it the command is refused with "no control power", and protection cannot trip.

After a series of fault interruptions an oil breaker needs repair. Current above its breaking capacity it cannot clear at all.

## Underfrequency load shedding

A generator trips, or load grows past what the machines can give, and the frequency falls. The steam engine governor is not fast enough. Below 47.5 Hz the generators' 81U operates and the whole grid goes dark. **Automatic underfrequency load shedding** (UFLS) drops part of the load in stages before that happens.

The [UFLS relay](item:promptcritical:relay_ufls) goes straight into the load: a settlement load or an electric motor, each with two module slots. It does not go into the relay cabinet.

Stages:

- UFLS-1/1…1/5: 48.8, 48.6, 48.4, 48.2, 48.0 Hz, 0.2 s delay. Fast shedding against a deep dip.
- UFLS-2/1 and 2/2: 49.0 Hz, 10 and 20 s. Against frequency that hangs below normal.
- Frequency-driven reclosing: load comes back by itself once frequency has held at 49.7 Hz or above, after 10–60 s depending on the stage.

The stage is stored in the module and shows in its tooltip; a new module is UFLS-1/1. A stage's volume is the sum of the loads assigned to it. Put first under shedding whatever can live a minute without power. An electromechanical frequency relay can be off by up to ±0.1 Hz, so do not set neighbouring stages any closer.

After it operates, raise machine output or drop some load by hand. Reclosing brings the shed load back only once frequency has recovered.
