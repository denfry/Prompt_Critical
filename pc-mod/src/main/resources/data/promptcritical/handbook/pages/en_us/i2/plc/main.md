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
- `ADD`, `SUB`, `MUL`, `DIV`, `MOD`, and the comparisons `GT GE EQ NE LE LT`.
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

## Structured Text

Instruction List reads like the relay circuit it replaced. For arithmetic, loops and branching there is Structured Text, the Pascal-like second language of IEC 61131-3. Start the program with `PROGRAM name` or put `(*ST*)` on the first line and the programmer switches to ST; the line under the editor shows which language it sees. Both languages compile into the same instructions, so the scan, the watchdog, the limits and the addresses are the same.

```
PROGRAM Pump
VAR
    Start AT %IX0.0 : BOOL;
    Stop  AT %IX0.1 : BOOL;   (* normally closed *)
    Motor AT %QX0.0 : BOOL;
    Delay : TON;
END_VAR
Motor := (Start OR Motor) AND Stop;
Delay(IN := Motor, PT := T#5s);
%QX0.1 := Delay.Q;
END_PROGRAM
```

- `x := expression;` assigns. Precedence, highest first: brackets; `NOT` and unary minus; `*`, `/`, `MOD`; `+`, `-`; `<`, `>`, `<=`, `>=`; `=`, `<>`; `AND` (or `&`); `XOR`; `OR`. So `a OR b AND c` is `a OR (b AND c)`.
- `IF … THEN … ELSIF … ELSE … END_IF;`
- `CASE n OF 1: …; 2, 3: …; 4..6: …; ELSE … END_CASE;` with whole-number labels.
- `WHILE … DO … END_WHILE;`, `REPEAT … UNTIL … END_REPEAT;`, `FOR %MW0 := 1 TO 10 BY 2 DO … END_FOR;` (the counter is a memory word, the step a constant), `EXIT;` and `RETURN;`.
- `VAR … END_VAR` names addresses (`Name AT %IX0.0 : BOOL;`) and declares block instances (`T1 : TON;`). A declared instance takes a free number from 15 down, so it does not collide with `TON0` written directly.
- A block call takes named inputs, and `=>` copies an output: `T1(IN := %IX0.2, PT := T#5S, Q => %QX0.1);`. Inputs must be a variable or a constant; compute an expression into a `%MX` or `%MW` first.

Nothing stops a loop that never ends except the watchdog: `WHILE TRUE DO` puts the PLC into FAULT on the first scan. Brackets in an expression use the same eight levels as in IL. Errors show the line and what the compiler expected.

## Memory

When the 24 V fails, `%MX0…31`, `%MW0…15`, the timers, edges and controllers are cleared. A battery keeps the retentive memory: `%MX32…63`, `%MW16…31` and the counters. A seal-in through `%QX` does not come back after a power failure, just as with a relay. That is protection, not a bug: a machine must not start by itself when power returns.

Text in the window reaches the PLC only with the Load button. A program with errors will not run: the PLC stays in STOP, and the errors are listed under the editor with their line numbers.

## LD, FBD and SFC

The PLC understands all five IEC 61131-3 languages. The language is detected by the program's first line. All five compile to the same instructions, so the same scheme behaves identically in any language.

**LD (ladder)** — one line per rung, first line `(*LD*)`. `XIC(a)` is a normally open contact, `XIO(a)` a normally closed one, `BST … NXB … BND` parallel branches, `TON/TOF/TP(name, time)` and `CTU(name, preset)` boxes in the rung, and at the end of the rung the coils `OTE` (output), `OTL`/`OTU` (latch/unlatch) and `RES` (counter reset):

```
(*LD*)
BST XIC(%IX0.0) NXB XIC(%QX0.0) BND XIC(%IX0.1) OTE(%QX0.0)
XIC(%QX0.0) TON(T1, T#5S) OTE(%QX0.1)
```

**FBD (function blocks)** — a netlist, one line per block output, first line `(*FBD*)`. Functions `AND OR XOR NOT MOVE ADD SUB MUL DIV GT GE LT LE EQ NE`, blocks `TON TOF TP CTU CTD R_TRIG F_TRIG`:

```
(*FBD*)
%QX0.0 := AND(OR(%IX0.0, %QX0.0), %IX0.1)
T1 := TON(%QX0.0, T#5S)
%QX0.1 := T1.Q
```

**SFC (sequential function chart)** — the standard's textual form, first line `(*SFC*)`. It uses `INITIAL_STEP` and `STEP` with actions (`name(N)` while the step is active, `S` set, `R` reset, `P` once on entry), `TRANSITION FROM a TO b := condition; END_TRANSITION` and `ACTION name: … END_ACTION` with an ST body.

LD, FBD and SFC keep their intermediate bits in `%MX48…%MX63`, and FBD also the words `%MW24…%MW31`. Do not use them yourself.


## Ladder editor

The **LD editor** button in the programmer opens the ladder diagram as a grid. Each rung has eight cells for contacts and blocks, a coil at the right, a main line and two parallel branches under it. A branch runs parallel to the main line over the same columns: a seal-in contact under the start button is a branch one cell long. Pick an element on the palette and click a cell: a normally open or closed contact, the TON, TOF and TP timers, the CTU counter, or a coil: output, set, reset or counter reset. Below the grid you type the selected cell's address or block name and the timer's or counter's preset. An empty cell on the main line above a branch is a plain wire. The grid becomes the LD text that the PLC compiles: **Load into PLC** sends it, and **Text** returns to the text programmer with it. LD text with nested branches or several coils in one rung does not fit the grid and stays in the text editor. While the PLC is in RUN, contacts with power through them are outlined in dark blue, as on an online ladder, by the state of the PLC's inputs and outputs.
