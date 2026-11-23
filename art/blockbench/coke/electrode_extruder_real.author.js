// Horizontal hydraulic extrusion press for carbon electrodes and graphite blanks at real scale (1 block = 1 m, 16 px
// = 1 m), a 600 t class press of the 1940s-50s: the main hydraulic cylinder behind the rear platen, four tie rods to
// the front platen, the crosshead and ram pushing the paste through the steam-jacketed mud cylinder with its charging
// hopper, the die head and nozzle, the green electrode coming out onto the roller table, and the hydraulic pump unit
// with its oil tank driven from a Create shaft at the front of the controller.
//
// Load in Blockbench after pcart.js, pcround.js, pcslice.js and core/vver/vverkit.js, then
//   EXR.build(repo); /* wait */ EXR.save(repo)
//   EXR.buildPartial(repo, p); EXR.savePartial(repo, p)    for ram, electrode
//   EXR.preview(repo)
//
// Controller block = (0, 0, 0), front north. Footprint x -3..3, y 0..1, z 0..1. Press axis along x through
// (y 20, z 16), the die at the west end. Connections (px): Create shaft in on the north face of (0, 0, 0) at
// (8, 8, 0) into the pump; paste in through the hopper on the top face of (-1, 1, 0) at (-8, 32, 16) (cells
// (-1, 1, 0) and (-1, 1, 1) share the hopper mouth); green electrodes out on the west face of (-3, 1, 0) at
// (-48, 20, 16), off the roller table; steam to the jacket in on the south face of (0, 1, 1) at (-2, 22, 32).
// Partials (electrode_extruder_real/partial/*):
// - ram: crosshead and ram; slides along x, stroke 0..-24 px (fully retracted as stored); stored +2 x, draw at -2 x.
// - electrode: the green electrode on the table; slides -x from the nozzle as the ram advances; stored +48 x (x 0..12),
//   draw at -48 x.
(function () {
  const A = [20, 16];                                     // axis [y, z]
  const MAT = Object.assign({}, PCVV.MAT, { frame: { tex: 'plant/structure', P: 16 }, paste: { tex: 'material/soot', P: 16 } });
  const mk = () => Object.assign(new PCART.Model(MAT, { pl: PCVV.ATLAS.pl }), { particle: 'vver/paint' });

  function bed(m) {
    m.box('foundation', [-48, 0, 0], [64, 2, 32], 'conc', { split: 'xz' });
    m.box('bedplate', [-36, 2, 3], [62, 4, 29], 'ci', { split: 'x' });
  }

  function frame(m) {
    // rear platen with the main cylinder behind it
    m.box('rear_platen', [30, 4, 2], [37, 32, 30], 'pnt', { split: '' });
    m.prism('main_cyl', 'x', A, 10, 37, 62, 'pnt', { split: 'x', ends: ['east'] });
    m.disc('main_cover', 'x', A, 11, 60, 62, 'ci', 'vver/flange', 'east');
    for (const x of [44, 54]) m.ring(`main_band_${x}`, 'x', A, 10.3, 0.6, x, x + 1, 'ci');
    // front platen carrying the mud cylinder and the die head
    m.box('front_platen', [-26, 4, 2], [-19, 32, 30], 'pnt', { split: '' });
    // four tie rods with their nuts
    for (const [y, z] of [[7, 4], [7, 28], [30, 4], [30, 28]]) {
      m.prism(`tie_${y}_${z}`, 'x', [y, z], 1.3, -28, 39, 'bright', { split: 'x' });
      for (const x of [-28, 37]) m.prism(`tie_nut_${x}_${y}_${z}`, 'x', [y, z], 2, x, x + 2, 'ci', { split: '', ends: ['west', 'east'] });
    }
  }

  function mud(m) {
    m.prism('mud_cyl', 'x', A, 8, -19, 6, 'ci', { split: 'x' });
    m.ring('jacket', 'x', A, 9, 1.4, -17, 4, 'felt');
    m.box('steam_in', [-3, 20.6, 25], [-1, 23.4, 32], 'gs', { split: '' });
    m.box('steam_valve', [-4, 19.6, 28], [0, 24.4, 30.4], 'gm', { split: '' });
    // charging hopper over the mud cylinder
    m.box('hopper_neck', [-14, 27, 10], [-2, 30, 22], 'pnt', { split: '' });
    m.box('hopper', [-16, 30, 6], [0, 32, 26], 'pnt', { split: '', hide: ['up'] });
    m.box('hopper_paste', [-15, 31.4, 7], [-1, 31.6, 25], 'paste', { split: '' });
    m.box('hopper_gate', [-1, 27.4, 14], [1, 29.6, 18], 'red', { split: '' });
  }

  function die(m) {
    m.box('die_head', [-32, 8, 6], [-26, 30, 26], 'ci', { split: '' });
    m.prism('nozzle', 'x', A, 6, -36, -32, 'ci', { split: '', ends: ['west'] });
    m.ring('die_heater', 'x', A, 7, 1, -35, -33, 'gs');
    // roller table for the green electrode
    for (let x = -46; x <= -38; x += 4) {
      m.prism(`roller_${x}`, 'z', [x, 14.6], 1.4, 8, 24, 'bright', { split: '' });
      m.box(`roller_post_${x}`, [x - 0.6, 2, 7], [x + 0.6, 14, 8], 'frame', { split: '' });
      m.box(`roller_post_b_${x}`, [x - 0.6, 2, 24], [x + 0.6, 14, 25], 'frame', { split: '' });
    }
    m.box('table_rail_n', [-48, 13, 7], [-36, 14, 8], 'frame', { split: 'x' });
    m.box('table_rail_s', [-48, 13, 24], [-36, 14, 25], 'frame', { split: 'x' });
  }

  // Hydraulic pump unit at the controller's front with the Create input, oil tank and pressure line to the cylinder.
  function pump(m) {
    m.box('pump_body', [3, 4, 1], [13, 12, 7], 'pnt', { split: '' });
    m.prism('input_boss', 'z', [8, 8], 2.2, 0, 1, 'ci', { split: '', ends: ['north'] });
    m.box('oil_tank', [14, 2, 1], [28, 12, 6], 'pnt', { split: '' });
    m.box('tank_glass', [16, 5, 0.9], [18, 10, 1], 'st', { split: '', face: { north: { tex: 'boilers/level', uv: [0, 0, 16, 16] } } });
    m.prism('pressure_gauge', 'z', [10, 14], 1.6, 0.4, 1, 'st', { split: '', ends: ['north'] });
    m.plate('gauge_face', [8.6, 12.6, 0.3], [11.4, 15.4, 0.4], 'st', 'north', 'material/gauge_dial');
    m.box('line_up', [12, 12, 2], [13.4, 28, 3.4], 'oil', { split: '' });
    m.box('line_run', [12, 28, 2], [48, 29.4, 3.4], 'oil', { split: 'x' });
    m.box('line_in', [46.6, 23, 2], [48, 28, 8], 'oil', { split: '' });
    m.box('valve_block', [40, 24, 1], [46, 29, 5], 'ci', { split: '' });
    for (let k = 0; k < 3; k++) m.box(`lever_${k}`, [41 + k * 1.8, 29, 2.4], [41.6 + k * 1.8, 32, 3], 'red', { split: '' });
    m.decal('plate', [-24, 14, 1.9], [-20, 18, 2], 'pnt', 'north', 'pl.maker');
  }

  // ------------------------------------------------------------------ partials
  function ram(m) {
    m.box('crosshead', [22, 6, 3], [30, 31, 29], 'pnt', { split: '' });
    for (const [y, z] of [[7, 4], [7, 28], [30, 4], [30, 28]]) m.prism(`guide_${y}_${z}`, 'x', [y, z], 2, 22, 30, 'ci', { split: '', ends: ['west', 'east'] });
    m.prism('ram', 'x', A, 6.4, -18, 22, 'bright', { split: '' });
  }
  function electrode(m) {
    m.prism('green_electrode', 'x', A, 5.6, -48, -36, 'dark', { split: '', ends: ['west'] });
  }

  const PART = { ram: { draw: ram, shift: [2, 0, 0], note: 'slides along x, stroke 0..-24; draw at -2 x' }, electrode: { draw: electrode, shift: [48, 0, 0], note: 'slides -x as it is extruded; draw at -48 x' } };
  function model() { const m = mk(); bed(m); frame(m); mud(m); die(m); pump(m); return m; }
  function partial(name) { const src = mk(); PART[name].draw(src); const [dx, dy, dz] = PART[name].shift; return mk().merge(src, dx, dy, dz, ''); }
  const ID = 'electrode_extruder_real';
  window.EXR = {
    ID, model, partial,
    build: (root) => { const m = PCVV.cut(model()); PCART.realize(root, m, ID); return { elements: m.els.length, bounds: PCVV.bounds(m) }; },
    save: (root) => PCVV.save(root, ID, 'coke'),
    buildPartial: (root, name) => { const m = partial(name); PCART.realize(root, m, ID + '_' + name); return { elements: m.els.length, bounds: PCVV.bounds(m) }; },
    savePartial: (root, name) => PCVV.savePartial(root, ID, name, 'coke'),
    preview: (root) => { const m = model(); for (const [k, p] of Object.entries(PART)) { const s = mk(); p.draw(s); m.merge(s, 0, 0, 0, k + '_'); } PCVV.cut(m); PCART.realize(root, m, ID + '_preview'); return { elements: m.els.length, bounds: PCVV.bounds(m) }; },
  };
})();
