## Watchdog relay

A Lua controller that crashes leaves every valve where it was. Nothing on the screen tells you: the last values stay, the loop just stopped. The [watchdog relay](item:promptcritical:watchdog_relay) is how you find out.

The computer calls `arm()` once and `kick()` after every good pass of its control loop, through the peripheral `pc_watchdog`. Put the kick after the controller step, never in a separate `parallel` task: otherwise the relay is fed while the controller hangs. A `pcui` screen does this for you with `pcui.app({watchdog = peripheral.find("pc_watchdog")})`: it arms the relay and kicks it after every frame that drew without an error. If no kick comes for the timeout (2 s by default, 0.5 to 60 s), the relay trips in that same tick:

- it powers the block it hangs on with redstone 15, so a redstone-driven machine can go to a safe state;
- its red TRIPPED lamp makes any annunciator within 16 blocks raise a priority 2 control system failure alarm.

The first computer that arms the relay owns it. Kicks from any other computer fail with `E_ACCESS: NOT_MASTER`. For hot standby, a second computer watches the first; when the relay has tripped it calls `takeover()`, `reset()` and `arm()` and runs the loop itself. Taking over only after the trip matters: a master that is late but alive and a standby that started too early would fight over the same valve.

The relay's panel has a local Reset button and the timeout setting.
