Every turning machine shakes a little. How it shakes tells you what is wrong with it long before it breaks. The [vibration analyser](item:promptcritical:vibrometer) reads that: right-click any Create machine that turns, from a pump or a fan to a mixer, a press or a generator.

## What the screen shows

The Create shaft drives the machine through a gearbox: 256 rpm on the shaft is 1000 rpm in the machine. The analyser shows that machine speed, the shaft power and the machine's class.

The bar on the left is the **overall level**, the RMS vibration velocity in mm/s, measured as ISO 10816 prescribes: from 10 to 1000 Hz, or from 2 Hz for machines slower than 600 rpm. The colours are the zones for the machine's class:

- A, green: a new machine;
- B, yellow: fine for long-term running;
- C, orange: limited running, plan a repair;
- D, red: the vibration itself damages the machine. Stop it.

The class comes from shaft power: up to 15 kW is class I, up to 75 kW class II, above that class III, or IV for generators on a flexible foundation. A big machine is allowed to shake more.

## Reading the spectrum

The plot on the right puts each source at its own frequency, on a log scale:

- **1x**, the shaft frequency: imbalance. It grows as the cube of speed.
- **2x**: misalignment of the coupling.
- **BPF**, blades times shaft frequency: pumps and fans. It grows when the machine runs off its best flow.
- **BPFO, BPFI, BSF, FTF**: the outer race, the inner race, the balls and the cage of the rolling bearing. Their frequencies follow from the bearing geometry and are not whole multiples of the shaft speed. That is how you tell a bearing from imbalance.
- **GMF**: gear mesh, teeth times shaft frequency, on cogwheels.

## The bearing wears out

A bearing is rated for 50 000 hours at its design speed (ISO 281). Faster running and the extra load from imbalance shorten that. Each bearing gets its own life on a Weibull spread, so one in ten fails before its rating. Wear goes through four stages:

1. The **HF envelope** under the spectrum climbs. Nothing else shows yet.
2. Peaks appear at the bearing frequencies.
3. The bearing can be heard and the housing runs 10 °C hotter.
4. Broadband noise, 1x grows, the overall level goes into zone D and the housing gets 30 °C hotter.

At the end the bearing seizes and the machine wrecks itself. From stage 1 to that point about 40 % of the life is left: plenty, if someone takes readings.

## Maintenance

Hold the item on the machine:

- [rolling bearing kit](item:promptcritical:bearing_kit): shaft stopped, 60 s. A new bearing with a fresh life.
- [balancing weights](item:promptcritical:balancing_weights): machine running, 10 s. Trial-weight balancing cuts the imbalance fivefold.
- [alignment tool](item:promptcritical:alignment_tool): shaft stopped, 30 s. Brings misalignment down to 0.05 mm.

A [vibration transmitter](item:promptcritical:instrument_vibration) on the side of a machine gives the overall level as a redstone signal from 1 to 15, where 15 is the C/D boundary of that machine's class. Wire it to a lamp or a trip.
