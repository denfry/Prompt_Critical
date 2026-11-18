// Plant utilities, hand-authored for Blockbench (replaces the spec_*.py placeholders): air separation (main
// compressor and its coupling, expander and its coupling, molecular sieve adsorbers, cryogenic tank and
// vaporiser), water treatment (deaerator, sand filter, clarifier and its rake), flue gas (CEMS analyser,
// electrostatic precipitator, FGD absorber), flare stack, glycol regenerator, wet drum magnetic separator with its
// drum, cooling tower cell, surface condenser, turbine gearbox.
// Load inside Blockbench desktop, after the shared toolkit:
//   eval(require('fs').readFileSync('<repo>/art/blockbench/lib/pcart.js', 'utf8'))
//   eval(require('fs').readFileSync('<repo>/art/blockbench/lib/pcround.js', 'utf8'))
//   eval(require('fs').readFileSync('<repo>/art/blockbench/lib/plantkit.js', 'utf8'))
//   eval(require('fs').readFileSync('<repo>/art/blockbench/water_treatment/plant_utilities.author.js', 'utf8'))
//   PCART.paintMaterials('<repo>'); PLANT.paint('<repo>'); UTL.paint('<repo>')
//   await UTL.exportAll('<repo>')
//
// Front to the north. Contracts: magnetic_separator/partial/drum spins about x through (y 10, z 8)
// (FoundryMachineRenderer). Static multipart parts: asu_main_compressor + partial/rotor, asu_expander +
// partial/rotor, water_clarifier + partial/rake. Footprints as the placeholders: main compressor 3x2x2
// (x -16..32, z 0..32), deaerator and glycol regenerator 3x2x1, clarifier, precipitator and cooling cell 3-wide
// round the anchor, magnetic separator 2x1x1; the tall single blocks rise to 31.
(function () {
  const path = require('path');
  const { rng, img, put, get, add, mix, rect, vnoise, grain, writePng } = PCART;
  const TEX = 'utilities/';
  const T = {};

  T.cryo_white = () => { const im = img(32, 32), r = grain(im, [226, 228, 226], 12101, 2, 3, 8); for (let x = 0; x < 32; x += 16) for (let y = 0; y < 32; y++) put(im, x, y, [200, 202, 200]); return im; };
  T.fill = () => { const im = img(16, 16); for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) put(im, x, y, (x + (y >> 1)) % 4 === 0 ? [210, 214, 210] : [70, 84, 80]); return im; };
  T.water = () => { const im = img(32, 32), r = rng(12111); for (let y = 0; y < 32; y++) for (let x = 0; x < 32; x++) put(im, x, y, add(mix([60, 104, 120], [110, 150, 160], (Math.sin(x * 0.6 + y * 0.2) + 1) / 2), (r() - 0.5) * 6)); return im; };
  T.gypsum = () => { const im = img(32, 32), r = grain(im, [196, 192, 180], 12121, 4, 5, 8); for (let k = 0; k < 6; k++) { const x = Math.floor(r() * 32); for (let y = 0; y < 32; y++) if (r() < 0.6) put(im, x, y, add(get(im, x, y), -12)); } return im; };
  T.red_white = () => { const im = img(16, 16); for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) put(im, x, y, Math.floor(y / 8) ? [230, 230, 226] : [196, 40, 32]); return im; };
  T.display = () => { const im = img(16, 16), r = rng(12131); rect(im, 0, 0, 16, 16, [14, 22, 30]); for (let y = 3; y < 14; y += 3) for (let x = 2; x < 4 + Math.floor(r() * 10); x++) put(im, x, y, [120, 200, 255]); return im; };
  T.hv_sign = () => { const im = img(16, 16); rect(im, 0, 0, 16, 16, [236, 236, 230]); for (let y = 2; y < 14; y++) for (let x = 8 - (y - 2) * 0.5; x <= 8 + (y - 2) * 0.5; x++) put(im, x, y, [240, 200, 30]); for (const [x, y] of [[8, 5], [7, 6], [7, 7], [8, 8], [9, 8], [8, 9], [8, 10], [7, 11]]) put(im, x, y, [20, 20, 20]); return im; };

  const MAT = Object.assign({}, PLANT.MAT, {
    cwhite: { tex: TEX + 'cryo_white', P: 16 }, fill: { tex: TEX + 'fill', P: 8 }, wat: { tex: TEX + 'water', P: 16 },
    gyp: { tex: TEX + 'gypsum', P: 16 }, rw: { tex: TEX + 'red_white', P: 16 },
    ss: { tex: 'electronics/ss', P: 16 }, frost: { tex: 'electronics/frost', P: 16 }, glass: { tex: 'electronics/glass', P: 16 },
    fglow: { tex: 'rolling/furnace_glow', P: 16 }, pulp: { tex: 'fuel/pulp', P: 16 }, en: { tex: 'switchgear_cell/enamel', P: 16 },
    tx: { tex: 'distribution_transformer/enamel', P: 16 }, fin: { tex: 'distribution_transformer/fins', P: 16 },
    grat: { tex: 'steelmaking/grating', P: 8 }, louv: { tex: 'grid_lines/louvre', P: 16 }, frpg: { tex: 'fuel/frp_green', P: 16 },
    porb: { tex: 'disconnector/porcelain', P: 16 },
    l_g: { tex: 'grid_lines/lamp_green', P: 16 }, l_r: { tex: 'grid_lines/lamp_red', P: 16 }, l_a: { tex: 'grid_lines/lamp_amber', P: 16 },
    l_off: { tex: 'grid_lines/lamp_off', P: 16 },
  });
  const mk = (particle) => Object.assign(new PCART.Model(MAT, {}), { particle });
  function paint(root) {
    for (const n of Object.keys(T)) writePng(T[n](), path.join(root, PCART.ASSETS, 'textures/block', TEX + n + '.png'));
    return Object.keys(T).length;
  }
  const P = (m, n, f, t, mat) => m.box(n, f, t, mat, { split: '' });
  function lamp(m, n, x, y, z, mat, glow, r) {
    const k = r || 0.6;
    m.box(`${n}_bezel`, [x - k - 0.2, y - k - 0.2, z - 0.15], [x + k + 0.2, y + k + 0.2, z], 'rubber', { split: '' });
    m.box((glow ? 'glow_' : '') + n, [x - k, y - k, z - 0.35], [x + k, y + k, z - 0.15], mat, { split: '', hide: ['south'] });
  }
  function ladder(m, n, x, z, y0, y1) {
    for (const dx of [0, 2.2]) m.box(`${n}_rail_${dx}`, [x + dx, y0, z], [x + dx + 0.4, y1, z + 0.4], 'yel', { split: '' });
    for (let y = y0 + 1; y < y1; y += 2) m.box(`${n}_rung_${y}`, [x, y, z], [x + 2.6, y + 0.3, z + 0.4], 'yel', { split: '' });
  }

  // ---------------------------------------------------------------- air separation
  // Main air compressor package: a blue motor to the west, the integral gear casing, three volute stages,
  // two intercoolers below, the inlet filter house and the blue air lines; the rotor part is the coupling between
  // motor and gear casing.
  function mainCompressor() {
    const m = mk('plant/blue');
    m.box('skid', [-16, 0, 0], [32, 2, 32], 'frame', { split: 'xz' });
    m.box('motor', [-16, 2, 8], [-4, 22, 24], 'blue', { split: 'y' });
    m.box('motor_fan', [-16, 22, 10], [-6, 26, 22], 'blue', { split: '' });
    m.box('gear_casing', [2, 2, 8], [20, 22, 24], 'frame', { split: 'y' });
    for (const [i, x, y] of [[0, 22, 18], [1, 22, 8], [2, 10, 26]]) {
      if (i < 2) m.prism(`volute_${i}`, 'x', [y, 16], 5, x, x + 6, 'st', { split: '', ends: ['east'] });
      else m.prismY(`volute_${i}`, [x, 16], 5, 22, 28, 'st', { ends: ['up'] });
    }
    for (const z of [3, 29]) m.prism(`cooler_${z}`, 'x', [5, z], 3, 0, 30, 'p_water', { split: '', ends: ['east', 'west'] });
    m.box('filter_house', [24, 2, 0], [32, 16, 8], 'light', { split: '', face: { north: 'louv' } });
    P(m, 'air_main', [6, 28, 15], [32, 30.4, 17.4], 'p_air');
    P(m, 'air_drop', [29.6, 16, 15], [32, 28, 17.4], 'p_air');
    m.box('guard', [-4, 8, 12], [2, 17, 20], 'yel', { split: '', hide: ['up'] });
    return m;
  }
  function mainCoupling() {
    const m = mk('material/steel');
    m.prism('shaft', 'x', [12, 16], 1.2, -4, 2, 'bright', { split: '' });
    m.prism('hub', 'x', [12, 16], 2.4, -1.4, -0.6, 'st', { split: '', ends: ['east', 'west'] });
    return m;
  }
  // Turbo-expander: the frosted expander volute on its cold end, the bearing housing, the blue brake compressor;
  // the rotor part is the shaft coupling seen between them.
  function expander() {
    const m = mk('electronics/frost');
    m.box('base', [0, 0, 0.6], [16, 2, 15.4], 'frame', { split: '' });
    m.prism('cold_volute', 'z', [8, 8], 5.4, 9.6, 15, 'frost', { split: '', ends: ['south'] });
    P(m, 'cold_in', [11, 6.4, 11], [16, 9.6, 14], 'frost');
    m.box('bearing', [5, 2, 4.6], [11, 11, 9.6], 'frame', { split: '' });
    m.prism('brake', 'z', [8, 8], 5, 0, 3.2, 'blue', { split: '', ends: ['north'] });
    P(m, 'brake_out', [0, 9, 0.4], [3, 12, 3], 'p_air');
    P(m, 'oil_line', [11, 10, 6], [16, 11, 7], 'p_liq');
    return m;
  }
  function expanderCoupling() {
    const m = mk('material/steel');
    m.prism('shaft', 'z', [8, 8], 0.9, 3.2, 4.6, 'bright', { split: '' });
    m.prism('hub', 'z', [8, 8], 1.8, 3.6, 4.2, 'st', { split: '', ends: ['north', 'south'] });
    return m;
  }
  // Molecular sieve unit: two vertical adsorbers with the switching valve nest between, the regeneration heater
  // and the air lines.
  function molsieve() {
    const m = mk('plant/light');
    m.box('base', [0, 0, 1.6], [16, 1, 14.4], 'conc', { split: '' });
    for (const x of [4, 12]) {
      m.cylY(`vessel_${x}`, [x, 9], 3.4, 1, 26, 'light', {});
      m.cylY(`head_${x}`, [x, 9], 2.4, 26, 28, 'light', { cap: 'plant/light' });
    }
    for (const y of [4, 24]) P(m, `manifold_${y}`, [1, y, 3], [15, y + 1.4, 4.4], 'p_air');
    for (const [x, y] of [[4, 4], [12, 4], [4, 24], [12, 24]]) m.box(`valve_${x}_${y}`, [x - 1, y - 0.6, 2.2], [x + 1, y + 2, 3], 'blue', { split: '' });
    m.box('heater', [6.4, 1, 2], [9.6, 14, 5], 'en', { split: 'y' });
    return m;
  }
  // Cryogenic tank: a white vacuum-insulated vertical tank on legs, the frosted bottom piping and pressure
  // building coil, the gauge panel; the vaporiser variant adds the finned ambient vaporiser beside it.
  function cryoTank(vap) {
    const m = mk(TEX + 'cryo_white');
    for (const [x, z] of [[4, 3], [12, 3], [4, 13], [12, 13]]) P(m, `leg_${x}_${z}`, [x - 0.6, 0, z - 0.6], [x + 0.6, 4, z + 0.6], 'frame');
    m.cylY('tank', [8, 8], 6.2, 4, 28, 'cwhite', {});
    m.cylY('head', [8, 8], 4.4, 28, 31, 'cwhite', { cap: TEX + 'cryo_white' });
    P(m, 'coil', [3, 1, 1], [13, 3, 2.4], 'frost');
    P(m, 'riser', [11.6, 3, 1.4], [12.8, 16, 2.6], 'frost');
    m.box('gauge_panel', [5, 14, 1.4], [10, 18, 2], 'ss', { split: '' });
    m.plate('gauge', [5.6, 14.6, 1.3], [9.4, 17.4, 1.4], 'ss', 'north', 'control/pressure_dial', { hide: ['south'] });
    if (vap) for (let i = 0; i < 4; i++) {
      const x = 0.4 + i * 1.2;
      m.box(`fin_${i}`, [x, 0, 9], [x + 0.6, 26, 15.6], 'frost', { split: 'y' });
    }
    return m;
  }

  // ---------------------------------------------------------------- water treatment
  // Deaerator: the horizontal storage tank on saddles with the deaerating column standing on it, the steam
  // (red) and feed water (green) lines, the vent, the relief valve and a ladder.
  function deaerator() {
    const m = mk('plant/cladding');
    for (const x of [-10, 22]) P(m, `saddle_${x}`, [x, 0, 2], [x + 4, 6, 14], 'frame');
    m.prism('tank', 'x', [11, 8], 6.4, -14, 30, 'clad', { split: '' });
    for (const x of [-16, 30]) m.prism(`head_${x}`, 'x', [11, 8], 5, x, x + 2, 'clad', { split: '', ends: [x < 0 ? 'west' : 'east'] });
    m.cylY('column', [16, 8], 4.4, 16, 30, 'clad', {});
    m.cylY('column_top', [16, 8], 3, 30, 31.9, 'clad', { cap: 'plant/cladding' });
    P(m, 'steam_in', [-6, 16, 7], [-4, 22, 9], 'p_steam'); P(m, 'steam_run', [-6, 20, 7], [12, 22, 9], 'p_steam');
    P(m, 'feed_out', [2, 0, 7], [4, 5, 9], 'p_water'); P(m, 'water_in', [20, 26, 7], [26, 27.6, 8.6], 'p_water');
    P(m, 'vent', [15.4, 32, 7.4], [16.6, 32, 8.6], 'st');
    m.box('relief', [24, 17, 6.6], [26.4, 20, 9.4], 'red', { split: '' });
    ladder(m, 'ladder', -13, 1, 0, 18);
    return m;
  }
  // Pressure sand filter (1x2): a vertical vessel with the face piping of five valves on the north, gauges and
  // the backwash lamp (amber while backwashing).
  function sandFilter(back) {
    const m = mk('plant/green');
    for (const [x, z] of [[3, 3], [12, 3], [3, 12], [12, 12]]) P(m, `leg_${x}_${z}`, [x, 0, z], [x + 1, 3, z + 1], 'frame');
    m.cylY('vessel', [8, 9], 6, 3, 27, 'green', {});
    m.cylY('dome', [8, 9], 4, 27, 30, 'green', { cap: 'plant/green' });
    P(m, 'face_run', [7.2, 4, 1], [8.8, 26, 2.6], 'p_water');
    for (const y of [6, 11, 16, 21, 25]) m.box(`valve_${y}`, [6.4, y, 0.4], [9.6, y + 1.6, 3], 'blue', { split: '' });
    P(m, 'inlet', [0, 24, 1], [7.2, 25.6, 2.6], 'p_water'); P(m, 'outlet', [8.8, 6, 1], [16, 7.6, 2.6], 'p_water');
    for (const y of [9, 19]) m.plate(`gauge_${y}`, [11, y, 2.3], [13, y + 2, 2.4], 'frame', 'north', 'control/pressure_dial', { hide: ['south'] });
    lamp(m, 'lamp_backwash', 4, 28, 3, back ? 'l_a' : 'l_off', back, 0.7);
    return m;
  }
  // Clarifier: a round concrete basin with the water surface, the bridge across it with the centre drive and the
  // peripheral launder; the rake part is the centre shaft and the rake arms under the water.
  function clarifier() {
    const m = mk('plant/concrete');
    m.cylY('basin', [8, 8], 22, 0, 16, 'conc', {});
    // water surface: a regular octagon of four slabs inside the basin wall
    const h = 20.6 * Math.tan(Math.PI / 8), only = ['down', 'north', 'south', 'east', 'west'];
    m.box('water_a', [8 - 20.6, 15, 8 - h], [8 + 20.6, 15.2, 8 + h], 'wat', { split: '', hide: only });
    m.box('water_b', [8 - h, 15.01, 8 - 20.6], [8 + h, 15.21, 8 + 20.6], 'wat', { split: '', hide: only });
    for (const [k, a, d] of [['c', 45, 0.02], ['d', -45, 0.03]]) m.box(`water_${k}`, [8 - 20.6, 15 + d, 8 - h], [8 + 20.6, 15.2 + d, 8 + h], 'wat', { split: '', hide: only, rot: { axis: 'y', angle: a, origin: [8, 8, 8] } });
    m.ringY('launder', [8, 8], 21, 1.4, 14, 16.2, 'conc');
    m.box('bridge', [-16, 18, 6], [32, 19.6, 10], 'grat', { split: 'x' });
    for (const z of [5.6, 10]) P(m, `rail_${z}`, [-16, 19.6, z], [32, 21.6, z + 0.4], 'yel');
    m.box('drive', [4, 19.6, 4], [12, 22, 12], 'blue', { split: '' });
    m.cylY('feed_well', [8, 8], 4, 10, 18, 'st', {});
    return m;
  }
  function clarifierRake() {
    const m = mk('material/steel');
    P(m, 'shaft', [7.4, 3, 7.4], [8.6, 18, 8.6], 'st');
    P(m, 'arm_w', [-14, 3, 7.4], [8, 4.4, 8.6], 'st');
    P(m, 'arm_e', [8, 3, 7.4], [30, 4.4, 8.6], 'st');
    for (const x of [-10, -4, 2, 14, 20, 26]) P(m, `blade_${x}`, [x, 2.4, 6.6], [x + 0.6, 3.6, 9.4], 'st');
    return m;
  }

  // ---------------------------------------------------------------- flue gas
  // CEMS analyser cabinet on the wall: display, sample line and the lamps (green on, red on alarm).
  function cems(state) {
    const m = mk('switchgear_cell/enamel');
    m.box('cabinet', [2, 2, 9.6], [14, 14, 16], 'en', { split: '' });
    m.box('door', [2.4, 2.4, 9.4], [13.6, 13.6, 9.6], 'en', { split: '' });
    m.box('glow_display', [4, 8, 9.2], [12, 12, 9.4], 'en', { split: '', face: { north: { tex: TEX + 'display', uv: [0, 0, 16, 16] } }, hide: ['south'] });
    lamp(m, 'lamp_on', 5, 5, 9.4, state !== 'off' ? 'l_g' : 'l_off', state !== 'off', 0.6);
    lamp(m, 'lamp_alarm', 8, 5, 9.4, state === 'alarm' ? 'l_r' : 'l_off', state === 'alarm', 0.6);
    P(m, 'sample_line', [12, 14, 12], [13, 16, 13], 'p_gas');
    return m;
  }
  // Electrostatic precipitator: a RAL 7032 casing round the anchor on columns, four pyramid hoppers below, the
  // inlet and outlet ducts, the transformer-rectifier sets on the roof with HV signs and the rapper drives; the
  // HV lamp glows red while energised.
  function esp(on) {
    const m = mk('distribution_transformer/enamel');
    for (const x of [-15, 30]) for (const z of [-15, 30]) P(m, `col_${x}_${z}`, [x, -16, z], [x + 1, -4, z + 1], 'frame');
    for (const x of [-12, 8]) for (const z of [-12, 8]) m.box(`hopper_${x}_${z}`, [x + 2, -12, z + 2], [x + 18, -4, z + 18], 'tx', { split: '' });
    m.box('casing', [-15, -4, -15], [31, 22, 31], 'tx', { split: 'xyz' });
    m.box('roof', [-15.6, 22, -15.6], [31.6, 23, 31.6], 'frame', { split: 'xz' });
    m.box('inlet', [-16, 0, -6], [-15, 18, 22], 'tx', { split: 'z' });
    m.box('outlet', [31, 0, -6], [32, 18, 22], 'tx', { split: 'z' });
    for (const x of [-8, 16]) {
      m.box(`trset_${x}`, [x, 23, -4], [x + 8, 29, 4], 'fin', { split: '' });
      m.prismY(`bushing_${x}`, [x + 4, 8], 0.8, 13, 16, 'porb', {});
      m.els.slice(-8).forEach((e) => { e.from[1] += 16; e.to[1] += 16; });
      m.plate(`hv_sign_${x}`, [x + 2, 24, -4.1], [x + 6, 28, -4], 'tx', 'north', TEX + 'hv_sign', { hide: ['south'] });
    }
    for (const x of [-10, 2, 14, 26]) P(m, `rapper_${x}`, [x, 23, 18], [x + 3, 26, 22], 'blue');
    lamp(m, 'lamp_hv', 8, 18, -15, on ? 'l_r' : 'l_off', on, 1);
    return m;
  }
  // FGD absorber base: the slurry tank of the spray tower in gypsum-streaked FRP, recirculation pumps, oxidation
  // air blower and the side-entry agitator drives.
  function fgd() {
    const m = mk(TEX + 'gypsum');
    m.box('pad', [-16, 0, -14], [30, 0.6, 30], 'conc', { split: 'xz' });
    m.cylY('tank', [7, 8], 14, 0.6, 31.9, 'gyp', { cap: 'material/steel' });
    for (const x of [-12, 24]) { P(m, `pump_${x}`, [x - 3, 0.6, -14], [x + 3, 6, -8], 'blue'); P(m, `pump_line_${x}`, [x - 1, 6, -10], [x + 1, 24, -8], 'frpg'); }
    for (const z of [2, 14]) P(m, `agitator_${z}`, [-12, 6, z], [-6.6, 11, z + 4], 'blue');
    m.box('blower', [20, 0.6, 20], [30, 8, 30], 'blue', { split: '' });
    P(m, 'air_line', [18, 4, 22], [20, 6, 24], 'p_air');
    ladder(m, 'ladder', 6, -7.6, 0.6, 31);
    return m;
  }
  // Flare stack base: concrete foundation, the red-and-white stack, the pilot gas line, the knockout drain.
  function flare() {
    const m = mk(TEX + 'red_white');
    m.box('foundation', [2, 0, 2], [14, 2, 14], 'conc', { split: '' });
    m.box('base_plate', [4, 2, 4], [12, 3, 12], 'frame', { split: '' });
    m.prismY('stack', [8, 8], 3, 3, 16, 'rw', { tex: TEX + 'red_white' });
    P(m, 'pilot', [11, 3, 7.4], [12, 16, 8.4], 'p_gas');
    P(m, 'drain', [7.4, 2, 2], [8.6, 3, 4], 'frame');
    return m;
  }
  // Glycol regenerator: the fire-tube reboiler on saddles with the still column on top, the burner front
  // (flame window glowing while running), the surge tank below, the stack.
  function glycol(running) {
    const m = mk('plant/structure');
    m.box('skid', [-16, 0, 0], [32, 1.4, 16], 'frame', { split: 'x' });
    m.prism('reboiler', 'x', [9, 8], 6, -12, 26, 'clad', { split: '' });
    m.box('burner_front', [-16, 3, 2], [-12, 15, 14], 'frame', { split: '' });
    m.box((running ? 'glow_' : '') + 'window', [-16.0, 7, 6.6], [-15.9, 10, 9.4], running ? 'fglow' : 'soot', { split: '' });
    m.cylY('still', [8, 8], 2.6, 15, 30, 'clad', { cap: 'plant/cladding' });
    m.prism('surge', 'x', [3, 8], 2.4, 0, 22, 'frame', { split: '' });
    m.cylY('stack', [22, 8], 2, 15, 32, 'frame', {});
    P(m, 'glycol_in', [10, 26, 7.4], [16, 27.4, 8.6], 'p_liq');
    P(m, 'gas_out', [7.4, 30, 7.4], [8.6, 32, 8.6], 'p_gas');
    return m;
  }

  // ---------------------------------------------------------------- separator, cooling, turbine aux
  // Wet drum magnetic separator: the tank under the drum with the pulp, the feed box on the north, concentrate
  // and tailings launders, the drive at the east end; the drum is the part that spins.
  function magSep() {
    const m = mk('plant/structure');
    m.box('tank', [0, 0, 1], [32, 7, 14], 'frame', { split: 'x' });
    m.box('pulp', [0.6, 6.8, 2], [31.4, 7, 13], 'pulp', { split: 'x', hide: ['down', 'north', 'south', 'east', 'west'] });
    m.box('feed_box', [2, 7, 0], [30, 12, 2.4], 'frame', { split: 'x' });
    m.box('conc_launder', [2, 4, 14], [30, 7, 16], 'st', { split: 'x' });
    for (const x of [0, 31]) P(m, `bearing_${x}`, [x, 7, 6], [x + 1, 12, 10], 'ci');
    m.box('drive', [26, 12, 6], [31, 16, 10], 'blue', { split: '' });
    return m;
  }
  function magDrum() {
    const m = mk('electronics/ss');
    m.prism('drum', 'x', [10, 8], 5.4, 1, 31, 'ss', { split: '', ends: ['east', 'west'] });
    m.prism('shaft', 'x', [10, 8], 0.8, 0, 32, 'bright', { split: '' });
    for (const x of [8, 16, 24]) m.ring(`band_${x}`, 'x', [10, 8], 5.6, 0.4, x, x + 0.6, 'blue');
    return m;
  }
  // Cooling tower cell: a concrete frame round the anchor with the air inlet louvres below, the fill behind them,
  // the fan deck with the FRP fan stack and the fan in it, the drift eliminators, a stair; iced: frost on the
  // louvres.
  function coolingCell(iced) {
    const m = mk('plant/concrete');
    m.box('basin', [-16, -16, -16], [32, -12, 32], 'conc', { split: 'xz' });
    for (const x of [-16, 30]) for (const z of [-16, 30]) P(m, `col_${x}_${z}`, [x, -12, z], [x + 2, 16, z + 2], 'conc');
    for (const [n, f, t] of [['n', [-14, -12, -15.6], [30, 2, -15]], ['s', [-14, -12, 31], [30, 2, 31.6]]]) m.box(`louvre_${n}`, f, t, iced ? 'frost' : 'louv', { split: 'xy' });
    m.box('fill', [-14, -12, -15], [30, 2, 31], 'fill', { split: 'xyz', hide: ['up', 'down'] });
    m.box('walls', [-16, 2, -16], [32, 16, 32], 'frpg', { split: 'xyz', hide: ['down'] });
    m.box('deck', [-16, 16, -16], [32, 17, 32], 'grat', { split: 'xz' });
    m.cylY('fan_stack', [8, 8], 12, 17, 28, 'frpg', {});
    m.box('fan_hub', [6, 21, 6], [10, 23, 10], 'frame', { split: '' });
    for (const [n, a] of [['a', 45], ['b', -45]]) m.box(`fan_${n}`, [-3, 22, 7.4], [19, 22.6, 8.6], 'frpg', { split: '', rot: { axis: 'y', angle: a, origin: [8, 8, 8] } });
    m.box('motor', [24, 17, 6], [30, 21, 10], 'blue', { split: '' });
    return m;
  }
  // Surface condenser block: shell with the steam inlet (red) from above, the water boxes at both ends (green)
  // with their inlet and outlet nozzles, the hotwell below.
  function condenser() {
    const m = mk('plant/structure');
    m.box('shell', [1, 2, 1], [15, 14, 15], 'frame', { split: '' });
    m.box('steam_in', [3, 14, 3], [13, 16, 13], 'p_steam', { split: '' });
    for (const x of [-0.5, 15]) m.box(`water_box_${x}`, [Math.max(0, x), 3, 2], [Math.max(0, x) + 1, 13, 14], 'p_water', { split: '' });
    P(m, 'cw_in', [0.4, 4, 0], [2, 7, 2], 'p_water'); P(m, 'cw_out', [14, 9, 0], [15.6, 12, 2], 'p_water');
    m.box('hotwell', [4, 0, 4], [12, 2, 12], 'frame', { split: '' });
    return m;
  }
  // Turbine reduction gearbox: split casing with the inspection cover, input and output shaft ends with their
  // couplings, the oil lines.
  function gearbox() {
    const m = mk('plant/structure');
    m.box('casing_lo', [1, 0, 1.6], [15, 8, 14.4], 'frame', { split: '' });
    m.box('casing_hi', [1.6, 8, 2.2], [14.4, 14, 13.8], 'frame', { split: '' });
    m.box('split_flange', [0.6, 7.6, 1.2], [15.4, 8.4, 14.8], 'ci', { split: '' });
    m.box('cover', [5, 14, 5], [11, 15, 11], 'ci', { split: '' });
    m.prism('shaft_in', 'x', [8, 8], 1.6, -1, 1, 'bright', { split: '', ends: ['west'] });
    m.prism('shaft_out', 'x', [8, 8], 1.4, 15, 16.6, 'bright', { split: '', ends: ['east'] });
    P(m, 'oil_in', [12, 14, 12], [13, 16, 13], 'p_liq');
    return m;
  }

  const BUILDS = {
    asu_main_compressor: { model: mainCompressor, cat: 'industrial_gases' },
    'asu_main_compressor/rotor': { model: mainCoupling, cat: 'industrial_gases', java: 'asu_main_compressor/partial/rotor' },
    asu_expander: { model: expander, cat: 'industrial_gases' },
    'asu_expander/rotor': { model: expanderCoupling, cat: 'industrial_gases', java: 'asu_expander/partial/rotor' },
    asu_molsieve: { model: molsieve, cat: 'industrial_gases' },
    cryogenic_tank: { model: () => cryoTank(false), cat: 'industrial_gases' },
    cryogenic_tank_vaporizer: { model: () => cryoTank(true), cat: 'industrial_gases' },
    deaerator: { model: deaerator, cat: 'water_treatment' },
    sand_filter: { model: () => sandFilter(false), cat: 'water_treatment' },
    sand_filter_backwash: { model: () => sandFilter(true), cat: 'water_treatment' },
    water_clarifier: { model: clarifier, cat: 'water_treatment' },
    'water_clarifier/rake': { model: clarifierRake, cat: 'water_treatment', java: 'water_clarifier/partial/rake' },
    cems_analyzer: { model: () => cems('off'), cat: 'flue_gas' }, cems_analyzer_on: { model: () => cems('on'), cat: 'flue_gas' },
    cems_analyzer_alarm: { model: () => cems('alarm'), cat: 'flue_gas' },
    electrostatic_precipitator: { model: () => esp(false), cat: 'flue_gas' },
    electrostatic_precipitator_energized: { model: () => esp(true), cat: 'flue_gas' },
    fgd_absorber: { model: fgd, cat: 'flue_gas' },
    flare_stack: { model: flare, cat: 'flare' },
    glycol_regenerator: { model: () => glycol(false), cat: 'gas_processing' },
    glycol_regenerator_running: { model: () => glycol(true), cat: 'gas_processing' },
    magnetic_separator: { model: magSep, cat: 'coal_prep' },
    'magnetic_separator/drum': { model: magDrum, cat: 'coal_prep', java: 'magnetic_separator/partial/drum' },
    cooling_tower_cell: { model: () => coolingCell(false), cat: 'cooling' },
    cooling_tower_cell_iced: { model: () => coolingCell(true), cat: 'cooling' },
    condenser: { model: condenser, cat: 'turbine' },
    turbine_gearbox: { model: gearbox, cat: 'turbine' },
  };

  window.UTL = {
    paint, ids: Object.keys(BUILDS),
    build: (root, id) => PCART.realize(root, BUILDS[id].model(), id),
    preview(root, ids, dx) {
      const m = BUILDS[ids[0]].model();
      ids.slice(1).forEach((id, i) => m.merge(BUILDS[id].model(), (i + 1) * dx, 0, 0, `m${i}_`));
      return PCART.realize(root, m, 'preview');
    },
    async exportAll(root, ids) {
      const out = {};
      for (const id of ids || Object.keys(BUILDS)) {
        ModelProject.all.slice().forEach((p) => p.close(true));
        PCART.realize(root, BUILDS[id].model(), id);
        out[id] = PCART.save(root, BUILDS[id].cat, id, BUILDS[id].java);
      }
      return out;
    },
  };
})();
