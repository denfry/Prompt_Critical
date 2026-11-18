# Hall ventilation and special filters

Whatever leaks from a reactor into the hall — iodine from failed fuel, caesium aerosol — goes up with the air. A ventilation stack with special filters decides how much of it leaves the plant.

Build a [ventilation stack](item:promptcritical:vent_stack) as a column of blocks; the lowest one is the exhaust unit. While it runs, every release within 16 blocks goes through the filters within 2 blocks of its base and leaves from the top of the column, high above the ground, instead of from the reactor. Each [HEPA filter](item:promptcritical:hepa_filter) is a stage against aerosols: 99.97 %, a decontamination factor of 3333 per stage — that is caesium. Each [charcoal filter](item:promptcritical:charcoal_filter) is a stage against iodine: 99.9 % of molecular iodine and 99 % of methyl iodide, about 500 for the mix; in rain the air is humid and methyl iodide gets through ten times more. Noble gases pass everything.

What the filters keep stays in them. The filter bank becomes a radiation source of its own, iodine decaying with an eight-day half-life and caesium staying for decades: shield it, and think before you change it.
