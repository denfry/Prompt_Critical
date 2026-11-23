A motor is iron and copper. The iron carries the field and heats up a little every half-cycle, the copper carries the current and heats up as I²R. How much it heats is settled at the press and on the winding machine, long before the motor is switched on.

## Electrical steel

Silicon makes iron a poorer conductor, and that is what you want: eddy currents inside the sheet weaken. Steel with about 3 % silicon and no more than 0.08 % carbon comes out of the melt as grade 2212. Plain iron of the same thickness loses twice as much.

The [cold sheet mill](item:promptcritical:sheet_mill) rolls a steel plate up to 4 mm thick down to 1.0, 0.65, 0.5 or 0.35 mm. Each sheet in the output is one metre by half a metre, so a plate rolled to a quarter of its thickness gives four sheets. Silicon steel is brittle: after a pass or two the mill stops and asks for an anneal. Above 4.5 % silicon it will not roll cold at all.

Anneal sheets in the [heating furnace](item:promptcritical:heating_furnace) at 800 °C for two minutes. A rolled sheet that was never annealed keeps its work hardening and loses far more in the field. The tooltip shows the loss in watts per kilogram at 1.5 T and 50 Hz.

## Varnish and paper

Laminations have to be insulated from each other. Put paper or phenolic resin into the coating slot of the furnace and hold the chamber at 150–250 °C for a minute: each sheet or bundle takes one item. Paper is thick and safe; varnish is thin and leaves more room for steel. Above 300 °C any coating burns off, so anneal first, then varnish.

## Punching

The [slot notching press](item:promptcritical:notching_press) cuts a stator lamination, 191 mm across with 36 slots, from each sheet ten times over. The whole lamination would need about 46 tonnes at once; the press has 25, so it punches slot by slot. Punching force is cut length × thickness × shear strength.

Every cut leaves a burr. The burr grows as the die wears, and a burr taller than the insulation between the laminations shorts them together: a shorted pair heats like one sheet twice as thick. Regrind the die when the burr reaches a few tens of microns. The cut edge is work hardened too; an anneal after punching relieves it, and varnish laid on after punching flows over the burr.

## Stacking and winding

The [stator winding machine](item:promptcritical:winding_machine) stacks bundles into a 125 mm core and winds it with insulated copper wire: about 290 m for three phases. Draw copper to 1.4–1.6 mm, anneal it, then coat it in the furnace: paper gives class A, phenolic enamel class B. Enamel on hard-drawn wire cracks when the end turns are bent.

Thick wire lowers resistance but must still fit the slot. The panel shows the slot fill: above 44 % the insulation is scraped on the slot edges. Without wire the machine gives a bare core.

The tooltip of a wound stator shows what a 5.5 kW motor built on it would lose in iron and copper, its efficiency and the insulation resistance of the new winding. A careful stator reaches about 86 %, a careless one barely 83 %, and a damaged winding starts its life closer to failure.
