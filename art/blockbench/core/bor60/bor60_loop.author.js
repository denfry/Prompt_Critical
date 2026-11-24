// BOR-60 loop equipment at real scale (1 block = 1 m, 16 px = 1 m), one set per 30 MW loop: the Na-Na intermediate
// heat exchanger, the TsNN-1 primary sodium pump and the once-through coil steam generator, all lagged and
// heat-traced. Sheet: docs/research/2026-11-24-reactor-prototypes/bor60_loop.md (disk only; pump duty and the SG type
// sourced, sizes estimated).
//
// Load in Blockbench after pcart.js, pcround.js, pcslice.js and core/vver/vverkit.js, then for k in 'ihx',
// 'primary_pump', 'steam_generator':
//   BORL.build(repo, k); /* wait */ BORL.save(repo, k)
//   for (const p of BORL.parts(k)) { BORL.buildPartial(repo, k, p); BORL.savePartial(repo, k, p) }
//
// Every unit: controller (0, 0, 0) under the vertical axis (8, y, 8), front north, footprint x -1..1, z -1..1.
// IHX (bor60_ihx_real), y 0..7: primary in on the west face of (-1, 6, 0) at (-16, 96, 8); primary out on the west face
//   of (-1, 1, 0) at (-16, 16, 8); secondary in/out (coaxial) on the top face of (0, 7, 0) at (8, 128, 8).
// Primary pump (bor60_primary_pump_real), y 0..5: suction on the north face of (0, 2, -1) at (8, 40, -16); discharge on
//   the south face of (0, 0, 1) at (8, 12, 32); power on the east face of (1, 5, 0) at (32, 88, 8); argon on the top
//   face of (-1, 5, 0) at (-8, 96, 8). Partial coupling: turns about y at (8, 8); stored -56 y, draw at +56 y.
// Steam generator (bor60_steam_generator_real), y 0..9: sodium in on the north face of (0, 8, -1) at (8, 134, -16);
//   sodium out on the north face of (0, 0, -1) at (8, 12, -16); feedwater in on the south face of (0, 1, 1) at
//   (8, 16, 32); steam out on the south face of (0, 8, 1) at (8, 136, 32); relief on the east face of (1, 7, 0) at
//   (32, 120, 8).
(function () {
  const C = [8, 8];
  const lag = { nuts: 8, boss: 'felt', pipe: 'felt' };
  function tracing(m, n, r, y0, y1) {
    for (let y = y0 + 8; y < y1 - 4; y += 24) m.box(`${n}_trace_${y}`, [8 + r - 0.2, y, 6.8], [8 + r + 1.6, y + 2.4, 9.2], 'yel', { split: '' });
  }

  function ihx() {
    const m = PCVV.mk();
    m.cylY('skirt', C, 8, 0, 6, 'pnt');
    m.cylY('shell', C, 10, 6, 110, 'felt');
    for (const y of [30, 60, 90]) m.ringY(`band_${y}`, C, 10.3, 0.6, y, y + 1, 'st');
    m.headUp('top', C, 10, 110, 8, 4, 'felt');
    m.cylY('sec_outer', C, 3.4, 117, 124, 'felt');
    m.cylY('sec_inner', C, 2, 124, 127.9, 'gs');
    m.cylY('sec_flange', C, 4.4, 122, 124, 'forged', { cap: 'vver/flange' });
    m.box('sec_branch', [11.4, 119, 6.6], [24, 121.8, 9.4], 'felt', { split: '' });
    m.nozzle('prim_in', 'x', [96, 8], -1, -16, 4.4, 3.2, 'gs', lag);
    m.nozzle('prim_out', 'x', [16, 8], -1, -16, 4.4, 3.2, 'gw', lag);
    m.ringY('support', C, 12, 1.6, 78, 82, 'pnt');
    for (const a of [45, 135, 225, 315]) m.postY(`support_leg_${a}`, C, 12.6, a, 1.6, 0, 78, 'pnt', { split: '' });
    tracing(m, 'ihx', 10, 6, 110);
    m.decal('plate', [5, 50, -2.6], [11, 53.6, -2], 'felt', 'north', 'pl.maker');
    return m;
  }

  function pump() {
    const m = PCVV.mk();
    for (const a of [45, 135, 225, 315]) m.postY(`leg_${a}`, C, 6.6, a, 2, 0, 4, 'pnt', { split: '' });
    m.cylY('tank', C, 8, 4, 52, 'felt');
    m.cylY('tank_cover', C, 9.4, 52, 55, 'forged', { cap: 'vver/flange' });
    m.nutsY('cover_nut', C, 8.6, 12, 55, 1, 1, 'bright');
    m.cylY('column', C, 3, 55, 74, 'st');
    for (const a of [45, 135, 225, 315]) m.postY(`stool_${a}`, C, 6, a, 1.6, 55, 80, 'pnt', { split: '' });
    m.cylY('motor', C, 7.2, 80, 94, 'motor');
    m.cylY('motor_cap', C, 6.4, 94, 95.9, 'mcap', { cap: 'vver/cap' });
    m.box('term_box', [15, 82, 4], [24, 92, 12], 'motor', { split: '' });
    m.box('cable', [24, 86, 7], [31.9, 89, 10], 'dark', { split: '' });
    m.nozzle('suction', 'z', [8, 40], -1, -16, 4.4, 3.2, 'gs', lag);
    m.nozzle('discharge', 'z', [8, 12], 17, 32, 4.4, 3.2, 'gw', lag);
    m.box('argon', [-9, 55, 7.4], [-1, 56.2, 8.6], 'yel', { split: '' });
    m.box('argon_up', [-9, 56.2, 7.4], [-7.8, 95.9, 8.6], 'yel', { split: 'y' });
    m.box('level_probe', [3, 55, 3], [5, 62, 5], 'st', { split: '' });
    tracing(m, 'pump', 8, 4, 52);
    m.decal('plate', [5, 30, -0.6], [11, 33.6, 0], 'felt', 'north', 'pl.maker');
    return m;
  }
  function coupling() {
    const m = PCVV.mk();
    m.cylY('hub_lo', C, 3.4, 74, 76.6, 'ci');
    m.cylY('hub_hi', C, 3.4, 77.4, 80, 'ci');
    for (let k = 0; k < 4; k++) { const a = k * Math.PI / 2, x = 8 + 2.4 * Math.cos(a), z = 8 + 2.4 * Math.sin(a); m.box(`pin_${k}`, [x - 0.4, 76.6, z - 0.4], [x + 0.4, 77.4, z + 0.4], 'st', { split: '' }); }
    return m;
  }

  function sg() {
    const m = PCVV.mk();
    m.cylY('skirt', C, 9.6, 0, 6, 'pnt');
    m.cylY('shell', C, 11.2, 6, 142, 'felt');
    for (const y of [40, 80, 120]) m.ringY(`band_${y}`, C, 11.5, 0.6, y, y + 1, 'st');
    m.headUp('top', C, 11.2, 142, 6, 3, 'felt');
    m.nozzle('na_in', 'z', [8, 134], -1, -16, 4.4, 3.2, 'gs', lag);
    m.nozzle('na_out', 'z', [8, 12], -1, -16, 4.4, 3.2, 'gw', lag);
    m.nozzle('feed', 'z', [8, 16], 17, 32, 3.4, 2.4, 'gw', { nuts: 8 });
    m.nozzle('steam', 'z', [8, 136], 17, 32, 3.4, 2.4, 'gs', { nuts: 8 });
    // sodium-water reaction relief: line to the east face with the rupture disc holder
    m.nozzle('relief', 'x', [120, 8], 17, 32, 3.6, 2.6, 'red', { nuts: 8 });
    m.box('rupture_disc', [24, 115, 3], [27, 125, 13], 'red', { split: '' });
    m.box('h2_detector', [17, 100, 2], [21, 104, 6], 'yel', { split: '' });
    m.ringY('support', C, 13, 1.6, 90, 94, 'pnt');
    for (const a of [45, 135, 225, 315]) m.postY(`support_leg_${a}`, C, 13.4, a, 1.6, 0, 90, 'pnt', { split: '' });
    tracing(m, 'sg', 11.2, 6, 142);
    m.ladderZ('ladder', -6, 2, 30, 1, 6, 146, 'yel', 0);
    m.decal('plate', [5, 60, -3.8], [11, 63.6, -3.2], 'felt', 'north', 'pl.maker');
    return m;
  }

  const UNITS = {
    ihx: { id: 'bor60_ihx_real', draw: ihx, parts: {} },
    primary_pump: { id: 'bor60_primary_pump_real', draw: pump, parts: { coupling: { draw: coupling, shift: [0, -56, 0] } } },
    steam_generator: { id: 'bor60_steam_generator_real', draw: sg, parts: {} },
  };
  const partial = (k, p) => { const d = UNITS[k].parts[p]; const [dx, dy, dz] = d.shift; return PCVV.mk().merge(d.draw(), dx, dy, dz, ''); };
  window.BORL = {
    UNITS, partial,
    parts: (k) => Object.keys(UNITS[k].parts),
    model: (k) => UNITS[k].draw(),
    build: (root, k) => { const m = PCVV.cut(UNITS[k].draw()); PCART.realize(root, m, UNITS[k].id); return { elements: m.els.length, bounds: PCVV.bounds(m) }; },
    save: (root, k) => PCVV.save(root, UNITS[k].id, 'core/bor60'),
    buildPartial: (root, k, p) => { const m = partial(k, p); PCART.realize(root, m, UNITS[k].id + '_' + p); return { elements: m.els.length, bounds: PCVV.bounds(m) }; },
    savePartial: (root, k, p) => PCVV.savePartial(root, UNITS[k].id, p, 'core/bor60'),
  };
})();
