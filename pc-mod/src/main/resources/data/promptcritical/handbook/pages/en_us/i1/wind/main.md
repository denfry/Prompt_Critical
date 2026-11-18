In 1931 the TsAGI D-30 went up near Balaklava: a 30 m rotor with three pitching blades on a 30 m tower, driving a 100 kW generator. It ran in parallel with the Sevastopol grid, and until 1940 no wind turbine in the world was bigger. This unit is built the same way.

## Building it

1. Put the [wind power unit](item:promptcritical:wind_turbine) somewhere open. The tower is nine blocks tall, and the rotor on top turns into the wind by itself.
2. Run a 0.4 kV cable to either of the two bottom tower blocks, from any side except the control cabinet.
3. A synchronous machine must be running on the grid: a shaft generator, a turbine or a hydro set. An induction generator makes no voltage of its own, so without a grid the turbine will not start.
4. Right-click to open the panel and press Start. Sneak and right-click switches the automatics on and off.

## How much the wind gives

The wind carries ½·ρ·A·V³ through the rotor: at 8 m/s that is about 220 kW through a 30 m circle. You cannot take all of it, because the air has to leave the rotor somehow; Betz's limit is 16/27, or 59 %. The D-30 rotor takes about 46 % at a tip speed ratio of λ ≈ 6, with the blade tips moving six times faster than the wind. Blade element theory works that curve out from the airfoil and the twist.

Wind depends on where you are. The ground slows it down over grass and forest, so at hub height it blows harder than at 10 m, and hardest over water and bare steppe. Over forest the wind at hub height is about a tenth weaker, and since power goes with the cube of the speed, a fifth of the output is gone. Gusts and lulls come every half a minute or so, while a strong wind lasts for days as a cyclone passes.

## How it runs

The generator is an induction machine. The rotor drives it through the gearbox a little faster than its synchronous 600 rpm, the slip goes negative, and the machine pushes power into the grid. It also draws reactive power from the grid to magnetise itself, so a capacitor bank nearby takes that load off the station.

The automatics work like this:

- Once the wind is above 4 m/s, the brake releases, the blades go to the start angle, the rotor runs up, and the contactor closes near synchronous speed.
- Above rated wind (about 8–9 m/s) the blades turn towards feather and hold 100 kW.
- If the generator motors for 20 s, the turbine stops.
- If the 10-minute mean goes above 20 m/s, or a gust reaches 25 m/s, the turbine makes a storm stop. It restarts only after the mean falls below 16 m/s.

With no load the rotor overspeeds within seconds. Overspeed above 115 % or a tower overload trips the turbine: the blades feather and the brake goes on. The trip resets only with the rotor stopped, with the Reset button or with sneak and right-click.
