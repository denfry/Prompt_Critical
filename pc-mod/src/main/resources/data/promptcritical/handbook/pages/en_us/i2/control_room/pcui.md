## Operator screens: pcui

With CC: Tweaked, the mod adds the Lua library `pcui` to every computer: `require("pcui")`. It draws operator screens on monitors in the ISA-101 style: grey background, blue values, colour only for trouble. A value outside its normal range turns orange, a missing value magenta, alarm rows take their priority colour.

A screen is an app with pages. Each page has an ISA-101 level: 1 is the overview (is everything fine?), 2 a system, 3 one device, 4 diagnostics. Keys 1-9 and the tabs at the bottom switch pages, OVERVIEW always goes back to level 1.

Widgets: `Value`, `Bar` (a bar with the normal range band and a setpoint mark), `Trend`, `AlarmList` (touch a row to acknowledge), `Button` (with `confirm = true` the first touch selects and the second operates), `Label` and `Status`.

Data come from devices with `readMany`. The simplest one is the `pc_annunciator`: it reads every machine tag within 16 blocks, `ann.getTags()` lists them. Each frame the screen reads every device once with all the page's tags and rewrites only the rows that changed, so a quiet screen costs nothing.

`help pcui` on the computer shows a short example, and `/rom/programs/pc/alarm_screen` builds an overview from the annunciator next to the computer.

A level 1 page tells the annunciator how many different tags it shows. An overview with 20 tags or more earns the High-Performance HMI achievement.
