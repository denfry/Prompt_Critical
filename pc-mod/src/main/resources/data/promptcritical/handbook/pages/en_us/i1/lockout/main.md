Equipment switched off for repair must stay off while people work on it. A sign on the switch does not ensure that: someone misses it, the automatics switch it on, a program on a computer sends a command. So at a plant every point where energy is cut off is held by a personal padlock, and only the person who hung it can take it off. The procedure is called LOTO (lockout/tagout); the US standard OSHA 29 CFR 1910.147 describes it.

## The procedure

1. Find every energy source of the equipment: electricity, rotation, pressure.
2. Stop the equipment the normal way.
3. Isolate it: open the breaker or the disconnector.
4. Hang a **personal padlock** on every isolation point (right-click with the [padlock](item:promptcritical:lockout_padlock)). Hold a [tag](item:promptcritical:lockout_tag) in the other hand and it goes on with the lock: "Do not operate! People at work".
5. Release stored energy: let a flywheel run down, vent the pressure.
6. **Verify the absence of energy**: right-click the locked switch with a [voltage tester](item:promptcritical:voltage_tester). With no voltage on the load side the point is marked verified.
7. Do the work.
8. When finished, clear the people and tools and take your lock off: Shift and right-click with an empty hand. The tag comes back with it.

A lock does not go on a closed switch: open it first.

## What the lock does

While at least one lock hangs on a point, the switch does not move on anyone's command. It cannot be closed by hand, from a panel, by a relay, a PLC, Lua through an RTU, auto-reclosing or protection. Every attempt answers "LOCKED OUT" with the names of the lock owners. The breaker drive loses its control power.

A locked switch cannot be broken. On a server in `realistic` mode it can, but that raises a "TAMPERING WITH ISOLATION" alarm and a journal entry. A broken point takes its locks with it.

## The crew

A point without a hasp takes one lock. A [lockout hasp](item:promptcritical:lockout_hasp) takes up to six: every crew member hangs their own. The point can go back into service only when the last lock is off. An empty hasp comes off the same way: Shift and right-click with an empty hand.

One person has at most eight locks.

## Someone else's lock

No player can take off someone else's lock, not even the station owner. If the owner has left and the lock is in the way, an administrator cuts it with `/pc lockout cut <x y z> <reason>`. The cut goes into the journal as critical and the server gets an announcement. `/pc lockout list` lists all locked points.

## Other isolation points

- A [mechanical disconnect](item:promptcritical:mechanical_disconnect) goes on a Create shaft. Right-click disengages it: the shaft before it keeps turning, the shaft after it stops. A disengaged coupling takes a lock.
- A [line blind](item:promptcritical:line_blind) goes between a pipe and a machine. Open, it passes the fluid; turned to its blind side, it cuts the line positively. This is how hazardous media are isolated.
- A [lockout box](item:promptcritical:lockout_box) is for complex isolations. The person in charge locks every point with their own locks and puts the keys in the box (right-click the box with an empty hand). Every crew member hangs their lock on the box. While at least one lock hangs on the box, the person in charge cannot take the locks off the points. The keys come out of the box once the crew has taken their locks off.
