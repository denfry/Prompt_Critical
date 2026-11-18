## Emergency core cooling

Nothing here works unless you build it within 12 blocks of the reactor controller.

**ECCS pump.** One block is one train: a high-head and a low-head stage on one shaft, fed from the borated water tank. Drive the shaft from the back with an electric motor on the grid, or with an engine for the emergency diesel. Each stage follows its pump curve, q = q_max·√(1 − p/p_shutoff):

- VVER: 15 kg/s against up to 14 MPa, plus 20 kg/s below 1.4 MPa;
- BWR: high-pressure core spray of 12 kg/s against up to 8.5 MPa, plus LPCI of 40 kg/s below 2 MPa.

One train holds a 1 cm² break with room to spare. A guillotine break needs two, plus the accumulators.

**Hydroaccumulator** (VVER only). A tank of borated water under nitrogen at 5.9 MPa behind a check valve. It needs no power and no signal. When the primary falls below its pressure it empties itself into the vessel, and in a large break that happens within the first half minute of blowdown. While the vessel is still above 1.5 MPa, the blowdown sweeps 70 % of what goes in straight back out of the break (ECC bypass). That is why the low-head pumps have to be there when it ends.

**ADS** (BWR). A small break leaves the boiling water reactor at 7 MPa, above the LPCI shutoff. If the level falls to a third of the core height above the fuel and stays there for 120 s while a low-pressure pump is running, the automatic depressurisation system opens the relief valves into the pool. The vessel blows down in a few minutes and LPCI refloods it.
