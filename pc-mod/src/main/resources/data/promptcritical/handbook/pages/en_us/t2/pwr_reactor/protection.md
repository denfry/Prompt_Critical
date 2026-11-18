## Protection

The scram trips by itself, three sensors per function, two out of three:

- power above 107 %, or above 25 % until the start-up trip is lifted. The "Permit" button lifts it, and only above 10 % power;
- period shorter than 10 s;
- pressure above 16.8 MPa or below 13.7 MPa;
- flow in either loop below 90 % at power, or pump speed below 93 %;
- hot leg above 330 °C;
- calculated departure-from-nucleate-boiling ratio below 1.45;
- steam generator drum level below 25 %;
- the SCRAM button (Shift + right-click on the reactor) or an ESD signal on the "SCRAM" terminal.

At full power DNBR is about 2.8. The calculator reads its sensors on the unsafe side, so it is more conservative than the true margin.

Rods fall in 2.3 s. The trip resets 10 s after the last cause clears; the scram bank then drives itself back up.

Below 12.5 MPa **safety injection** starts: 16 g/kg solution at up to 15 kg/s, against up to 14 MPa. A primary leak runs its course: pressure falls, scram, injection. Cladding that sits past departure from nucleate boiling for more than 5 s fails, and its iodine reaches the hall through the leak or the relief valve.
