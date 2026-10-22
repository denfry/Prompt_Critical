An installation that has just been built is not yet accepted. Erection leaves defects: a seam that weeps under pressure, a valve set wrong, a blocked gauge glass, a brake with the wrong clearance. While the unit is new, the defects are hidden. **Commissioning** is a set of checks, and each one is certain to find its own defect. No defect is undetectable; some are just unchecked.

## The maintenance panel

A [maintenance panel](item:promptcritical:maintenance_panel) hangs on any block of an installation: a boiler, a steam engine, a main mine fan, a drainage pump, a mine hoist. Right-click opens commissioning, faults and the passport. Tools and parts used on the panel act on its installation.

Installation states:

- **New**: defects were rolled at assembly and are hidden. "NOT ACCEPTED" lights on the panel.
- **Testing**: at least one check passed; defects found so far are shown.
- **Accepted**: every check on the list and the trial run passed. A passport is issued.
- **Modified**: a component was replaced; only that component's checks are reset.

You may run an unaccepted installation, but at your own risk: the start goes into the log. A server may run in strict mode, and then neither the furnace lights nor the drive starts until the checks are done.

## The checks

- **Hydrotest.** Put a [hand test pump](item:promptcritical:hydrotest_pump) right against a boiler or a steam engine. The boiler is cold, unfired, and filled with water to working level. Each stroke of the lever (right-click) raises pressure by 0.1 MPa. Test pressure is 1.25 times working pressure, but no less than working plus 0.3 MPa: 1.5 MPa for a 1.2 MPa boiler. Hold the pressure and the check completes by itself. A weeping seam or a porous casting shows as drips: the check fails. On cold water this is safe.
- **Instrument check.** Right-click the boiler with a [test pressure gauge](item:promptcritical:test_pressure_gauge): the boiler gauge is compared with the reference, and a blocked gauge glass shows up at the same time.
- **Safety valve set.** Raise pressure until the safety valve lifts, then right-click the boiler with a [torque wrench](item:promptcritical:torque_wrench) to read the lift and reseat pressures and adjust the setting. You need the test gauge in your inventory or a passed instrument check.
- **Bump test.** The "Bump" button on a fan or pump panel gives a short start: rotation forward or reverse. Fix reverse by reversing the Create drive.
- **Priming.** Fill the drainage pump with a bucket while it stands still. A dry pump does not pump and wears out.
- **Brake test.** A button on the hoist panel, with a loaded cage; the clearance is set with the wrench.
- **Trial run.** The last check: 10 minutes at no less than 70 % load with no protection trips and one load rejection. The trial run figures go into the passport.

## Fixing defects

- A weeping boiler seam is caulked with a [caulking hammer](item:promptcritical:caulking_hammer) on an empty, cold boiler.
- A seized or mis-set valve is adjusted with the wrench, and a bad one is replaced with a new [safety valve](item:promptcritical:safety_valve), only with no pressure in the boiler. After replacement the valve check is due again.
- An offset gauge and a blocked glass are put right during the check itself.
- A porous casting is replaced.

## The passport

The passport is created at acceptance: test results, the actual valve setting, gauge corrections and the **baseline** from the trial run: efficiency, flue gas temperature, speed, pump head. From then on the panel compares the present with the passport. Flue gas 60 °C hotter than at acceptance means scale in the boiler. Pump head below the passport at the same flow means a worn impeller. An unaccepted installation has no passport, and the panel cannot make these comparisons.

Machine tools, jigs and foundry furnaces need no commissioning: they hold neither stored pressure nor large rotating masses.
