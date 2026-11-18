A reactor is an expensive power plant, but it has something no steam engine has: neutrons. Put a piece of cobalt in the flux and a year later you hold a source that sterilises surgical tools and crosslinks cable polyethylene. Iridium becomes a radiography source in a month. Neptunium turns into plutonium-238, the fuel of RTGs, in a few months.

## Irradiation channel

The [irradiation channel](item:promptcritical:irradiation_channel) is a graphite block with an aluminium liner. It goes in one of two places:

- in the stack of a [graphite pile](item:promptcritical:reactor_controller) instead of graphite. The pile console hands the channel the flux of its node once a second. A pile's flux is weak (a few hundred watts give millions of neutrons per cm² per second), so this is an experiment rather than production;
- against the vessel of the [power reactor](item:promptcritical:power_reactor), within two blocks. That is an AM-1 experimental channel at 0.3 m radius with about 10¹³–10¹⁴ at full power. The reactor has four of them.

The channel takes an [irradiation capsule](item:promptcritical:irradiation_capsule). Fill it on a crafting table: an empty capsule plus [cobalt](item:promptcritical:cobalt_ingot) (100 g of pellets), [iridium](item:promptcritical:iridium_ingot) (10 g of discs) or [neptunium dioxide](item:promptcritical:npo2_powder) (50 g).

## What the flux does

A target nucleus captures a neutron and turns radioactive. The product decays and burns in the flux too, so activity climbs towards saturation instead of growing forever. The chain is solved exactly (Bateman). At a flux of 10¹³:

- 1 kg of cobalt for a year gives about 465 TBq of Co-60, 12.6 Ci per gram. Half-life 5.27 years;
- 10 g of iridium for 30 days gives 26 TBq of Ir-192, enough for several cameras. Half-life 74 days, so sources need replacing;
- Np-237 → Np-238 → Pu-238: the yield rises for months, then falls as Pu-238 burns. When to pull it is your call.

A thick target shields itself: inner layers see fewer neutrons. A 6 mm cobalt pellet gets a factor of 0.63, which is why cobalt is irradiated as thin pellets.

## The price

The target eats neutrons that would otherwise split uranium. 100 g of cobalt in an AM-1 channel costs a few hundred pcm of reactivity. The channel panel shows its worth, and the regulator has to pull the rods out further.

Core gamma rays heat the capsule, about 5 W per gram at PWR power density. In the AM-1 channel water that is nothing. In the air of a pile with poor cooling the capsule goes past 427 °C and fails: a tenth of the product stays in the channel, and the channel starts to shine on its own.

## Pile slugs

Slug cassettes of the T0 pile build up fission products from the fissions of their node: caesium, strontium, iodine, zirconium, ruthenium, krypton. The tooltip shows burnup and activity. A cassette pulled from a pile that has been running is a radiation source, so don't carry it around bare. A discharged power-reactor assembly carries activity from the moment it leaves: fission products plus Co-60 from the steel tubes.

## Americium-241

Americium needs no irradiation: it grows by itself in aged PuO₂ from plutonium-241 (half-life 14.3 years). A kilogram of plutonium with 10 % Pu-241 gains 21 g of Am-241 in five years. Americium is separated at the crafting table: PuO₂ and a [strong-base anion exchange resin](item:promptcritical:ion_exchange_resin_sba) give [americium-241 oxide](item:promptcritical:am241_oxide), and the PuO₂ comes back clean. The oxide with an iron ingot makes a sealed Am-241 source. Its gamma rays are soft (59.5 keV); it is dangerous mainly when the capsule breaks: alpha aerosol.

Americium and curium-244 also build up in the spent fuel itself. After five years of cooling, together with Pu-238, they give about a quarter of an assembly's decay heat. Curium-244 is also the main neutron source of aged fuel. In PUREX americium and curium go to the raffinate and then into the HLW glass.
