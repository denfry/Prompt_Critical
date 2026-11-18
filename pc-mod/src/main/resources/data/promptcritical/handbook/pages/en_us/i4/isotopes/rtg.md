## RTG

The [thermoelectric RTG](item:promptcritical:rtg_thermoelectric) turns decay heat into electricity and asks for no fuel or maintenance for years. That is how Arctic lighthouses were lit and how deep-space probes run.

Heat sources:

- [Sr-90](item:promptcritical:sr90_heat_source): the strontium fraction is taken from a [vitrified waste canister](item:promptcritical:hlw_glass_canister) on a crafting table with an iron ingot (steel capsule), and the canister comes back without its strontium. Sr-90 with Y-90 gives 0.92 W per gram of Sr-90, half-life 28.8 years;
- [Pu-238](item:promptcritical:pu238_heat_source): an irradiated NpO₂ capsule plus an iridium ingot (iridium cladding, as in a GPHS). 0.57 W per gram of Pu-238, half-life 87.7 years.

The converter is sized for the source power at first loading. Heat flows through the thermoelements to the fins. The efficiency comes from the junction temperatures and the figure of merit ZT:

η = (T_h − T_c)/T_h · (√(1+ZT) − 1)/(√(1+ZT) + T_c/T_h)

times a system factor of 0.8. A Beta-M with 230 W of heat gives about 10 W. Under water the fins run colder and you get more power, which is why marine RTGs sat in the sea. The source weakens with its half-life and the thermoelements age 3 % a year, so electric output falls faster than heat: the hot junction cools down too.

## What it powers

Off the grid an RTG feeds lamps within 16 blocks while its power lasts (a lighthouse), and a [relay power supply](item:promptcritical:relay_power_supply) placed right against it. That is enough for the telemetry of a remote well, not for a pump.

A [telemetry gateway](item:promptcritical:rtu_station) placed right against an RTG runs off it alone, as a remote site with no mains. It is a low-power RTU with a radio modem and draws 4 W, so a single Beta-M carries it with power to spare. If the RTG stops giving those 4 W (source spent, taken out, or the power already promised to lamps), the gateway goes dark and the dispatcher sees an empty snapshot. It comes back only when the full 4 W is there again. A gateway without an RTG next to it is taken to be on the site's mains. An RTG-fed gateway that polls a wellhead or a pumpjack for a whole minute without a break earns *Everlasting Battery*.

## Hazards

The casing shielding holds the radiation back: hundreds of kilograms of depleted uranium against Y-90 bremsstrahlung in a strontium RTG, polyethylene against neutrons in a plutonium one. A bare source in your hands is another matter: a 230 W strontium source gives a few grays per hour at a metre. Break the RTG and the source drops out unshielded. Abandoned RTGs and sources stripped for scrap metal are real history, best not repeated.
