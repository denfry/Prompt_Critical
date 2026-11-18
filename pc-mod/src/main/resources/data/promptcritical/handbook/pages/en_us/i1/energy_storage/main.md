Electricity cannot be put in a warehouse: at every moment the grid uses exactly what the generators produce. A surplus speeds the machines up and the frequency rises; a shortfall slows them and it falls. So surplus energy is either stored to be given back later, or turned into something useful.

## Pumped storage

A pumped storage plant stores energy as lifted water. At night, when there is energy to spare, the reversible unit runs as a pump and lifts water from the lower basin to the upper one. In the day, when energy is short, the same water runs back down through the same runner, and the machine works as a turbine. That is how the Zagorsk plant near Moscow works: six units of 200 MW as turbines and 220 MW as pumps, 100 m of head. Out of every 100 kWh pumped up, 70 to 80 come back. The rest is lost in the pump, the turbine, the machine and the penstock.

### Building it

1. Make an upper basin and set a [dam gate](item:promptcritical:dam_gate) in its water, at least 10 m above the unit and within 64 blocks of it.
2. Put the [pumped storage unit](item:promptcritical:pumped_storage_unit) by the lower water and bring a 6.3 kV cable to its top or sides.
3. The unit sizes itself: the turbine empties the basin in 8 hours, but never runs above 1 MW. The basin works over 5 m of drawdown.

The basin level shows only on the panel, because the water in the world does not change. The energy account is honest: whatever the unit takes from the grid, the grid gets back minus the losses.

### Modes

- **Pump.** The unit lifts water. At constant speed the pump's power cannot be adjusted, because the head sets the flow.
- **Turbine.** The unit generates at the set power, which the ±10 % buttons change.
- **Auto.** If the grid frequency is above 50.2 Hz there is a surplus in the system, and the unit pumps. If it is below 49.8 Hz there is a shortfall, and the unit generates with a 4 % droop. These are the same thresholds as in the European grid code for generators.

## The electrode boiler

When there is nowhere else for the energy to go, it can be turned into steam. In an electrode boiler the current runs straight through the boiler water between the electrodes, so the water itself is the heater. The higher the water stands on the electrodes, the more current it passes. So the power is set by the level: a pump raises the water onto the electrodes, a drain lowers it. The power also goes with the square of the grid voltage.

Put the [electrode boiler](item:promptcritical:electrode_boiler) right against a boiler of the boiler house (fire-tube or water-tube), and its steam goes into the same header. If there is no boiler next to it, or the boiler has exploded or is blowing off through its safety valve, the steam has nowhere to go and the electrodes are drained.

In automatic mode the boiler starts taking load above 50.2 Hz and reaches its full 1 MW at 51 Hz. That way it soaks up the surplus that would otherwise run the generators away.
