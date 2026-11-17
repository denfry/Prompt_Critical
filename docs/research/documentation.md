# Technical documentation: by hand first, on computers later

Goal: while players have no computers they write the technical documents for
their machines by hand; once computers exist, they work with documents and tables on
them, as it went in real plants. Evidence labels are explained in [README.md](README.md).

## What exists

The mod already produces a good part of a plant's paperwork, but writes it itself:

| Feature | Where | What it is |
|---|---|---|
| Engineering handbook | `pc-mod/.../content/EngineeringHandbookItem`, `HandbookManager`; Markdown pages in English and Russian | The reference book, written by the mod |
| Operating procedures | `OperatingProcedureItem`, handbook category `procedures` | Step lists for the boiler, hoist, blast furnace, generator |
| Document lectern | `DocumentLecternBlock` | A place to keep a document at the machine |
| Recording gauge | `ops/ChartDrum`, `RecordedChartItem`, `ChartScreen` | A pen on a clockwork drum; the finished chart is an item |
| Equipment passport | `ops/UnitPassport`, `PassportEntry` | Baseline figures of the trial run, issued at acceptance |
| Commissioning | `ops/CommissioningService` | Assembly defects, checks, acceptance |
| Logs | `ops/OpsLogEntry`, `IronmakingLog`, `SteelmakingLog`, `UtilitiesLog` | Event records kept by the simulation |
| Maintenance panel | `ops/MaintenancePanel` | Wear state, alarms |

What is missing is the player as author, and the change of medium over time.

## A rule to keep

Documents should matter because of the information in them, not because a filed paper
grants a bonus. The mod already works this way: there is no global readout, the true
baseline of a unit is only in its passport, the history of a variable is only on its
chart. If a procedure on a lectern made a machine 5 % more efficient, that would be a
magic number of exactly the kind the first design pillar rules out.

The payoff of the computer era follows the same rule. A computer does not make the
plant better; it makes the same information faster to collect, combine and find.

## The paper era

What plants kept on paper **[M]**, and what each could be in the game:

| Document | In real plants | In the game |
|---|---|---|
| Operating procedure | Typed or hand-written step lists, revised after incidents | A writable document; the mod's own procedures become the starting template, the player's revisions are theirs |
| Shift log | The operator's rounds: readings taken from gauges every hour, events, handover notes | A log form the player fills in at the instruments; on a server it is how one shift tells the next what happened |
| Equipment passport | Nameplate data, test results, repair history | Exists for the trial run; add the repair history and the player's notes |
| Process sheet | Route of a part through the shop: operations, machines, settings | Needed to repeat a part on another machine or by another player |
| Drawing | Made on a drawing board, copied as blueprints | A drawing of a machine or a structure; required to build a second one (see below) |
| Calculation sheet | Heat and mass balances done by hand, with slide rule, nomograms and printed tables | The mod could print its own steam tables and balance forms |
| Chart | Paper from a recorder, annotated and filed | Exists |
| Work permit, lock-out tag | Permission to work on isolated equipment | Fits the hardware-protection pillar; a tag on a breaker or valve |
| Maintenance card | One card per machine: inspections due, parts used | The paper form of the maintenance panel |
| Spare parts register | A card index | Stock records |

Supporting pieces:

- **Writing.** Vanilla already has a writable book. Plant documents are forms with
  fields plus free text, kept as item data.
- **Drawings.** A drawing board produces a drawing of something the player has built
  (the structure is surveyed, not drawn pixel by pixel). The drawing can show a ghost
  outline when placed, and a shop needs it to manufacture that machine again
  ([machine-building.md](machine-building.md)).
- **Copies.** The blueprint process is real chemistry: paper coated with iron salts,
  exposed through the tracing, washed in water **[M]**. It gives white lines on blue and
  a reason to keep an original. Diazo copying came later.
- **Calculation aids.** Slide rule, nomograms, printed property tables. The mod has an
  IF97 implementation; a printed steam table is that data in the medium of the era.
- **Filing.** Lecterns at machines, cabinets in an office, an archive.

## The computer era

It starts when the player has computers and monitors
([electronics.md](electronics.md)). In the mod these are CC: Tweaked blocks, an optional
dependency.

What replaced what **[M]**:

| Paper | Computer | Real milestone |
|---|---|---|
| Hand-written and typed text | Word processing, printing | — |
| Calculation sheets, slide rule | Spreadsheets | VisiCalc, 1979 |
| Drawing board, blueprints | CAD, plotters | Early 1980s |
| Paper charts | Historian with trends | With the first DCS, mid 1970s |
| Shift log book | Electronic log | — |
| Maintenance cards | Maintenance management system | 1980s |
| Card index of parts | Database | — |

What this means in the game:

- **Text.** CC: Tweaked computers have a filesystem, a text editor and a printer
  peripheral **[M]**. A printed page is a paper document again, so both media coexist,
  as they did.
- **Tables.** A spreadsheet program supplied with the mod, whose cells can be bound to
  live readings from the mod's peripherals. A heat balance the player once computed by
  hand from gauge readings now recalculates itself. This is the single most concrete
  gain, and it is the historical one.
- **History.** Trends on a monitor in place of chart drums; the data comes from the
  same instruments, with the same sensor error and lag.
- **Maintenance.** Work orders generated from maintenance alarms, with history per
  unit.
- **Drawings.** Edited on screen, plotted on paper.
- **Scanning.** Paper documents brought into the computer, so nothing written in the
  first era is lost.

Programs for CC: Tweaked can be shipped by the mod; the mechanism (ROM files through a
data pack, or disks) should be confirmed against the CC: Tweaked documentation.

## Open points

- **Without CC: Tweaked.** The README states that the plant then runs on analog desks,
  relays and PLCs. Either the computer era of documentation also depends on
  CC: Tweaked, or the mod gets a computer block of its own. Not decided.
- **How much is mandatory.** On a single-player world nobody reads a shift log. A
  sensible split: drawings and passports have mechanical consequences (building,
  acceptance); logs and procedures are tools the player may use.
- **Language of documents** on multilingual servers.
