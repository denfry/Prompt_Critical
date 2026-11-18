A controller keeps the process normal. When it fails to, a second, independent line is needed: the safety instrumented system (SIS, IEC 61511). It has one job, to stop the process before an accident, and so it is built differently from everything else.

## Failing safe

The [SIS logic solver](item:promptcritical:sis_logic_solver) keeps its output relay energised while the process is permitted: contact 11-14 closed means run. A trip, a lost 24 V or a burnt coil all drop the relay, and that is a shutdown. A protection that needs energy to act stays silent when the power fails. A protection that acts by removing power stops the process whatever fails.

Transmitters connect as 4–20 mA loops to AI1…AI3, for example from a [gas detector](item:promptcritical:gas_detector). Below 3.6 mA or above 20.8 mA means a broken wire, a short or a dead transmitter (NAMUR NE 43), and that channel votes for shutdown.

## Voting

- **1oo1** — one transmitter. If it fails, there is no protection, or a spurious trip.
- **1oo2** — either of two. Stops reliably, trips falsely more often.
- **2oo2** — both needed. Fewer spurious trips, but it protects worse than a single transmitter.
- **2oo3** — two of three. One failed or drifting transmitter neither stops the process nor disables the protection. A failed channel counts as a vote to stop, so the scheme works as 1oo2 of the remaining two.

## Latch, reset, bypass

A tripped function does not come back by itself. The Reset button on the panel, or +24 V on the RST input, clears it only when every channel is healthy and clear of the setpoint by 0.2 mA. Otherwise the process would be restarted into the same accident.

Bypass takes the function out of service while a transmitter is tested: the output stays up, the alarm contact 21-24 is lit, and the time in bypass is counted. Every bypass on and off is announced with the player's name. A forgotten bypass is a classic cause of major accidents.

## What protection costs

The panel works out the average probability of failure on demand, PFDavg, by the simplified equations of IEC 61508-6:

- 1oo1: λT/2;
- 1oo2: ((1−β)λT)²/3 + βλT/2;
- 2oo3: ((1−β)λT)² + βλT/2.

Here λ is the rate of dangerous undetected failures of one transmitter, T the proof test interval, and β the share of common cause failures (the same dirt, the same batch, the same cable tray). PFDavg sets the safety integrity level: SIL 1 below 0.1, SIL 2 below 0.01, SIL 3 below 0.001.

You can see what each number does. Test half as often and the PFD of 1oo1 doubles. 2oo3 redundancy gains an order of magnitude while β is small; at β = 10 % the common cause eats most of that gain.

## Proof test

PFDavg is an average. The real probability of failure grows from the moment of the last proof test: a transmitter can stick or drift unnoticed, and only a test finds it. The panel shows it as **PFD now**, a sawtooth that starts at zero after each signed test. The **Proof test** line counts hours of plant time since the test (one hour of play is 72 hours of plant time) and turns to OVERDUE once the interval set on the panel has passed. An overdue test also lights the alarm lamp.

The test follows the procedure of a real plant:

1. Put the function in **Bypass**, or a test signal would stop the process. Then press **Test**.
2. Press **Inject** for each transmitter channel in turn. A healthy channel answers with a vote for shutdown. A channel in loop fault (below 3.6 or above 20.8 mA) is a found failure: repair the loop and start the test again.
3. When every channel of the voting scheme has answered, remove the bypass.
4. Press **Sign**. The time since the test goes back to zero. A test signed in time earns the "Proof Tested" achievement.

## Cause and effect matrix

A real SIS is a table: rows are causes (high level, low pressure, gas detected, the ESD button), columns are effects (close a valve, stop a pump, sound the horn). Each logic solver is one cause with its own voting and setpoint. Logic solvers fed from the same +24 V wire form one cabinet, one matrix, and their output contacts in the trip circuits are the effects. The **Cabinet** line counts the powered causes and how many of them vote 2oo3 on three connected transmitters. Ten causes or more with 2oo3 among them make a real matrix.
