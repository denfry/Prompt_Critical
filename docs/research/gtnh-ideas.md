# Ideas from GregTech: New Horizons

Goal: look at what GT New Horizons (GTNH) does and what can be taken from it as
an idea. Evidence labels are explained in [README.md](README.md).

## Scope and caveats

- Only mechanics are considered. Code, textures, names and recipe data are not to be
  reused. The licence of GT5-Unofficial was not confirmed; one forum
  post claims "all rights reserved", so check the repository before touching anything
  beyond ideas.
- The GTNH wiki (gtnh.miraheze.org) could not be opened through search. Details below
  come from pages about GregTech 5 Unofficial, the code GTNH builds on, and from the
  documentation of GregTech CEu. Where nothing was found the item is marked **[M]**.
- GTNH is a 1.7.10 modpack. This is about design, not about compatibility.

## The difference in kind

GregTech abstracts a process into a recipe: inputs, outputs, a duration, an energy
rate, a voltage tier. Prompt Critical simulates the process. That decides what
transfers:

- ideas about **what the player has to care about** (maintenance, cleanliness,
  pollution, documentation, logistics) transfer well;
- ideas about **how progress is gated** (voltage tiers, overclocking) mostly do not,
  because here the gate is supposed to be the physics.

## Worth taking

| GTNH mechanic | What it does there | How it would look here |
|---|---|---|
| **Maintenance on multiblocks** | A new problem appears every 48–72 hours of running on average; each is fixed with one specific tool (wrench, screwdriver, soft hammer and so on); every open problem costs 10 % efficiency **[S]** | The mod already has physical wear (bearings, packing, impellers, Weibull life) and a maintenance panel. Worth borrowing: a specific tool and a specific action per fault, so repair is something the player does at the machine |
| **Cleanroom** | A multiblock room with filter casings; recipes for wafers and circuits need it; open doors and maintenance problems lower its effectiveness, and poor state can void products **[M]** (one modpack page of unclear origin agrees) | Same role, physical model: ISO class from air changes and filters, defect density, yield. See [electronics.md](electronics.md) |
| **Pollution** | Machines emit pollution into their chunk; it spreads and has effects on the surroundings; muffler hatches and air filters reduce it **[S]** for the parts on hatches and filters, **[M]** for the effects | Combustion here already computes SO₂, NOx and particulates. With the wind field of [environment.md](environment.md) a plume can be carried and deposited, and stack height and gas cleaning become design choices |
| **Research and data for advanced recipes** | Advanced items need their data scanned and stored before an assembly line can build them (GregTech CEu documents scanner research, station research and computation as recipe requirements) **[S]** | Matches [documentation.md](documentation.md): a machine is built from drawings, and a shop needs them to build the next one |
| **Turbine rotors as parts** | A large turbine takes a rotor item; its material and size set efficiency, durability and optimal flow **[S]** | Rotors and blades as manufactured parts whose accuracy and material set efficiency, erosion and life. See [machine-building.md](machine-building.md) |
| **Hand tools in early crafting** | Early recipes consume durability of hammers, files, saws; machines later replace the hand work **[M]** | The same arc as "machine tools make life easier": manual operations first, then machines that remove them |
| **A material system** | One definition of a material generates its ingots, plates, rods, wires, gears and their properties **[M]** | Alloys are defined in several places today (`CastAlloy`, `SteelGrade`, cutting constants in `Machining`). One material record with thermal, mechanical and machining properties would also fix the room-temperature c_p finding of the audit |
| **Mixed ore veins and prospecting** | Large veins of several ores, found by prospecting **[M]** | Already present: vein generation with assays and a geological map |
| **By-product chains** | Ore processing yields secondary metals at each stage **[M]** | The "connected industries" pillar; coke by-products already work this way |
| **Structure preview** | A projector shows where the blocks of a multiblock go **[M]** | A drawing that projects a ghost outline, tied to documentation |
| **A quest book** | A long guided questline is what makes the pack learnable **[M]** | The handbook, procedures and advancements cover reference and steps. A guided path through each era, phrased as commissioning tasks, would cover onboarding |

## Not worth taking

| GTNH mechanic | Why not here |
|---|---|
| **Voltage tiers as the progression gate** | A machine of tier N needs voltage N because the recipe says so. Here voltage classes are real ones with insulation and losses; the gate should be what the player can build |
| **Overclocking** | A recipe run one tier up draws four times the power and takes half the time **[S]**. There is no process behind it. A real machine run harder has its own limits: flow, heat removal, strength, wear |
| **Explosion on over-voltage as a rule** | Cables and machines burn or explode when fed above their rating **[S]**. Here the consequence should be the physical one: insulation breakdown, a fault current, protection operating, and damage within the damage budget |
| **Micro-crafting depth** | Many intermediate items per recipe. Depth should come from process and operation, not from the number of crafting steps |
| **Length for its own sake** | The pack is known for thousands of hours. The eras here already give a long arc |

## Things GTNH has that are already better grounded here

Listed so they are not re-borrowed in a simpler form:

- cable loss and amperage: there is a load flow with real impedances;
- boiler behaviour: there is a fire-tube boiler with draft, a fusible plug and a relief
  valve, and a water-tube boiler with a burner management system;
- fluid pipes: there is a lumped network with friction, pumps and water hammer;
- coil temperature tiers in the blast furnace: there are real furnace temperatures;
- nuclear reactors: there is point kinetics with a lattice model.

## Sources

- [Maintenance Hatch, GregTech 5, FTB wiki](https://ftbwiki.org/Maintenance_Hatch_(GregTech_5))
- [Large Turbine, GregTech 5 Unofficial, FTB wiki](https://ftb.fandom.com/wiki/Large_Turbine)
- [GTNH issue referring to the wiki's Pollution page](https://github.com/GTNewHorizons/GT-New-Horizons-Modpack/issues/14202)
- [GTNH notes on pollution](https://crazedd0ge.github.io/gtnhnotes/pollution)
- [GregTech CEu, research requirements in recipes](https://gregtechceu.github.io/GregTech-Modern/Modpacks/Recipes/Adding-and-Removing-Recipes)
- [GregTech CEu, electric machines and overclocking](https://gregtechceu.github.io/GregTech-Modern/Gameplay/Electricity/Machines)
- [GTNH wiki](https://gtnh.miraheze.org/wiki/Wiki_TODO)
