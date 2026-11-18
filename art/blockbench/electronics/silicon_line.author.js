// The silicon line of 11 §11.5 and three reagent units, hand-authored for Blockbench (replaces the spec_*.py
// placeholders): submerged arc furnace, trichlorosilane fluidised-bed reactor, Siemens bell-jar reactor,
// Czochralski puller, wafer saw, cleanroom panel, window and airlock; the acid plant, the small air separator
// with its intake fan and the coke oven.
// Load inside Blockbench desktop, after the shared toolkit:
//   eval(require('fs').readFileSync('<repo>/art/blockbench/lib/pcart.js', 'utf8'))
//   eval(require('fs').readFileSync('<repo>/art/blockbench/lib/pcround.js', 'utf8'))
//   eval(require('fs').readFileSync('<repo>/art/blockbench/lib/plantkit.js', 'utf8'))
//   eval(require('fs').readFileSync('<repo>/art/blockbench/electronics/silicon_line.author.js', 'utf8'))
//   PCART.paintMaterials('<repo>'); PLANT.paint('<repo>'); SIL.paint('<repo>')
//   await SIL.exportAll('<repo>')
//
// Front to the north. These are single blocks; the models keep the placeholders' reach (the arc furnace spreads
// over x, z -16..32 and sinks to -16, the tall units rise to 28..32). The Czochralski puller and the wafer saw
// take a Create shaft at the back (south). air_separator is multipart with air_separator/partial/fan (static).
// cleanroom_panel is a full block (faces on the boundary cull).
(function () {
  const path = require('path');
  const { rng, img, put, get, add, mix, rect, vnoise, grain, writePng } = PCART;
  const TEX = 'electronics/';
  const T = {};

  // ---------------------------------------------------------------- textures (32 px per block)
  // Brushed stainless steel: vertical grain, cool grey.
  T.ss = () => {
    const im = img(32, 32), r = rng(9101);
    for (let x = 0; x < 32; x++) { const c = (r() - 0.5) * 10; for (let y = 0; y < 32; y++) put(im, x, y, add([186, 190, 194], c + (r() - 0.5) * 6)); }
    return im;
  };
  // Cleanroom wall panel: white enamel sandwich panel with a seam frame and rivets.
  T.clean = () => {
    const im = img(32, 32), r = grain(im, [232, 234, 232], 9111, 2, 2, 8);
    for (let i = 0; i < 32; i++) { put(im, i, 0, [196, 198, 198]); put(im, 0, i, [196, 198, 198]); put(im, i, 31, [210, 212, 212]); put(im, 31, i, [210, 212, 212]); }
    for (const [x, y] of [[3, 3], [28, 3], [3, 28], [28, 28]]) put(im, x, y, [170, 172, 172]);
    return im;
  };
  // Dark tinted glass with a reflection streak.
  T.glass = () => {
    const im = img(16, 16), r = rng(9121);
    for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) put(im, x, y, add(mix([60, 76, 88], [30, 40, 48], y / 16), (r() - 0.5) * 4 + (Math.abs(x - y - 2) < 1.2 ? 40 : 0)));
    return im;
  };
  // Viewport glow of a hot zone (Czochralski melt, Siemens rods).
  T.view_glow = () => { const im = img(16, 16), r = rng(9131); for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) { const d = Math.hypot(x - 7.5, y - 7.5) / 8; put(im, x, y, add(mix([255, 240, 190], [240, 120, 30], d), (r() - 0.5) * 10)); } return im; };
  T.view_dark = () => { const im = img(16, 16), r = rng(9141); for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) put(im, x, y, add([30, 32, 36], (r() - 0.5) * 6 + (x + y === 10 ? 40 : 0))); return im; };
  // Wafer saw screen: green text lines on black.
  T.screen = () => { const im = img(16, 16), r = rng(9151); rect(im, 0, 0, 16, 16, [10, 16, 14]); for (let y = 2; y < 14; y += 2) for (let x = 2; x < 2 + Math.floor(r() * 12); x++) put(im, x, y, [90, 230, 120]); return im; };
  // Frost on cold piping and the cold box casing (light blue-white crystals).
  T.frost = () => { const im = img(32, 32), r = grain(im, [214, 226, 234], 9161, 8, 6, 4); for (let k = 0; k < 60; k++) put(im, r() * 32, r() * 32, [250, 252, 255]); return im; };
  // Cold box casing: blue-grey painted panels with insulation fill ports.
  T.coldbox = () => {
    const im = img(32, 32), r = grain(im, [120, 146, 170], 9171, 3, 5, 8);
    for (let i = 0; i < 32; i++) { put(im, i, 15, [96, 118, 140]); put(im, 15, i, [96, 118, 140]); }
    return im;
  };
  // Furnace hood soot and the acid tank's PVC (grey, welded seams).
  T.pvc = () => { const im = img(32, 32), r = grain(im, [118, 124, 128], 9181, 3, 3, 8); for (let x = 0; x < 32; x += 16) for (let y = 0; y < 32; y++) put(im, x, y, [96, 100, 104]); return im; };

  const MAT = Object.assign({}, PLANT.MAT, {
    ss: { tex: TEX + 'ss', P: 16 }, clean: { tex: TEX + 'clean', P: 16 }, glass: { tex: TEX + 'glass', P: 16 },
    vglow: { tex: TEX + 'view_glow', P: 16 }, vdark: { tex: TEX + 'view_dark', P: 16 }, screen: { tex: TEX + 'screen', P: 16 },
    frost: { tex: TEX + 'frost', P: 16 }, cbox: { tex: TEX + 'coldbox', P: 16 }, pvc: { tex: TEX + 'pvc', P: 16 },
    fb: { tex: 'boilers/firebrick', P: 16 }, gph: { tex: 'steelmaking/graphite', P: 16 }, melt: { tex: 'steelmaking/melt', P: 16 },
    fglow: { tex: 'rolling/furnace_glow', P: 16 }, cu: { tex: 'disconnector/copper', P: 16 }, en: { tex: 'switchgear_cell/enamel', P: 16 },
    gal: { tex: 'disconnector/galvanised', P: 16 }, louv: { tex: 'grid_lines/louvre', P: 16 }, grat: { tex: 'steelmaking/grating', P: 8 },
    l_g: { tex: 'grid_lines/lamp_green', P: 16 }, l_r: { tex: 'grid_lines/lamp_red', P: 16 }, l_b: { tex: 'grid_lines/lamp_white', P: 16 },
    l_off: { tex: 'grid_lines/lamp_off', P: 16 },
  });
  const mk = (particle) => Object.assign(new PCART.Model(MAT, {}), { particle });

  function paint(root) {
    for (const n of Object.keys(T)) writePng(T[n](), path.join(root, PCART.ASSETS, 'textures/block', TEX + n + '.png'));
    return Object.keys(T).length;
  }
  function lamp(m, n, x, y, z, mat, glow, r) {
    const k = r || 0.6;
    m.box(`${n}_bezel`, [x - k - 0.2, y - k - 0.2, z - 0.15], [x + k + 0.2, y + k + 0.2, z], 'rubber', { split: '' });
    m.box((glow ? 'glow_' : '') + n, [x - k, y - k, z - 0.35], [x + k, y + k, z - 0.15], mat, { split: '', hide: ['south'] });
  }
  const viewport = (m, n, x, y, z, lit) => {
    m.box(`${n}_rim`, [x - 1.4, y - 1.4, z - 0.4], [x + 1.4, y + 1.4, z], 'ss', { split: '' });
    m.box((lit ? 'glow_' : '') + n, [x - 1, y - 1, z - 0.5], [x + 1, y + 1, z - 0.4], lit ? 'vglow' : 'vdark', { split: '', hide: ['south'] });
  };

  // ---------------------------------------------------------------- silicon line
  // Submerged arc furnace: a round refractory hearth sunk below the floor in a steel shell, the low hood over it,
  // three graphite electrodes through the hood on copper contact clamps and busbars, the tap hole (glowing
  // while running) and the fume stack.
  function arcFurnace(running) {
    const m = mk('steelmaking/graphite');
    m.box('floor', [-16, -1, -16], [32, 0, 32], 'grat', { split: 'xz' });
    m.cylY('shell', [8, 8], 14, -16, 0, 'frame', { cap: 'boilers/firebrick' });
    m.cylY('hood', [8, 8], 12, 4, 8, 'frame', { cap: 'material/steel' });
    for (const [x, z] of [[-6, 10], [22, 10], [8, -6], [8, 22]]) m.box(`hood_leg_${x}_${z}`, [x - 0.6, 0, z - 0.6], [x + 0.6, 4, z + 0.6], 'frame', { split: '' });
    const el = [[2, 3], [14, 3], [8, 13.4]];
    el.forEach(([x, z], i) => {
      m.cylY(`electrode_${i}`, [x, z], 2.6, -2, 26, 'gph', { cap: 'steelmaking/graphite' });
      m.box(`clamp_${i}`, [x - 3.2, 10, z - 3.2], [x + 3.2, 12, z + 3.2], 'cu', { split: '' });
      m.box(`bus_${i}`, [x - 0.8, 12, z - 0.8], [x + 0.8, 24, z + 0.8], 'cu', { split: 'y' });
      if (running) m.box(`glow_arc_${i}`, [x - 2.8, -1.2, z - 2.8], [x + 2.8, 0.2, z + 2.8], 'melt', { split: '', hide: ['down'] });
    });
    m.box('bus_bridge', [-1, 23, 2], [17, 24.6, 14], 'cu', { split: '' });
    m.box((running ? 'glow_' : '') + 'taphole', [6.6, -6, -6.2], [9.4, -3.6, -5.8], running ? 'melt' : 'soot', { split: '' });
    m.box('runner', [6, -7, -16], [10, -6, -6], 'fb', { split: '' });
    m.cylY('stack', [8, 8], 3, 8, 14, 'frame', { cap: 'material/soot' });
    return m;
  }
  // Fluidised-bed trichlorosilane reactor: stainless column on legs, the wider disengagement section and dome on
  // top, the cyclone, the yellow HCl inlet ring and the silicon powder feed.
  function tcsReactor() {
    const m = mk(TEX + 'ss');
    for (const [x, z] of [[3, 3], [13, 3], [3, 13], [13, 13]]) m.box(`leg_${x}_${z}`, [x - 0.6, 0, z - 0.6], [x + 0.6, 4, z + 0.6], 'frame', { split: '' });
    m.cylY('bed', [8, 8], 4, 2, 18, 'ss', {});
    m.cylY('cone', [8, 8], 5.2, 18, 20, 'ss', {});
    m.cylY('freeboard', [8, 8], 6, 20, 26, 'ss', {});
    m.cylY('dome', [8, 8], 4, 26, 28, 'ss', { cap: TEX + 'ss' });
    m.ringY('hcl_ring', [8, 8], 5, 0.8, 4, 5, 'p_gas');
    m.box('hcl_in', [0, 4, 7.4], [3, 5, 8.6], 'p_gas', { split: '' });
    m.cylY('cyclone', [14.4, 8], 1.4, 14, 24, 'ss', { cap: TEX + 'ss' });
    m.box('cyclone_in', [13.6, 22.6, 7.4], [14.6, 23.6, 8.6], 'ss', { split: '' });
    m.box('feed_hopper', [-0.4, 12, 6], [3, 16, 10], 'frame', { split: '' });
    m.box('feed_line', [3, 12.6, 7.4], [4.2, 13.6, 8.6], 'frame', { split: '' });
    m.box('platform', [0, 18, 0], [16, 18.6, 3], 'grat', { split: '' });
    return m;
  }
  // Siemens reactor: stainless bell jar with a dome on a base plate, the sight glasses (rods glowing inside while
  // running), the copper electrode feedthroughs under the base, the cooling water lines and the gas inlet.
  function siemens(running) {
    const m = mk(TEX + 'ss');
    m.box('stand', [1.4, 0, 1.4], [14.6, 3, 14.6], 'frame', { split: '' });
    for (const [x, z] of [[5, 5], [11, 5], [5, 11], [11, 11]]) m.box(`feed_${x}_${z}`, [x - 0.7, 0.4, z - 0.7], [x + 0.7, 3, z + 0.7], 'cu', { split: '' });
    m.cylY('base', [8, 8], 7, 3, 5, 'ss', { cap: TEX + 'ss' });
    m.cylY('bell', [8, 8], 6, 5, 22, 'ss', {});
    m.cylY('dome', [8, 8], 4.6, 22, 25, 'ss', {});
    m.cylY('top', [8, 8], 2.6, 25, 28, 'ss', { cap: TEX + 'ss' });
    viewport(m, 'sight_lo', 8, 10, 2.2, running);
    viewport(m, 'sight_hi', 8, 17, 2.2, running);
    for (const y of [8, 20]) m.ringY(`jacket_${y}`, [8, 8], 6.4, 0.6, y, y + 0.8, 'p_water');
    m.box('water_line', [14, 3, 7.4], [15, 21, 8.6], 'p_water', { split: 'y' });
    m.box('gas_in', [1, 3.4, 7.4], [3, 4.6, 8.6], 'p_gas', { split: '' });
    return m;
  }
  // Czochralski puller: stainless double-walled growth chamber on a frame with viewports (the melt glowing while
  // pulling), the isolation valve, the narrow pull chamber, the seed lift and rotation head, the argon line and
  // the drive from the Create shaft at the back.
  function puller(pulling) {
    const m = mk(TEX + 'ss');
    m.box('frame', [1, 0, 1], [15, 2, 15], 'frame', { split: '' });
    m.cylY('chamber', [8, 8], 6.4, 2, 12, 'ss', {});
    m.cylY('chamber_lid', [8, 8], 5.4, 12, 14, 'ss', { cap: TEX + 'ss' });
    viewport(m, 'view_n', 8, 8, 1.8, pulling);
    m.box('valve', [5.6, 14, 5.6], [10.4, 16.4, 10.4], 'ss', { split: '' });
    m.box('valve_actuator', [10.4, 14.4, 6.6], [13, 16, 9.4], 'blue', { split: '' });
    m.cylY('pull_tube', [8, 8], 2.6, 16.4, 28, 'ss', {});
    m.box('head', [5, 28, 5], [11, 31.6, 11], 'en', { split: '' });
    m.box('head_motor', [6.4, 31.6, 6.4], [9.6, 32, 9.6], 'blue', { split: '' });
    m.box('argon', [12.6, 2, 3], [13.6, 28, 4], 'p_gas', { split: 'y' });
    m.box('water_jacket', [1.2, 2, 12], [2.2, 12, 13], 'p_water', { split: '' });
    m.box('drive', [5, 2, 12.6], [11, 7, 16], 'frame', { split: '' });
    m.box('panel', [12, 4, 0.4], [15, 10, 2], 'en', { split: '' });
    lamp(m, 'lamp_run', 13.5, 8.6, 0.4, pulling ? 'l_g' : 'l_off', pulling, 0.5);
    return m;
  }
  // Wafer saw: white cabinet with a glazed hood over the inner-diameter blade and the ingot holder, the slurry
  // line, the control screen; the Create shaft drives it from the back.
  function waferSaw() {
    const m = mk(TEX + 'clean');
    m.box('cabinet', [0.4, 0, 2], [15.6, 8, 15], 'clean', { split: '' });
    m.box('hood_back', [0.4, 8, 12], [15.6, 15, 15], 'clean', { split: '' });
    m.box('hood_glass', [1, 8, 2.4], [15, 14.4, 3], 'glass', { split: '' });
    m.box('hood_top', [0.4, 14.4, 2], [15.6, 15, 12], 'clean', { split: '' });
    for (const x of [0.4, 15]) m.box(`hood_side_${x}`, [x, 8, 2], [x + 0.6, 14.4, 12], 'glass', { split: '' });
    m.prism('blade', 'z', [7, 11], 3.2, 6, 6.6, 'bright', { split: '', ends: ['north', 'south'] });
    m.prism('blade_hub', 'z', [7, 11], 1.2, 6.6, 12, 'st', { split: '' });
    m.prism('ingot', 'x', [10.6, 6.2], 1.4, 9, 15, 'gph', { split: '', ends: ['west'] });
    m.box('screen_case', [11, 9.4, 2.2], [14.4, 12.4, 2.4], 'rubber', { split: '' });
    m.box('glow_screen', [11.3, 9.7, 2.1], [14.1, 12.1, 2.2], 'screen', { split: '', hide: ['south'] });
    m.box('slurry', [1, 8, 13], [2, 14, 14], 'p_water', { split: '' });
    for (let i = 0; i < 4; i++) m.box(`button_${i}`, [2 + i * 1.6, 5, 1.6], [3 + i * 1.6, 6, 2], i === 0 ? 'red' : 'green', { split: '' });
    return m;
  }
  // Cleanroom: the white sandwich panel (full block); the window panel with its dark glass; the airlock frame with
  // the air-shower nozzles, the sliding door (gone into the wall when open) and the state lamp (green open, blue
  // purging).
  function cleanPanel() {
    const m = mk(TEX + 'clean');
    m.box('panel', [0, 0, 0], [16, 16, 16], 'clean', { split: '' });
    return Object.assign(m, { cull: true });
  }
  function cleanWindow() {
    const m = mk(TEX + 'clean');
    m.box('sill', [0, 0, 6], [16, 3, 10], 'clean', { split: '' });
    m.box('head', [0, 13, 6], [16, 16, 10], 'clean', { split: '' });
    m.box('jamb_w', [0, 3, 6], [1.6, 13, 10], 'clean', { split: '' });
    m.box('jamb_e', [14.4, 3, 6], [16, 13, 10], 'clean', { split: '' });
    m.box('glass', [1.6, 3, 7.6], [14.4, 13, 8.4], 'glass', { split: '' });
    return m;
  }
  function airlock(state) {
    const m = mk(TEX + 'clean');
    m.box('jamb_w', [0, 0, 5.4], [2, 32, 10], 'clean', { split: 'y' });
    m.box('jamb_e', [14, 0, 5.4], [16, 32, 10], 'clean', { split: 'y' });
    m.box('head', [2, 28, 5.4], [14, 32, 10], 'clean', { split: '' });
    m.box('threshold', [2, 0, 5.4], [14, 0.4, 10], 'ss', { split: '' });
    for (let y = 6; y < 26; y += 5) for (const [x, x1] of [[1.6, 2], [14, 14.4]]) m.box(`nozzle_${x}_${y}`, [x, y, 7.2], [x1, y + 1, 8.2], 'ss', { split: '' });
    if (state !== 'open') {
      m.box('door', [2, 0.4, 7.4], [14, 28, 8.6], 'clean', { split: 'y' });
      m.box('door_glass', [5, 16, 7.3], [11, 25, 8.7], 'glass', { split: '' });
      m.box('door_handle', [11.6, 12, 7], [12.2, 15, 7.4], 'ss', { split: '' });
    }
    const col = { closed: 'l_off', open: 'l_g', purging: 'l_b' }[state];
    m.box((state === 'closed' ? '' : 'glow_') + 'lamp', [7, 29.4, 5], [9, 30.6, 5.4], col, { split: '' });
    m.box('sign', [4, 29.6, 5.2], [6.4, 30.4, 5.4], 'yel', { split: '' });
    return m;
  }

  // ---------------------------------------------------------------- reagent units
  // Acid plant module: acid-tiled tank with a PVC lid and agitator, orange acid lines, a dosing pump, the small
  // control panel with run (green) and alarm (red) lamps.
  function acidPlant() {
    const m = mk('plant/acid_tile');
    m.box('skid', [0, 0, 0.4], [16, 1, 16], 'frame', { split: '' });
    m.box('tank', [1, 1, 5], [11, 13, 15], 'tile', { split: '' });
    m.box('lid', [0.6, 13, 4.6], [11.4, 14, 15.4], 'pvc', { split: '' });
    m.box('agitator', [4.6, 14, 8.6], [7.4, 17.4, 11.4], 'blue', { split: '' });
    m.box('acid_in', [2, 14, 13], [3.2, 16, 14.2], 'p_acid', { split: '' });
    m.box('acid_out', [11, 2, 9], [16, 3.2, 10.2], 'p_acid', { split: '' });
    m.box('pump', [12, 1, 10.6], [15.4, 5, 14.6], 'p_acid', { split: '' });
    m.box('panel', [11.6, 1, 0.6], [15.6, 12, 4], 'en', { split: '' });
    lamp(m, 'lamp_run', 13.6, 10, 0.6, 'l_g', true, 0.5);
    lamp(m, 'lamp_alarm', 13.6, 8.4, 0.6, 'l_off', false, 0.5);
    m.box('drip_tray', [0.6, 1, 0.6], [11, 1.4, 4.6], 'tile', { split: '' });
    return m;
  }
  // Small air separator: a blue-grey cold box with frosted cold piping, the oxygen and nitrogen outlets, the
  // intake with its louvred guard (the fan is the static partial in it), the expansion turbine housing.
  function airSeparator() {
    const m = mk(TEX + 'coldbox');
    m.box('base', [1, 0, 1], [15, 1, 16], 'conc', { split: '' });
    m.box('intake', [1.6, 1, 1], [14.4, 15, 3.4], 'frame', { split: '' });
    m.box('intake_mouth', [2.6, 2.6, 0.95], [13.4, 13.4, 1], 'soot', { split: '', hide: ['south'] });
    m.box('coldbox', [3, 1, 4], [15, 17.4, 16], 'cbox', { split: 'y' });
    m.box('cold_line', [1.2, 3, 6], [3, 17, 7.6], 'frost', { split: 'y' });
    m.box('cold_line_2', [1.2, 3, 10], [3, 15, 11.6], 'frost', { split: '' });
    m.box('o2_out', [15, 12, 6], [16, 13.4, 7.4], 'p_air', { split: '' });
    m.box('n2_out', [15, 9, 10], [16, 10.4, 11.4], 'yel', { split: '' });
    m.box('turbine', [10, 17.4, 8], [14, 19.4, 12], 'ss', { split: '' });
    return m;
  }
  function separatorFan() {
    const m = mk('material/steel');
    m.box('hub', [7, 7, 1], [9, 9, 3], 'st', { split: '' });
    for (const [n, a] of [['a', 0], ['b', 45], ['c', -45]]) m.box(`blade_${n}`, [2.6, 7.6, 1.6], [13.4, 8.4, 2.4], 'gal', { split: '', rot: a ? { axis: 'z', angle: a, origin: [8, 8, 2] } : undefined });
    m.box('blade_d', [7.6, 2.6, 1.6], [8.4, 13.4, 2.4], 'gal', { split: '' });
    for (const y of [3.4, 6, 8.6, 11.2]) m.box(`guard_${y}`, [2.6, y, 0.9], [13.4, y + 0.4, 1.2], 'bright', { split: '' });
    return m;
  }
  // Coke oven (beehive-and-door type single chamber): firebrick body between steel buckstays with tie rods, the
  // cast door with its refractory plug and glowing seams, the charging hole lid and standpipe on top.
  function cokeOven() {
    const m = mk('boilers/firebrick');
    m.box('body', [0, 0, 1], [16, 16, 16], 'fb', { split: '' });
    for (const x of [0, 14.4]) m.box(`stay_${x}`, [x, 0, 0], [x + 1.6, 18, 1.4], 'frame', { split: 'y' });
    m.box('tie', [0, 17, 0.4], [16, 17.6, 1], 'st', { split: '' });
    m.box('door', [2, 1, -0.4], [14, 14.6, 1], 'ci', { split: '' });
    for (const y of [4, 8, 12]) m.box(`door_bar_${y}`, [1.6, y, -0.8], [14.4, y + 0.6, -0.4], 'st', { split: '' });
    m.box('glow_seam_w', [1.6, 1, 0.6], [2, 14.6, 1.05], 'fglow', { split: '', hide: ['south'] });
    m.box('glow_seam_e', [14, 1, 0.6], [14.4, 14.6, 1.05], 'fglow', { split: '', hide: ['south'] });
    m.box('glow_seam_t', [2, 14.6, 0.6], [14, 15, 1.05], 'fglow', { split: '', hide: ['south'] });
    m.box('charge_lid', [5, 16, 7], [11, 16.6, 13], 'ci', { split: '' });
    m.box('standpipe', [11.6, 16, 3], [14.4, 18, 5.8], 'soot', { split: '' });
    return m;
  }

  const BUILDS = {
    submerged_arc_furnace: { model: () => arcFurnace(false), cat: 'electronics' },
    submerged_arc_furnace_running: { model: () => arcFurnace(true), cat: 'electronics' },
    trichlorosilane_reactor: { model: tcsReactor, cat: 'electronics' },
    siemens_reactor: { model: () => siemens(false), cat: 'electronics' },
    siemens_reactor_running: { model: () => siemens(true), cat: 'electronics' },
    czochralski_puller: { model: () => puller(false), cat: 'electronics' },
    czochralski_puller_pulling: { model: () => puller(true), cat: 'electronics' },
    wafer_saw: { model: waferSaw, cat: 'electronics' },
    cleanroom_panel: { model: cleanPanel, cat: 'electronics' },
    cleanroom_window: { model: cleanWindow, cat: 'electronics' },
    cleanroom_airlock: { model: () => airlock('closed'), cat: 'electronics' },
    cleanroom_airlock_open: { model: () => airlock('open'), cat: 'electronics' },
    cleanroom_airlock_purging: { model: () => airlock('purging'), cat: 'electronics' },
    acid_plant: { model: acidPlant, cat: 'reagents' },
    air_separator: { model: airSeparator, cat: 'reagents' },
    'air_separator/fan': { model: separatorFan, cat: 'reagents', java: 'air_separator/partial/fan' },
    coke_oven: { model: cokeOven, cat: 'reagents' },
  };

  function cullBoundary() {
    for (const c of Cube.all) for (const [d, a, v] of [['north', 2, 0], ['south', 2, 16], ['west', 0, 0], ['east', 0, 16], ['down', 1, 0], ['up', 1, 16]]) {
      const on = (v === 0 ? c.from[a] : c.to[a]) === v && !c.rotation.some((r) => r);
      if (on && [0, 1, 2].every((i) => c.from[i] >= 0 && c.to[i] <= 16) && c.faces[d].texture !== null) c.faces[d].cullface = d;
    }
  }
  function realize(root, id) {
    const m = BUILDS[id].model();
    PCART.realize(root, m, id);
    if (m.cull) cullBoundary();
  }
  window.SIL = {
    paint, ids: Object.keys(BUILDS), build: realize,
    preview(root, ids, dx) {
      const m = BUILDS[ids[0]].model();
      ids.slice(1).forEach((id, i) => m.merge(BUILDS[id].model(), (i + 1) * dx, 0, 0, `m${i}_`));
      return PCART.realize(root, m, 'preview');
    },
    async exportAll(root, ids) {
      const out = {};
      for (const id of ids || Object.keys(BUILDS)) {
        ModelProject.all.slice().forEach((p) => p.close(true));
        realize(root, id);
        await new Promise((r) => setTimeout(r, 1200));
        out[id] = PCART.save(root, BUILDS[id].cat, id, BUILDS[id].java);
      }
      return out;
    },
  };
})();
