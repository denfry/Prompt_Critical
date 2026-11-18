## The alarm server in Lua

With CC: Tweaked, a computer next to the annunciator sees it as the peripheral `pc_annunciator`. The alarm states live in the panel, not in the program: they survive a crashed or rebooted computer.

- `getAlarms({state="UNACK"})` lists alarms with their tag, priority, state, message, value and limit.
- `acknowledge(id)` or `acknowledge("ALL")`, and `silence()`.
- `defineAlarm({kind="HIGH", tag=..., limit=..., priority=1})` adds your own alarm on any machine tag. `getTags()` lists the tags. A `SOFT` alarm is raised and cleared by the program with `raise(id)` and `clear(id)`.
- `shelve(id, seconds)` hides a nuisance alarm for up to 8 hours. Priority 1 cannot be shelved. The panel always counts shelved alarms.
- `setSuppression(group, true)` hides a group while the plant is in a state where those alarms are a consequence, not a cause. Every machine's alarms share a group named after the machine.
- `getStatistics()` returns the alarm rate over 10 minutes, standing, chattering and shelved alarms and the flood flag.

Events: `pc_alarm` when an alarm comes in, `pc_alarm_clear`, `pc_alarm_ack` and `pc_alarm_flood`.
