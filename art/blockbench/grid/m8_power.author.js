// M8 engines, wind, geothermal and analysers not yet in the mod, hand-authored. Replaces the spec_grid2.py,
// spec_oilgas.py and spec_metallurgy2.py placeholders of the diesel and gas engine gensets and their flywheels, the
// gasoline engine and its crankshaft, fired heater casing pieces, process chromatograph, quality analyser and its
// CFR flywheel, PGM refinery hood; draws the GDD 15 wind turbine (tower sections, nacelle, hub, blade segments,
// controller), the geothermal wellhead and its stem, flash separator and NCG ejector, and the weather station.
// Run headless: node art/tools/pcart_headless.js <repo> grid/m8_power.author.js M8P --paint
//
// Front to the north. Pivots: engine flywheels about z through (8, -2) on the front face (z -16); gasoline
// crankshaft about z through (8, 8); analyser flywheel about x through (y 6, z 6); wellhead stem about y through
// (8, 8); the nacelle turns about y through (8, 8) (yaw), the hub about z through (8, 8), blades about their own
// axis (z through (8, 8)). Big turbines exceed 3 blocks, so the tower and nacelle come in 3x3x3 segments.
(function () {
  const { rng, img, put, get, add, mix, rect, grain } = PCART;
  const { P, lamp, cabinet, vessel, drum, flange, motor, gauge, ladder } = FUTK;
  const D = 'm8power/', T = {};

  T.fibre = () => { const im = img(32, 32); grain(im, [232, 234, 232], 37101, 2, 3, 8); return im; };
  T.ice = () => { const im = img(16, 16), r = rng(37102); for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) put(im, x, y, add(r() < 0.5 ? [220, 236, 246] : [180, 210, 228], (r() - 0.5) * 12)); return im; };
  T.ral6011 = FUTK.enamel([90, 110, 82], 37103, 2);
  T.tower = () => { const im = img(32, 32), r = grain(im, [214, 218, 218], 37104, 2, 3, 8); for (let x = 0; x < 32; x++) put(im, x, 31, [180, 184, 184]); return im; };
  T.louvre_white = () => { const im = img(16, 16); for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) put(im, x, y, y % 3 === 0 ? [150, 150, 146] : [238, 238, 232]); return im; };
  T.coil_hot = () => { const im = img(16, 16), r = rng(37105); for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) put(im, x, y, add([220, 90, 40], (r() - 0.5) * 30 + (x % 4 === 0 ? -40 : 0))); return im; };
  T.steel_blade = FUTK.enamel([150, 154, 156], 37106, 0);

  const MATX = {
    fibre: { tex: D + 'fibre', P: 16 }, ice: { tex: D + 'ice', P: 16 }, g6011: { tex: D + 'ral6011', P: 16 }, twr: { tex: D + 'tower', P: 16 },
    lwh: { tex: D + 'louvre_white', P: 16 }, chot: { tex: D + 'coil_hot', P: 16 }, sblade: { tex: D + 'steel_blade', P: 16 },
    refr: { tex: 'm8ref/refr', P: 16 }, sb: { tex: 'm8ref/sblue', P: 16 }, frost: { tex: 'm8ref/frost', P: 16 },
    gband: { tex: 'm8field/gasband', P: 16 }, ypost: { tex: 'm8field/yellow_post', P: 16 }, gb: { tex: 'm8field/greyblue', P: 16 }, cfr: { tex: 'm8ref/cfr', P: 16 },
  };
  const mk = (p) => FUTK.mk(MATX, p);
  const paint = (root) => FUTK.paintSet(root, D, T);

  // ---------------------------------------------------------------- engines
  // Genset 3x3x3: base frame, V engine block with heads and rocker covers, turbocharger and exhaust manifold,
  // radiator at the back, the flywheel housing at the front face (the flywheel is the partial).
  function genset(gas) {
    const m = mk(gas ? 'switchgear_cell/enamel' : D + 'ral6011');
    const body = gas ? 'en' : 'g6011';
    P(m, 'base', [-14, -16, -12], [30, -13, 28], 'frame');
    P(m, 'block', [-4, -13, -12], [20, 2, 20], body, { split: 'xyz' });
    for (const s of [-1, 1]) {
      P(m, 'bank' + s, [s < 0 ? -6 : 12, 2, -12], [s < 0 ? 4 : 22, 8, 20], body, { rot: { axis: 'z', angle: s * 22.5, origin: [s < 0 ? -1 : 17, 2, 4] } });
      P(m, 'cover' + s, [s < 0 ? -5 : 13, 8, -11], [s < 0 ? 3 : 21, 10, 19], body, { rot: { axis: 'z', angle: s * 22.5, origin: [s < 0 ? -1 : 17, 2, 4] } });
      P(m, 'manifold' + s, [s < 0 ? -9 : 23, -2, -10], [s < 0 ? -6 : 26, 1, 18], 'soot');
    }
    m.prism('turbo', 'x', [12, 22], 3, 2, 10, 'st', { split: '' });
    P(m, 'turbo_out', [4, 14, 20], [7, 24, 23], 'soot');
    P(m, 'radiator', [-12, -13, 22], [28, 8, 28], 'fin');
    P(m, 'rad_frame', [-12.6, 8, 21.4], [28.6, 9, 28.6], body);
    m.prism('fw_housing', 'z', [8, -2], 9.6, -12, -14, body, { split: '', ends: ['north'] });
    if (gas) {
      P(m, 'enclosure_roof', [-12, 18, -12], [28, 18.6, 22], 'en');
      for (const x of [-12, 27.4]) P(m, 'enc_post' + x, [x, -13, -12], [x + 0.6, 18, -11.4], 'en');
      P(m, 'gas_train', [24, -10, -8], [30, -8, 20], 'gband');
      P(m, 'gas_valve', [25, -8, 0], [29, -4, 4], 'ypost');
    } else {
      P(m, 'filter', [10, 8, 10], [16, 14, 16], 'g6011');
      P(m, 'fuel_filter', [-12, -12, 0], [-9, -6, 3], 'p_liq');
    }
    cabinet(m, 'ctrl', [-14, -13, -10], [-9, -2, -4], body, { plate: false, inset: 0.4 });
    m.decal('plate', [4, -10, -12.1], [8, -8, -12], body, 'north', 'parts.plate');
    return m;
  }
  function engineFlywheel() {
    const m = mk('material/steel');
    m.ring('rim', 'z', [8, -2], 9, 1.6, -15.99, -13.43, 'st');
    for (const a of [0, 45, -45]) P(m, 'spoke' + a, [7.2, -10, -15.4], [8.8, 6, -14], 'st', { rot: a ? { axis: 'z', angle: a, origin: [8, -2, -14.7] } : undefined });
    P(m, 'spoke_h', [0, -2.8, -15.4], [16, -1.2, -14], 'st');
    m.prism('hub', 'z', [8, -2], 2, -15.99, -13.43, 'ci', { split: '', ends: ['north', 'south'] });
    return m;
  }
  function gasoline() {
    const m = mk('material/cast_iron');
    P(m, 'pan', [3, 0, 2], [13, 3, 14], 'ci');
    P(m, 'block', [3, 3, 2], [13, 11, 14], 'ci');
    P(m, 'head', [4, 11, 2.6], [12, 13, 13.4], 'ci');
    P(m, 'rocker', [4.6, 13, 3], [11.4, 14, 13], 'red');
    P(m, 'tank', [13, 6, 4], [16, 12, 12], 'red');
    P(m, 'tank_cap', [14, 12, 7], [15, 13, 9], 'st');
    P(m, 'carb', [1, 8, 6], [3, 11, 9], 'br');
    P(m, 'air_filter', [0, 10, 5], [3, 12, 10], 'blue');
    P(m, 'exhaust', [1.6, 4, 10], [3, 6, 14], 'soot');
    P(m, 'muffler', [0, 1, 11], [2, 5, 15], 'soot');
    P(m, 'starter', [9, 2, 0.4], [12, 6, 2], 'st');
    return m;
  }
  function gasolineCrank() {
    const m = mk('material/steel');
    m.prism('shaft', 'z', [8, 8], 1.2, -0.04, 16.04, 'bright', { split: '', ends: ['north', 'south'] });
    m.prism('pulley', 'z', [8, 8], 2.8, 0.2, 1.6, 'st', { split: '', ends: ['north', 'south'] });
    return m;
  }

  // ---------------------------------------------------------------- fired heater casing pieces
  function heaterWall(coil, lit) {
    const m = mk('m8ref/refr');
    P(m, 'casing', [0, 0, 15], [16, 16, 16], 'casing');
    P(m, 'lining', [0, 0, 10.4], [16, 16, 15], 'refr');
    if (coil) {
      for (const x of [2, 6, 10, 14]) m.cylY('tube' + x, [x, 7.6], 1.2, 0, 16, lit ? 'chot' : 'st');
      if (lit) P(m, 'glow_flame', [3, 0, 4], [13, 3, 8], 'fglow', { hide: ['down'] });
    } else {
      P(m, 'peep', [6, 6, 10.2], [10, 10, 10.4], 'ci');
      P(m, lit ? 'glow_peep' : 'peep_glass', [6.8, 6.8, 10], [9.2, 9.2, 10.2], lit ? 'fglow' : 'vdark');
    }
    return m;
  }
  function heaterRoof() { const m = mk('m8ref/refr'); P(m, 'lining', [0, 9, 0], [16, 15, 16], 'refr'); P(m, 'casing', [0, 15, 0], [16, 16, 16], 'casing'); return m; }

  // ---------------------------------------------------------------- analysers
  function chromatograph(state) {
    const m = mk('switchgear_cell/enamel');
    cabinet(m, 'cab', [0, 0, 1], [16, 31.5, 16], 'en', { plate: true });
    m.box('disp', [3, 22, 0.6], [11, 27, 0.8], 'en', { split: '', face: { north: { tex: 'electronics/screen', uv: [0, 0, 16, 10] } } });
    lamp(m, 'cycle', 13, 25, 0.8, { measuring: 'l_g', calibrating: 'l_a', fault: 'l_r' }[state], true, 0.6);
    P(m, 'oven_window', [3, 10, 0.6], [13, 18, 0.8], 'glass');
    m.cylY('carrier', [17.4, 8], 1.5, 0, 22, 'gb', { cap: 'm8field/greyblue' });
    m.cylY('carrier_valve', [17.4, 8], 0.6, 22, 24, 'br');
    P(m, 'reg', [16, 20, 4], [18.6, 22, 7], 'br');
    P(m, 'tube', [16, 21, 5], [16.4, 22, 9], 'br');
    return m;
  }
  function analyzer(kind) {
    const m = mk('m6auto/white');
    cabinet(m, 'cab', [0, 0, 1], [16, 31.5, 16], 'wh', { plate: true });
    P(m, 'window', [3, 16, 0.6], [13, 26, 0.8], 'glass');
    lamp(m, 'state', 13, 28, 0.8, kind === 'fault' ? 'l_r' : 'l_g', true, 0.6);
    if (kind === 'octane') {
      P(m, 'engine_bay', [2, 2, 0.6], [16, 12, 1], 'vdark');
      P(m, 'cfr', [3, 2, 1], [9, 10, 8], 'cfr');
      P(m, 'cfr_cyl', [4.4, 10, 3], [7.6, 13, 6], 'cfr');
    } else m.box('vent', [3, 3, 0.6], [13, 8, 0.8], 'wh', { split: '', face: { north: { tex: 'grid_lines/louvre', uv: [0, 0, 16, 8] } } });
    return m;
  }
  function analyzerFly() {
    const m = mk('material/steel');
    m.prism('wheel', 'x', [6, 6], 3, 10, 12, 'st', { split: '', ends: ['west', 'east'] });
    m.prism('shaft', 'x', [6, 6], 0.6, 9.97, 14.03, 'bright', { split: '', ends: ['east'] });
    return m;
  }
  function pgm(running) {
    const m = mk('m6auto/white');
    P(m, 'base', [0, 0, 2], [32, 12, 16], 'wh');
    P(m, 'top', [0, 12, 1.5], [32, 13, 16], 'ss');
    for (const x of [0, 31]) P(m, 'side' + x, [x, 13, 2], [x + 1, 30, 16], 'wh');
    P(m, 'back', [1, 13, 15], [31, 30, 16], 'wh');
    P(m, 'canopy', [0, 30, 1.5], [32, 31, 16], 'wh');
    P(m, 'sash', [1, 20, 2.4], [31, 29, 2.8], 'glass');
    P(m, 'duct', [12, 31, 8], [20, 31.01, 14], 'ss', { hide: ['down'] });
    for (const [x, r] of [[5, 2], [11, 1.6], [17, 2], [24, 1.4]]) {
      P(m, 'plate' + x, [x - 2.4, 13, 6], [x + 2.4, 14, 11], running ? 'chot' : 'ss');
      m.cylY('vessel' + x, [x, 8.5], r, 14, 17, 'wh', { cap: 'm6auto/white' });
    }
    if (running) P(m, 'glow_plate', [2.6, 14, 6.4], [7.4, 14.05, 10.6], 'fglow', { hide: ['down'] });
    return m;
  }
  function weatherStation() {
    const m = mk(D + 'louvre_white');
    for (const [x, z] of [[3, 3], [12, 3], [3, 12], [12, 12]]) P(m, `leg_${x}_${z}`, [x, 0, z], [x + 1, 10, z + 1], 'wh');
    P(m, 'box', [2, 10, 2], [14, 20, 14], 'lwh');
    P(m, 'roof', [1.4, 20, 1.4], [14.6, 21, 14.6], 'wh');
    P(m, 'roof2', [2.4, 21, 2.4], [13.6, 21.6, 13.6], 'wh');
    P(m, 'mast', [7.4, 21.6, 7.4], [8.6, 32, 8.6], 'galv');
    P(m, 'arm', [3, 30, 7.6], [13, 30.6, 8.4], 'galv');
    m.cylY('rain', [3.6, 8], 1, 30.6, 32, 'ss', { cap: 'electronics/ss' });
    P(m, 'solar', [9.6, 27, 4], [14, 27.4, 8], 'vdark', { rot: { axis: 'x', angle: -22.5, origin: [12, 27, 6] } });
    return m;
  }

  // ---------------------------------------------------------------- geothermal
  function wellhead(state) {
    const m = mk('plant/green');
    P(m, 'cellar', [1, 0, 1], [15, 1, 15], 'conc');
    m.cylY('casing', [8, 8], 3.4, 1, 5, 'st', { cap: 'material/steel' });
    for (const y of [5, 13, 21]) m.cylY('flange' + y, [8, 8], 4.2, y, y + 1, 'ci', { cap: 'material/cast_iron' });
    P(m, 'master', [4, 6, 4], [12, 13, 12], 'green');
    P(m, 'cross', [3, 14, 5.6], [13, 20, 10.4], 'green');
    P(m, 'wing', [13, 15, 6.4], [16, 19, 9.6], 'p_steam');
    P(m, 'swab', [5, 22, 5], [11, 26, 11], 'green');
    gauge(m, 'gauge', 8, 29, 6.4, 1.4);
    P(m, 'gauge_stem', [7.6, 26, 7.6], [8.4, 27.6, 8.4], 'br');
    lamp(m, 'state', 12, 24, 4.9, state === 'flowing' ? 'l_g' : state === 'shut' ? 'l_r' : 'l_off', state !== 'idle', 0.5);
    return m;
  }
  function wellStem() { const m = mk('material/bright_steel'); m.cylY('stem', [8, 8], 0.5, 6, 9.6, 'bright', { cap: 'material/bright_steel' }); m.wheelY('hw', 8, 9.6, 8, 3, 'red'); return m; }
  function flashSep(top) {
    const m = mk('plant/cladding');
    if (!top) {
      for (const a of [45, 135, 225, 315]) m.postY('leg' + a, [8, 8], 12, a, 2, -16, -6, 'frame');
      m.cylY('cone', [8, 8], 8, -10, -4, 'clad', { bottom: true });
      m.cylY('shell', [8, 8], 13, -4, 32, 'clad');
      P(m, 'inlet', [21, 18, 6], [32, 22, 10], 'p_steam');
      P(m, 'brine', [6, -16, 6], [10, -10, 10], 'p_water');
      ladder(m, 'ladder', 6.6, -5.6, -6, 32, 'x');
    } else {
      m.cylY('shell', [8, 8], 13, -16, 4, 'clad');
      m.cylY('head', [8, 8], 10.6, 4, 8, 'clad', { cap: 'plant/cladding' });
      m.cylY('outlet', [8, 8], 3, 8, 16, 'p_steam', { cap: 'plant/pipe_steam' });
      P(m, 'outlet_run', [5, 13, 8], [32, 16, 11], 'p_steam');
      for (const x of [-2, 18]) { P(m, 'psv' + x, [x, 4, 6], [x + 2, 8, 8], 'red'); }
      P(m, 'platform', [-6, 4, -6], [22, 5, -3], 'grat');
      PLANT.railing(m, 'rail', -6, 22, 5, -6, 'x');
    }
    return m;
  }
  function ncgEjector() {
    const m = mk('electronics/ss');
    for (const z of [3, 24]) P(m, 'support' + z, [5, 0, z], [11, 4, z + 3], 'frame');
    m.prism('chest', 'z', [8, 8], 3, 0, 6, 'ss', { split: '', ends: ['north'] });
    m.prism('throat', 'z', [8, 8], 2, 6, 14, 'ss', { split: '' });
    for (const [z0, z1, r] of [[14, 20, 2.6], [20, 26, 3.2], [26, 32, 3.8]]) m.prism('diff' + z0, 'z', [8, 8], r, z0, z1, 'ss', { split: '', ends: z1 === 32 ? ['south'] : [] });
    P(m, 'steam', [7.2, 11, 2], [8.8, 16, 3.6], 'p_steam');
    m.prism('suction', 'x', [8, 10], 1.8, 10, 16, 'ss', { split: '', ends: ['east'] });
    return m;
  }

  // ---------------------------------------------------------------- wind turbine (GDD 15)
  function towerSection(top) {
    const m = mk(D + 'tower');
    m.cylY('shell', [8, 8], top ? 9 : 10, -16, top ? 28 : 32, 'twr', { cap: top ? D + 'tower' : undefined });
    m.ringY('flange', [8, 8], top ? 9.8 : 10.8, 0.8, -16, -15, 'twr');
    if (top) { m.ringY('yaw_ring', [8, 8], 9.4, 1.2, 28, 30, 'ci'); m.cylY('yaw_deck', [8, 8], 8, 28, 29, 'ci', { cap: 'material/cast_iron' }); }
    else P(m, 'door', [5, -15, -2.2], [11, -5, -1.6], 'twr');
    return m;
  }
  // Nacelle front segment: main bearing housing at the front (north), gearbox, the white fibreglass cover.
  function nacelle(iced) {
    const m = mk(D + 'fibre');
    const cov = iced ? 'ice' : 'fibre';
    P(m, 'bedplate', [-4, -4, -16], [20, 0, 32], 'ci', { split: 'z' });
    P(m, 'cover', [-6, 0, -12], [22, 18, 32], cov, { split: 'xyz' });
    P(m, 'nose_ring', [-4, 2, -16], [20, 16, -12], cov);
    m.prism('main_shaft', 'z', [8, 8], 2.4, -16, -12, 'bright', { split: '', ends: ['north'] });
    P(m, 'hatch', [4, 18, 20], [12, 18.4, 28], cov);
    P(m, 'met_mast', [6, 18, 28], [7, 26, 29], 'galv');
    lamp(m, 'aviation', 6.5, 26.4, 28.5, 'l_r', true, 0.6, 'up');
    return m;
  }
  function nacelleRear() {
    const m = mk(D + 'fibre');
    P(m, 'cover', [-6, 0, -16], [22, 18, 12], 'fibre', { split: 'xyz' });
    P(m, 'cooler', [-4, 2, 12], [20, 16, 16], 'fin');
    P(m, 'tail', [-4, 0, 16], [20, 2, 20], 'fibre');
    lamp(m, 'aviation_r', 2, 18.4, 0, 'l_r', true, 0.6, 'up');
    lamp(m, 'aviation_l', 14, 18.4, 0, 'l_r', true, 0.6, 'up');
    return m;
  }
  // Hub: spinner nose and three blade root flanges (at 0 and +-135 degrees, the nearest the 22.5-degree steps get
  // to 120), about z through (8, 8).
  function hub() {
    const m = mk(D + 'fibre');
    m.prism('spinner', 'z', [8, 8], 8, -16, 8, 'fibre', { split: 'z', ends: ['south'] });
    m.prism('nose', 'z', [8, 8], 5, -16, -12, 'fibre', { split: '', ends: ['north'] });
    m.prism('nose_tip', 'z', [8, 8], 2.4, -16, -14, 'fibre', { split: '', ends: ['north'] });
    P(m, 'root_up', [3, 15, -6], [13, 24, 4], 'ci');
    for (const a of [45, -45]) P(m, 'root' + a, [3, -8, -6], [13, 1, 4], 'ci', { rot: { axis: 'z', angle: a, origin: [8, 8, -1] } });
    return m;
  }
  // Blade segment along z (the blade's own axis) at (8, 8): an airfoil from a thick spar and a thin trailing edge.
  function blade(kind) {
    const m = mk(D + 'fibre');
    const mat = kind === 'steel' ? 'sblade' : kind === 'iced' ? 'ice' : 'fibre';
    P(m, 'spar', [4, 6.4, 0], [10, 9.6, 16], mat);
    P(m, 'lead', [2.6, 7, 0], [4, 9, 16], mat);
    P(m, 'trail', [10, 7.4, 0], [14, 8.6, 16], mat);
    P(m, 'te', [14, 7.8, 0], [15, 8.2, 16], mat);
    return m;
  }
  function windController(state) {
    const m = mk('switchgear_cell/enamel');
    cabinet(m, 'cab', [1, 0, 1], [15, 30, 15], 'en', { plate: true });
    m.box('disp', [3, 20, 0.6], [10, 25, 0.8], 'en', { split: '', face: { north: { tex: 'electronics/screen', uv: [0, 0, 16, 12] } } });
    lamp(m, 'run', 11.6, 24, 0.8, 'l_g', state === 'running', 0.6);
    lamp(m, 'storm', 11.6, 21.6, 0.8, 'l_a', state === 'storm', 0.6);
    P(m, 'estop', [5.4, 14, 0], [7.4, 16, 0.8], 'red');
    m.box('vent', [3, 2, 0.6], [13, 7, 0.8], 'en', { split: '', face: { north: { tex: 'grid_lines/louvre', uv: [0, 0, 16, 8] } } });
    return m;
  }

  const B = (model, cat, java) => ({ model, cat, java });
  const BUILDS = {
    diesel_engine: B(() => genset(false), 'grid'), 'diesel_engine/flywheel': B(engineFlywheel, 'grid', 'diesel_engine/partial/flywheel'),
    gas_engine: B(() => genset(true), 'grid'), 'gas_engine/flywheel': B(engineFlywheel, 'grid', 'gas_engine/partial/flywheel'),
    gasoline_engine: B(gasoline, 'grid'), 'gasoline_engine/crankshaft': B(gasolineCrank, 'grid', 'gasoline_engine/partial/crankshaft'),
    fired_heater_casing: B(() => heaterWall(false, false), 'boilers'), fired_heater_casing_lit: B(() => heaterWall(false, true), 'boilers'),
    fired_heater_casing_coil: B(() => heaterWall(true, false), 'boilers'), fired_heater_casing_coil_lit: B(() => heaterWall(true, true), 'boilers'),
    fired_heater_casing_roof: B(heaterRoof, 'boilers'),
    process_chromatograph: B(() => chromatograph('measuring'), 'instruments'), process_chromatograph_calibrating: B(() => chromatograph('calibrating'), 'instruments'),
    process_chromatograph_fault: B(() => chromatograph('fault'), 'instruments'),
    quality_analyzer: B(() => analyzer('nir'), 'instruments'), quality_analyzer_fault: B(() => analyzer('fault'), 'instruments'),
    quality_analyzer_octane_engine: B(() => analyzer('octane'), 'instruments'), 'quality_analyzer/flywheel': B(analyzerFly, 'instruments', 'quality_analyzer/partial/flywheel'),
    weather_station: B(weatherStation, 'instruments'),
    pgm_refinery: B(() => pgm(false), 'nonferrous'), pgm_refinery_running: B(() => pgm(true), 'nonferrous'),
    geothermal_wellhead: B(() => wellhead('idle'), 'geothermal'), geothermal_wellhead_flowing: B(() => wellhead('flowing'), 'geothermal'),
    geothermal_wellhead_shut: B(() => wellhead('shut'), 'geothermal'), 'geothermal_wellhead/stem': B(wellStem, 'geothermal', 'geothermal_wellhead/partial/stem'),
    flash_separator: B(() => flashSep(false), 'geothermal'), flash_separator_top: B(() => flashSep(true), 'geothermal'),
    ncg_ejector: B(ncgEjector, 'geothermal'),
    wind_tower_section: B(() => towerSection(false), 'wind'), wind_tower_section_top: B(() => towerSection(true), 'wind'),
    wind_nacelle: B(() => nacelle(false), 'wind'), wind_nacelle_iced: B(() => nacelle(true), 'wind'), wind_nacelle_rear: B(nacelleRear, 'wind'),
    wind_rotor_hub: B(hub, 'wind'),
    wind_blade: B(() => blade('glass'), 'wind'), wind_blade_steel: B(() => blade('steel'), 'wind'), wind_blade_iced: B(() => blade('iced'), 'wind'),
    wind_controller: B(() => windController('idle'), 'wind'), wind_controller_running: B(() => windController('running'), 'wind'),
    wind_controller_storm: B(() => windController('storm'), 'wind'),
  };
  window.M8P = FUTK.machine(BUILDS, paint);
})();
