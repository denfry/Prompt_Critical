# The flare system

A flare tip on its own burns whatever a separator sends it, with pilots that never go out. A real flare is a system, and the [flare ignition panel](item:promptcritical:flare_ignition_panel) makes it one: it takes over the flare tip within 8 blocks and counts its burning instead of the separator.

**Pilots.** Three pilot burners run on fuel gas piped into the panel, about 1.5 m³/h each. The Ignite button lights them. When the fuel runs out they go out, and the relief leaves the tip unburnt — methane straight into the air, the worst thing a flare can do.

**Steam.** Soot forms when the flame lacks air; steam at the tip draws air in. The smoke index is 1 − s/s_req, where s is steam per kilogram of gas and s_req is 0.3 for associated gas. Steam comes from the drum of a water-tube boiler within 16 blocks. Too much steam cools the flame: above three times s_req the combustion falls by 0.1 per unit, and below 60 % the flame lifts off.

**Knock-out drum.** The [knock-out drum](item:promptcritical:flare_knockout_drum) catches the condensate the gas carries, here 50 L per 1000 m³. Pump it off as natural gasoline. Without the drum, or with a full one, liquid reaches the flame and falls as burning rain within 40 m.

**Seal drum.** The [seal drum](item:promptcritical:flare_seal_drum) holds water between the stack and the header so that air and flame cannot go back. Relief carries the water off slowly. Below 250 mB with a burning flare the flame flashes back into the header: an explosion at the seal drum, and the pilots go out.

**Radiation.** The flame radiates K = F·Q/(4πD²) with F = 0.25. Up to 1.58 kW/m² you can stay; at 4.73 a fire resistance effect is stripped and you take damage; at 9.46 you burn. A fire suit takes the flux to a tenth. The panel shows how far out 1.58 kW/m² reaches: keep that ring empty.
