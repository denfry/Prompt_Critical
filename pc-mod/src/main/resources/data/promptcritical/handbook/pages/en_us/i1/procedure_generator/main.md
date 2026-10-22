## Purpose

This procedure sets out how to start a shaft generator, put it on line, run it and shut it down. Generator: 500 kVA, 400 kW, stator 0.4 or 6.3 kV, input 16–256 RPM. Breaker: a 6 kV switchgear cell (oil breaker, closing time 0.45 s) or a 0.4 kV switchboard.

## Before start-up

- The generator is stopped. The exciter stands right against it. A shear-pin coupling is fitted.
- The stator voltage class matches the bus. The class is changed only with the machine stopped.
- The steam engine or other drive is connected by a Create shaft to the back of the generator. The drive has more power in hand than the load the generator will take.
- The gear on the generator panel equals the shaft speed.
- The generator cable runs to the back face of the cell. The breaker is open.
- A synchroscope hangs on the generator cell.
- If the cell's "+" and "−" terminals are wired, the 24 V supply is live.
- In the relay cabinet by the cell: overcurrent, undervoltage, underfrequency; reverse power and synchronism check are recommended.

## Start-up

- Run the drive up. The panel shows "rolling".
- Switch the field on. The panel shows "excited", and voltage rises to the setpoint.
- Check generator voltage and frequency on the synchroscope.

## Closing onto a dead bus

- The bus has no voltage: no synchronizing is needed.
- Close the breaker. The generator takes up the grid load.
- Pick up load gradually and hold frequency at 50 Hz with the trim.

## Paralleling

- Match the voltage: generator 0–5 % above the grid (V −/+ on the synchroscope).
- Match the frequency with the trim: the pointer creeps **clockwise**, one turn in 15 seconds or slower.
- Wait for the pointer to approach twelve o'clock. Press "ON" ahead by an angle of 360°·Δf·0.45 s, which is 8° at 0.05 Hz slip.
- A jolt is normal. A knock, vibration or a damage message means the closure was rough.
- Once on line, raise the generator's load with the trim.

## Running

- Hold grid frequency at 49.8–50.2 Hz with the generator trims.
- Keep voltage near rated; generators share reactive power through their V setpoints.
- Do not run a generator at its limit: keep reserve for the loss of a neighbouring machine.
- If steam is lost the generator motors, and reverse power trips it.

## Shutdown

- Bring the load down to zero with the trim.
- Open the breaker.
- Switch the field off.
- Stop the drive.

## Emergencies

- **Pin sheared.** The generator is uncoupled from the shaft. Open the breaker, stop the machine, fit a new coupling, synchronize again.
- **Generator damaged.** Shaft or stator used up its life after rough closures. The machine is replaced.
- **Out of step.** The machine has pulled out of synchronism: open the breaker, check field and drive.
- **Create network overstressed.** The blow of a bad closure reached a shaft with no shear pin. Shed load, restore the drive, fit a shear-pin coupling.
- **Frequency below 49 Hz.** Not enough generation. Raise drive power or shed load; UFLS will drop its stages by itself.
- **Breaker will not close.** "Interlock": the synchronism check relay refuses outside the window. "No control power": check the 24 V supply.
