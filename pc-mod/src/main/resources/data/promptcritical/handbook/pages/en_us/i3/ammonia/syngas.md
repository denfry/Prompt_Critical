Nitrogen is everywhere, yet plants and chemistry can hardly use it: a triple bond holds the N₂ molecule together. Haber and Bosch learned to bind it with hydrogen into ammonia, and since then ammonia has carried fertilisers, explosives, nitric acid and later the uranyl nitrate of the nuclear fuel cycle. The hydrogen comes from natural gas.

## Steam reformer

The [steam reformer](item:promptcritical:steam_reformer) is a 3×3×3 tube furnace. Sales gas from the gas plant, piped to any face, feeds both the tubes and the burners. Boiler feedwater makes the process steam. The tubes hold a nickel catalyst: at 800 °C and 3.2 MPa methane and steam give CO and hydrogen (CH₄ + H₂O → CO + 3H₂), and about 10 % methane is left at the outlet. Catalysts go in by hand: **nickel** into the furnace tubes, **iron-chromium** into the high temperature shift, **copper-zinc** into the low temperature shift. Without nickel the furnace will not start.

Steam is given with a margin, 3.5 mol per mol of methane. Push the steam-to-carbon ratio below 2.5 and gas gets cheaper, but whisker carbon grows on the nickel and kills the catalyst in a few game hours. The Carbon lamp warns you.

## Secondary reformer

The hot gas from the tubes enters a refractory-lined shaft reactor together with air. Oxygen burns with hydrogen, the gas heats to almost 1000 °C and reforms the methane down to a fraction of a percent. The air also brings the nitrogen that ammonia needs. A ratio controller sets the air: the panel holds (H₂ + CO)/N₂, 3.10 by default. After the shift and the methanator this lands almost exactly at 3 mol of hydrogen per mol of nitrogen.

## CO shift

CO gives its oxygen to water: CO + H₂O → CO₂ + H₂. The reaction releases heat, so it runs in two stages: on iron-chromium at 370–440 °C (about 3 % CO left) and on copper-zinc at 205–230 °C (about 0.3 %). The heat of the gas after the reactors raises steam that regenerates the purification solvent.

## Purification

The [CO₂ removal column](item:promptcritical:co2_absorber) stands against the reformer. In the absorber a monoethanolamine (MEA) solution takes CO₂ down to 200 ppm; in the stripper the solution boils and the CO₂ goes to the air. The methanator turns the last CO and CO₂ into methane, because any oxygen poisons the iron synthesis catalyst. The panel shows the CO₂ after the absorber and the methanator temperature.

Cut the solvent circulation and CO₂ slips through, the methanator heats up (about 60 K per percent of CO₂) and trips above 450 °C. Reset the trip with Shift + use or the panel key. Off-spec gas (H₂:N₂ outside 2.7–3.3 or more than 10 ppm CO + CO₂) goes to the vent stack, as at start-up. Take the finished synthesis gas by pipe from any face of the column.
