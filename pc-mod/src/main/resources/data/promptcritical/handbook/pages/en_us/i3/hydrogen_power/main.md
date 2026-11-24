# Hydrogen power

Electricity in, hydrogen stored for weeks, electricity out again. The round trip keeps about a third of the energy: half the hydrogen's energy is lost in the fuel cell, and electrolysis and compression take more than the cell gives back. Pumped storage keeps three quarters, so hydrogen pays only when you need to store energy for a long time.

## Electrolyser

The alkaline electrolyser splits water in 230 cells of 30 % potash lye at 80 °C. At its rated 4 kA/m² it draws 2.2 MW of direct current and makes about 480 Nm³ of hydrogen an hour, 4.6 kWh per normal cubic metre. Feed it from a 6.3 kV cable to the busbars on the west end. The rectifier breaker drops out if the voltage stays below 85 % for half a second; restart the electrolyser by hand.

It needs demineralised water at its water port (about 9 kg for every kilogram of hydrogen) and cooling water at the lye cooler. Without water the lye gets stronger and the stack trips. Without cooling the stack heats up and trips at 90 °C.

Watch the hydrogen in oxygen. Some hydrogen always gets through the diaphragm. At full load it is 0.3 % of the oxygen, but at low load there is so little oxygen that it reaches 2 %, and the stack trips: hydrogen in oxygen burns from 4 %. So do not run the electrolyser below about a fifth of its load. After that trip, purge the stack with nitrogen (panel, two minutes) before you start it again.

## Gas lines

Hydrogen goes through Create pipes run from port to port and flows by its own pressure, so it needs no pump. The electrolyser delivers into the gasholder at a little above atmospheric pressure. If the gasholder goes above 1.3 bar the electrolyser trips, and above 1.5 bar the gasholder's relief valve vents to the stack. With no line at all, the hydrogen goes straight to the air.

## Compressor and tube bank

The two-stage diaphragm compressor takes from the gasholder and fills the tube bank to 200 bar, using 2.2 to 2.4 kWh per kilogram. Its 132 kW motor runs from a 3-phase socket or a power board, and the pressure switches start and stop it by themselves. Give it cooling water: without it there is no intercooling, and it trips at 230 °C. Gas pumped in fast comes out hot. A tube bank filled to 200 bar while hot holds only 155 kg; it cools over the next hour, the pressure drops and the compressor tops it up to about 174 kg. Above 240 bar the burst disc opens, and you must replace it before the tube bank holds pressure again.

## Fuel cell

The 200 kW PEM module runs on hydrogen from the tube bank. It stops below 3 bar. Its inverter feeds a 0.4 kV cable on the east face. At full power it is about 50 % efficient on the lower heating value, and at part load it does better. A cold module gives only about 175 kW until it has warmed up to 70 °C. It makes 9 L of water for every kilogram of hydrogen; the water runs out of the bottom port into a tank, or to the drain.
