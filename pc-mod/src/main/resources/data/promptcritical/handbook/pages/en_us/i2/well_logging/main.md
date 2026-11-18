Seismic shows a structure. Logs and cores tell what is in it.

## Logging

Put a [logging unit](item:promptcritical:logging_unit) near the rig and load its sondes with a right click. Stop the pumps and open the preventer, then press Log: the sondes go down the open hole from the casing shoe to the bottom and record on the way up at 0.15 m/s, one sample every 0.1524 m.

- The [gamma ray sonde](item:promptcritical:gamma_logging_sonde) counts the natural radioactivity of the rock. Shale is rich in potassium, thorium and uranium and reads about 115 API, clean sandstone about 22. The count is Poisson: 60 counts a second per 100 API, so in each sample window the error is 1/√N, worse in clean sand.
- The [neutron sonde](item:promptcritical:neutron_logging_sonde) carries an Am-Be source. Fast neutrons slow down on hydrogen. Two detectors 30 cm apart give a ratio 2·e^(Δr/Ls) that hardly depends on the source. Water-filled pores give the true porosity; gas has little hydrogen and hides it. The gas effect is how you find the gas-oil contact.
- The [resistivity sonde](item:promptcritical:resistivity_logging_sonde) reads Archie's law, Rt = Rw/(φ²Sw²). Salt water conducts and oil does not: at φ = 0.2 and Rw = 0.05 Ω·m a water sand reads 1.25 Ω·m, an oil sand with Sw = 0.25 reads 20. The jump with depth is the oil-water contact.

A sonde can stick in the hole: 0.3 % a run, four times that with more than 50 m of shale in the interval. A stuck sonde stays down there, and with the neutron sonde so does its source: write it in the station journal.

## Cores

Use a [core barrel](item:promptcritical:core_barrel) on the driller's console to cut a 9 m core from the bottom of the hole. A [reservoir core](item:promptcritical:reservoir_core) tells sandstone from shale, and oil in it glows under UV. The [core analysis station](item:promptcritical:core_analysis_station) measures it: porosity with a helium porosimeter to ±0.005, permeability with a gas permeameter to ±10 %, water saturation by Dean-Stark to ±0.03. Without a core porosity comes from the neutron log, and permeability only from well tests.
