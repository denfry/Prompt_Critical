## What is wrong with raw water

River water carries dissolved calcium and magnesium salts, its **hardness**, about 5 meq/L. On boiling they come out on hot surfaces as scale. Scale conducts heat tens of times worse than steel: the metal under it overheats, and the boiler burns more fuel. The water also carries dissolved oxygen, which eats boiler tubes from inside.

Vanilla water in the game is raw water. Each treatment stage produces its own fluid, and Create tanks keep them apart.

## Scale in the fire-tube boiler

An I0 boiler on raw water scales up on the crown of its furnace tube. You can see it without instruments if the boiler has been accepted and has a passport: the [maintenance panel](item:promptcritical:maintenance_panel) compares flue gas temperature and efficiency with the figures at acceptance. Hotter gas and lower efficiency mean the scale is growing. The thresholds are 0.25, 0.5 and 1 mm. Beyond that the furnace crown overheats and creeps.

It is cleaned with a [boiler scraper](item:promptcritical:boiler_scraper) on a boiler that is shut down, cooled and drained. Only water treatment removes the cause.

## The stages

Water enters a block at the top and back and leaves at the bottom and front. Each block passes half a kilogram a second; set them in a chain in this order.

- [Lime clarifier](item:promptcritical:water_clarifier). Slaked lime takes up the bicarbonates and the carbonate hardness drops out as chalk. About 0.6 meq/L remains, with sludge in suspension. Lime is [burnt lime](item:promptcritical:burnt_lime) in its hopper; the panel shows the stock.
- [Sand filter](item:promptcritical:sand_filter). Sand holds back the sludge and clogs; its loading shows on the panel. Backwash with Shift + right-click, empty hand.
- [Sodium ion exchange column](item:promptcritical:ion_exchange_column). The resin trades calcium and magnesium for sodium: hardness drops a hundredfold, and out comes softened water. The resin fills up. After **breakthrough** the column passes water through unchanged; the panel shows resin loading in percent. Regenerate with [salt](item:promptcritical:salt); the panel says how much. Salt is evaporated from water by heated mixing in Create.
- [Deaerator](item:promptcritical:deaerator). Softened water is brought to the boil with steam from the drum of a water-tube boiler (within 12 blocks, drum above 0.3 MPa). Oxygen leaves with the vent steam; out comes feedwater at 104 °C with no more than 15 µg/kg of oxygen.

The clarifier ahead of the column is optional, but it takes most of the hardness off it: the resin runs longer and asks for less salt.

## Which water where

- I0 fire-tube boiler: softened water is enough. No scale.
- Water-tube boiler: feedwater from the deaerator. Softened water without deaeration leaves no scale, but the oxygen pits the water walls. Raw water ruins the boiler with both scale and corrosion.

Water treatment in this era does not remove chlorides: they stay in softened water and slowly eat the metal. That is corrosion over years, not hours, but it is there.
