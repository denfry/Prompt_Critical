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
