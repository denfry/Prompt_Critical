A deep mine needs two services that never stop: the hoist, to lower men and raise coal, and drainage, to keep the mine from flooding.

## The hoisting installation

- The [mine hoist](item:promptcritical:mine_hoist) is a drum on a Create shaft. The shaft goes to the coupling at the end of the drum; rated speed is 64 rpm, up to 400 kW.
- The [headframe](item:promptcritical:headframe) is the tower with the sheave over the shaft, 2–4 segments. It stands in front of or behind the hoist, no more than 32 blocks away.
- The [mine cage](item:promptcritical:mine_cage) runs in the shaft under the headframe. The top landing is just below the lowest headframe segment. The shaft extends down as long as the space under the cage is clear.
- [Wire rope](item:promptcritical:wire_rope) comes in 10 m coils: 32 mm diameter, 1770 MPa wire, 6×19 construction.

A landing exists wherever there is a way out into a heading beside the cage: two free blocks over a solid floor. The bottom landing is the shaft bottom, the sump.

Hang the rope by right-clicking the hoist with a stack of coils. You need enough coils for the shaft depth plus the run over the sheave.

An empty-hand click on the hoist opens the panel: landing buttons, "Stop", "Reset trip" and a speed limit up to 12 m/s. Close the cage door with an empty hand before sending it. With the door open, the hoist will not start.

## Rope safety factor

The load is whatever is in the cage when it departs: a person is 80 kg, a minecart 300 kg. The hoist divides the rope's breaking force by the highest tension: the cage, the load and the rope's own weight, including acceleration. That is the **safety factor**. Limits: man-riding 9, mixed duty 7.5, materials only 6.5. The duty is set by who is in the cage. Below the limit, the hoist refuses to start.

## Protections and wear

- Overspeed more than 15 % above the limit, overwind or loss of shaft power: the hoist goes onto the brake. Clear the cause, then press "Reset trip".
- The rope fatigues from bending over the sheave and corrodes in a wet shaft. When wear approaches the limit, the panel reports that the rope is due for replacement. Change it only with the cage stopped.
- A rope worn to the end breaks. The cage either lands on its safety catches or falls.

## Drainage

Water reaches a mine in two ways: every ton mined opens up about a cubic metre of water, and another 2 m³/h or so seeps in from the strata. Water drains to the **sump** by the lowest workings. Its capacity is about 400 m³. When it overflows, water floods the lowest levels.

The [mine drainage pump](item:promptcritical:mine_drainage_pump) is a multistage centrifugal pump on a Create shaft, shaft on the left. At 64 rpm it delivers 100 m³/h against 150 m and takes about 58 kW. The head is the height from the pump to the surface. Pumped water leaves through a pipe on the top face.

The rule: the sump must hold at least four hours of inflow. The gas monitoring cabinet's summary warns you if it is too small. An empty-hand click on the pump shows flow, head and power.

Water from sulphurous seams is acidic. It must not be discharged without neutralising.

If drainage stops, the lowest workings flood within a few hours of physical time.
