Oil and products move through ordinary Create pipes and sit in ordinary Create tanks. The tank farm and pipeline blocks do not replace them: they watch them and add what a real plant has to deal with.

## Tank gauge

Put a tank gauge on the roof of a Create tank holding crude or a product. Its panel shows the level, the filling rate and the breathing losses.

- High level is 90 %; high-high is 95 %. At high-high the gauge gives a redstone signal: wire it to the clutch of the filling pump. Nothing stops the pump for you.
- A tank filled to the brim with the pump still running overflows. Each second the product spills over the edge and runs down onto the ground next to the tank as crude oil (see Oil and gas accidents).
- Breathing losses (API MPMS 19): every filling pushes vapour out of the breather, about 0.5 kg per cubic metre of crude and more for gasoline, and the daily temperature cycle breathes out a little more. The mass leaves the tank and is booked as VOC. While a fixed-roof tank fills fast, the vapour by its breather forms a flammable zone.
- A floating roof (right-click the gauge with 8 steel plates) cuts these losses by 94 %.

## Pipeline sections and pigs

A pig launcher and a pig receiver bound a pipeline section: the shortest run of Create pipes, pumps and valves from the middle block of the launcher to the middle block of the receiver. One block is one metre of line; the line is Ø426×8 X52.

Wax builds up in the line, 2 % of the bore a day. Clean it with a pig:

1. Vent the launcher (Vent on the panel) and open the door. Under pressure the door stays locked.
2. Load a cleaning pig or a smart pig (right-click with it), close the door, press Vent again to pressurise, press Launch.
3. The pig travels at about 1 m/s. A cleaning pig pushes the wax into the receiver. A smart pig (magnetic flux leakage) reports wall defects deeper than 10 % with a 90 % chance and ±10 % sizing; the panel shows the deepest and the burst pressure it leaves.
4. Collect the pig from the receiver (Shift + right-click) once the line is vented.

Wax over 20 % of the bore, a smart pipe or a pump in the line stops the pig halfway. Vent the section and press Cut out.

## Corrosion and cathodic protection

Buried steel loses 0.2 mm a year. A cathodic protection rectifier (grid powered) or magnesium sacrificial anodes (right-click the launcher with one) push the pipe-to-soil potential below −0.85 V against a copper sulphate electrode; then corrosion drops fifty-fold. The current needed is about 20 mA per square metre of bare steel, times 0.01 for a coated line. The rectifier shares its current between the sections within 64 blocks. A test post shows the on and off potentials of the nearest section. Below −1.2 V the line is overprotected: the coating peels.
