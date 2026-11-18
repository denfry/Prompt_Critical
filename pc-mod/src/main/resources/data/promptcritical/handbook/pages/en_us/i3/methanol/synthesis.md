Until now methanol came from dry distillation of wood: a few litres per charge, enough to keep the gas plant's chiller free of hydrates and not much more. The [methanol synthesis unit](item:promptcritical:methanol_converter) makes it from natural gas by the hundredweight, the way every plant has since ICI's low-pressure process of 1966.

## Reformer

The unit carries its own tube furnace. Pipe sales gas and boiler feedwater to any face: the gas is both feed and burner fuel, the water becomes process steam and also boils in the converter shell. Load **nickel** into the reformer and **copper** (the methanol catalyst) into the converter by hand, then Shift + use starts it. The furnace runs at 2 MPa and 860 °C with 3 mol of steam per mol of methane. There is no secondary reformer and no shift here: methanol needs the carbon monoxide that the ammonia plant works so hard to remove.

Reformed gas is hydrogen-rich. The stoichiometric number S = (H₂ − CO₂)/(CO + CO₂) tells you how much: 2.0 is exactly what methanol takes, the furnace gives about 3.0. The extra hydrogen has nowhere to go but the purge.

## Converter and loop

A Create shaft at the back drives the make-up compressor and the recycle wheel, about 0.9 MJ per kilogram of methanol. On copper at 220–280 °C two reactions run side by side: CO₂ + 3H₂ ⇌ CH₃OH + H₂O and the shift CO₂ + H₂ ⇌ CO + H₂O, which keeps feeding CO₂ back from CO. Synthesis gives off heat and shrinks the gas, so pressure helps and heat hurts. From a 2:1 mix of hydrogen and CO the equilibrium converts 64 % of the CO at 250 °C and 8 MPa, 50 % at 5 MPa and only 45 % at 280 °C. The model uses the Graaf equilibrium constants and the Vanden Bussche–Froment rate equations.

So one pass makes only 4–7 % methanol. The gas is cooled to 40 °C, crude methanol condenses in the separator and the rest goes round again, four to five times. Nobody sets the loop pressure: unconverted gas builds it up until the converter keeps pace, usually 6–9 MPa. Methane and nitrogen from the furnace, plus all that surplus hydrogen, pile up in the loop. Purge too little and the pressure climbs to 10 MPa, where the compressor goes on bypass. Purge too much and you burn good syngas. The purge always goes to the reformer burners.

## Copper

The converter is a bundle of tubes in a shell of boiling water, like the Lurgi reactor. The steam pressure sets the bed temperature: 40 bar boils at 250 °C and the catalyst sits about 8 K above that. Raise the steam and the reaction speeds up but the equilibrium slips. Above 300 °C copper crystals sinter and the activity falls within hours. At normal temperature a charge lasts years. If the feedwater runs out, the shell boils dry and the bed heads for its adiabatic equilibrium temperature, well past 300 °C. Watch the Sintering and No water lamps.
