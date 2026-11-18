// M9 chemicals, polymers, solar and storage equipment not yet in the mod, hand-authored. Replaces the
// spec_petrochem.py / spec_grid2.py / spec_metallurgy2.py placeholders of the chlor-alkali end plate,
// chlorination reactor, HCl synthesis furnace, tar still, VFD, fermentation vat, pyrolysis quench tower, quench
// exchanger, convection section, high-pressure autoclave and agitator, hot press and platen, hypercompressor and
// its crankshaft and plunger, loop reactor and its pump impeller, polymer stripper; draws the GDD 15 set without
// placeholders (ORC evaporator, pyranometer, PV panel, combiner box, inverters, tracker drive, battery rack).
// Run headless: node art/tools/pcart_headless.js <repo> petrochem/m9_chemicals.author.js M9C --paint
//
// Front to the north. Pivots as the placeholders: autoclave agitator about y through (8, 8); hot press platen in
// y at x/z 3..13; hypercompressor crankshaft about x through (y 6, z 8), plunger along z at x 7..9, y 5..7; loop
// pump impeller about x through (y -13, z 8); PV panel about x through (y 12, z 8) on the tracker; tracker torque
// tube about x through (y 12, z 8).
(function () {
  const { rng, img, put, get, add, mix, rect, grain } = PCART;
  const { P, lamp, cabinet, vessel, drum, flange, motor, gauge, ladder } = FUTK;
  const D = 'm9chem/', T = {};

  T.nickel = () => { const im = img(32, 32); grain(im, [190, 192, 186], 38101, 3, 4, 8); return im; };
  T.violet = () => { const im = img(16, 16), r = rng(38102); for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) put(im, x, y, add([200, 150, 240], (r() - 0.5) * 30)); return im; };
  T.tar = () => { const im = img(16, 16); grain(im, [70, 46, 30], 38103, 4, 6, 4); return im; };
  T.foam = () => { const im = img(16, 16), r = rng(38104); for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) put(im, x, y, add([226, 214, 170], (r() - 0.5) * 24)); return im; };
  T.beer = () => { const im = img(16, 16), r = rng(38105); for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) put(im, x, y, add([150, 110, 40], Math.sin(x * 0.7) * 6 + (r() - 0.5) * 8)); return im; };
  T.forging = () => { const im = img(32, 32), r = grain(im, [74, 74, 72], 38106, 4, 6, 8); for (let k = 0; k < 20; k++) put(im, r() * 32, r() * 32, [44, 44, 40]); return im; };
  const pv = (soil, snow) => () => {
    const im = img(32, 32), r = rng(38110 + soil);
    for (let y = 0; y < 32; y++) for (let x = 0; x < 32; x++) {
      let c = add([28, 44, 90], (r() - 0.5) * 8);
      if (x % 8 === 0 || y % 8 === 0) c = [170, 176, 182];
      if ((x % 8 === 3 || x % 8 === 5) && y % 8) c = [120, 130, 150];
      if (x === 0 || y === 0 || x === 31 || y === 31) c = [190, 194, 198];
      if (soil && r() < soil * 0.18) c = mix(c, [150, 130, 100], 0.6);
      if (snow && y > 10 + Math.sin(x * 0.4) * 3) c = add([236, 240, 244], (r() - 0.5) * 8);
      put(im, x, y, c);
    }
    return im;
  };
  T.pv = pv(0, false); T.pv_soiled = pv(2, false); T.pv_dirty = pv(3, false); T.pv_snow = pv(0, true);
  T.plastic = () => { const im = img(16, 16); grain(im, [168, 170, 166], 38121, 2, 3, 8); return im; };
  T.cells = () => { const im = img(16, 16); for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) put(im, x, y, x % 4 === 0 ? [30, 30, 32] : y < 3 ? [60, 62, 66] : [214, 214, 206]); return im; };
  T.flame_pipe = FUTK.enamel([220, 186, 40], 38131, 1);

  const MATX = {
    ni: { tex: D + 'nickel', P: 16 }, vio: { tex: D + 'violet', P: 16 }, tar: { tex: D + 'tar', P: 16 }, foam: { tex: D + 'foam', P: 16 },
    beer: { tex: D + 'beer', P: 16 }, forg: { tex: D + 'forging', P: 16 }, pv: { tex: D + 'pv', P: 16 }, pvs: { tex: D + 'pv_soiled', P: 16 },
    pvd: { tex: D + 'pv_dirty', P: 16 }, pvw: { tex: D + 'pv_snow', P: 16 }, pls: { tex: D + 'plastic', P: 16 }, cells: { tex: D + 'cells', P: 16 },
    ylw: { tex: D + 'flame_pipe', P: 16 }, gb: { tex: 'm8field/greyblue', P: 16 },
    riv: { tex: 'm3met/riveted', P: 16 }, finned: { tex: 'm8ref/finned', P: 8 }, refr: { tex: 'm8ref/refr', P: 16 }, gband: { tex: 'm8field/gasband', P: 16 },
  };
  const mk = (p) => FUTK.mk(MATX, p);
  const paint = (root) => FUTK.paintSet(root, D, T);

  // ---------------------------------------------------------------- chlor-alkali and chemicals
  function endPlate() {
    const m = mk('material/steel');
    P(m, 'plate', [0, -16, 4], [16, 32, 12], 'st', { split: 'y' });
    for (const y of [-12, 0, 12, 24]) for (const x of [1, 13]) m.prism(`nut_${x}_${y}`, 'z', [x + 1, y + 1], 1.2, 2.6, 4, 'ci', { split: '', ends: ['north'] });
    for (const x of [3, 9]) P(m, 'lead' + x, [x, -6, 0], [x + 4, 28, 4], 'cu', { split: 'y' });
    for (const x of [3, 11]) FUTK.insulatorY(m, 'ins' + x, [x + 1, 14], 1.4, -15.4, 4, 'porc', { pitch: 1 });
    return m;
  }
  function chlorination() {
    const m = mk(D + 'nickel');
    for (const a of [45, 135, 225, 315]) m.postY('leg' + a, [8, 8], 5, a, 1.2, 0, 6, 'frame');
    vessel(m, 'shell', [8, 8], 5.6, 6, 24, 'ni', { bottom: true });
    m.box('cut', [5, 10, 2.3], [11, 20, 2.5], 'refr', { split: '' });
    P(m, 'cut_frame', [4.6, 9.6, 2.2], [11.4, 10, 2.6], 'ni'); P(m, 'cut_frame_t', [4.6, 20, 2.2], [11.4, 20.4, 2.6], 'ni');
    m.ringY('ring', [8, 8], 5.9, 0.4, 21, 22.4, 'gband');
    P(m, 'cl2_in', [12, 8, 7.4], [16, 9.2, 8.6], 'p_gas');
    P(m, 'out', [7.4, 26, 7.4], [8.6, 28.07, 8.6], 'p_acid');
    return m;
  }
  function hclFurnace(lit) {
    const m = mk('steelmaking/graphite');
    P(m, 'base', [2, -16, 2], [14, -13, 14], 'frame');
    m.cylY('body', [8, 8], 5.4, -13, 26, 'graph');
    for (const y of [-6, 6, 18]) m.ringY('band' + y, [8, 8], 5.8, 0.4, y, y + 1, 'st');
    m.cylY('top', [8, 8], 3, 26, 30, 'graph', { cap: 'steelmaking/graphite' });
    P(m, 'hcl_out', [7.4, 30, 7.4], [8.6, 31.93, 8.6], 'p_acid');
    P(m, 'peep', [6, -6, 2.2], [10, -2, 2.6], 'ci');
    P(m, lit ? 'glow_flame' : 'peep_glass', [6.6, -5.4, 2], [9.4, -2.6, 2.2], lit ? 'vio' : 'vdark');
    P(m, 'h2_in', [12, -12, 7.4], [16, -10.8, 8.6], 'p_gas'); P(m, 'cl2_in', [12, -10, 7.4], [16, -8.8, 8.6], 'gband');
    P(m, 'cw_in', [12, 0, 1.2], [13, 22, 2.2], 'p_water');
    return m;
  }
  function tarStill(running) {
    const m = mk('material/brick');
    P(m, 'furnace', [1, -16, 1], [15, -4, 15], 'brick');
    P(m, 'fire_door', [5, -14, 0.6], [11, -9, 1], 'ci');
    if (running) P(m, 'glow_fire', [6, -13, 0.4], [10, -10, 0.6], 'fglow');
    m.cylY('kettle', [8, 8], 6, -4, 4, 'st', { cap: 'material/steel' });
    m.cylY('column', [8, 8], 3.6, 4, 28, 'st', { cap: 'material/steel' });
    for (const y of [8, 14, 20]) m.ringY('tray' + y, [8, 8], 3.9, 0.3, y, y + 0.6, 'ci');
    P(m, 'vapour', [7.4, 28, 7.4], [8.6, 31.97, 8.6], 'tar'); P(m, 'vapour_run', [8.6, 30.6, 7.4], [16.03, 31.8, 8.6], 'tar');
    P(m, 'pitch_out', [13, -6, 7.4], [16.03, -4.8, 8.6], 'tar');
    P(m, 'feed', [11, 12, 7.4], [16.03, 13.2, 8.6], 'tar');
    P(m, 'chimney', [12, -4, 12], [14, 8, 14], 'brick');
    return m;
  }
  function vfd(state) {
    const m = mk('switchgear_cell/enamel');
    cabinet(m, 'cab', [1, 0, 0.4], [15, 32, 15], 'en', { plate: true });
    m.box('disp', [3, 22, 0], [9, 26, 0.2], 'en', { split: '', face: { north: { tex: 'electronics/screen', uv: [0, 0, 16, 10] } } });
    lamp(m, 'run', 11, 25, 0.2, 'l_g', state !== 'off', 0.6);
    lamp(m, 'fault', 13, 25, 0.2, 'l_r', state === 'fault', 0.6);
    for (const y of [3, 13]) m.box('vent' + y, [3, y, 0], [13, y + 6, 0.2], 'en', { split: '', face: { north: { tex: 'grid_lines/louvre', uv: [0, 0, 16, 10] } } });
    P(m, 'roof_fan', [4, 32, 4], [12, 32.01, 12], 'dk', { hide: ['down'] });
    return m;
  }
  function fermenter(fermenting) {
    const m = mk('m3met/riveted');
    m.cylY('vat', [16, 16], 15, 0, 26, 'riv');
    m.ringY('rim', [16, 16], 15.4, 0.8, 25, 26.4, 'st');
    for (const y of [6, 16]) m.ringY('hoop' + y, [16, 16], 15.3, 0.4, y, y + 1, 'st');
    m.cylY('liquid', [16, 16], 14.2, 20, 22, fermenting ? 'foam' : 'beer', { cap: fermenting ? D + 'foam' : D + 'beer' });
    P(m, 'agit_bridge', [0, 26, 14.6], [32, 27.6, 17.4], 'frame');
    P(m, 'drain', [14.8, 1, -0.4], [17.2, 3.4, 1.4], 'p_liq');
    ladder(m, 'ladder', 28, 1, 0, 26, 'x');
    return m;
  }

  // ---------------------------------------------------------------- pyrolysis and furnaces
  function quenchTower() {
    const m = mk('material/steel');
    m.cylY('shell', [8, 8], 13, -16, 28, 'st');
    m.cylY('head', [8, 8], 10, 28, 31.97, 'st', { cap: 'material/steel' });
    for (const y of [-4, 8, 20]) {
      P(m, 'spray' + y, [21, y, 6], [31.95, y + 1.6, 9], 'p_water');
      m.ringY('platform_ring' + y, [8, 8], 14, 1.2, y - 1, y - 0.4, 'grat');
    }
    P(m, 'gas_in', [-15.95, -10, 4], [-5, -4, 12], 'st');
    P(m, 'water_out', [6, -16, -10], [10, -12, -5], 'p_water');
    ladder(m, 'ladder', 6.6, -6, -16, 28, 'x');
    return m;
  }
  function quenchExchanger() {
    const m = mk('material/steel');
    m.cylY('tube', [8, 8], 4.6, -15.99, 20, 'st');
    for (const y of [-12, 0, 12]) m.ringY('flange' + y, [8, 8], 5.4, 0.8, y, y + 1, 'ci');
    drum(m, 'drum', 'x', [26, 8], 3.6, 3.4, 12.6, 'st');
    P(m, 'riser', [7.4, 20, 7.4], [8.6, 22.4, 8.6], 'p_steam');
    P(m, 'steam_out', [7.4, 29.6, 7.4], [8.6, 31.97, 8.6], 'p_steam');
    P(m, 'steam_band', [7.2, 30.6, 7.2], [8.8, 31.2, 8.8], 'red');
    P(m, 'downcomer', [12.6, 4, 7.4], [16.03, 5.2, 8.6], 'p_steam');
    P(m, 'gas_out', [12, -10, 6], [16.03, -6, 10], 'st');
    return m;
  }
  function convection() {
    const m = mk('plant/furnace_casing');
    P(m, 'casing', [-16, -16, -12], [32, 32, 28], 'casing', { split: 'xyz' });
    P(m, 'cutaway', [-12, -12, -12.2], [28, 24, -12], 'finned', { hide: ['south'] });
    for (const x of [-16, 30]) P(m, 'buckstay' + x, [x, -16, -14], [x + 2, 32, -12], 'frame');
    for (const y of [-6, 6, 18]) P(m, 'header_' + y, [28, y, -14], [32, y + 2, 26], 'p_steam');
    P(m, 'sootblower', [-16, 0, -14], [-12, 2, 26], 'st');
    return m;
  }
  function orcEvap(head) {
    const m = mk(D + 'flame_pipe');
    if (!head) {
      m.prism('shell', 'z', [8, 8], 13, -16, 32, 'ylw', { split: 'z' });
      for (const z of [-12, 24]) P(m, 'saddle' + z, [-4, -16, z], [20, -6, z + 4], 'frame');
      P(m, 'vapour_out', [5, 20, 6], [11, 32, 10], 'ylw');
      P(m, 'band', [4.8, 26, 5.8], [11.2, 27, 10.2], 'red');
      P(m, 'brine_in', [-8, -14, 10], [-4, -8, 14], 'p_water');
    } else {
      m.prism('shell', 'z', [8, 8], 13, 8, 32, 'ylw', { split: 'z' });
      m.prism('channel', 'z', [8, 8], 12, -8, 8, 'ylw', { split: 'z' });
      m.prism('cover', 'z', [8, 8], 13.6, -12, -8, 'ylw', { split: '', ends: ['north'] });
      m.ring('flange', 'z', [8, 8], 13.6, 1.4, 7, 8, 'ci');
      for (const y of [-10, 26]) P(m, 'noz' + y, [5, y < 0 ? -16 : 20, -4], [11, y < 0 ? -4 : 32, 2], 'p_water');
    }
    return m;
  }

  // ---------------------------------------------------------------- polymers
  function autoclave() {
    const m = mk(D + 'forging');
    for (const [f, t] of [[[0, -16, 13], [16, 32, 14]], [[0, -16, 0], [1, 32, 13]], [[15, -16, 0], [16, 32, 13]]]) P(m, 'bunker', f, t, 'conc', { split: 'y' });
    P(m, 'skirt', [3, -16, 3], [13, -12, 13], 'frame');
    m.cylY('body', [8, 8], 5, -12, 22, 'forg');
    m.cylY('closure', [8, 8], 5.8, 22, 25, 'forg', { cap: D + 'forging' });
    for (const a of [0, 45, 90, 135, 180, 225, 270, 315]) m.postY('stud' + a, [8, 8], 5.4, a, 0.6, 24.6, 26, 'st');
    m.cylY('mag_drive', [8, 8], 2, 25, 28, 'forg', { cap: D + 'forging' });
    motor(m, 'motor', 'z', [8, 29.2], 1.2, 4, 12, 'blue', { feet: false });
    P(m, 'burst', [12, 24, 7], [14, 26, 9], 'red');
    P(m, 'burst_vent', [13, 26, 7.4], [14, 31.93, 8.6], 'st');
    return m;
  }
  function autoclaveAgitator() {
    const m = mk('material/steel');
    m.cylY('shaft', [8, 8], 0.6, -14.03, 28.03, 'st', { cap: 'material/steel', bottom: true });
    for (const y of [-12, 4]) for (const a of [0, 45]) P(m, `blade_${y}_${a}`, [4, y, 7.4], [12, y + 1, 8.6], 'st', { rot: a ? { axis: 'y', angle: a, origin: [8, 8, 8] } : undefined });
    return m;
  }
  function hotPress(hot) {
    const m = mk('electronics/ss');
    P(m, 'bed', [1, 0, 1], [15, 8, 15], 'frame');
    for (const [x, z] of [[2, 2], [12.6, 2], [2, 12.6], [12.6, 12.6]]) m.cylY(`col_${x}_${z}`, [x + 0.7, z + 0.7], 0.8, 8, 27, 'bright');
    P(m, 'lower_platen', [3, 8, 3], [13, 13, 13], 'ss');
    if (hot) P(m, 'glow_heater', [2.8, 9, 2.8], [13.2, 11, 13.2], 'fglow', { hide: ['up', 'down'] });
    P(m, 'crown', [1, 27, 1], [15, 31, 15], 'frame');
    m.cylY('cyl', [8, 8], 3, 26, 27, 'bright');
    P(m, 'panel', [12, 2, 0], [15, 7, 1], 'dk');
    lamp(m, 'heat', 13.5, 5.5, 0, 'l_a', hot, 0.5);
    return m;
  }
  function platen() { const m = mk('electronics/ss'); P(m, 'platen', [3, 14, 3], [13, 19, 13], 'ss'); m.cylY('ram', [8, 8], 2.4, 19, 26, 'bright', { cap: 'material/bright_steel' }); return m; }
  function hyper() {
    const m = mk('m3grid/tank');
    P(m, 'frame', [-16, 0, 4], [32, 10, 16], 'forg', { split: 'x' });
    for (const x of [4, 20]) {
      P(m, 'guard' + x, [x, 2, -0.03], [x + 8, 12, 4], 'yel');
      m.prism('cyl' + x, 'z', [x + 4, 6], 2.4, -0.03, 4, 'forg', { split: '', ends: ['north'] });
    }
    P(m, 'top', [-16, 10, 4], [32, 12, 16], 'forg');
    for (const x of [-12, 26]) P(m, 'lube' + x, [x, 12, 8], [x + 3, 15, 12], 'yel');
    return m;
  }
  function hyperCrank() { const m = mk('material/steel'); m.prism('shaft', 'x', [6, 8], 1.2, -15.95, 31.95, 'bright', { split: '', ends: ['west', 'east'] }); for (const x of [6, 22]) P(m, 'web' + x, [x, 3.5, 6.5], [x + 1.6, 8.5, 9.5], 'st'); return m; }
  function plunger() { const m = mk('material/bright_steel'); P(m, 'rod', [7, 5, -0.03], [9, 7, 6.03], 'bright'); return m; }
  function loopReactor(top) {
    const m = mk(D + 'flame_pipe');
    for (const x of [-12, 24]) {
      m.cylY('leg' + x, [x + 2, 8], 3, top ? -16 : -10, top ? 26 : 32, 'ylw');
      for (let y = top ? -12 : -6; y < (top ? 24 : 32); y += 10) m.ringY(`ring_${x}_${y}`, [x + 2, 8], 3.3, 0.4, y, y + 1, 'red');
    }
    if (top) m.prism('elbow', 'x', [26, 8], 3, -12, 28, 'ylw', { split: 'x', ends: [] });
    else {
      m.prism('elbow', 'x', [-13, 8], 3, -12, 28, 'ylw', { split: 'x' });
      m.prism('pump_case', 'x', [-12.4, 8], 3.4, 2, 14, 'ylw', { split: '' });
      motor(m, 'pump_motor', 'x', [-13, 8], 2.4, -12, -4, 'blue', { feet: false });
    }
    return m;
  }
  function loopImpeller() { const m = mk('material/steel'); m.prism('shaft', 'x', [-13, 8], 0.6, 1.97, 14.03, 'st', { split: '', ends: ['west', 'east'] }); for (const a of [0, 45, -45]) P(m, 'blade' + a, [7, -16, 7.6], [9, -10, 8.4], 'st', { rot: a ? { axis: 'x', angle: a, origin: [8, -13, 8] } : undefined }); P(m, 'blade90', [7, -13.4, 5], [9, -12.6, 11], 'st'); return m; }
  function stripper() {
    const m = mk('material/steel');
    for (const a of [45, 135, 225, 315]) m.postY('leg' + a, [7.25, 8], 5.4, a, 1, 0, 10, 'frame');
    m.cylY('cone', [7.25, 8], 3, 4, 8, 'st', { bottom: true });
    m.cylY('cone2', [7.25, 8], 5, 8, 11, 'st');
    m.cylY('body', [7.25, 8], 6, 11, 27, 'st', { cap: 'material/steel' });
    P(m, 'feed', [6.6, 27, 7.4], [8, 30.07, 8.6], 'st');
    P(m, 'steam', [-0.03, 14, 7.4], [1.4, 15.2, 8.6], 'p_steam');
    P(m, 'steam_ring', [0.4, 13.8, 7.2], [1, 15.4, 8.8], 'red');
    P(m, 'outlet', [6.6, 0, 7.4], [8, 4, 8.6], 'st');
    return m;
  }

  // ---------------------------------------------------------------- solar and storage (GDD 15)
  function pyranometer() {
    const m = mk('m6auto/white');
    P(m, 'post', [7.4, 0, 7.4], [8.6, 6, 8.6], 'galv');
    m.cylY('body', [8, 8], 3, 6, 8, 'wh', { cap: 'm6auto/white' });
    m.cylY('shield', [8, 8], 4, 7, 7.6, 'wh', { cap: 'm6auto/white' });
    m.cylY('dome', [8, 8], 1.6, 8, 9.4, 'glass', { cap: 'electronics/glass' });
    P(m, 'level', [10.4, 8, 7.4], [11.4, 8.4, 8.4], 'glass');
    return m;
  }
  // Fixed-mount panel on its rack at 22.5 degrees, south facing; the soiling and snow variants change the texture.
  function panelFixed(tex) {
    const m = mk(D + 'pv');
    for (const z of [2, 13]) P(m, 'leg' + z, [1, 0, z], [2, z < 8 ? 3 : 8, z + 1], 'galv'), P(m, 'leg_e' + z, [14, 0, z], [15, z < 8 ? 3 : 8, z + 1], 'galv');
    P(m, 'panel', [0, 4, 0], [16, 5, 16], 'alu', { rot: { axis: 'x', angle: 22.5, origin: [8, 5.5, 8] }, face: { up: { tex: D + tex, uv: [0, 0, 16, 16] } } });
    return m;
  }
  function panelTrackerBase() { const m = mk(D + 'pv'); P(m, 'post', [7, 0, 7], [9, 11, 9], 'galv'); P(m, 'bearing', [6.4, 10, 6.4], [9.6, 13, 9.6], 'ci'); return m; }
  function panelModule() { const m = mk(D + 'pv'); P(m, 'module', [0, 13, 0], [16, 14, 16], 'alu', { face: { up: { tex: D + 'pv', uv: [0, 0, 16, 16] } } }); P(m, 'clamp', [6.6, 12, 0], [9.4, 13, 16], 'galv'); return m; }
  function trackerDrive() {
    const m = mk('disconnector/galvanised');
    P(m, 'post', [6, 0, 6], [10, 9, 10], 'galv');
    P(m, 'gearbox', [5, 9, 4], [11, 15, 12], 'gb');
    motor(m, 'motor', 'z', [8, 16.4], 1.2, 9, 13, 'gb', { feet: false });
    P(m, 'ctrl', [11, 4, 6.6], [13, 8, 9.4], 'pls');
    return m;
  }
  function torqueTube() { const m = mk('disconnector/galvanised'); P(m, 'tube', [0, 11, 7], [16, 13, 9], 'galv'); return m; }
  function combiner() {
    const m = mk(D + 'plastic');
    P(m, 'box', [3, 3, 11], [13, 13, 16], 'pls');
    P(m, 'lid', [3.4, 3.4, 10.6], [12.6, 12.6, 11], 'pls');
    for (const x of [4.4, 6.6, 8.8, 11]) P(m, 'gland' + x, [x - 0.5, 1.6, 13], [x + 0.5, 3, 14], 'bk');
    for (const x of [4.4, 6.6, 8.8, 11]) P(m, 'cable' + x, [x - 0.3, 0, 13.2], [x + 0.3, 1.6, 13.8], 'bk');
    m.decal('hv', [6.4, 9, 10.5], [9.6, 11.6, 10.6], 'pls', 'north', 'parts.warning');
    return m;
  }
  function inverter(state, battery) {
    const m = mk('switchgear_cell/enamel');
    cabinet(m, 'cab', [1, 0, 1], [15, 30, 15], 'en', { plate: true });
    if (battery) P(m, 'stripe', [1, 27, 0.6], [15, 28.4, 0.8], 'p_air');
    P(m, (state === 'off' ? '' : 'glow_') + 'disp', [3, 20, 0.6], [10, 24, 0.8], state === 'fault' ? 'l_r' : state === 'off' ? 'vdark' : 'l_g');
    lamp(m, 'run', 12, 22, 0.8, state === 'forming' ? 'l_a' : 'l_g', state === 'running' || state === 'forming', 0.6);
    lamp(m, 'fault', 12, 19.6, 0.8, 'l_r', state === 'fault', 0.6);
    for (const y of [3, 11]) m.box('vent' + y, [3, y, 0.6], [13, y + 5, 0.8], 'en', { split: '', face: { north: { tex: 'grid_lines/louvre', uv: [0, 0, 16, 8] } } });
    return m;
  }
  function batteryRack(state) {
    const m = mk('material/steel');
    for (const [x, z] of [[1, 1], [14, 1], [1, 14], [14, 14]]) P(m, `post_${x}_${z}`, [x, 0, z], [x + 1, 30, z + 1], 'frame');
    for (const y of [0, 7.4, 14.8, 22.2]) {
      P(m, 'shelf' + y, [1, y, 1], [15, y + 0.8, 15], 'frame');
      for (let x = 2; x < 14; x += 3) P(m, `cell_${y}_${x}`, [x, y + 0.8, 3], [x + 2.6, y + 6.4, 13], 'cells');
      for (let x = 3.6; x < 12; x += 3) P(m, `link_${y}_${x}`, [x, y + 6.4, 6], [x + 2, y + 6.8, 7], 'cu');
    }
    P(m, 'top', [1, 29, 1], [15, 30, 15], 'frame');
    const mat = { charging: 'l_a', discharging: 'l_g', fault: 'l_r', idle: 'l_off' }[state];
    lamp(m, 'state', 8, 29.5, 0.6, mat, state !== 'idle', 0.5);
    return m;
  }

  const B = (model, cat, java) => ({ model, cat, java });
  const BUILDS = {
    chlor_alkali_end_plate: B(endPlate, 'chlor_alkali'), chlorination_reactor: B(chlorination, 'chlor_alkali'),
    hcl_synthesis_furnace: B(() => hclFurnace(false), 'chlor_alkali'), hcl_synthesis_furnace_lit: B(() => hclFurnace(true), 'chlor_alkali'),
    tar_still: B(() => tarStill(false), 'coke'), tar_still_running: B(() => tarStill(true), 'coke'),
    variable_frequency_drive: B(() => vfd('off'), 'electronics'), variable_frequency_drive_on: B(() => vfd('on'), 'electronics'),
    variable_frequency_drive_fault: B(() => vfd('fault'), 'electronics'),
    fermentation_vat: B(() => fermenter(false), 'petrochem'), fermentation_vat_fermenting: B(() => fermenter(true), 'petrochem'),
    pyrolysis_quench_tower: B(quenchTower, 'petrochem'), quench_exchanger: B(quenchExchanger, 'petrochem'), tube_furnace_convection: B(convection, 'petrochem'),
    orc_evaporator: B(() => orcEvap(false), 'geothermal'), orc_evaporator_head: B(() => orcEvap(true), 'geothermal'),
    high_pressure_autoclave: B(autoclave, 'polymers'), 'high_pressure_autoclave/agitator': B(autoclaveAgitator, 'polymers', 'high_pressure_autoclave/partial/agitator'),
    hot_press: B(() => hotPress(false), 'polymers'), hot_press_hot: B(() => hotPress(true), 'polymers'), 'hot_press/platen': B(platen, 'polymers', 'hot_press/partial/platen'),
    hyper_compressor: B(hyper, 'polymers'), 'hyper_compressor/crankshaft': B(hyperCrank, 'polymers', 'hyper_compressor/partial/crankshaft'),
    'hyper_compressor/plunger': B(plunger, 'polymers', 'hyper_compressor/partial/plunger'),
    loop_reactor: B(() => loopReactor(false), 'polymers'), loop_reactor_top: B(() => loopReactor(true), 'polymers'),
    'loop_reactor/impeller': B(loopImpeller, 'polymers', 'loop_reactor/partial/impeller'),
    polymer_stripper: B(stripper, 'polymers'),
    pyranometer: B(pyranometer, 'instruments'),
    solar_panel: B(() => panelFixed('pv'), 'solar'), solar_panel_soiled: B(() => panelFixed('pv_soiled'), 'solar'),
    solar_panel_dirty: B(() => panelFixed('pv_dirty'), 'solar'), solar_panel_snow: B(() => panelFixed('pv_snow'), 'solar'),
    solar_panel_tracker: B(panelTrackerBase, 'solar'), 'solar_panel/panel': B(panelModule, 'solar', 'solar_panel/partial/panel'),
    solar_tracker_drive: B(trackerDrive, 'solar'), 'solar_tracker_drive/torque_tube': B(torqueTube, 'solar', 'solar_tracker_drive/partial/torque_tube'),
    solar_combiner_box: B(combiner, 'solar'),
    solar_inverter: B(() => inverter('off', false), 'solar'), solar_inverter_running: B(() => inverter('running', false), 'solar'),
    solar_inverter_fault: B(() => inverter('fault', false), 'solar'),
    battery_inverter: B(() => inverter('off', true), 'storage'), battery_inverter_running: B(() => inverter('running', true), 'storage'),
    battery_inverter_forming: B(() => inverter('forming', true), 'storage'),
    grid_battery_rack: B(() => batteryRack('idle'), 'storage'), grid_battery_rack_charging: B(() => batteryRack('charging'), 'storage'),
    grid_battery_rack_discharging: B(() => batteryRack('discharging'), 'storage'), grid_battery_rack_fault: B(() => batteryRack('fault'), 'storage'),
  };
  window.M9C = FUTK.machine(BUILDS, paint);
})();
