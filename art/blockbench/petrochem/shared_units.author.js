// Shared process units of the GDD registry (rows marked as cross-milestone) not yet in the mod, hand-authored
// (replaces the spec_oilgas.py / spec_petrochem.py placeholders): BOP ram variants, helium purifier, pig launcher
// and receiver with their closures, adsorber vessel, coal gasifier segments, tube furnace controller and radiant
// section, batch reactor and agitator, polymer extruder with its dies and screw, coke drum segments and their
// deheaders and cutting stem, fixed-bed reactor; and the M2 wind pump with its wheel and pump rod.
// Run headless: node art/tools/pcart_headless.js <repo> petrochem/shared_units.author.js SHU --paint
//
// Front to the north. Pivots as the placeholders: pig trap closure on its hinge at x -16 about y; batch agitator
// about y through (8, 8); extruder screw about x through (y 9, z 8); coke top deheader about x at the hatch edge,
// bottom deheader slides in x, cutting stem in y and about y through (8, 8); wind pump wheel about z through
// (8, 8) on the top segment, pump rod in y.
(function () {
  const { rng, img, put, get, add, mix, rect, grain } = PCART;
  const { P, lamp, cabinet, vessel, drum, flange, motor, gauge, ladder } = FUTK;
  const D = 'shared/', T = {};

  T.enamel_blue = FUTK.enamel([46, 84, 150], 42101, 0);
  T.heater_bands = () => { const im = img(16, 16), r = rng(42102); for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) put(im, x, y, add(x % 4 < 3 ? [150, 130, 90] : [60, 60, 62], (r() - 0.5) * 8)); return im; };
  T.coil = () => { const im = img(16, 16), r = rng(42103); for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) put(im, x, y, y % 4 < 2 ? add([90, 70, 60], (r() - 0.5) * 12) : [40, 30, 28]); return im; };
  T.coil_hot = () => { const im = img(16, 16), r = rng(42104); for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) put(im, x, y, y % 4 < 2 ? add([230, 110, 40], (r() - 0.5) * 30) : [90, 30, 20]); return im; };
  T.catalyst_tubes = () => { const im = img(16, 16), r = rng(42105); for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) put(im, x, y, x % 4 < 2 ? add([120, 110, 100], (r() - 0.5) * 10) : [40, 30, 28]); return im; };
  T.catalyst_hot = () => { const im = img(16, 16), r = rng(42106); for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) put(im, x, y, x % 4 < 2 ? add([236, 120, 50], (r() - 0.5) * 30) : [90, 30, 20]); return im; };
  T.coke = () => { const im = img(16, 16); grain(im, [40, 40, 42], 42107, 6, 6, 4); return im; };
  T.wood_vane = () => { const im = img(16, 16), r = rng(42108); for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) put(im, x, y, add([150, 112, 70], Math.sin(x * 1.4) * 6 + (r() - 0.5) * 10)); return im; };

  const MATX = {
    enb: { tex: D + 'enamel_blue', P: 16 }, hband: { tex: D + 'heater_bands', P: 8 }, coil: { tex: D + 'coil', P: 16 }, coilh: { tex: D + 'coil_hot', P: 16 },
    ctub: { tex: D + 'catalyst_tubes', P: 16 }, ctubh: { tex: D + 'catalyst_hot', P: 16 }, coke: { tex: D + 'coke', P: 16 }, vane: { tex: D + 'wood_vane', P: 16 },
  };
  const mk = (p) => FUTK.mk(MATX, p);
  const paint = (root) => FUTK.paintSet(root, D, T);

  // ---------------------------------------------------------------- drilling and pipelines
  function bopRam(kind, closed) {
    const m = mk('oil/rig_red');
    P(m, 'body', [3, 1, 3], [13, 15, 13], 'rigred');
    for (const y of [0, 15]) P(m, 'flange' + y, [2.4, y - 0.03, 2.4], [13.6, y + 1.03, 13.6], 'ci');
    const out = closed ? 2.7 : 4.2;
    for (const s of [-1, 1]) {
      m.prism('bonnet' + s, 'x', [8, 8], 4, s < 0 ? 0 : 13, s < 0 ? 3 : 16, 'rigred', { split: '' });
      m.prism('cyl' + s, 'x', [8, 8], 2.6, s < 0 ? -out : 16, s < 0 ? 0 : 16 + out, 'rigred', { split: '', ends: [s < 0 ? 'west' : 'east'] });
      P(m, 'lock' + s, s < 0 ? [-out - 0.4, 7.4, 7.4] : [16 + out, 7.4, 7.4], s < 0 ? [-out, 8.6, 8.6] : [16 + out + 0.4, 8.6, 8.6], 'bright');
    }
    P(m, 'tag', [6, 10, 2.8], [10, 13, 3], kind === 'blind' ? 'p_air' : kind === 'shear' ? 'haz' : 'wh');
    return m;
  }
  function pigTrap(receiver) {
    const m = mk('m3grid/tank');
    for (const x of [-12, 4, 22]) P(m, 'support' + x, [x, 0, 5], [x + 3, 4, 11], 'frame');
    m.prism('barrel', 'x', [9, 8], 5, -15, 8, 'tank', { split: 'x' });
    m.ring('closure_hub', 'x', [9, 8], 5.6, 1.2, -15, -14, 'ci');
    m.prism('reducer', 'x', [9, 8], 4, 8, 12, 'tank', { split: '' });
    m.prism('line', 'x', [9, 8], 3, 12, 31.99, 'gband', { split: 'x' });
    P(m, 'kicker', [2, 13.6, 7], [4, 18, 9], 'gband');
    P(m, 'kicker_run', [2, 16, 7], [20, 18, 9], 'gband');
    P(m, 'valve', [16, 10, 5], [21, 16, 11], 'green');
    m.wheelY('valve_hw', 18.5, 16.6, 8, 2.4, 'red');
    P(m, receiver ? 'drain' : 'vent', receiver ? [-10, 0, 7.4] : [-10, 14, 7.4], receiver ? [-8.8, 4, 8.6] : [-8.8, 18, 8.6], 'p_liq');
    P(m, 'signaler', [10, 12.6, 7.4], [11.6, 14.6, 8.6], 'ypost');
    m.decal('gauge', [-6, 14.4, 7.2], [-3.6, 16.8, 7.4], 'tank', 'north', 'parts.gauge');
    P(m, 'gauge_stem', [-5.2, 13, 7.4], [-4.4, 14.4, 8], 'br');
    return m;
  }
  function trapDoor() {
    const m = mk('material/cast_iron');
    m.prism('door', 'x', [9, 8], 5.4, -16, -14.97, 'ci', { split: '', ends: ['west', 'east'] });
    P(m, 'handle', [-16, 7.4, 4], [-15.6, 8.6, 12], 'bright');
    P(m, 'hinge', [-16, 4.5, 12.4], [-15, 15.5, 13.5], 'ci');
    return m;
  }

  // ---------------------------------------------------------------- gas processing
  function heliumPurifier(cycle) {
    const m = mk('electronics/ss');
    FUTK.skid(m, 'skid', [0, 0], [32, 16]);
    for (const [x, n] of [[5, 'a'], [12, 'b']]) {
      vessel(m, 'bed_' + n, [x, 9], 2.6, 2, 24, 'ss');
      lamp(m, 'lamp_' + n, x, 26.4, 6, (cycle === n) ? 'l_g' : 'l_off', cycle === n, 0.5);
    }
    m.cylY('dewar', [22, 9], 4, 1.4, 20, 'ss', { cap: 'electronics/ss' });
    P(m, 'dewar_neck', [21, 20, 8], [23, 24, 10], 'frost');
    P(m, 'manifold', [3, 26, 8.4], [24, 27, 9.6], 'ss');
    cabinet(m, 'panel', [26, 1.4, 1], [31.6, 14, 4], 'en', { plate: false });
    m.decal('gauge', [27.4, 9, 0.6], [30.2, 11.8, 0.8], 'en', 'north', 'parts.gauge');
    P(m, 'he_out', [28, 26, 8.4], [32, 27, 9.6], 'gb');
    P(m, 'riser', [28, 14, 8.4], [29, 27, 9.6], 'gb');
    return m;
  }
  function adsorber(regen) {
    const m = mk('m8ref/sblue');
    P(m, 'skirt', [3, -15.95, 2.5], [13, -12, 12.5], 'frame');
    vessel(m, 'shell', [8, 7.5], 5.4, -12, 26, 'sb', { bottom: false });
    for (const y of [-6, 8, 20]) m.ringY('flange' + y, [8, 7.5], 5.8, 0.5, y, y + 0.8, 'ci');
    P(m, 'in', [12, 20, 7], [13.5, 22, 8.6], 'p_gas'); P(m, 'out', [12, -8, 7], [13.5, -6, 8.6], 'p_gas');
    lamp(m, 'phase', 8, 12, 1.9, regen ? 'l_a' : 'l_g', true, 0.6);
    ladder(m, 'ladder', 3.4, 1.6, -12, 31.99, 'x');
    return m;
  }

  // ---------------------------------------------------------------- furnaces and reactors
  function gasifier(top, lit) {
    const m = mk('m3met/riveted');
    if (!top) {
      P(m, 'found', [-16, -16, -16], [32, -12, 32], 'conc');
      P(m, 'ash_lock', [2, -12, 2], [14, -2, 14], 'riv');
      m.cylY('shell', [8, 8], 15, -2, 32, 'riv');
      for (const a of [0, 90, 180, 270]) m.postY('tuyere' + a, [8, 8], 15.6, a, 1.6, 2, 4, 'p_steam');
      m.ringY('bustle', [8, 8], 16, 1.2, 4, 6, 'p_air');
      P(m, 'poke', [6, 8, -7.6], [10, 12, -7], 'ci');
      if (lit) P(m, 'glow_poke', [6.8, 8.8, -7.8], [9.2, 11.2, -7.6], 'fglow');
      ladder(m, 'ladder', 20, -6, -12, 32, 'x');
    } else {
      m.cylY('shell', [8, 8], 15, -15.99, 8, 'riv');
      m.cylY('cone', [8, 8], 10, 8, 14, 'riv', { cap: 'm3met/riveted' });
      P(m, 'coal_lock', [2, 14, 2], [14, 22, 14], 'riv');
      P(m, 'hopper', [0, 22, 0], [16, 26.03, 16], 'st');
      P(m, 'gas_out', [18, 0, 5], [26, 6, 11], 'p_gas');
      P(m, 'platform', [-10, 8, -10], [26, 9, -6], 'grat');
      PLANT.railing(m, 'rail', -10, 26, 9, -10, 'x');
    }
    return m;
  }
  function furnaceController(state) {
    const m = mk('switchgear_cell/enamel');
    cabinet(m, 'cab', [1, 0, 3], [15, 16, 15], 'en', { plate: true });
    const mat = { cold: 'l_off', hot: 'l_a', cracking: 'l_g', decoking: 'l_b', tripped: 'l_r' }[state];
    lamp(m, 'state', 4, 12, 2.8, mat, state !== 'cold', 0.8);
    m.decal('tt', [7, 10, 2.6], [11, 14, 2.8], 'en', 'north', 'parts.gauge');
    P(m, 'estop', [11.6, 4, 2.2], [13.6, 6, 2.8], 'red');
    return m;
  }
  function radiant(catalyst, lit) {
    const m = mk('plant/furnace_casing');
    P(m, 'casing', [-16, -16, -12], [32, 32, 28], 'casing', { split: 'xyz' });
    const tex = catalyst ? (lit ? 'ctubh' : 'ctub') : (lit ? 'coilh' : 'coil');
    P(m, (lit ? 'glow_' : '') + 'window', [-10, -10, -12.2], [26, 26, -12], tex, { hide: ['south'] });
    for (const x of [-16, 30]) P(m, 'buckstay' + x, [x, -16, -14], [x + 2, 32, -12], 'frame');
    for (const x of [-6, 8, 22]) {
      P(m, 'burner' + x, [x - 2, -16, -14], [x + 2, -12, -12], 'ci');
      P(m, 'peep' + x, [x - 1.2, 10, -12.4], [x + 1.2, 12.4, -12], lit ? 'fglow' : 'ci');
    }
    P(m, 'header', [-16, 28, -14], [32, 30, -12], catalyst ? 'p_gas' : 'p_liq');
    return m;
  }
  function batchReactor(relief) {
    const m = mk(D + 'enamel_blue');
    P(m, 'deck', [-8, 6, -8], [24, 7, 24], 'grat');
    m.cylY('jacket', [8, 8], 10, -10, 22, 'enb');
    m.cylY('bottom', [8, 8], 7, -14, -10, 'enb', { bottom: true });
    P(m, 'outlet', [7, -16, 7], [9, -14, 9], 'ci');
    m.cylY('head', [8, 8], 8, 22, 24, 'enb', { cap: D + 'enamel_blue' });
    P(m, 'gearbox', [5, 24, 5], [11, 28, 11], 'green');
    motor(m, 'motor', 'z', [8, 29.2], 1.2, 4, 12, 'blue', { feet: false });
    P(m, 'manhole', [12, 24, 2], [16, 25, 6], 'enb');
    P(m, 'psv', [1, 24, 11], [3, 27, 13], relief ? 'red' : 'ci');
    if (relief) P(m, 'glow_burst', [1.4, 27, 11.4], [2.6, 27.4, 12.6], 'l_r');
    for (const y of [-6, 4, 14]) P(m, 'steam' + y, [18, y, 7.4], [24, y + 1.2, 8.6], 'p_steam');
    return m;
  }
  function batchAgitator() {
    const m = mk('material/steel');
    m.cylY('shaft', [8, 8], 0.7, -8.03, 24.03, 'st', { cap: 'material/steel', bottom: true });
    for (const a of [0, 45, -45, 90]) P(m, 'blade' + a, a === 90 ? [7.4, -7, -1] : [-1, -7, 7.4], a === 90 ? [8.6, -4, 17] : [17, -4, 8.6], 'st', { rot: a && a !== 90 ? { axis: 'y', angle: a, origin: [8, 8, 8] } : undefined });
    return m;
  }
  function extruder(die) {
    const m = mk('material/steel');
    P(m, 'bed', [-16, 0, 2], [32, 4, 14], 'frame');
    m.prism('barrel', 'x', [9, 8], 3, -10, 22, 'hband', { split: 'x' });
    P(m, 'hopper', [14, 12, 5], [20, 18, 11], 'st');
    P(m, 'throat', [15.6, 11, 6.6], [18.4, 12, 9.4], 'st');
    P(m, 'gearbox', [22, 4, 3], [28, 14, 13], 'green');
    motor(m, 'motor', 'x', [9, 8], 3, 28, 32, 'blue', { feet: false });
    for (const x of [-6, 4, 14]) P(m, 'support' + x, [x, 4, 6], [x + 2, 6, 10], 'frame');
    if (die === 'pellets') {
      P(m, 'die', [-14, 5, 4], [-10, 13, 12], 'st');
      P(m, 'water_box', [-16, 2, 3], [-14, 14, 13], 'ss');
    } else if (die === 'pipe') {
      m.prism('die', 'x', [9, 8], 3.6, -14, -10, 'st', { split: '' });
      P(m, 'calibrator', [-16, 4, 4], [-14, 14, 12], 'ss');
    } else {
      P(m, 'die', [-14, 6, 1], [-10, 11, 15], 'st');
      for (const y of [5, 12]) m.prism('roll' + y, 'z', [-14.6, y], 1.2, 1, 15, 'bright', { split: '' });
    }
    return m;
  }
  function screw() { const m = mk('material/bright_steel'); m.prism('screw', 'x', [9, 8], 2.4, -12.03, 24.03, 'bright', { split: '' }); for (let x = -10; x < 22; x += 3) m.ring('flight' + x, 'x', [9, 8], 2.5, 0.6, x, x + 0.6, 'st'); return m; }

  // ---------------------------------------------------------------- coke drums and fixed beds
  function cokeDrum(top) {
    const m = mk('material/steel');
    for (const x of [-16, 30]) for (const z of [-16, 30]) P(m, `col_${x}_${z}`, [x, -16, z], [x + 2, 32, z + 2], 'frame', { split: 'y' });
    if (!top) {
      m.cylY('cone', [8, 8], 9, -12, -4, 'st', { bottom: true });
      m.cylY('shell', [8, 8], 14, -4, 32, 'st');
      P(m, 'deheader_frame', [-2, -16, 0], [18, -14, 16], 'frame');
      P(m, 'skirt_deck', [-16, -4, -16], [32, -3, -10], 'grat');
      P(m, 'inlet', [20, -10, 6], [30, -8, 10], 'p_liq');
    } else {
      m.cylY('shell', [8, 8], 14, -16, 6, 'st');
      m.cylY('head', [8, 8], 10, 6, 10, 'st', { cap: 'material/steel' });
      P(m, 'deck', [-16, 13, -16], [32, 14, 32], 'grat');
      P(m, 'vapour_line', [12, 6, 6], [30, 10, 10], 'st');
      for (const x of [-4, 18]) P(m, 'derrick' + x, [x, 14, 6], [x + 2, 32, 8], 'yel');
      P(m, 'derrick_top', [-4, 30, 6], [20, 32, 8], 'yel');
    }
    return m;
  }
  function topDeheader() { const m = mk('material/steel'); P(m, 'cover', [2, 10, 2], [14, 13, 14], 'st'); P(m, 'hinge', [14, 11, 6], [16, 13, 10], 'ci'); return m; }
  function bottomDeheader() { const m = mk('material/steel'); P(m, 'slide', [2, -15.95, 2], [14, -13.97, 14], 'st'); return m; }
  function cuttingStem() { const m = mk('electronics/ss'); m.cylY('stem', [8, 8], 1.2, -6, 31.93, 'ss', { cap: 'electronics/ss' }); m.cylY('tool', [8, 8], 2.5, -12.03, -6, 'st', { cap: 'material/steel', bottom: true }); return m; }
  function fixedBed(kind) {
    const m = mk('plant/cladding');
    if (kind === 'large') {
      P(m, 'skirt', [-4, -16, -4], [20, -10, 20], 'frame');
      m.cylY('shell', [8, 8], 17.6, -10, 26, 'clad');
      m.cylY('head', [8, 8], 12, 26, 31.99, 'clad', { cap: 'plant/cladding' });
      P(m, 'inlet', [6, 30, 6], [10, 31.99, 10], 'st');
      return m;
    }
    P(m, 'skirt', [4, -0.03, 3.6], [12, 4, 11.6], 'frame');
    m.cylY('shell', [8, 7.6], 5.6, 4, 28, 'clad');
    m.cylY('head', [8, 7.6], 3, 28, 31.99, 'clad', { cap: 'plant/cladding' });
    for (const y of [10, 20]) m.ringY('flange' + y, [8, 7.6], 6, 0.5, y, y + 0.8, 'ci');
    lamp(m, 'mode', 8, 16, 1.8, kind === 'regen' ? 'l_a' : 'l_g', true, 0.5);
    P(m, 'outlet', [12, 6, 7], [14, 8, 8.6], 'p_liq');
    return m;
  }

  // ---------------------------------------------------------------- wind pump (M2)
  function windPump() {
    const m = mk('disconnector/galvanised');
    for (const [x, z] of [[1, 1], [14, 1], [1, 14], [14, 14]]) P(m, `leg_${x}_${z}`, [x, 0, z], [x + 1, 32, z + 1], 'galv', { split: 'y' });
    for (const y of [8, 16, 24]) { P(m, 'girt_n' + y, [1, y, 1], [15, y + 0.6, 1.6], 'galv'); P(m, 'girt_s' + y, [1, y, 14.4], [15, y + 0.6, 15], 'galv'); }
    P(m, 'pump_head', [6, 0, 6], [10, 4, 10], 'ci');
    P(m, 'handle', [10, 3, 7.6], [14, 3.6, 8.4], 'ci');
    P(m, 'spout', [3, 2, 7.4], [6, 3, 8.6], 'ci');
    P(m, 'trough', [0, 0, 2], [3, 2, 14], 'twood');
    return m;
  }
  function windPumpTop(furled) {
    const m = mk('disconnector/galvanised');
    for (const [x, z] of [[2, 2], [13, 2], [2, 13], [13, 13]]) P(m, `leg_${x}_${z}`, [x, 0, z], [x + 1, 10, z + 1], 'galv');
    P(m, 'platform', [1, 8, 1], [15, 9, 15], 'twood');
    P(m, 'head', [6, 10, 5], [10, 14, 11], 'galv');
    P(m, 'tail_boom', [7.6, 11.6, 11], [8.4, 12.4, 26], 'galv', { rot: furled ? { axis: 'y', angle: 45, origin: [8, 12, 11] } : undefined });
    P(m, 'tail_vane', [7.8, 9, 22], [8.2, 16, 30], 'vane', { rot: furled ? { axis: 'y', angle: 45, origin: [8, 12, 11] } : undefined });
    return m;
  }
  function pumpWheel() {
    const m = mk(D + 'wood_vane');
    m.prism('hub', 'z', [8, 12], 1.2, 2.6, 5, 'ci', { split: '', ends: ['north', 'south'] });
    m.ring('rim', 'z', [8, 12], 9, 0.6, 3.4, 4, 'galv');
    for (const a of [0, 22.5, 45, -22.5, -45, 67.5, -67.5, 90]) P(m, 'blade' + a, [7, 12, 3], [9, 21, 4.4], 'vane', { rot: a ? { axis: 'z', angle: a > 45 ? a - 90 : a < -45 ? a + 90 : a, origin: [8, 12, 3.7] } : undefined });
    return m;
  }
  function pumpRod() { const m = mk('material/steel'); P(m, 'rod', [7.6, 4, 7.6], [8.4, 32, 8.4], 'st'); return m; }

  const B = (model, cat, java) => ({ model, cat, java });
  const BUILDS = {
    bop_ram: B(() => bopRam('pipe', false), 'drilling'), bop_ram_closed: B(() => bopRam('pipe', true), 'drilling'),
    bop_ram_blind: B(() => bopRam('blind', false), 'drilling'), bop_ram_shear: B(() => bopRam('shear', false), 'drilling'),
    pig_launcher: B(() => pigTrap(false), 'oil_transport'), 'pig_launcher/door': B(trapDoor, 'oil_transport', 'pig_launcher/partial/door'),
    pig_receiver: B(() => pigTrap(true), 'oil_transport'), 'pig_receiver/door': B(trapDoor, 'oil_transport', 'pig_receiver/partial/door'),
    helium_purifier: B(() => heliumPurifier('a'), 'gas_processing'), helium_purifier_b: B(() => heliumPurifier('b'), 'gas_processing'),
    adsorber_vessel: B(() => adsorber(false), 'petrochem'), adsorber_vessel_regen: B(() => adsorber(true), 'petrochem'),
    coal_gasifier: B(() => gasifier(false, false), 'petrochem'), coal_gasifier_lit: B(() => gasifier(false, true), 'petrochem'),
    coal_gasifier_top: B(() => gasifier(true, false), 'petrochem'),
    tube_furnace_controller: B(() => furnaceController('cold'), 'petrochem'), tube_furnace_controller_hot: B(() => furnaceController('hot'), 'petrochem'),
    tube_furnace_controller_cracking: B(() => furnaceController('cracking'), 'petrochem'), tube_furnace_controller_decoking: B(() => furnaceController('decoking'), 'petrochem'),
    tube_furnace_controller_tripped: B(() => furnaceController('tripped'), 'petrochem'),
    tube_furnace_radiant: B(() => radiant(false, false), 'petrochem'), tube_furnace_radiant_lit: B(() => radiant(false, true), 'petrochem'),
    tube_furnace_radiant_catalyst: B(() => radiant(true, false), 'petrochem'), tube_furnace_radiant_catalyst_lit: B(() => radiant(true, true), 'petrochem'),
    batch_reactor: B(() => batchReactor(false), 'polymers'), batch_reactor_relief: B(() => batchReactor(true), 'polymers'),
    'batch_reactor/agitator': B(batchAgitator, 'polymers', 'batch_reactor/partial/agitator'),
    polymer_extruder: B(() => extruder('pellets'), 'polymers'), polymer_extruder_pipe: B(() => extruder('pipe'), 'polymers'),
    polymer_extruder_sheet: B(() => extruder('sheet'), 'polymers'), 'polymer_extruder/screw': B(screw, 'polymers', 'polymer_extruder/partial/screw'),
    coke_drum: B(() => cokeDrum(false), 'refinery'), coke_drum_top: B(() => cokeDrum(true), 'refinery'),
    'coke_drum/top_deheader': B(topDeheader, 'refinery', 'coke_drum/partial/top_deheader'),
    'coke_drum/bottom_deheader': B(bottomDeheader, 'refinery', 'coke_drum/partial/bottom_deheader'),
    'coke_drum/cutting_stem': B(cuttingStem, 'refinery', 'coke_drum/partial/cutting_stem'),
    fixed_bed_reactor: B(() => fixedBed('run'), 'refinery'), fixed_bed_reactor_regen: B(() => fixedBed('regen'), 'refinery'),
    fixed_bed_reactor_large: B(() => fixedBed('large'), 'refinery'),
    wind_pump: B(windPump, 'wind'), wind_pump_top: B(() => windPumpTop(false), 'wind'), wind_pump_top_furled: B(() => windPumpTop(true), 'wind'),
    'wind_pump/wheel': B(pumpWheel, 'wind', 'wind_pump/partial/wheel'), 'wind_pump/pump_rod': B(pumpRod, 'wind', 'wind_pump/partial/pump_rod'),
  };
  window.SHU = FUTK.machine(BUILDS, paint);
})();
