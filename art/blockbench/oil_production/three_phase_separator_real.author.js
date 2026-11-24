// Horizontal oil-gas-water separator at real scale (1 block = 1 m, 16 px = 1 m), the Soviet NGS-1.6-2000 / NGSV
// (25 m3, 2.0 m shell, 8.5 m between tangent lines, 2:1 heads): the shell on two saddles on concrete pads, the
// well-fluid inlet on the west head, the gas nozzle over the mist extractor at the east end, the oil draw through
// the oil level-control valve out east, the water boot and its level-control valve below, the relief valve on top,
// the magnetic level gauges on the north side, manway and ladder.
// Sheet: docs/research/2026-11-24-i2-batch2/three_phase_separator_real.md.
//
// Load in Blockbench after pcart.js, pcround.js, pcslice.js and core/vver/vverkit.js, then
//   SEP.build(repo); /* wait */ SEP.save(repo)
//   SEP.buildPartial(repo, p); SEP.savePartial(repo, p)   for oil_level, water_level, oil_valve_stem, water_valve_stem
//   SEP.preview(repo)
//
// Controller block = (0, 0, 0) mid-vessel, front north. Footprint x -5..5, y 0..2, z -1..1 (11 x 3 x 3). Shell axis
// along x through (y 23, z 8), r 16, cylinder x -60..76, heads to x -68 and 84.
// Connections (px): well fluid in on the west face of (-5, 1, 0) at (-80, 23, 8); gas out on the top face of
// (4, 2, 0) at (72, 48, 8); oil out on the east face of (5, 0, 0) at (96, 8, 8); water out on the bottom face of
// (2, 0, 0) at (40, 0, 8).
// Partials (three_phase_separator_real/partial/*):
// - oil_level: float of the oil gauge on the north side at x -8, z -10; slides y 14..36 with the oil level; stored
//   at rest (y 14), no shift.
// - water_level: float of the interface gauge at x -14, z -10; slides y 8..22; stored at rest (y 8), no shift.
// - oil_valve_stem: stem and position pointer of the oil LCV at (88, 14, 8); slides y 0..1.5; stored -80 x, draw at
//   +80 x.
// - water_valve_stem: stem of the water LCV at (40, 4, 18); slides y 0..1.5; stored -32 x, draw at +32 x.
(function () {
  const MAT = Object.assign({}, PCVV.MAT, { frame: { tex: 'plant/structure', P: 16 } });
  const mk = () => Object.assign(new PCART.Model(MAT, { pl: PCVV.ATLAS.pl }), { particle: 'vver/paint' });
  const A = [23, 8], R = 16;

  // 2:1 ellipsoidal head along x from the tangent line x0, `dir` +1 (east) or -1 (west), depth 8: terraced 16-gons.
  function head(m, name, x0, dir) {
    const n = 4, depth = 8;
    for (let i = 0; i < n; i++) {
      const r = R * Math.sqrt(1 - Math.pow((i + 0.5) / n, 2)) + 0.4;
      const a = x0 + dir * depth * i / n, b = x0 + dir * depth * (i + 1) / n;
      m.prism(`${name}_${i}`, 'x', A, r, Math.min(a, b), Math.max(a, b), 'pnt', { split: '', ends: [dir > 0 ? 'east' : 'west'] });
    }
  }

  function shell(m) {
    m.prism('shell', 'x', A, R, -60, 76, 'pnt', { split: 'x' });
    head(m, 'head_w', -60, -1);
    head(m, 'head_e', 76, 1);
    for (const x of [-60, 76]) m.ring(`seam_${x}`, 'x', A, R + 0.3, 0.6, x - 0.4, x + 0.4, 'pnt');
    // saddles with wear plates and the concrete pads
    for (const x of [-36, 52]) {
      m.box(`pad_${x}`, [x - 7, 0, -8], [x + 7, 2, 24], 'conc', { split: '' });
      m.box(`saddle_web_${x}`, [x - 0.6, 2, -6], [x + 0.6, 12, 22], 'frame', { split: '' });
      m.box(`saddle_base_${x}`, [x - 5, 2, -6], [x + 5, 3, 22], 'frame', { split: '' });
      for (const z of [-6, 21]) m.box(`saddle_rib_${x}_${z}`, [x - 4, 3, z], [x + 4, 11, z + 1], 'frame', { split: '' });
      m.box(`wear_${x}`, [x - 5, 7, -2], [x + 5, 8, 18], 'frame', { split: '' });
    }
    // name plate and the vessel number in the GOST style
    m.decal('plate', [-30, 26, -8.3], [-24, 29.6, -8], 'pnt', 'north', 'pl.maker');
  }

  function nozzles(m) {
    // well fluid inlet on the west head
    m.prism('inlet', 'x', A, 3.2, -79.4, -66, 'oil', { split: '' });
    m.disc('inlet_flange', 'x', A, 4.4, -79.9, -79.4, 'ci', 'vver/flange', 'west');
    // gas outlet over the mist extractor
    m.cylY('gas_nozzle', [72, 8], 2.6, 38, 46, 'yel');
    m.cylY('gas_flange', [72, 8], 3.6, 46, 47.9, 'ci', { cap: 'vver/flange' });
    // relief valve on the top with its tail pipe into the gas nozzle
    m.cylY('psv_nozzle', [8, 8], 1.6, 38, 41, 'pnt');
    m.box('psv_body', [5.6, 41, 5.6], [10.4, 44, 10.4], 'red', { split: '' });
    m.cylY('psv_bonnet', [8, 8], 1.4, 44, 47, 'red');
    m.box('psv_tail', [10.4, 42, 7.2], [70, 43.6, 8.8], 'yel', { split: 'x' });
    // manway on the top at the west end, davit beside it
    m.cylY('manway_neck', [-40, 8], 4, 37, 42, 'pnt');
    m.cylY('manway_cover', [-40, 8], 4.8, 42, 43.4, 'ci', { cap: 'vver/flange' });
    m.box('davit_post', [-46, 38, 13], [-45, 47, 14], 'st', { split: '' });
    m.box('davit_arm', [-46, 46, 7.4], [-40, 47, 14], 'st', { split: '' });
    // oil draw: down from the oil chamber, east through the LCV to the east face
    m.cylY('oil_draw', [70, 8], 2, 4, 8, 'oil');
    m.box('oil_run', [70, 6, 6], [95.4, 10, 10], 'oil', { split: 'x' });
    m.disc('oil_flange', 'x', [8, 8], 3.2, 95.4, 95.9, 'ci', 'vver/flange', 'east');
    m.box('oil_lcv_body', [85, 4.6, 4.6], [91, 11.4, 11.4], 'ci', { split: '' });
    m.box('oil_lcv_yoke', [86.6, 11.4, 7], [89.4, 14, 9], 'st', { split: '' });
    m.cylY('oil_lcv_diaphragm', [88, 8], 4.4, 17, 20, 'gs');
    m.box('oil_lcv_signal', [88, 19, 7.6], [91, 20, 8.4], 'st', { split: '' });
    // water boot under the settling section, the water LCV to the south and the bottom outlet
    m.cylY('boot', [40, 8], 4.6, 3, 8, 'pnt');
    m.box('water_out_s', [38.4, 3.4, 8], [41.6, 6.6, 15], 'gw', { split: '' });
    m.box('water_lcv_body', [37, 2, 15], [43, 7, 21], 'ci', { split: '' });
    m.cylY('water_lcv_diaphragm', [40, 18], 3.6, 10, 12.6, 'gs');
    m.box('water_lcv_yoke', [39, 7, 17], [41, 10, 19], 'st', { split: '' });
    m.box('water_down', [38.4, 0.6, 15], [41.6, 2, 21], 'gw', { split: '' });
    m.box('water_back', [38.4, 0, 6], [41.6, 1.6, 18], 'gw', { split: '' });
    // LCV alarm lamp box on the oil valve
    m.box('lamp_box', [91.4, 14, 2], [95, 18, 6], 'pnt', { split: '' });
    m.box('lamp', [92.4, 18, 3], [94, 19.4, 4.6], 'gs', { split: '' });
  }

  function gauges(m) {
    // magnetic level gauges on the north side: oil column x -8, interface column x -14, each with two nozzles
    for (const [x, y0, y1] of [[-8, 12, 38], [-14, 6, 24]]) {
      m.box(`gauge_col_${x}`, [x - 1, y0, -11], [x + 1, y1, -9], 'st', { split: '' });
      m.box(`gauge_scale_${x}`, [x - 1.6, y0 + 1, -11.2], [x - 1, y1 - 1, -11], 'yel', { split: '' });
      for (const y of [y0 + 1, y1 - 2]) m.box(`gauge_nz_${x}_${y}`, [x - 0.6, y, -9], [x + 0.6, y + 1.2, -2], 'st', { split: '' });
      m.box(`gauge_drain_${x}`, [x - 0.4, y0 - 2, -10.4], [x + 0.4, y0, -9.6], 'st', { split: '' });
    }
    // pressure gauge on the gas end
    m.box('pg_stem', [60, 39, 7.4], [61, 43, 8.6], 'st', { split: '' });
    m.prism('pg', 'z', [60.5, 44.6], 2, 6.6, 7.4, 'st', { split: '', ends: ['north'] });
    m.plate('pg_face', [58.8, 42.9, 6.5], [62.2, 46.3, 6.6], 'st', 'north', 'material/gauge_dial');
    // ladder to the top at the west saddle, on the south side
    for (const x of [-30, -26]) m.box(`ladder_rail_${x}`, [x, 2, 25.4], [x + 0.6, 46, 26], 'st', { split: 'y' });
    for (let y = 5; y < 46; y += 5) m.box(`ladder_rung_${y}`, [-30, y, 25.4], [-25.4, y + 0.5, 25.9], 'st', { split: '' });
  }

  // ------------------------------------------------------------------ partials
  const float = (x, y) => (m) => {
    m.box(`float_${x}`, [x - 1.4, y, -11.6], [x + 1.4, y + 2, -8.4], 'gs', { split: '' });
  };
  function oilStem(m) {
    m.box('stem', [87.6, 11.4, 7.6], [88.4, 17, 8.4], 'bright', { split: '' });
    m.box('pointer', [88.4, 14, 7.8], [90, 14.4, 8.2], 'red', { split: '' });
  }
  function waterStem(m) {
    m.box('stem', [39.6, 7, 17.6], [40.4, 10, 18.4], 'bright', { split: '' });
    m.box('pointer', [40.4, 8.4, 17.8], [42, 8.8, 18.2], 'red', { split: '' });
  }

  const PART = {
    oil_level: { draw: float(-8, 14), shift: [0, 0, 0] },
    water_level: { draw: float(-14, 8), shift: [0, 0, 0] },
    oil_valve_stem: { draw: oilStem, shift: [-80, 0, 0] },
    water_valve_stem: { draw: waterStem, shift: [-32, 0, 0] },
  };
  function model() { const m = mk(); shell(m); nozzles(m); gauges(m); return m; }
  function partial(name) { const src = mk(); PART[name].draw(src); const [dx, dy, dz] = PART[name].shift; return mk().merge(src, dx, dy, dz, ''); }
  const ID = 'three_phase_separator_real';
  window.SEP = {
    ID, model, partial, PART,
    build: (root) => { const m = PCVV.cut(model()); PCART.realize(root, m, ID); return { elements: m.els.length, bounds: PCVV.bounds(m) }; },
    save: (root) => PCVV.save(root, ID, 'oil_production'),
    buildPartial: (root, name) => { const m = partial(name); PCART.realize(root, m, ID + '_' + name); return { elements: m.els.length, bounds: PCVV.bounds(m) }; },
    savePartial: (root, name) => PCVV.savePartial(root, ID, name, 'oil_production'),
    preview: (root) => { const m = model(); for (const [k, p] of Object.entries(PART)) { const s = mk(); p.draw(s); m.merge(s, 0, 0, 0, k + '_'); } PCVV.cut(m); PCART.realize(root, m, ID + '_preview'); return { elements: m.els.length, bounds: PCVV.bounds(m) }; },
  };
})();
