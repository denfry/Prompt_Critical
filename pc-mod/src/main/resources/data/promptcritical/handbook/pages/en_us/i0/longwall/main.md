A longwall is a long working face along the seam, tens of blocks wide. A shearer travels along it and takes the coal off in a strip, while powered supports hold the roof above. Behind the supports the roof caves, and the face advances step by step into the seam.

## The shearer

The [longwall shearer](item:promptcritical:longwall_shearer) is the head of a Create contraption. Mount it on the carriage of a gantry and run it along the face.

- Drums at each end of the body cut a strip 1 block deep on the front side. The leading drum takes the upper rows, the trailing drum the floor row. The strip is 3 blocks high.
- Until the column under a drum is cut out, the contraption waits. The shearer never skips uncut coal.
- Drive power depends on the gantry shaft speed: about 220 kW at 64 rpm, but no more than the spare power in the network. The shearer declares it as stress on the gantry shaft gear under the carriage.
- Feed is up to 6 m/min. Hard rock cuts slower than coal and takes more energy.
- Cut coal falls behind the body. Lay a Create belt along the face to carry it away.

## Water sprays

The shearer makes more dust than anything else in the mine. Put water tanks on the contraption: the shearer uses about 30 l per ton and sprays its drums. Five times less dust is left. An unsprayed face quickly buries the workings in coal dust, and they need stone dusting more often.

## Powered supports

A [powered roof support](item:promptcritical:powered_roof_support) is a canopy on hydraulic legs. Left alone, a support sets itself against the roof within a couple of seconds and holds it. A set support holds the face, but it cannot be moved.

- To lower a support: a redstone signal on any of its blocks, or an empty-hand right-click on its manual lever.
- A Create piston pushes a lowered support 1 block towards the face.
- Remove the signal or put the lever back, and the support sets again.

## The longwall cycle

- The shearer cuts a stretch of the face.
- Behind it, supports are lowered one at a time, pushed to the new face and set again.
- Behind the advanced supports the roof is left unsupported and caves by the span rule. This is the goaf; nobody goes there.

The "lower, advance, set" sequence is easy to build with redstone timers and logic. In later epochs a controller takes it over.

## Gas and air

Every exposed coal block is a methane source, and a shearer exposes dozens a minute. A longwall needs plenty of air, but no faster than 4 m/s: a strong current raises dust and blows a lamp flame through the gauze. Keep a canary there and check methane in the return. See [The mine: ventilation and methane](promptcritical:i0/mine_ventilation).
