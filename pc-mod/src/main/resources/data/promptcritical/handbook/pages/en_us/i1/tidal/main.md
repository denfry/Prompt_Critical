The sea rises and falls twice a day because the Moon and the Sun pull on the water. Dam a bay, and the water behind the dam lags behind the sea, so the difference in level can be run through a turbine. That is how Kislaya Guba near Murmansk (1968) and La Rance in France (1966, 240 MW) work.

## The tide in the world

The tide here is the sum of five waves. The main one is the lunar semidiurnal M2, joined by the solar S2, the elliptic N2 and the diurnal K1 and O1. The Minecraft moon runs through its phases in 8 days. At full and new moon the lunar and solar waves add up, and the tides are at their biggest (springs). At the quarters they cancel, and the tides are small (neaps). Every 4 days springs give way to neaps.

How big the tide is depends on the place. In the open ocean the M2 amplitude is about half a metre. It grows on a shallow shelf, and grows more in a narrow bay open to a wide sea, because the wave is squeezed in width and depth. A lake or a river far from the sea has no tide.

The [tide gauge](item:promptcritical:tide_gauge) shows the sea level, the M2 amplitude at that spot and the moon phase.

The visible water in the world does not move. A tide of a couple of metres would dry out strips tens of blocks wide, and the water would spill over buildings. So the sea level exists only on the instruments and in the head calculation.

## Building the plant

1. Dam the bay. The basin behind the dam must be closed, with no way out to the sea, and hold no more than 40,000 blocks of water.
2. Set the [bulb unit](item:promptcritical:tidal_unit) into the dam, facing the sea, with its back to the basin.
3. Put a generator on the shaft on top, as with the hydro turbine. The wrench changes the gear.

The unit sizes itself to the basin area and the tidal range. The rated head is 0.7 of the mean range, and the flow is set to work through the tidal prism in 4 hours.

## Modes

- **One-way ebb generation.** On the flood the basin fills through the sluices, and after high water the gates close. Once the sea has fallen by the starting head, the water runs from the basin through the unit until the head drops to the minimum.
- **Two-way generation.** The unit works on both the ebb and the flood, with the sluices equalising the levels in between. It yields more, but the unit runs less efficiently in reverse.
- **Gates open.** The basin follows the sea.

Change the mode with the panel buttons or with sneak and right-click. The plant runs on game time: a Minecraft day counts as 24 hours, so high water turns to low water in about 6 minutes.
