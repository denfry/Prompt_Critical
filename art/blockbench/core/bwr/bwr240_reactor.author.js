// Boiling water reactor of the Big Rock Point class (240 MWt) at real scale (1 block = 1 m): an insulated vessel
// 2.95 m across and 9.14 m tall on a concrete pedestal of eight columns, the bottom-entry control rod drive
// housings hanging under the bottom head between them, the bolted head with its vent and lifting lugs, a service
// platform at the head flange reached by a caged ladder on the front. Two recirculation nozzles (east and west),
// feedwater (east) and the steam riser to the drum (south). Dimensions and sources: docs/research/bwr-dimensions.md
// (disk only). Load after vverkit.js, then BWR240.build(repo); /* wait */ BWR240.save(repo).
//
// Controller block = (0, 0, 0) on the floor under the vessel axis (x = z = 8). Front north.
// Connections (ends on block faces, axes on block centres):
//   recirculation loop 1: east face of cell (2, 6, 0)     recirculation loop 2: west face of cell (-2, 6, 0)
//   feedwater: east face of cell (2, 9, 0)                 steam to the drum: south face of cell (0, 10, 2)
(function () {
  const C = [8, 8], R = 25;
  const Y0 = 74, Y1 = 184;                 // cylindrical shell between the bottom head and the main flange

  // control rod drive housings under the bottom head: a 6-unit lattice inside r 16 (21 of the 32 drives shown)
  function drives() {
    const out = [];
    for (let i = -3; i <= 3; i++) for (let k = -3; k <= 3; k++) {
      const x = C[0] + i * 5.5, z = C[1] + k * 5.5;
      if (Math.hypot(x - C[0], z - C[1]) < 17) out.push([x, z]);
    }
    return out;
  }

  function model() {
    const m = PCVV.mk();
    // pedestal: eight concrete columns on a base slab, a ring beam carrying the vessel skirt
    m.cylY('slab', C, 30, 0, 2, 'conc', { bottom: true });
    for (let k = 0; k < 8; k++) m.postY(`col_${k}`, C, 26, k * 45 + 22.5, 4, 2, 52, 'conc', { split: 'y' });
    m.ringY('beam', C, 29, 6, 52, 60, 'conc');
    m.cylY('vskirt', C, 22, 56, 68, 'pnt');

    // drive housings with their flanges and the hydraulic insert/withdraw lines to a header ring
    drives().forEach(([x, z], i) => {
      m.box(`crd_${i}`, [x - 0.8, 10, z - 0.8], [x + 0.8, 62, z + 0.8], 'drv', { split: 'y' });
      m.box(`crd_fl_${i}`, [x - 1.3, 8, z - 1.3], [x + 1.3, 10, z + 1.3], 'forged', { split: '' });
      m.box(`crd_line_${i}`, [x + 0.8, 14, z - 0.25], [x + 1.6, 15, z + 0.25], 'st', { split: '' });
    });
    m.ringY('hcu_header', C, 21, 1, 14, 15.2, 'gw');
    m.ringY('crd_support', C, 19, 1.2, 30, 31.5, 'st');

    // vessel: bottom head, shell, bolted flange, top head with its vent and lifting lugs
    m.headDown('bot', C, R, Y0, 14, 4, 'clad');
    m.cylY('shell', C, R, Y0, Y1, 'clad');
    m.ringY('band_lo', C, R + 0.4, 0.8, 100, 101.2, 'clad');
    m.ringY('band_hi', C, R + 0.4, 0.8, 150, 151.2, 'clad');
    m.cylY('flange', C, 29, Y1, Y1 + 8, 'forged', { cap: 'vver/flange', bottom: true });
    m.nutsY('stud', C, 27.5, 24, Y1 + 8, 2, 1.6, 'bright');
    m.headUp('top', C, R, Y1 + 8, 14, 4, 'clad');
    m.cylY('vent', C, 2.4, 205, 212, 'forged');
    m.cylY('vent_fl', C, 3.6, 212, 214, 'forged', { cap: 'vver/flange', bottom: true });
    m.nutsY('vent_nut', C, 3, 8, 214, 0.8, 0.8, 'bright');
    for (const a of [45, 135, 225, 315]) m.radial(`lug_${a}`, C, 19, 27, a, 2, 194, 202, 'forged');

    // nozzles: recirculation (water, green), feedwater (green), steam riser to the drum (red)
    m.nozzle('rc_e', 'x', [104, 8], 30, 48, 6.2, 4.6, 'gw', { nuts: 12 });
    m.nozzle('rc_w', 'x', [104, 8], -14, -32, 6.2, 4.6, 'gw', { nuts: 12 });
    m.nozzle('fw', 'x', [152, 8], 30, 48, 4.2, 3, 'gw', { nuts: 8 });
    m.nozzle('steam', 'z', [8, 168], 30, 48, 6.8, 5, 'gs', { nuts: 12 });
    // relief valve on the steam riser, discharge line down to the suppression pool (out of the model)
    m.box('rv_body', [4, 175, 38], [12, 182, 44], 'pnt', { split: '' });
    m.wheelY('rv_wheel', 8, 183.4, 41, 2.6, 'red');
    m.box('rv_line', [12, 140, 39.5], [15, 178, 42.5], 'st', { split: 'y' });

    // service platform at the head flange with its rail and brackets
    m.cylY('deck', C, 40, 182, 184, 'pnt', { cap: 'vver/grating_disc', bottom: true });
    m.railY('rail', C, 39.7, 184, 'yel');
    for (let k = 0; k < 8; k++) m.radial(`deck_brk_${k}`, C, R, 39, k * 45 + 22.5, 1.6, 172, 182, 'pnt');

    // caged ladder up the front from the floor to the platform
    m.ladderZ('ladder', 4, 12, -30, -1, 2, 184, 'yel', 3);

    // level and pressure instrument lines down the west side, taps into the shell
    for (const z of [5, 11]) {
      m.box(`lvl_${z}`, [-22.2, 70, z - 0.6], [-21, 176, z + 0.6], 'st', { split: 'y' });
      for (const y of [80, 170]) m.box(`lvl_tap_${z}_${y}`, [-22.2, y - 0.6, z - 0.6], [-17, y + 0.6, z + 0.6], 'st', { split: '' });
      m.box(`lvl_valve_${z}`, [-23, 120, z - 1.4], [-20.4, 123, z + 1.4], 'gw', { split: '' });
    }
    // plates on the back, the pedestal sign on the front column
    m.decal('sign', [3, 112, 33], [13, 122, 33.7], 'clad', 'south', 'pl.sign');
    m.decal('name', [3, 124, 33], [13, 126.6, 33.7], 'clad', 'south', 'pl.bwr_name');
    m.decal('sign_ped', [5, 34, -21.2], [11, 40, -20.5], 'conc', 'north', 'pl.sign_s');
    return m;
  }

  window.BWR240 = {
    model, drives,
    build: (root) => { const m = PCVV.cut(model()); PCART.realize(root, m, 'bwr240_reactor'); return { elements: m.els.length, bounds: PCVV.bounds(m) }; },
    save: (root) => PCVV.save(root, 'bwr240_reactor', 'core/bwr'),
  };
})();
