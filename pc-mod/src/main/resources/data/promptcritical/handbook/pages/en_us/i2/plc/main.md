A relay cabinet does one thing: the circuit wired into it. To change the logic you rewire it. The programmable controller arrived in 1969 for exactly this reason: car plants were tired of rewiring cabinets for every new model. A [PLC](item:promptcritical:plc) keeps its logic in memory; the wires go only to its inputs and outputs.

## Terminals

- **L+ / M** — 24 V supply from a [power supply](item:promptcritical:relay_power_supply). No supply, no PLC: the outputs drop out.
- **I0…I7** — discrete inputs, optocouplers to M. An input is on when its terminal sees +24 V from a button, a relay contact or a process switch.
- **1L, Q0…Q7** — relay outputs: the contact closes 1L to Qn. Usually 1L goes to +24 V and Qn to a lamp, a starter coil or the input of another cabinet.
- **AI0…AI3** — analogue inputs ±10 V, **AO0…AO1** — analogue outputs, the same signals as the [analogue blocks](item:promptcritical:analog_summer).

## The scan

A PLC works in scans. It reads all inputs into the process image, runs the program from top to bottom, then writes all outputs at once. Within a scan the program sees one snapshot of the inputs, and an output changes only at the end. One scan is one game tick, 50 ms.

A watchdog catches endless loops: more than 4096 instructions in a scan means FAULT, and the outputs drop out. To clear it, turn the key to STOP and back.

## The program

An empty hand on the PLC opens the programmer. The language is the IEC 61131-3 Instruction List that Simatic S5 and Lomikont controllers were programmed in. Each line is one instruction: an operator and an operand. The operator works on the current result.

- `LD x` loads, `ST y` stores, `S y` / `R y` set or reset when the result is true.
- `AND`, `OR`, `XOR`, `NOT`; an N suffix inverts the operand: `ANDN %IX0.1`.
- `ADD`, `SUB`, `MUL`, `DIV`, and the comparisons `GT GE EQ NE LE LT`.
- Brackets: `AND( %IX0.1` … `)` evaluates what is inside first.
- Jumps: `JMP label`, `JMPC` if true, `JMPCN` if false; `RETC` ends the scan.
- Comments: `(* … *)` and `//`.

Addresses: `%IX0.0…%IX0.7` inputs, `%QX0.0…%QX0.7` outputs, `%MX0…%MX63` marker bits, `%IW0…%IW3` and `%QW0…%QW1` analogue channels, `%MW0…%MW31` memory words. Time is written `T#5S`, `T#1M30S`, `T#200MS`.

## Blocks

Blocks are called with CAL, with the instance number in the name:

- `TON` on-delay, `TOF` off-delay, `TP` pulse. Inputs IN, PT; outputs Q, ET.
- `CTU`, `CTD` counters. Inputs CU/CD, R/LD, PV; outputs Q, CV.
- `R_TRIG`, `F_TRIG` rising and falling edges.
- `PID` controller. Inputs PV, SP, KP, TI, TD, LO, HI, MAN, XM; output Y. The derivative acts on PV, so a setpoint step does not kick the output. It does not wind up, and switching to manual and back is bumpless.

Example — start and stop with a seal-in, exactly what relay K1 does in the relay circuit:

```
LD  %IX0.0     (* START *)
OR  %QX0.0
AND %IX0.1     (* STOP, normally closed *)
ST  %QX0.0
```

A 5 s delay:

```
CAL TON0(IN := %IX0.2, PT := T#5S)
LD  TON0.Q
ST  %QX0.1
```

## Memory

When the 24 V fails, `%MX0…31`, `%MW0…15`, the timers, edges and controllers are cleared. A battery keeps the retentive memory: `%MX32…63`, `%MW16…31` and the counters. A seal-in through `%QX` does not come back after a power failure, just as with a relay. That is protection, not a bug: a machine must not start by itself when power returns.

Text in the window reaches the PLC only with the Load button. A program with errors will not run: the PLC stays in STOP, and the errors are listed under the editor with their line numbers.
