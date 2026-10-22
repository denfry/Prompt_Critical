A blast furnace runs on coke: strong, porous carbon that carries the weight of the burden and burns at the tuyeres. Raw coal would cake in the shaft and choke the furnace. Coke is made in a **coke battery**: a blend of coking coals is heated without air to 1000 °C, the volatiles leave as gas, and a fused "cake" stays behind.

## The battery

You build the battery freely, block by block. A [coke oven](item:promptcritical:coke_oven) is a wall of blocks 3 high and 4–8 long along the pushing axis. Ovens alternate with [flue walls](item:promptcritical:coke_battery_wall) of the same height and length, and flue walls close both ends. A battery has 3–12 ovens. Every oven needs at least one standpipe of the [gas collector main](item:promptcritical:gas_collector_main) on top, and the standpipes join into one horizontal main. Right-click an oven to open the battery panel, or to be told what is missing.

Flue walls are built of [silica brick](item:promptcritical:silica_brick). Silica changes its crystal form as it heats and grows in volume. Two rules follow:

- up to 1000 °C the battery is heated slowly, no faster than 5 K per hour of physical time. Faster, and the brickwork collects damage;
- a battery once heated must never cool below 600 °C. The whole brickwork cracks.

Heat-up uses **warm-up fireboxes**: coal or coke into the panel slot or the side faces of the ovens. Once there is gas, switch the heating to coke-oven or blast-furnace gas, piped with Create into any block of the battery. The default wall setpoint is 1300 °C; the heat-up rate is set on the panel.

## The oven cycle

The charge is coking coal, 100 kg per item. It goes in from above: a hopper into the oven's charging hole, or a **larry car**, a Create contraption with storage that stops over the holes. Coking time grows with the square of oven width: at 1300 °C it is about 13.5 minutes. Colder walls or wetter coal take longer.

The finished cake is pushed out by a [pusher ram](item:promptcritical:coke_pusher_ram) on a Create contraption: a piston, gantry or cart brings it to the end of the oven. Coke goes into the storage of a contraption on the coke side, the **quench car**, or into a storage block by the door. With nowhere to go, burning coke spills on the ground.

Do not push an oven that is not ready: the cake sticks and the ram overloads. Coke left too long burns off: its M25 strength drops. Push soon after the oven is ready.

## Quenching and coke

Hot coke is quenched with water in the 3×3×3 [quench tower](item:promptcritical:quench_tower): the car runs in under the tower, water comes by pipe. Each kilogram of coke takes 0.45 kg of water. The blast furnace accepts quenched coke only.

[Coke](item:promptcritical:coke) carries an assay: ash, sulfur, moisture, M25. Ash and sulfur come from the coal. Below M25 80 the coke breaks down in the furnace and the burden hangs. The fines, [coke breeze](item:promptcritical:coke_breeze), go to sintering.

## Gas main and by-products

Gas from the ovens collects in the gas main. Its pressure is held by [exhausters](item:promptcritical:gas_exhauster) on Create shafts touching the main: shaft speed is suction. Normal is a slight positive pressure, +50…200 Pa.

- Too little suction: above 300 Pa the bleeders open and the gas burns uselessly on the standpipes.
- Too much suction: vacuum. Below −50 Pa air leaks into the main; 30 seconds of that and it explodes.

Pipes draw the gas from the exhauster tank: it fuels the battery, the stoves and the boilers. [By-product units](item:promptcritical:byproduct_plant) touch the main: the primary cooler and the tar precipitator take out coal tar, the saturator binds ammonia with sulfuric acid into ammonium sulfate, the scrubber takes crude benzol out with wash oil. Coal tar mixed with heat gives pitch, the raw material for pitch coke and electrodes.
