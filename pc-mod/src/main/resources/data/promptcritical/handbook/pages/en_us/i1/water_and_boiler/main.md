In a fire-tube boiler the fire is inside and the water outside, and a thick shell holds the pressure. A **water-tube** boiler turns that round: water and steam run through tubes, with the fire around them. Thin tubes hold high pressure, and a furnace walled with tubes can be large. Power station boilers were built this way: 1.4 MPa, superheated steam, gas or pulverized coal.

## Construction

The boiler is built freely around its [BMS panel](item:promptcritical:water_tube_boiler), which touches a furnace wall from outside.

- **Furnace**: a hollow box of [water walls](item:promptcritical:boiler_wall) on all six sides, 5–9 blocks across and 8–16 high, empty inside. Output follows furnace volume, 200 kW per cubic metre.
- [Burners](item:promptcritical:boiler_burner): 1–8 in the side walls, not on the edges; up to 4 MW per burner.
- [Steam drum](item:promptcritical:steam_drum): at least one on the furnace roof. Steam separates from water in it.
- **Convection pass**: [superheater](item:promptcritical:boiler_superheater), [economizer](item:promptcritical:boiler_economizer) and [air preheater](item:promptcritical:air_preheater), up to six blocks of each, within two blocks of the furnace. Three superheater blocks give steam at about 250 °C; the economizer and the air preheater take the flue gas down to 150 °C.
- **Draught**: a [forced draft fan](item:promptcritical:forced_draft_fan) and an [induced draft fan](item:promptcritical:induced_draft_fan) on Create shafts within 12 blocks of the boiler. Flow follows shaft speed; the inlet vanes set the share.

Fuel is fuel gas from Create pipes: mine methane, coke-oven, blast-furnace or converter gas. Or [coal dust](item:promptcritical:coal_dust), coal milled in Create, by funnel into the panel or a burner. The fuel sets the burner type; it can only be changed with the boiler shut down. Steam engines touching the drum, a water wall or the panel take superheated steam.

## Controls

The panel runs three loops, each in AUTO or MAN:

- **feedwater** holds drum level with the built-in feed pump;
- **combustion** holds steam pressure (1.3 MPa setpoint) with fuel flow. Air is **cross-limited**: on a load rise air goes up first and fuel follows; on a drop fuel comes down first. Never for a moment is there more fuel than air;
- **draught** holds 20–50 Pa of suction in the furnace with the induced draft fan.

In MAN the operator moves the fuel valve, forced draft, induced draft and feed in 5 % steps.

## BMS

Lighting a furnace is the most dangerous job in a boiler house. Fuel admitted without a flame collects in the furnace as a cloud. A spark in that cloud is an explosion, and the furnace comes apart. So lighting up is run by the **burner management system**, and its steps cannot be skipped:

- **Purge.** Allowed when the forced and induced draft fans are running and the fuel valves are closed. For 300 s air sweeps every trace of fuel out of the furnace.
- **Igniter.** Within 300 s after purge. The flame scanner must see the igniter flame within 10 s, or the fuel trips.
- **Fuel.** The main flame must light within 5 s on gas or 10 s on coal dust, or the fuel trips.
- **Running.** Loss of flame or any protection trips the fuel, followed by a 300 s post-purge.

Boiler trips: drum level very high or very low, steam pressure too high, furnace pressure or suction out of limits, CO in the flue gas, no fuel pressure, a water wall tube burst. The safety valve on the drum lifts at 1.5 MPa.

The **bypass key** shunts the flame scanners. With it the BMS cannot tell a lit furnace from a dead one: fuel will flow into a cold furnace, and the next spark blows it up.

## Boiler water

Thin water wall tubes do not forgive bad water. Hard water leaves scale, and the tube under it overheats. Dissolved oxygen eats the metal from inside, and so do chlorides. An overheated, thinned tube bursts and the boiler trips. Clean feedwater is the subject of the next page.
