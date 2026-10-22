An ingot is not yet usable metal. Its cast structure is coarse, with porosity inside. Hot rolling closes the pores, refines the grain and gives shape: plate, rod, billet. Steel is rolled hot: at 1100–1200 °C it resists deformation several times less than cold.

## Reheating furnace

The 3×2×3 [reheating furnace](item:promptcritical:reheating_furnace) heats ingots and billets with fuel gas from a pipe. Charge comes from the input or from the roller table behind the furnace. A piece within 30 K of the setpoint leaves through the front opening onto the roller table in front. The working window is 1150–1250 °C. Every heat loses 1–2 % of the metal to scale. Above 1300 °C the steel is burnt: the grain boundaries oxidise and the piece is scrap.

## Roller table

A [roller table](item:promptcritical:roller_table) carries the piece on its rollers. A Create shaft runs through a row of tables along their axis: at 64 RPM the piece travels 1 block a second. Shaft direction is travel direction; reverse the shaft and the piece comes back. At the end the piece passes to the next receiver: another table, a mill stand, a furnace, the piercing mill. Shift + right-click turns a table into a **cooling bed**, which holds the piece until it is below 200 °C.

## Mill stand

The [rolling mill stand](item:promptcritical:rolling_mill_stand) is a 3×3×1 two-high stand: two rolls of 0.3 m radius, Create shaft through the port on the left. The piece arrives on a roller table along the front, passes the rolls at the gap set on the panel and leaves on the table at the other side. For the next pass, reverse the table shaft and close the gap. That is how rolling goes: back and forth, pass after pass.

The rolls draw the piece in by friction. Reduction per pass is at most μ²R, about 37 mm with these rolls. More, and the piece is not bitten and stays on the table. Roll force grows with reduction, width, and the hardening of cooling metal. A 150 mm billet reduced by 30 mm at 60 RPM takes 1.12 MW from the shaft. Torque above 300 kN·m shears the coupling **pin**: the stand stays down until a new one goes in, an iron nugget, right-click.

Each hot pass costs about 15 K. Below 850 °C the steel is no longer austenite: cracks and overload. When the piece gets cold, send it back to the furnace.

The panel button changes the **rolls**:

- **flat** rolls squeeze: a piece at least four thicknesses wide becomes [plate](item:promptcritical:steel_plate), a narrower one a [billet](item:promptcritical:steel_billet);
- **section** rolls close in from all sides: a piece under 30 mm becomes [rod](item:promptcritical:steel_rod).

A 100 kg ingot is about 160 mm square. A [beam](item:promptcritical:steel_beam) is welded from plates on a crafting table.

## Heat treatment

The 2×2×2 [heat treatment furnace](item:promptcritical:heat_treatment_furnace) heats a load of rolled stock and pipe to its setpoint; the "Unload" button raises the door. In "Quench" mode it unloads into a [quench tank](item:promptcritical:quench_tank) next to it. The quench works when the tank holds at least 2000 mB of water or oil below 80 °C; hot water gives a slack quench, and above 100 °C the water boils off. Hardness after quenching is about 20 + 60√C HRC, C being carbon in percent. "Air" mode is for normalizing, tempering quenched steel and stress relief.

Quench and temper together make **hardened and tempered** steel: 30–60 % more strength. The grade changes with the treatment and shows in the item tooltip.

## Where steel goes

Steel replaces iron in generators, motors, transformers, switchgear cells and concrete poles. The generator coupling is made from rod. A Create tank is made from plate, Create pipes from [seamless pipe](item:promptcritical:seamless_pipe), covered on the next page.
