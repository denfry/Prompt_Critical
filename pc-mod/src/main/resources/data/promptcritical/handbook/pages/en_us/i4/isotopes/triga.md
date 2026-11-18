## TRIGA pulse reactor

A TRIGA is a pool research reactor whose fuel is uranium mixed with zirconium hydride. When the fuel heats, the hydrogen in the hydride passes energy back to the neutrons and the spectrum hardens. Reactivity falls by about 0.01 % per kelvin, and it falls **at once**, with no wait for heat to reach the moderator. That prompt negative coefficient lets you put the reactor on prompt neutrons on purpose. You get a flash of hundreds of megawatts lasting tens of milliseconds, and the reactor stops it by itself. In a power reactor the same insertion would be the accident of 1986.

The [transient rod drive](item:promptcritical:transient_rod_drive) stands on a bridge over the pool. The core of [TRIGA fuel elements](item:promptcritical:triga_fuel_element) hangs under it. It works only under water: the bridge needs at least **five blocks of water** straight below it. The water cools the core and shields you.

## Steady mode

The reactor starts scrammed, all rods in. Reset, then withdraw the control rods. The core goes critical with them about three quarters out. Protection trips on a period shorter than 3 s and on power above 1.1 MW, the licensed megawatt plus a margin. At 1 MW the fuel runs near 450 °C.

## A pulse

1. Bring the reactor just critical at watts. Pulse mode is refused above 1 kW.
2. Turn the **mode key** to pulse. This bypasses the period and power trips on purpose, and the bypass shows on the desk.
3. Set the pulse reactivity in cents. The rod is worth $3.00 and nothing below $1.00 gives a pulse at all.
4. **Arm** the air cylinder, then **FIRE**. The rod leaves the core in a tenth of a second.

The power climbs on prompt neutrons with a period of a few milliseconds until the fuel heat cancels the excess. The desk then shows the peak, the energy, the width at half height and the hottest fuel. The Fuchs–Nordheim model gives the order:

P_max = (ρ₀ − β)² C / (2|α|Λ),  E = 2(ρ₀ − β) C / |α|,  FWHM = 3.53 Λ / (ρ₀ − β)

With β = 0.007, Λ = 40 µs, C = 70 kJ/K and |α| = 10⁻⁴ per K, a $2 pulse comes to about 430 MW, 10 MJ and 20 ms, and the fuel warms by 140 K. A full $3 pulse goes past a gigawatt. Fifteen seconds after the rod fires, the pulse timer drops all rods.

The fuel limit is **1000 °C**. Above it the hydrogen pressure splits the stainless cladding, the elements are lost and the reactor stays down. A pulse of 100 MW or more with the fuel intact earns *Pulse*.
