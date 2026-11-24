// CANDU NPD heat transport loop at real scale (1 block = 1 m, 16 px = 1 m): the boiler (a horizontal U-tube heat
// exchanger with D2O in the tubes and a separate steam drum above on risers and downcomers, the NPD arrangement) and the
// vertical heat transport pump. Sheet: docs/research/2026-11-24-reactor-prototypes/candu_npd_loop.md (disk only; the
// drum-on-risers arrangement is sourced, sizes estimated).
//
// Load in Blockbench after pcart.js, pcround.js, pcslice.js and core/vver/vverkit.js, then for k in 'boiler', 'pump':
//   NPDL.build(repo, k); /* wait */ NPDL.save(repo, k)
//   for (const p of NPDL.parts(k)) { NPDL.buildPartial(repo, k, p); NPDL.savePartial(repo, k, p) }
//
// Boiler (candu_npd_boiler_real): controller (0, 0, 0), front north, footprint x -3..3, y 0..5, z -1..1. Heat
// exchanger axis along x at (y 20, z 8), drum axis along x at (y 72, z 8). Ports: D2O in on the west face of (-3, 1, 0)
// at (-48, 24, 8); D2O out on the west face of (-3, 0, 0) at (-48, 12, 8); feedwater in on the south face of (1, 4, 1)
// at (24, 72, 32); steam out on the top face of (0, 5, 0) at (8, 96, 8); blowdown on the bottom face of (2, 0, 0) at
// (40, 0, 8). Partial level_float: drum level gauge float at (50, 66, 8), slides y 0..12; stored -32 x, -48 y, draw at
// +32 x, +48 y.
// Pump (candu_npd_pump_real): controller (0, 0, 0) under the axis (8, y, 8), footprint x -1..1, y 0..4, z -1..1. Ports:
// suction on the north face of (0, 0, -1) at (8, 8, -16); discharge on the south face of (0, 0, 1) at (8, 12, 32);
// power on the east face of (1, 3, 0) at (32, 56, 8); seal water on the west face of (-1, 1, 0) at (-16, 20, 8).
// Partial coupling_flywheel: turns about y at (8, 8); stored -32 y, draw at +32 y.
(function () {
  const C = [8, 8], HX = [20, 8], DR = [72, 8];

  function headX(m, n, c, R, x0, dir, depth, mat) {
    for (let i = 0; i < 3; i++) {
      const r = R * Math.sqrt(1 - Math.pow((i + 0.5) / 3, 2)) + 0.4, a = x0 + dir * depth * i / 3, b = x0 + dir * depth * (i + 1) / 3;
      m.prism(`${n}_${i}`, 'x', c, r, Math.min(a, b), Math.max(a, b), mat, { split: '', ends: [dir > 0 ? 'east' : 'west'] });
    }
  }

  function boiler() {
    const m = PCVV.mk();
    // heat exchanger: shell, U-bend head east, channel head with the partition flange west, two saddles
    m.prism('hx_shell', 'x', HX, 12.8, -34, 44, 'clad', { split: 'x' });
    headX(m, 'hx_bend', HX, 12.8, 44, 1, 7, 'clad');
    m.prism('hx_channel', 'x', HX, 12.8, -42, -36, 'forged', { split: '' });
    m.prism('hx_tubesheet', 'x', HX, 14, -36, -34, 'forged', { split: '', ends: ['west', 'east'] });
    m.disc('hx_cover', 'x', HX, 13.4, -43, -42, 'forged', 'vver/flange', 'west');
    for (const x of [-22, 30]) {
      m.box(`hx_saddle_${x}`, [x - 3, 0, -6], [x + 3, 10, 22], 'pnt', { split: '' });
      m.box(`hx_saddle_base_${x}`, [x - 5, 0, -8], [x + 5, 1.4, 24], 'pnt', { split: '' });
    }
    // D2O nozzles on the channel head to the west face: in (upper, red band), out (lower, green band)
    m.nozzle('d2o_in', 'x', [24, 8], -40, -48, 3.2, 2.4, 'gs', { nuts: 8 });
    m.nozzle('d2o_out', 'x', [12, 8], -40, -48, 3.2, 2.4, 'gw', { nuts: 8 });
    // steam drum on four columns, its heads, safety valves, steam outlet with the stop valve
    m.prism('drum', 'x', DR, 11.2, -32, 40, 'clad', { split: 'x' });
    headX(m, 'drum_w', DR, 11.2, -32, -1, 5, 'clad');
    headX(m, 'drum_e', DR, 11.2, 40, 1, 5, 'clad');
    for (const x of [-26, 34]) for (const z of [-12, 26]) m.box(`col_${x}_${z}`, [x - 1.2, 0, z - 1.2], [x + 1.2, 62, z + 1.2], 'pnt', { split: 'y' });
    for (const x of [-26, 34]) m.box(`beam_${x}`, [x - 1.4, 60, -13], [x + 1.4, 62, 27], 'pnt', { split: '' });
    m.cylY('steam_out', C, 2.6, 82, 90, 'gs');
    m.box('steam_valve', [4, 90, 4], [12, 94, 12], 'gs', { split: '' });
    m.cylY('steam_flange', C, 3.4, 94, 95.9, 'forged', { cap: 'vver/flange' });
    for (const x of [-20, 30]) {
      m.cylY(`sv_${x}`, [x, 8], 1.4, 82, 86, 'red');
      m.box(`sv_body_${x}`, [x - 1.8, 86, 6.2], [x + 1.8, 89, 9.8], 'red', { split: '' });
    }
    // risers from the shell top to the drum, downcomers from the drum down beside the shell
    for (const x of [-16, 4, 24]) m.cylY(`riser_${x}`, [x, 8], 2, 32, 62, 'gs');
    for (const x of [-28, 36]) {
      m.cylY(`downcomer_${x}`, [x, 22], 2.4, 14, 62, 'gw');
      m.box(`downcomer_elbow_${x}`, [x - 2.4, 11.6, 18], [x + 2.4, 16.4, 24.4], 'gw', { split: '' });
      m.box(`downcomer_top_${x}`, [x - 2.4, 62, 9], [x + 2.4, 66, 24.4], 'gw', { split: '' });
    }
    // feedwater to the south face, blowdown through the bottom face
    m.nozzle('feed', 'z', [24, 72], 18, 32, 2.6, 1.8, 'gw', { nuts: 6 });
    m.cylY('blowdown', [40, 8], 1.2, 0, 8, 'gw');
    m.box('blowdown_valve', [38, 2, 6], [42, 5, 10], 'ci', { split: '' });
    // drum level gauge column off the east head
    m.box('level_col', [49, 60, 7], [51, 86, 9], 'st', { split: '' });
    for (const y of [64, 82]) m.box(`level_nz_${y}`, [44, y, 7.4], [49, y + 1.2, 8.6], 'st', { split: '' });
    m.box('level_scale', [51, 62, 7], [51.6, 84, 9], 'yel', { split: '' });
    // walkway along the drum on the north side
    m.box('ladder_walk', [-32, 60, -16], [40, 61, -12], 'grate', { split: 'x' });
    m.decal('plate', [0, 14, -5.2], [6, 17.6, -4.6], 'clad', 'north', 'pl.maker');
    return m;
  }
  function drumFloat() { const m = PCVV.mk(); m.box('float', [48.4, 66, 6.4], [51.6, 68, 9.6], 'red', { split: '' }); return m; }

  function pump() {
    const m = PCVV.mk();
    for (const a of [45, 135, 225, 315]) m.postY(`foot_${a}`, C, 9.6, a, 3, 0, 4, 'pnt', { split: '' });
    m.cylY('casing', C, 10.4, 4, 18, 'forged');
    m.cylY('cover', C, 11.2, 18, 20, 'forged', { cap: 'vver/flange' });
    m.nutsY('cover_nut', C, 10.2, 14, 20, 1.2, 1.2, 'bright');
    m.nozzle('suction', 'z', [8, 8], -2, -16, 6, 4.4, 'gw', { nuts: 10 });
    m.nozzle('discharge', 'z', [8, 12], 18, 32, 6, 4.4, 'gw', { nuts: 10 });
    m.box('seal_housing', [4, 20, 4], [12, 24, 12], 'ci', { split: '' });
    m.box('seal_water', [-16, 19, 7.2], [4, 20.6, 8.8], 'gw', { split: 'x' });
    for (const a of [45, 135, 225, 315]) m.postY(`stool_${a}`, C, 8, a, 2.2, 20, 36, 'pnt', { split: '' });
    m.cylY('motor', C, 8.8, 36, 58, 'motor');
    for (let y = 40; y < 56; y += 5) m.ringY(`motor_rib_${y}`, C, 9.4, 0.6, y, y + 1, 'motor');
    m.cylY('motor_cowl', C, 8, 58, 64, 'mcap', { cap: 'vver/grating_disc' });
    m.box('term_box', [16, 50, 4], [26, 60, 12], 'motor', { split: '' });
    m.box('cable', [26, 54, 7], [31.9, 57, 10], 'dark', { split: '' });
    for (const a of [0, 180]) m.radial(`lug_${a}`, C, 8.8, 11, a, 1.4, 60, 66, 'yel');
    m.decal('plate', [5, 44, -1.2], [11, 47.6, -0.6], 'motor', 'north', 'pl.maker');
    return m;
  }
  function couplingFlywheel() {
    const m = PCVV.mk();
    m.cylY('shaft', C, 1.6, 24, 36, 'bright');
    m.cylY('coupling', C, 3.6, 25, 28, 'ci');
    m.cylY('flywheel', C, 6.4, 30, 34, 'st', { cap: 'vver/flange' });
    return m;
  }

  const UNITS = {
    boiler: { id: 'candu_npd_boiler_real', draw: boiler, parts: { level_float: { draw: drumFloat, shift: [-32, -48, 0] } } },
    pump: { id: 'candu_npd_pump_real', draw: pump, parts: { coupling_flywheel: { draw: couplingFlywheel, shift: [0, -32, 0] } } },
  };
  const partial = (k, p) => { const d = UNITS[k].parts[p]; const [dx, dy, dz] = d.shift; return PCVV.mk().merge(d.draw(), dx, dy, dz, ''); };
  window.NPDL = {
    UNITS, partial,
    parts: (k) => Object.keys(UNITS[k].parts),
    model: (k) => UNITS[k].draw(),
    build: (root, k) => { const m = PCVV.cut(UNITS[k].draw()); PCART.realize(root, m, UNITS[k].id); return { elements: m.els.length, bounds: PCVV.bounds(m) }; },
    save: (root, k) => PCVV.save(root, UNITS[k].id, 'core/candu_npd'),
    buildPartial: (root, k, p) => { const m = partial(k, p); PCART.realize(root, m, UNITS[k].id + '_' + p); return { elements: m.els.length, bounds: PCVV.bounds(m) }; },
    savePartial: (root, k, p) => PCVV.savePartial(root, UNITS[k].id, p, 'core/candu_npd'),
  };
})();
