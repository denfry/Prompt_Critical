With many machines, walking from panel to panel takes too long. A dispatcher sits at one screen: readings from every machine arrive over a communication line and commands go back. That is telemetry, and the program on the screen is a SCADA system.

## The telemetry gateway

The [telemetry gateway](item:promptcritical:rtu_station) is a remote terminal unit (RTU). It sees the panels of every machine within 8 blocks and reads their instruments once a second. That is the scan cycle: asking more often gets you the same snapshot and its age.

Attach a CC: Tweaked computer to the gateway and it sees the **pc_rtu** peripheral:

- **machines()** — machines on the gateway: **id**, type, coordinates;
- **describe(id)** — the panel's instruments (name, kind, label, units) and buttons;
- **read(id)** — instrument values by name, plus **_age**, the snapshot age in seconds;
- **press(id, button)** — press a panel button.

Analog values are dials and levels, status values are lamps (true or false), and a dynamometer card comes as a table of points. Text readouts are not sent: an RTU transmits values, not text.

## Rights and protection

The dispatcher can do what a player at the panel can do, and no more: read instruments and press buttons. You can't shovel coal or change a machine setting from the screen. Commands go one at a time, no more than two a second, as in real telecontrol. A program that sends requests without pause gets a "busy" error back.

The gateway keeps no trend archive: that is the dispatcher program's job. It writes values to a file on the computer's disk, like a real SCADA historian. There is an example on the next page.
