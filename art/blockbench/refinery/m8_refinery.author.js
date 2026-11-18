// M8 refinery units not yet in the mod, hand-authored (replaces the spec_oilgas.py placeholders): air cooler and
// fan, Claus reaction furnace, column bottoms, kettle reboiler, dewaxing drum filter, FCC reactor, regenerator and
// riser, quality lab with the CFR engine flywheel, reflux drum, shell-and-tube exchanger, slide valve and stem,
// steam ejector, sulphur condenser, granulator and its drum, sulphur pit.
// Run headless: node art/tools/pcart_headless.js <repo> refinery/m8_refinery.author.js M8R --paint
//
// Front to the north. Footprints and pivots as the placeholders: air cooler fan about y through (8, 8) under the
// bundles; dewaxing drum about x through (y 12, z 16); CFR flywheel about z through (7, 15); slide valve stem
// slides along x at (y 8, z 8); granulator drum about x through (y 16, z 16). Column bottoms keep the placeholder
// centres ((9, 7) small, (6, 6) large).
(function () {
  const { rng, img, put, get, add, mix, rect, grain, vnoise } = PCART;
  const { P, lamp, cabinet, vessel, drum, flange, motor, gauge, ladder } = FUTK;
  const D = 'm8ref/', T = {};

  T.sblue = FUTK.enamel([118, 136, 150], 35101, 3);
  T.finned = () => { const im = img(16, 16), r = rng(35111); for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) put(im, x, y, add(x % 2 ? [150, 154, 156] : [96, 100, 104], (y % 4 === 0 ? -30 : 0) + (r() - 0.5) * 8)); return im; };
  T.refr = () => { const im = img(32, 32), r = grain(im, [138, 134, 128], 35121, 6, 8, 8); for (let k = 0; k < 30; k++) put(im, r() * 32, r() * 32, [104, 100, 94]); return im; };
  T.frost = () => { const im = img(16, 16), r = rng(35131); for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) put(im, x, y, add(r() < 0.5 ? [214, 226, 234] : [176, 192, 204], (r() - 0.5) * 10)); return im; };
  T.sulfur = () => { const im = img(16, 16), r = rng(35141); for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) put(im, x, y, add(mix([214, 150, 40], [236, 196, 70], (Math.sin(x * 0.6 + y * 0.4) + 1) / 2), (r() - 0.5) * 10)); return im; };
  T.sulfur_dust = () => { const im = img(16, 16), r = grain(im, [212, 196, 90], 35151, 6, 8, 4); return im; };
  T.drips = () => {
    const im = img(16, 16), r = rng(35161); grain(im, [118, 136, 150], 35162, 3, 4, 8);
    for (let k = 0; k < 6; k++) { const x = Math.floor(r() * 16), l = 3 + Math.floor(r() * 10); for (let y = 0; y < l; y++) put(im, x, y, [214, 180, 50]); put(im, x, l, [190, 150, 30]); }
    return im;
  };
  T.cfr = FUTK.enamel([170, 40, 34], 35171, 0);
  T.cloth = () => { const im = img(16, 16), r = rng(35181); for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) put(im, x, y, add(x % 4 === 0 ? [80, 82, 80] : [150, 148, 136], (r() - 0.5) * 10)); return im; };

  const MATX = {
    sb: { tex: D + 'sblue', P: 16 }, finned: { tex: D + 'finned', P: 8 }, refr: { tex: D + 'refr', P: 16 }, frost: { tex: D + 'frost', P: 16 },
    sulf: { tex: D + 'sulfur', P: 16 }, sdust: { tex: D + 'sulfur_dust', P: 16 }, drips: { tex: D + 'drips', P: 16 }, cfr: { tex: D + 'cfr', P: 16 },
    cloth: { tex: D + 'cloth', P: 16 },
  };
  const mk = (p) => FUTK.mk(MATX, p);
  const paint = (root) => FUTK.paintSet(root, D, T);
  const saddle = (m, n, x, w, y1, z0, z1) => P(m, n, [x, 0, z0], [x + w, y1, z1], 'frame');

  // ---------------------------------------------------------------- air cooler
  function airCooler() {
    const m = mk(D + 'finned');
    for (const x of [-16, 30.6]) for (const z of [-16, 30.6]) P(m, `leg_${x}_${z}`, [x, -16, z], [x + 1.4, 4, z + 1.4], 'frame', { split: 'y' });
    for (const z of [-16, 30.6]) P(m, 'beam' + z, [-16, 2.6, z], [32, 4, z + 1.4], 'frame');
    for (const x of [-16, 29]) P(m, 'header' + x, [x, 4, -15], [x + 3, 12, 31], 'sb');
    P(m, 'bundle', [-13, 5, -15], [29, 11, 31], 'finned');
    P(m, 'side_n', [-13, 4, -15.4], [29, 12, -15], 'frame'); P(m, 'side_s', [-13, 4, 31], [29, 12, 31.4], 'frame');
    P(m, 'inlet', [-15, 12, 6], [-13, 15, 10], 'p_liq'); P(m, 'outlet', [29, 12, 6], [31, 15, 10], 'p_liq');
    m.cylY('plenum', [8, 8], 12, -9, 4, 'galv');
    m.ringY('ring', [8, 8], 12.6, 0.6, -9.4, -8.6, 'galv');
    P(m, 'motor_frame', [4, -16, 4], [12, -14, 12], 'frame');
    motor(m, 'motor', 'z', [8, -11.6], 2.4, 3, 13, 'blue', { feet: false });
    P(m, 'shaft_bearing', [6.6, -9, 6.6], [9.4, -8, 9.4], 'ci');
    return m;
  }
  function acFan() {
    const m = mk('disconnector/galvanised');
    m.cylY('shaft', [8, 8], 0.8, -8, -2, 'bright', { cap: 'material/bright_steel' });
    m.cylY('hub', [8, 8], 2, -2, 0.2, 'galv', { cap: 'disconnector/galvanised', bottom: true });
    for (const a of [0, 45, -45, 90]) {
      const f = a === 90 ? [7, -1.4, -3] : [-3, -1.4, 7], t = a === 90 ? [9, -0.6, 19] : [19, -0.6, 9];
      P(m, 'blade' + a, f, t, 'galv', { rot: a && a !== 90 ? { axis: 'y', angle: a, origin: [8, 8, 8] } : undefined });
    }
    return m;
  }

  // ---------------------------------------------------------------- sulphur recovery
  function claus(lit) {
    const m = mk(D + 'refr');
    for (const x of [-12, 4, 20, 28]) saddle(m, 'saddle' + x, x, 2, 4, 9, 23);
    m.prism('furnace', 'x', [11, 16], 7, -12, 12, 'refr', { split: 'x' });
    m.prism('furnace_cap', 'x', [11, 16], 6, -13.4, -12, 'refr', { split: '', ends: ['west'] });
    m.prism('burner', 'x', [11, 16], 2.4, -15.95, -13.4, 'ci', { split: '', ends: ['west'] });
    P(m, 'air_in', [-15, 11, 7], [-13.6, 17, 9], 'p_air');
    P(m, 'gas_in', [-15, 14, 23], [-13.6, 15.4, 24.6], 'p_gas');
    P(m, 'peep_box', [-11, 17.4, 10], [-8, 19.4, 13], 'ci');
    P(m, lit ? 'glow_peep' : 'peep', [-10.4, 19.4, 10.6], [-8.6, 19.6, 12.4], lit ? 'melt' : 'vdark');
    m.prism('whb', 'x', [11, 16], 6, 12, 31.99, 'sb', { split: 'x', ends: ['east'] });
    m.ring('whb_flange', 'x', [11, 16], 7.6, 1.2, 12, 13, 'ci');
    drum(m, 'steam_drum', 'x', [20, 16], 2.6, 16, 28, 'sb');
    for (const x of [18, 26]) P(m, 'downcomer' + x, [x, 16, 15.4], [x + 1.2, 18, 16.6], 'sb');
    P(m, 'steam_out', [21.4, 22.4, 15.4], [22.6, 23, 16.6], 'p_steam');
    P(m, 'sulfur_leg', [30, 0, 15.4], [31.2, 5, 16.6], 'sulf');
    P(m, 'shell_plate', [-6, 10, 8.8], [-2, 12, 9], 'refr');
    m.decal('plate', [-6, 10, 8.75], [-2, 12, 8.85], 'refr', 'north', 'parts.plate');
    return m;
  }
  function sulfurCondenser() {
    const m = mk(D + 'drips');
    for (const x of [3, 26]) saddle(m, 'saddle' + x, x, 3, 4, 4, 12);
    m.prism('shell', 'x', [7, 8], 4.5, 2, 30, 'drips', { split: 'x' });
    for (const x of [0, 30]) m.prism('head' + x, 'x', [7, 8], 4, x === 0 ? -0.01 : 30, x === 0 ? 2 : 31.99, 'sb', { split: '', ends: [x === 0 ? 'west' : 'east'] });
    for (const x of [8, 22]) P(m, 'steam' + x, [x, 11, 7.4], [x + 1.2, 12, 8.6], 'p_steam');
    P(m, 'rundown', [28, 0, 7.4], [29.2, 3, 8.6], 'sulf');
    P(m, 'seal_pot', [26.6, 0, 3], [30.6, 2, 7], 'sb');
    return m;
  }
  function granulator() {
    const m = mk(D + 'sulfur_dust');
    for (const x of [2, 22]) {
      P(m, 'pier' + x, [x, 0, 6], [x + 5, 4, 26], 'conc');
      for (const z of [9, 23]) m.prism(`roller_${x}_${z}`, 'x', [5.6, z], 1.6, x, x + 5, 'ci', { split: '', ends: ['west', 'east'] });
    }
    P(m, 'hood', [28, 4, 4], [32, 30, 28], 'st');
    P(m, 'hood_dust', [28, 4, 3.8], [32, 10, 4], 'sdust');
    P(m, 'feed', [0, 22, 13], [4, 30, 19], 'st');
    P(m, 'feed_jacket', [1, 30, 14], [3, 32, 18], 'p_steam');
    P(m, 'exhaust', [29, 30, 13], [32, 32, 19], 'st');
    motor(m, 'motor', 'x', [3, 28], 2.2, 8, 16, 'green', { feet: false });
    P(m, 'gear_guard', [16, 0, 26], [22, 8, 30], 'yel');
    P(m, 'dust_floor', [0, 0, 2], [32, 0.2, 30], 'sdust', { hide: ['down'] });
    return m;
  }
  function granDrum() {
    const m = mk('material/steel');
    m.prism('shell', 'x', [16, 16], 9.4, 0, 28, 'st', { split: 'x', ends: ['west', 'east'] });
    for (const x of [3, 23]) m.prism('tyre' + x, 'x', [16, 16], 10, x, x + 2, 'ci', { split: '', ends: ['west', 'east'] });
    m.prism('gear', 'x', [16, 16], 10, 18, 19.6, 'ci', { split: '', ends: ['west', 'east'] });
    P(m, 'dust', [0, 6.2, 12], [28, 7, 20], 'sdust');
    return m;
  }
  function sulfurPit(level) {
    const m = mk('plant/concrete');
    P(m, 'floor', [-16, 0, -16], [32, 1, 32], 'conc');
    for (const [f, t] of [[[-16, 1, -16], [32, 16, -13]], [[-16, 1, 29], [32, 16, 32]], [[-16, 1, -13], [-13, 16, 29]], [[29, 1, -13], [32, 16, 29]]]) P(m, 'wall', f, t, 'conc');
    for (let z = -10; z < 28; z += 6) P(m, 'coil' + z, [-12, 2, z], [28, 3, z + 1], 'p_steam');
    if (level) P(m, 'sulfur', [-13, 1, -13], [29, level, 29], 'sulf', { hide: ['down'] });
    if (level) for (let x = -13; x < 29; x += 8) P(m, 'grate' + x, [x, 15.6, -13], [Math.min(29, x + 7.6), 16, 29], 'grat');
    P(m, 'pump', [20, 16, 20], [26, 22, 26], 'blue');
    P(m, 'pump_col', [22, 4, 22], [24, 16, 24], 'sulf');
    P(m, 'vent', [-10, 16, -10], [-7, 26, -7], 'st');
    m.decal('warn', [4, 12, -16.1], [8, 15.6, -16], 'conc', 'north', 'parts.warning');
    return m;
  }

  // ---------------------------------------------------------------- columns, drums, exchangers
  function columnBase(kind) {
    const m = mk(kind === 'insulated' ? 'plant/cladding' : D + 'sblue');
    const big = kind === 'large', c = big ? [6, 6] : [9, 7], r = big ? 21.4 : 6.6, mat = kind === 'insulated' ? 'clad' : 'sb';
    const y0 = big ? -16 : 0;
    m.cylY('skirt', c, r * 0.94, y0, y0 + (big ? 12 : 8), 'sb');
    m.cylY('shell', c, r, y0 + (big ? 12 : 8), 32, mat);
    P(m, 'access', big ? [c[0] - 4, y0, c[1] - r - 0.2] : [c[0] - 2, 1, c[1] - r * 0.94 - 0.2], big ? [c[0] + 4, y0 + 8, c[1] - r + 2] : [c[0] + 2, 6, c[1] - r * 0.94 + 0.6], 'vdark');
    const yb = y0 + (big ? 14 : 10);
    if (big) { m.cylY('manhole', [27.6, 6], 2.4, yb + 4, yb + 7.6, 'ci'); P(m, 'reboiler_ret', [27.4, yb + 10, 5], [28, yb + 12, 7], 'p_steam'); return m; }
    P(m, 'bottoms', [c[0] - 0.8, yb, c[1] - r - 3], [c[0] + 0.8, yb + 1.6, c[1] - r], 'p_liq');
    P(m, 'reboiler_ret', big ? [c[0] + r - 1, yb + 8, c[1] - 1] : [c[0] + r - 0.6, yb + 8, c[1] - 0.8], big ? [c[0] + r + 2, yb + 11, c[1] + 1] : [16.03, yb + 9.6, c[1] + 0.8], 'p_steam');
    m.cylY('manhole', [c[0], c[1] - r - 0.6], big ? 2.4 : 1.6, yb + 4, yb + 7.6, 'ci');
    P(m, 'lt', [c[0] - r - 0.6, yb + 2, c[1] - 0.6], [c[0] - r + 0.4, yb + 12, c[1] + 0.6], 'ci');
    ladder(m, 'ladder', c[0] + 1, c[1] - r - 1.6, y0 + 4, 32, 'x');
    return m;
  }
  function reboiler() {
    const m = mk(D + 'sblue');
    for (const x of [6, 24]) saddle(m, 'saddle' + x, x, 2.4, 4, 3, 13);
    m.prism('channel', 'x', [8, 8], 4, 0, 5, 'sb', { split: '', ends: ['west'] });
    m.ring('ch_flange', 'x', [8, 8], 4.8, 1, 4.6, 5.6, 'ci');
    m.prism('cone', 'x', [8.6, 8], 5, 5.6, 9, 'sb', { split: '' });
    m.prism('kettle', 'x', [9.4, 8], 6.4, 9, 31.99, 'sb', { split: 'x', ends: ['east'] });
    P(m, 'steam_in', [1, 12, 7.4], [2.4, 16.03, 8.6], 'p_steam');
    P(m, 'cond_out', [1, 0, 7.4], [2.4, 4, 8.6], 'p_steam');
    P(m, 'vapour_out', [20, 15.6, 6.8], [22.4, 16.03, 9.2], 'p_liq');
    P(m, 'feed', [12, 1.4, 7.2], [14, 3.2, 8.8], 'p_liq');
    P(m, 'weir_lt', [28, 4, 1.5], [30, 14, 2.5], 'ci');
    return m;
  }
  function dewax() {
    const m = mk(D + 'frost');
    P(m, 'trough', [-14, 0, 4], [30, 12, 28], 'sb', { split: 'x' });
    for (const x of [-15.95, 30]) P(m, 'bearing' + x, [x, 0, 12], [x + 1.95, 14, 20], 'ci');
    for (const s of [-1, 1]) P(m, 'hood' + s, [-14, 20, s < 0 ? 4 : 27], [30, 26, s < 0 ? 5 : 28], 'sb');
    P(m, 'hood_top', [-14, 25, 5], [30, 26, 27], 'sb');
    P(m, 'wash_header', [-14, 22, 7], [30, 23, 8], 'frost');
    P(m, 'filtrate', [-12, 0, 1], [-9, 22, 4], 'frost');
    P(m, 'feed', [28, 6, 0], [31, 9, 4], 'frost');
    P(m, 'cake_chute', [-14, 4, 0], [30, 6, 4], 'st', { rot: { axis: 'x', angle: -22.5, origin: [8, 6, 4] } });
    return m;
  }
  function dewaxDrum() {
    const m = mk(D + 'cloth');
    m.prism('drum', 'x', [12, 16], 8.6, -12, 28, 'cloth', { split: 'x' });
    for (const x of [-14, 28]) m.prism('end' + x, 'x', [12, 16], 8.6, x, x + 2, 'st', { split: '', ends: ['west', 'east'] });
    m.prism('shaft', 'x', [12, 16], 1.4, -15.95, 31.95, 'bright', { split: '', ends: ['west', 'east'] });
    return m;
  }
  function refluxDrum() {
    const m = mk(D + 'sblue');
    for (const x of [5, 24]) saddle(m, 'saddle' + x, x, 3, 4, 3, 13);
    drum(m, 'drum', 'x', [8.6, 8], 5.2, 1.2, 30.8, 'sb');
    P(m, 'boot', [20, 0.6, 6.6], [23, 3.6, 9.4], 'sb');
    P(m, 'glass', [12, 6, 2.2], [16, 11, 2.6], 'vdark');
    for (const x of [6, 26]) P(m, 'nozzle' + x, [x, 13.4, 7.4], [x + 1.2, 14, 8.6], 'p_liq');
    P(m, 'psv', [16, 13.6, 7.2], [17.6, 14, 8.8], 'red');
    return m;
  }
  function exchanger() {
    const m = mk(D + 'sblue');
    for (const x of [-8, 16]) saddle(m, 'saddle' + x, x, 3, 4, 3, 13);
    m.prism('channel', 'x', [8, 8], 5, -14, -9, 'sb', { split: '' });
    m.prism('channel_cover', 'x', [8, 8], 5.6, -15.95, -14, 'sb', { split: '', ends: ['west'] });
    for (const x of [-14.6, -9.6]) m.ring('flange' + x, 'x', [8, 8], 5.8, 1.2, x, x + 0.8, 'ci');
    m.prism('shell', 'x', [8, 8], 5, -9, 22, 'sb', { split: 'x' });
    m.prism('float_head', 'x', [8, 8], 5.4, 22, 28.07, 'sb', { split: '', ends: ['east'] });
    m.ring('fh_flange', 'x', [8, 8], 5.8, 1.2, 21.6, 22.4, 'ci');
    for (const [x, y0, y1] of [[-13, 13, 16], [-13, 0, 3], [-4, 13, 16], [17, 0, 3]]) P(m, `noz_${x}_${y0}`, [x, y0, 7], [x + 2, y1, 9], 'p_liq');
    m.decal('plate', [2, 6, 2.85], [6, 8, 2.95], 'sb', 'north', 'parts.plate');
    return m;
  }
  function slideValve(open) {
    const m = mk(D + 'refr');
    m.prism('pipe', 'z', [8, 8], 4.6, -0.07, 16.07, 'refr', { split: '' });
    P(m, 'body', [3, 3, 4], [13, 13, 12], 'refr');
    P(m, 'bonnet', [13, 5, 5.6], [22, 11, 10.4], 'st');
    P(m, 'packing', [22, 6.4, 6.6], [23.4, 9.6, 9.4], 'ci');
    P(m, 'yoke_t', [23.4, 10, 7], [26, 11, 9], 'st'); P(m, 'yoke_b', [23.4, 5, 7], [26, 6, 9], 'st');
    m.prism('cylinder', 'x', [8, 8], 2.4, 26, 32, 'yel', { split: '', ends: ['east'] });
    P(m, 'hpu', [27, 0, 4], [32, 5, 12], 'yel');
    lamp(m, 'pos', 18, 11.6, 5.6, open === 0 ? 'l_r' : open === 4 ? 'l_g' : 'l_a', true, 0.5);
    return m;
  }
  function slideStem() {
    const m = mk('electronics/ss');
    m.prism('rod', 'x', [8, 8], 0.8, 12, 24.03, 'ss', { split: '', ends: ['east'] });
    P(m, 'plate', [3, 4, 6.5], [12, 12, 9.5], 'refr');
    return m;
  }
  function ejector() {
    const m = mk('plant/pipe_steam');
    m.prism('chest', 'x', [10, 8], 3.2, -0.03, 6, 'st', { split: '', ends: ['west'] });
    m.prism('throat', 'x', [10, 8], 2, 6, 14, 'st', { split: '' });
    for (const [x0, x1, r] of [[14, 20, 2.4], [20, 26, 2.9], [26, 31.95, 3.4]]) m.prism('diff' + x0, 'x', [10, 8], r, x0, x1, 'st', { split: '', ends: x1 > 31 ? ['east'] : [] });
    P(m, 'steam', [1.4, 13.2, 7.2], [3, 16.03, 8.8], 'p_steam');
    m.prism('suction', 'z', [8, 10], 1.8, 4.5, 8, 'st', { split: '', ends: ['north'] });
    m.ring('suct_flange', 'z', [8, 10], 2.4, 0.8, 4.5, 5.3, 'ci');
    P(m, 'gauge_line', [16, 12.4, 7.6], [16.8, 14.6, 8.4], 'br');
    gauge(m, 'gauge', 16.4, 15, 7.6, 1);
    return m;
  }

  // ---------------------------------------------------------------- FCC
  function fccReactor(unformed) {
    const m = mk(D + 'refr');
    m.cylY('shell', [8, 8], 19, -16, 20, 'refr', { seed: 1 });
    m.cylY('dome', [8, 8], 15, 20, 24, 'refr', { cap: D + 'refr' });
    m.cylY('outlet', [8, 8], 4, 24, 31.93, 'refr', { cap: D + 'refr' });
    if (!unformed) {
      for (const y of [-6, 6]) m.ringY('stiff' + y, [8, 8], 19.6, 0.6, y, y + 1, 'st');
      P(m, 'platform', [-12, 20, -12], [28, 21, -10], 'grat');
      PLANT.railing(m, 'rail', -12, 28, 21, -12, 'x');
      ladder(m, 'ladder', 26, -11, -16, 21, 'x');
    } else {
      P(m, 'cut_back', [0, -6, -11.4], [16, 14, -10.4], 'vdark');
      for (const x of [3, 13]) { m.cylY('cyclone' + x, [x, -6], 2.4, 2, 12, 'st', { cap: 'material/steel' }); m.cylY('dipleg' + x, [x, -6], 0.8, -8, 2, 'st'); }
      P(m, 'scaffold', [-14, -16, -14], [-12, 20, -12], 'yel');
    }
    m.decal('plate', [4, -10, -11.1], [8, -8, -11], 'refr', 'north', 'parts.plate');
    return m;
  }
  function regenerator(top) {
    const m = mk(D + 'refr');
    if (!top) {
      m.cylY('shell', [8, 8], 21.4, -16, 32, 'refr', { seed: 2 });
      for (const y of [-8, 8, 24]) m.ringY('stiff' + y, [8, 8], 22, 0.6, y, y + 1, 'st');
      P(m, 'air_grid_in', [-14, -12, 6], [-12, -6, 10], 'p_air');
      P(m, 'spent_in', [28, -4, 6], [30, 2, 10], 'refr');
    } else {
      m.cylY('shell', [8, 8], 21.4, -16, 12, 'refr', { seed: 2 });
      m.cylY('cone', [8, 8], 16, 12, 18, 'refr', { cap: D + 'refr' });
      m.cylY('plenum', [8, 8], 8, 18, 24, 'refr', { cap: D + 'refr' });
      m.cylY('flue', [8, 8], 4, 24, 31.93, 'refr', { cap: D + 'refr' });
      P(m, 'flue_duct', [8, 26, 6], [30, 30, 10], 'refr');
      P(m, 'platform', [-14, 12, -14], [30, 13, -11], 'grat');
      PLANT.railing(m, 'rail', -14, 30, 13, -14, 'x');
    }
    return m;
  }
  function riser() {
    const m = mk(D + 'refr');
    m.cylY('pipe', [8, 8], 4.4, -0.03, 16.03, 'refr');
    m.ringY('bellows_a', [8, 8], 4.6, 0.3, 6, 7, 'st');
    m.ringY('bellows_b', [8, 8], 4.6, 0.3, 8, 9, 'st');
    for (const a of [0, 90, 180, 270]) m.postY('tie' + a, [8, 8], 4.8, a, 0.4, 4, 11, 'st');
    return m;
  }

  // ---------------------------------------------------------------- quality lab
  function lab(busy) {
    const m = mk('m6auto/white');
    for (const x of [0.6, 30.4]) P(m, 'bench_leg' + x, [x, 0, 2], [x + 1, 11, 14], 'dk');
    P(m, 'bench', [0, 11, 1.4], [32, 12, 15], 'wh');
    P(m, 'cupboard', [20, 0, 2.4], [30, 11, 14], 'wh');
    // CFR engine: crankcase, cylinder, carburettor bowls, the flywheel (partial) at its front
    P(m, 'cfr_base', [2, 12, 4], [13, 14, 14], 'cfr');
    P(m, 'cfr_case', [3, 14, 4], [11, 20, 12], 'cfr');
    P(m, 'cfr_cyl', [5, 20, 6], [9, 25, 10], 'cfr');
    for (const x of [12, 14]) P(m, 'bowl' + x, [x, 16, 6], [x + 1.4, 20, 7.4], 'glass');
    P(m, 'exhaust', [8, 23, 10], [9, 25, 14], 'st');
    // distillation: heater, flask, condenser, receiver
    P(m, 'heater', [18, 12, 6], [22, 14, 10], 'dk');
    m.cylY('flask', [20, 8], 1.6, 14, 17, 'glass', { cap: 'electronics/glass' });
    P(m, 'condenser', [20, 18, 7.6], [28, 19, 8.4], 'glass', { rot: { axis: 'z', angle: -22.5, origin: [20, 18.5, 8] } });
    m.cylY('receiver', [28, 8], 1, 12, 15, 'glass', { cap: 'electronics/glass' });
    P(m, 'shelf', [16, 20, 12], [32, 21, 15], 'wh');
    lamp(m, 'busy', 30, 13.4, 1.4, 'l_g', busy, 0.5);
    return m;
  }
  function cfrFlywheel() {
    const m = mk('material/steel');
    m.ring('rim', 'z', [7, 15], 4, 1, 2, 3.4, 'st');
    for (const a of [0, 45, -45]) P(m, 'spoke' + a, [6.6, 11.4, 2.4], [7.4, 18.6, 3], 'st', { rot: a ? { axis: 'z', angle: a, origin: [7, 15, 2.7] } : undefined });
    P(m, 'spoke_h', [3.4, 14.6, 2.4], [10.6, 15.4, 3], 'st');
    m.prism('hub', 'z', [7, 15], 0.9, 1.93, 3.57, 'red', { split: '', ends: ['north', 'south'] });
    return m;
  }

  const B = (model, java) => ({ model, cat: 'refinery', java });
  const BUILDS = {
    air_cooler: B(airCooler), 'air_cooler/fan': B(acFan, 'air_cooler/partial/fan'),
    claus_reaction_furnace: B(() => claus(false)), claus_reaction_furnace_lit: B(() => claus(true)),
    column_base: B(() => columnBase('blue')), column_base_insulated: B(() => columnBase('insulated')), column_base_large: B(() => columnBase('large')),
    column_reboiler: B(reboiler),
    dewaxing_chiller: B(dewax), 'dewaxing_chiller/drum_filter': B(dewaxDrum, 'dewaxing_chiller/partial/drum_filter'),
    fcc_reactor_vessel: B(() => fccReactor(false)), fcc_reactor_vessel_unformed: B(() => fccReactor(true)),
    fcc_regenerator: B(() => regenerator(false)), fcc_regenerator_top: B(() => regenerator(true)), fcc_riser: B(riser),
    quality_lab: B(() => lab(false)), quality_lab_busy: B(() => lab(true)), 'quality_lab/cfr_flywheel': B(cfrFlywheel, 'quality_lab/partial/cfr_flywheel'),
    reflux_drum: B(refluxDrum), shell_tube_exchanger: B(exchanger),
    slide_valve: B(() => slideValve(2)), slide_valve_open0: B(() => slideValve(0)), slide_valve_open4: B(() => slideValve(4)),
    'slide_valve/stem': B(slideStem, 'slide_valve/partial/stem'),
    steam_ejector: B(ejector), sulfur_condenser: B(sulfurCondenser),
    sulfur_granulator: B(granulator), 'sulfur_granulator/drum': B(granDrum, 'sulfur_granulator/partial/drum'),
    sulfur_pit: B(() => sulfurPit(9)), sulfur_pit_empty: B(() => sulfurPit(0)), sulfur_pit_full: B(() => sulfurPit(14)),
  };
  window.M8R = FUTK.machine(BUILDS, paint);
})();
