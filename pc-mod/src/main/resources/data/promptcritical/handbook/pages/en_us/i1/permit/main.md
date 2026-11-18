A padlock keeps energy away from the equipment. A **work permit** says who may work on it, on what conditions and until when. At a refinery or a power plant no repair starts without one: the permit is where isolation, the gas test, the fire watch and the crew's signatures meet.

## Writing a permit

Right-click the equipment with a blank [work permit](item:promptcritical:work_permit). Switchgear keeps its own electrical permit; anything else gets a permit of the type its hazards call for:

- **hot work** — a flammable medium (oil products, fuel gases, hydrogen, methane in a mine) within 11 m. 11 m is the 35 feet of OSHA 29 CFR 1910.252. The permit is valid for 4 hours;
- **radiation work** — a main coolant pump of a reactor, or a dose rate above 12 µSv/h at the equipment (a room you may enter only on a permit);
- **cold work** — everything else.

The blank becomes the permit. The original is kept in the plant register; the paper refers to it. Right-click the air with it to read its state.

## From isolation to work

Click the equipment with the permit to move it on, step by step:

1. **Isolation.** Every point of the isolation plan is locked and verified with the voltage tester (see Lockout and tagout).
2. **Gas test** (hot work). Stand in the work area and hold use on a [portable gas detector](item:promptcritical:portable_gas_detector) for 30 s, the T90 of the sensors. It reads oxygen, flammables in % of the lower explosive limit (LEL), H₂S and CO, and writes the result into every permit around you. Hot work needs 19.5–23.5 % O₂, 0 % LEL, less than 10 ppm H₂S and 25 ppm CO. Before that, Shift-use the detector with [calibration gas](item:promptcritical:calibration_gas) in the other hand: an instrument not bump-tested in the last 24 hours is not trusted.
3. **Admission.** Hot work needs a fire watch: someone with a fire extinguisher within 11 m. The permit becomes ACTIVE. Every crew member clicks the equipment with the permit to sign it.
4. **Work complete.** Shift-click. Radiation work counts the crew's time at the equipment: the job takes a minute of work with the machine stopped.
5. **Closing.** Take the locks off, then Shift-click again. The equipment is handed back.

## Hot work

Striking a flint and steel, lighting a torch or a campfire, or setting a Create pipe against a pipe or tank with a flammable medium (the game's welding) is hot work. Without an active hot work permit the server rule decides: by default the action is allowed but warned and written to the journal; on a server with enforced permits it is refused.

The paper does not stop chemistry. The atmosphere is checked at 27 points around the spot, and if any of them is at 100 % LEL the cloud flashes. That is why the permit keeps watching after admission: once a second the gas at the work site is measured, and at 10 % LEL or bad oxygen the permit is SUSPENDED at once. A new gas test resumes it. Without a new test every 2 hours it is suspended too.

## ALARA

Radiation work counts the dose of every crew member inside the work area. The collective dose of the crew is the sum, in person-sieverts. Repair a main coolant pump under a permit with a collective dose below 10 mSv and you have worked as low as reasonably achievable: wait for short-lived activity to decay, shield, work fast, keep only the needed people near the pump.

## Turnaround

The [permit board](item:promptcritical:permit_board) shows the permits of the plant around it (16 m by default; − and + on the board set the plant radius from 4 to 32 m) and leads its turnaround, the full shutdown repair, through phases:

1. **Shutdown** — no machine around the board is running.
2. **Freeing** — drain, steam out, purge; then a gas test at the board shows a breathable, non-flammable atmosphere.
3. **Open** — repairs under permits. At least one job must be completed under a permit.
4. **Box-up** — every permit of the plant is closed.
5. **Pre-startup safety review** — no open permits, no locks, no SIS function in bypass, no overdue SIS proof test (OSHA 1910.119(i)).
6. **Startup** — a machine runs again. The turnaround is complete.
