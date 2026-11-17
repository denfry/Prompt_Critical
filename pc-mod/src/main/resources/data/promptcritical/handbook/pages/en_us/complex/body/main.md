You choose your character's sex, height, mass and age once per world. You can set them in advance with the "Character body" button on the title screen. On first join the screen opens by itself, filled with that profile, and you only have to confirm it. After that only a server operator can change it (`/pc body <player> reset`).

## Height

The player model scales with height: 180 cm is the usual model, 200 cm gives a hitbox of exactly 2 blocks, and such a character still fits a 2-block roadway. Taller people take longer steps: at the same Froude number walking speed grows with the square root of leg length, so 196 cm walk 6 % faster than 175 cm.

## Mass

- **The hoist cage** weighs you as body mass plus 10 kg of gear: overalls, boots, helmet, lamp battery, self-rescuer. A heavy shift in the cage eats into the rope safety factor.
- **Blows and blast waves** transfer momentum, and the speed it gives is inversely proportional to mass. A heavy character is thrown back less; below 75 kg there is no bonus.

## Gas in the mine

Carbon monoxide binds to haemoglobin. Blood saturation moves towards an equilibrium set by the CO concentration in the air, but how fast it gets there depends on how much air passes through the lungs per litre of blood (the Coburn–Forster–Kane model).

- **Activity** matters most. Standing, you breathe about as at rest; running or climbing a ladder, about six times as much. Running through a gassy roadway gives you the same poisoning six times faster. In fresh air, running also clears it faster.
- **The body** changes the rate by about ±10 %. Blood volume follows Nadler, basal metabolism follows Mifflin–St Jeor, which also depends on age. A light, young character is poisoned a little faster than a heavy one.

The equilibrium itself depends on neither body nor activity: no mass and no careful pace will save you from a lethal concentration. A self-rescuer will.

## Electricity

A bare live [busbar](item:promptcritical:busbar) gives you a shock if you touch it while standing on the ground, in water or on a ladder. An insulated [power cable](item:promptcritical:power_cable) does not. The current through the body is the phase voltage divided by the body impedance. Dry-skin impedance follows IEC 60479-1: about 1 kΩ at 230 V, and above 700 V the skin breaks down, leaving 620 Ω of internal tissue. Wet skin (rain, water) gives those 620 Ω straight away.

- **Let-go current** (Dalziel): 16 mA for men, 10.5 mA for women. Above it the hand cramps, and you cannot step away while the current flows. That is why portable tools run on 36 V.
- **Fibrillation** (Dalziel): the threshold is K/√t, where K grows with body mass: 0.116 at 50 kg, 0.157 at 70 kg. A 0.4 kV busbar drives about 240 mA and kills in a fraction of a second, a light body sooner and a heavy one a little later. At 6 kV the current is in amperes: instant death and an arc burn.
- **Protection**: a wool carpet or wool block underfoot works as a dielectric mat and holds up to 1 kV. Against 6 kV it is useless. The real protection is to switch off, verify there is no voltage, and earth.
