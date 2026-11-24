// VVER-70 (V-2, Rheinsberg) loop equipment at real scale (1 block = 1 m, 16 px = 1 m): the vertical steam generator,
// the sealless (canned-motor) main coolant pump and the gas volume compensator. Three of each SG and pump, one
// compensator. Sheets: docs/research/2026-11-24-reactor-prototypes/vver70_{steam_generator,coolant_pump,pressurizer}_real.md
// (disk only; most sizes there are estimates, the loop layout and DN 500 are sourced).
//
// Load in Blockbench after pcart.js, pcround.js, pcslice.js and core/vver/vverkit.js, then for k in
// 'steam_generator', 'coolant_pump', 'pressurizer':
//   V70L.build(repo, k); /* wait */ V70L.save(repo, k)
//   for (const p of V70L.parts(k)) { V70L.buildPartial(repo, k, p); V70L.savePartial(repo, k, p) }
//
// Every unit: controller (0, 0, 0) under the vertical axis (8, y, 8), front north, footprint x -1..1, z -1..1.
// Steam generator, y 0..8: primary in on the north face of (0, 0, -1) at (8, 14, -16), primary out on the south face
//   of (0, 0, 1) at (8, 14, 32), feedwater in on the east face of (1, 5, 0) at (32, 80, 8), steam out on the top face
//   of (0, 8, 0) at (8, 144, 8), blowdown on the west face of (-1, 0, 0) at (-16, 6, 8). Partial level_float: float
//   of the secondary level gauge at (-12, 60, 8), slides y 0..40; stored -48 y.
// Coolant pump, y 0..4: suction in on the north face of (0, 0, -1) at (8, 10, -16), discharge out on the south face of
//   (0, 1, 1) at (8, 18, 32), power (6 kV) on the east face of (1, 3, 0) at (32, 56, 8), stator cooling water on the
//   west face of (-1, 2, 0) at (-16, 40, 8). Partial speed_disc: tacho disc on top, turns about y at (8, 8); stored
//   -48 y, draw at +48 y.
// Pressurizer, y 0..7: surge line on the south face of (0, 0, 1) at (8, 6, 32), relief out on the top face of
//   (0, 7, 0) at (8, 128, 8), nitrogen make-up on the east face of (1, 0, 0) at (32, 8, 8). Partials: level_float on
//   the north gauge at (8, 20, -7), slides y 0..80, no shift; gas_gauge_needle on the bottle rack gauge, turns about z
//   at (26, 40, -13.4), 0..270 deg over 0..15 MPa; stored -24 y, draw at +24 y.
(function () {
  const C = [8, 8];

  // ------------------------------------------------------------------ steam generator
  function sg() {
    const m = PCVV.mk();
    m.cylY('skirt', C, 16, 0, 10, 'pnt');
    m.ringY('skirt_base', C, 17.5, 3, 0, 1.4, 'pnt');
    m.headDown('bot', C, 19, 22, 12, 4, 'clad');
    m.nozzle('prim_in', 'z', [8, 14], -4, -16, 7, 5, 'gs', { nuts: 10 });
    m.nozzle('prim_out', 'z', [8, 14], 20, 32, 7, 5, 'gw', { nuts: 10 });
    m.cylY('shell_lo', C, 19, 22, 84, 'clad');
    m.cylY('cone_0', C, 20, 84, 88, 'clad', { seed: 3 });
    m.cylY('cone_1', C, 21, 88, 92, 'clad', { seed: 5 });
    m.cylY('drum', C, 22, 92, 128, 'clad', { seed: 7 });
    m.headUp('top', C, 22, 128, 6, 4, 'clad');
    for (const y of [22, 92]) m.ringY(`seam_${y}`, C, 22.2, 0.6, y - 0.4, y + 0.4, 'clad');
    // steam outlet with its isolation valve on top, two safety valves beside it
    m.cylY('steam_nz', C, 3, 133, 138, 'gs');
    m.box('steam_valve', [4, 138, 4], [12, 142, 12], 'gs', { split: '' });
    m.cylY('steam_out', C, 2.6, 142, 143.9, 'forged', { cap: 'vver/flange' });
    for (const x of [-4, 20]) {
      m.cylY(`sv_${x}`, [x, 8], 1.6, 130, 136, 'red');
      m.box(`sv_body_${x}`, [x - 1.8, 136, 6.2], [x + 1.8, 139, 9.8], 'red', { split: '' });
    }
    // feedwater nozzle east, blowdown west, manway on the drum front
    m.nozzle('feed', 'x', [80, 8], 18, 32, 3.4, 2.4, 'gw', { nuts: 8 });
    m.box('blowdown', [-16, 5, 7], [-4, 7, 9], 'gw', { split: 'x' });
    m.prism('manway', 'z', [8, 108], 4.2, -16, -12, 'clad', { split: '', ends: ['north'] });
    m.disc('manway_cover', 'z', [8, 108], 5, -15.9, -15.4, 'forged', 'vver/flange', 'north');
    // secondary level gauge on the west side
    m.box('level_col', [-13, 56, 7], [-11, 104, 9], 'st', { split: '' });
    for (const y of [58, 101]) m.box(`level_nz_${y}`, [-11, y, 7.4], [-2, y + 1.2, 8.6], 'st', { split: '' });
    m.box('level_scale', [-13.6, 58, 9], [-13, 102, 9.6], 'yel', { split: '' });
    // lateral restraint ring with four struts to the cell faces
    m.ringY('restraint', C, 23.6, 1.6, 96, 100, 'pnt');
    for (const [f, t] of [[[-16, 96, 6], [-14, 100, 10]], [[30, 96, 6], [32, 100, 10]], [[6, 96, -16], [10, 100, -14]], [[6, 96, 30], [10, 100, 32]]]) m.box(`strut_${f.join('_')}`, f, t, 'pnt', { split: '' });
    // ladder up the south side
    m.ladderZ('ladder', -6, 2, 30, 1, 10, 128, 'yel', 0);
    m.decal('plate', [5, 60, -11.4], [11, 63.6, -10.8], 'clad', 'north', 'pl.maker');
    return m;
  }
  function sgFloat() { const m = PCVV.mk(); m.box('float', [-14, 60, 6.4], [-10, 62, 9.6], 'red', { split: '' }); return m; }

  // ------------------------------------------------------------------ canned-motor coolant pump
  function pump() {
    const m = PCVV.mk();
    for (const a of [45, 135, 225, 315]) m.postY(`foot_${a}`, C, 13, a, 4, 0, 6, 'pnt', { split: '' });
    m.headDown('volute_bot', C, 15, 8, 4, 3, 'forged');
    m.cylY('volute', C, 15, 8, 22, 'forged');
    m.nozzle('suction', 'z', [8, 10], -4, -16, 7, 5, 'gw', { nuts: 10 });
    m.nozzle('discharge', 'z', [8, 18], 20, 32, 7, 5, 'gw', { nuts: 10 });
    // thermal barrier flange and the canned motor with its cooling jacket bands
    m.cylY('barrier', C, 17, 22, 26, 'forged', { cap: 'vver/flange' });
    m.nutsY('barrier_nut', C, 15.8, 16, 26, 1.6, 1.4, 'bright');
    m.cylY('motor', C, 10.4, 26, 66, 'motor');
    for (let y = 30; y < 64; y += 6) m.ringY(`jacket_${y}`, C, 11.2, 0.8, y, y + 1.4, 'motor');
    m.cylY('motor_top', C, 11, 66, 70, 'mcap', { cap: 'vver/cap' });
    for (const a of [0, 180]) m.radial(`lug_${a}`, C, 10, 13, a, 1.4, 66, 72, 'yel');
    // terminal box east with the cable gland, cooling water in and out on the west
    m.box('term_box', [18, 50, 3], [28, 62, 13], 'motor', { split: '' });
    m.box('term_neck', [17, 54, 6], [18.6, 58, 10], 'motor', { split: '' });
    m.prism('gland', 'x', [56, 8], 2, 28, 31.9, 'dark', { split: '', ends: ['east'] });
    for (const [y, n] of [[36, 'in'], [44, 'out']]) m.box(`cw_${n}`, [-15.9, y - 1.2, 6.8], [-2, y + 1.2, 9.2], 'gw', { split: 'x' });
    m.box('cw_valve', [-12, 33, 5.4], [-9, 47, 10.6], 'ci', { split: '' });
    m.decal('plate', [4, 40, -2.6], [12, 44, -2.1], 'motor', 'north', 'pl.maker');
    return m;
  }
  function speedDisc() {
    const m = PCVV.mk();
    m.cylY('shaft_end', C, 1.2, 70, 71.4, 'bright');
    m.cylY('disc', C, 4, 71.4, 72.2, 'st', { cap: 'vver/flange' });
    m.box('mark', [8, 72.2, 7.4], [12, 72.5, 8.6], 'red', { split: '' });
    return m;
  }

  // ------------------------------------------------------------------ gas volume compensator
  function przr() {
    const m = PCVV.mk();
    m.cylY('skirt', C, 10, 0, 8, 'pnt');
    m.headDown('bot', C, 12.3, 16, 8, 4, 'clad');
    m.cylY('shell', C, 12.3, 16, 110, 'clad');
    m.headUp('top', C, 12.3, 110, 8, 4, 'clad');
    // surge line from the bottom head out to the south face
    m.cylY('surge_down', C, 1.6, 3, 9, 'gw');
    m.box('surge_run', [6.4, 4.4, 8], [9.6, 7.6, 31.6], 'gw', { split: 'z' });
    m.disc('surge_flange', 'z', [8, 6], 2.6, 31.3, 31.9, 'forged', 'vver/flange', 'south');
    // two safety valves on top, discharge to the top face; gas inlet nozzle
    for (const x of [3, 13]) {
      m.cylY(`sv_${x}`, [x, 8], 1.4, 117, 121, 'red');
      m.box(`sv_body_${x}`, [x - 1.6, 121, 6.4], [x + 1.6, 124, 9.6], 'red', { split: '' });
    }
    m.box('sv_header', [3, 124, 7], [13, 125.4, 9], 'st', { split: '' });
    m.cylY('relief_out', C, 1.2, 125.4, 127.9, 'st');
    m.box('gas_in_top', [12, 112, 7.4], [27, 113.2, 8.6], 'yel', { split: 'x' });
    // nitrogen bottle rack in the east column, manifold, reducing valve, line up to the top and the make-up port
    for (const z of [-12, -6, 0]) {
      m.cylY(`bottle_${z}`, [27, z], 2.4, 2, 28, 'yel');
      m.cylY(`bottle_cap_${z}`, [27, z], 1.2, 28, 30, 'st');
    }
    for (const y of [4, 22]) m.box(`rack_band_${y}`, [24, y, -15], [30.4, y + 1, 3], 'pnt', { split: '' });
    m.box('rack_post', [29.6, 0, -15], [30.4, 34, -14.2], 'pnt', { split: '' });
    m.box('manifold', [26.4, 30, -12.6], [27.6, 31.2, 6], 'st', { split: '' });
    m.box('reducer', [25, 30, 4], [29, 34, 8], 'ci', { split: '' });
    m.box('gas_riser', [26.4, 34, 7.4], [27.6, 113.2, 8.6], 'yel', { split: 'y' });
    m.box('makeup_in', [24, 7.4, 7.4], [31.9, 8.6, 8.6], 'yel', { split: '' });
    m.box('makeup_up', [26.4, 8.6, 7.4], [27.6, 30, 8.6], 'yel', { split: '' });
    // rack pressure gauge facing north
    m.prism('rack_gauge', 'z', [26, 40], 2.2, -14, -13.4, 'st', { split: '', ends: ['north'] });
    m.plate('rack_gauge_face', [23.8, 37.8, -14.1], [28.2, 42.2, -14], 'st', 'north', 'material/gauge_dial');
    m.box('rack_gauge_stem', [25.6, 34, -13.4], [26.4, 37.8, -12.6], 'st', { split: '' });
    m.box('rack_gauge_line', [25.6, 33, -12.6], [26.4, 34, -12], 'st', { split: '' });
    // water level gauge on the north side
    m.box('level_col', [7, 18, -7.6], [9, 104, -5.6], 'st', { split: '' });
    for (const y of [20, 100]) m.box(`level_nz_${y}`, [7.4, y, -5.6], [8.6, y + 1.2, -3.6], 'st', { split: '' });
    m.box('level_scale', [9, 20, -8.2], [9.6, 102, -7.6], 'yel', { split: '' });
    m.ladderZ('ladder', 2, 10, 23, 1, 8, 112, 'yel', 0);
    m.decal('plate', [10, 60, -4.6], [16, 63.6, -4], 'clad', 'north', 'pl.maker');
    return m;
  }
  function przrFloat() { const m = PCVV.mk(); m.box('float', [6.4, 20, -8.2], [9.6, 22, -5], 'red', { split: '' }); return m; }
  function needle() { const m = PCVV.mk(); m.box('needle', [25.7, 39.7, -14.3], [26.3, 41.8, -14.15], 'red', { split: '' }); return m; }

  const UNITS = {
    steam_generator: { draw: sg, parts: { level_float: { draw: sgFloat, shift: [0, -48, 0] } } },
    coolant_pump: { draw: pump, parts: { speed_disc: { draw: speedDisc, shift: [0, -48, 0] } } },
    pressurizer: { draw: przr, parts: { level_float: { draw: przrFloat, shift: [0, 0, 0] }, gas_gauge_needle: { draw: needle, shift: [0, -24, 0] } } },
  };
  const id = (k) => `vver70_${k}_real`;
  const partial = (k, p) => { const d = UNITS[k].parts[p]; const [dx, dy, dz] = d.shift; return PCVV.mk().merge(d.draw(), dx, dy, dz, ''); };
  window.V70L = {
    UNITS, id, partial,
    parts: (k) => Object.keys(UNITS[k].parts),
    model: (k) => UNITS[k].draw(),
    build: (root, k) => { const m = PCVV.cut(UNITS[k].draw()); PCART.realize(root, m, id(k)); return { elements: m.els.length, bounds: PCVV.bounds(m) }; },
    save: (root, k) => PCVV.save(root, id(k), 'core/vver70'),
    buildPartial: (root, k, p) => { const m = partial(k, p); PCART.realize(root, m, id(k) + '_' + p); return { elements: m.els.length, bounds: PCVV.bounds(m) }; },
    savePartial: (root, k, p) => PCVV.savePartial(root, id(k), p, 'core/vver70'),
    preview: (root, k) => { const m = UNITS[k].draw(); for (const d of Object.values(UNITS[k].parts)) m.merge(d.draw(), 0, 0, 0, 'p_'); PCVV.cut(m); PCART.realize(root, m, id(k) + '_preview'); return { elements: m.els.length, bounds: PCVV.bounds(m) }; },
  };
})();
