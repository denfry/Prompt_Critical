// M15 late-era equipment not yet in the mod, hand-authored (replaces the spec_*.py placeholders): district heat
// exchanger, compact reactor module, frac pump and crank, top drive motor, pond liner, tailings dam, fire station,
// gas-gas reheater and rotor, detritiation column segments, horizontal boring mill and CNC machining centre with
// their spindles, bituminous sand, oil shale, ilmenite sand, LNG tank, acid settler, HF alkylation reactor, shore
// washer; draws the GDD 15 set without placeholders (tidal turbine and runner, heliostat and mirror, solar
// receiver, NaS battery).
// Run headless: node art/tools/pcart_headless.js <repo> core/m15_late.author.js M15L --paint
//
// Front to the north. Pivots as the placeholders: frac crank about x through (y 10, z 16); top drive in y and its
// quill about y through (9.5, 8); reheater rotor about y through (8, 8); boring spindle about x through (y 19,
// z 15); CNC spindle about y through (13, 18); tidal runner about z through (8, 8) at the north nose; heliostat
// mirror about x and y through (8, 26, 8).
(function () {
  const { rng, img, put, get, add, mix, rect, grain } = PCART;
  const { P, lamp, cabinet, vessel, drum, flange, motor, gauge, ladder } = FUTK;
  const D = 'm15late/', T = {};

  T.membrane = () => { const im = img(16, 16), r = rng(43101); for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) put(im, x, y, add([30, 30, 32], (x % 8 === 0 ? 14 : 0) + (r() - 0.5) * 6)); return im; };
  T.soil = () => { const im = img(16, 16), r = grain(im, [122, 104, 80], 43102, 8, 8, 4); for (let y = 3; y < 16; y += 4) for (let x = 0; x < 16; x++) if (r() < 0.5) put(im, x, y, [100, 86, 66]); return im; };
  T.tailings_water = () => { const im = img(16, 16), r = rng(43103); for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) put(im, x, y, add([150, 140, 110], Math.sin(x * 0.7) * 6 + (r() - 0.5) * 10)); return im; };
  T.bitsand = () => { const im = img(16, 16), r = grain(im, [52, 42, 32], 43104, 8, 8, 4); for (let k = 0; k < 14; k++) put(im, r() * 16, r() * 16, [96, 84, 66]); for (let k = 0; k < 6; k++) put(im, r() * 16, r() * 16, [12, 10, 8]); return im; };
  T.oilshale = () => { const im = img(16, 16), r = rng(43105); for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) put(im, x, y, add(Math.floor(y / 2) % 2 ? [110, 98, 84] : [86, 78, 70], (r() - 0.5) * 14)); return im; };
  T.ilmenite = () => { const im = img(16, 16), r = grain(im, [36, 36, 38], 43106, 8, 6, 4); for (let k = 0; k < 16; k++) put(im, r() * 16, r() * 16, [110, 110, 116]); return im; };
  T.beige = FUTK.enamel([214, 206, 182], 43107, 0);
  T.frac_red = FUTK.enamel([176, 40, 34], 43108, 2);
  T.mirror = () => { const im = img(16, 16), r = rng(43109); for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) put(im, x, y, add(mix([150, 190, 220], [230, 240, 250], (x + y) / 30), (x % 8 === 0 || y % 8 === 0 ? -50 : 0) + (r() - 0.5) * 6)); return im; };
  T.receiver = () => { const im = img(16, 16); for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) put(im, x, y, x % 2 ? [30, 30, 32] : [56, 56, 60]); return im; };
  T.receiver_hot = () => { const im = img(16, 16), r = rng(43110); for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) put(im, x, y, add(x % 2 ? [240, 140, 50] : [255, 200, 90], (r() - 0.5) * 20)); return im; };
  T.container = () => { const im = img(16, 16), r = rng(43111); for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) put(im, x, y, add(x % 2 ? [214, 216, 214] : [190, 192, 190], (r() - 0.5) * 6)); return im; };

  const MATX = {
    memb: { tex: D + 'membrane', P: 16 }, soil: { tex: D + 'soil', P: 16 }, twat: { tex: D + 'tailings_water', P: 16 }, bsand: { tex: D + 'bitsand', P: 16 },
    oshale: { tex: D + 'oilshale', P: 16 }, ilm: { tex: D + 'ilmenite', P: 16 }, beige: { tex: D + 'beige', P: 16 }, fracred: { tex: D + 'frac_red', P: 16 },
    mir: { tex: D + 'mirror', P: 16 }, recv: { tex: D + 'receiver', P: 16 }, recvh: { tex: D + 'receiver_hot', P: 16 }, cont: { tex: D + 'container', P: 16 },
  };
  const mk = (p) => FUTK.mk(MATX, p);
  const paint = (root) => FUTK.paintSet(root, D, T);
  const cube = (tex, mat) => () => { const m = mk(tex); P(m, 'block', [0, 0, 0], [16, 16, 16], mat, { split: 'xyz' }); return m; };

  function districtHx() {
    const m = mk('plant/green');
    P(m, 'skirt', [4, 0, 2], [14, 4, 12], 'frame');
    m.cylY('shell', [9, 7], 4.8, 4, 26, 'green');
    m.cylY('head', [9, 7], 4, 26, 31.03, 'green', { cap: 'plant/green' });
    for (const y of [10, 20]) m.ringY('band' + y, [9, 7], 5, 0.4, y, y + 1.4, 'p_water');
    P(m, 'steam_in', [13, 24, 6.4], [16.03, 25.4, 7.6], 'p_steam'); P(m, 'cond_out', [13, 6, 6.4], [16.03, 7.4, 7.6], 'p_steam');
    P(m, 'net_in', [8.4, 12, -0.03], [9.6, 13.4, 2.4], 'p_water'); P(m, 'net_out', [8.4, 22, -0.03], [9.6, 23.4, 2.4], 'p_water');
    return m;
  }
  function compactReactor(shutdown) {
    const m = mk('material/steel');
    m.cylY('vessel', [8, 8], 20, -16, 22, 'st');
    m.cylY('head', [8, 8], 15, 22, 27, 'st', { cap: 'material/steel' });
    m.cylY('cdm', [8, 8], 5, 27, 29, 'st', { cap: 'material/steel' });
    for (const y of [-8, 6]) m.ringY('flange' + y, [8, 8], 20.6, 0.8, y, y + 1.4, 'ci');
    P(m, 'hatch', [4, -12, -12.4], [12, -4, -11.6], 'ci');
    lamp(m, 'state', 8, 18, -11.8, shutdown ? 'l_a' : 'l_g', true, 1);
    m.box('tref', [6, 10, -11.9], [10, 14, -11.7], 'ycase', { split: '', face: { north: { tex: 'm12iso/trefoil', uv: [0, 0, 16, 16] } } });
    return m;
  }
  function fracPump() {
    const m = mk(D + 'frac_red');
    for (const x of [-12, 8, 24]) for (const z of [1, 27]) m.prism(`wheel_${x}_${z}`, 'z', [x + 2, 3], 3, z, z + 4, 'rubber', { split: '', ends: ['north', 'south'] });
    P(m, 'chassis', [-16, 3, 4], [32, 5, 28], 'frame');
    P(m, 'fluid_end', [-14, 5, 10], [-6, 15, 22], 'fracred');
    P(m, 'power_end', [-6, 5, 10], [10, 16, 22], 'fracred');
    P(m, 'engine', [12, 5, 8], [30, 18, 24], 'fracred');
    P(m, 'radiator', [30, 5, 8], [32, 20, 24], 'fin');
    P(m, 'discharge', [-16, 8, 14], [-14, 10, 18], 'ci');
    P(m, 'iron', [-16, 10, 14.6], [-15, 20, 16.4], 'ci');
    return m;
  }
  function fracCrank() { const m = mk('material/steel'); m.prism('shaft', 'x', [10, 16], 1.2, -6.03, 14.03, 'bright', { split: '', ends: ['west', 'east'] }); for (const x of [-4, 2, 8]) P(m, 'throw' + x, [x, 8.5, 14.5], [x + 1.4, 13, 17.5], 'st'); return m; }
  function topDrive() {
    const m = mk('oil/rig_red');
    P(m, 'bail', [6, 24, 4], [13, 30, 12], 'st');
    P(m, 'motor', [4, 12, 3], [15, 24, 13], 'rigred');
    P(m, 'blower', [12, 20, 8], [16, 26, 12], 'rigred');
    P(m, 'gearbox', [5, 6, 4], [14, 12, 12], 'rigred');
    P(m, 'guide', [3, 4, 11], [5, 28, 13], 'yel');
    m.cylY('quill', [9.5, 8], 1.2, -6.03, 6, 'bright', { cap: 'material/bright_steel', bottom: true });
    P(m, 'ibop', [8.3, -2, 6.8], [10.7, 0, 9.2], 'red');
    return m;
  }
  function pondLiner() { const m = mk(D + 'membrane'); P(m, 'liner', [0, 0, 0], [16, 1, 16], 'memb'); return m; }
  function tailingsDam(breach) {
    const m = mk(D + 'soil');
    if (!breach) { P(m, 'berm', [0, 0, 0], [16, 16, 16], 'soil'); P(m, 'gauge', [7.4, 16, -0.5], [8.6, 24, 0.5], 'wh'); return m; }
    P(m, 'berm_w', [0, 0, 0], [5, 16, 16], 'soil'); P(m, 'berm_e', [11, 0, 0], [16, 16, 16], 'soil');
    P(m, 'bed', [5, 0, 0], [11, 3, 16], 'soil');
    P(m, 'flow', [5, 3, 0], [11, 4, 16], 'twat', { hide: ['down'] });
    return m;
  }
  function fireStation() {
    const m = mk('material/brick');
    P(m, 'wall', [0, 0, 0.5], [16, 16, 16], 'brick');
    P(m, 'door', [2, 0, 0.3], [14, 12, 0.5], 'fred');
    for (let y = 2; y < 12; y += 3) P(m, 'panel' + y, [2.6, y, 0.2], [13.4, y + 0.6, 0.3], 'wh');
    P(m, 'sign', [3, 13, 0.2], [13, 15, 0.5], 'fred');
    lamp(m, 'beacon', 8, 14, 0.2, 'l_r', true, 0.5);
    return m;
  }
  function gasReheater() {
    const m = mk('m13env/flue');
    P(m, 'base', [-16, 0, -16], [32, 2, 32], 'conc');
    m.cylY('housing', [8, 8], 19, 2, 30, 'flue');
    m.cylY('lid', [8, 8], 19, 30, 32, 'flue', { cap: 'm13env/flue' });
    P(m, 'divider', [-12, 2, 7], [28, 32, 9], 'st');
    for (const [x, z, n] of [[-16, 0, 'raw_in'], [28, 0, 'clean_out']]) P(m, n, [x, 6, z - 8], [x + 4, 26, z + 4], 'flue');
    P(m, 'drive', [5, 30, -16], [11, 32, -11], 'blue');
    return m;
  }
  function reheaterRotor() { const m = mk('distribution_transformer/fins'); m.cylY('rotor', [8, 8], 17.6, 3, 29, 'fin', { cap: 'distribution_transformer/fins' }); m.cylY('shaft', [8, 8], 1.6, -0.03, 31.93, 'st'); return m; }
  function detrit(part) {
    const m = mk('plant/cladding');
    if (part === 'mid') { m.cylY('shell', [8, 8], 4, -0.03, 16.03, 'clad'); P(m, 'plate', [6.4, 8, 3.8], [9.6, 10, 4], 'ycase'); return m; }
    if (part === 'bottom') {
      P(m, 'skid', [0, 0, 3], [13, 2, 13], 'frame');
      m.cylY('shell', [8, 8], 4, 2, 16.03, 'clad');
      P(m, 'pump', [0, 2, 4], [4, 6, 8], 'ss');
      P(m, 'feed', [0, 6, 7.4], [4, 7.2, 8.6], 'ss');
      return m;
    }
    m.cylY('shell', [8, 8], 4, -0.03, 10, 'clad');
    m.cylY('head', [8, 8], 3, 10, 13.03, 'ss', { cap: 'electronics/ss' });
    P(m, 'vent', [7.4, 12, 7.4], [8.6, 13.03, 8.6], 'ss');
    P(m, 'h3', [6, 4, 3.6], [10, 7, 4], 'ycase');
    return m;
  }
  function boringMill() {
    const m = mk('rolling/mill_blue');
    P(m, 'bed', [-16, 0, 6], [32, 6, 26], 'mblue', { split: 'x' });
    P(m, 'column', [12, 6, 20], [24, 32, 30], 'mblue', { split: 'y' });
    P(m, 'headstock', [6, 14, 10], [14, 24, 20], 'mblue');
    P(m, 'table', [-14, 6, 8], [4, 10, 24], 'st');
    P(m, 'work', [-10, 10, 10], [0, 18, 20], 'ci');
    P(m, 'pendant', [26, 12, 6], [30, 20, 8], 'dk');
    return m;
  }
  function boringSpindle() { const m = mk('material/bright_steel'); m.prism('spindle', 'x', [19, 15], 1.4, -0.03, 6, 'bright', { split: '', ends: ['west'] }); m.prism('nose', 'x', [19, 15], 3, 6, 12.03, 'st', { split: '', ends: ['east'] }); return m; }
  function cnc(running) {
    const m = mk(D + 'beige');
    P(m, 'base', [0, 0, 2], [32, 6, 30], 'beige');
    P(m, 'enclosure_back', [0, 6, 26], [32, 30, 30], 'beige');
    for (const x of [0, 30]) P(m, 'side' + x, [x, 6, 2], [x + 2, 30, 26], 'beige');
    P(m, 'roof', [0, 30, 2], [32, 32, 30], 'beige');
    P(m, 'door_frame', [2, 6, 1.6], [24, 30, 2.4], 'beige');
    P(m, 'window', [4, 12, 1.4], [22, 26, 1.6], 'glass');
    P(m, 'table', [4, 6, 8], [22, 9, 22], 'st');
    P(m, 'vice', [10, 9, 12], [16, 12, 16], 'blue');
    P(m, 'pendant', [24, 10, 0.6], [31, 26, 2], 'dk');
    P(m, running ? 'glow_screen' : 'screen', [25, 18, 0.4], [30, 24, 0.6], running ? 'l_g' : 'vdark');
    lamp(m, 'tower', 28, 31, 2, running ? 'l_g' : 'l_off', running, 0.5, 'up');
    return m;
  }
  function cncSpindle() { const m = mk('m6auto/dark'); P(m, 'head', [10, 20, 15], [16, 28.03, 21], 'dk'); m.cylY('spindle', [13, 18], 1.2, 11.97, 20, 'bright', { cap: 'material/bright_steel', bottom: true }); return m; }
  function lngTank() {
    const m = mk(D + 'container');
    m.cylY('shell', [8.5, 8], 22.6, -16, 24, 'wh');
    m.cylY('roof', [8.5, 8], 18, 24, 28, 'wh', { cap: 'm6auto/white' });
    m.cylY('dome', [8.5, 8], 10, 28, 31.97, 'wh', { cap: 'm6auto/white' });
    for (const y of [-6, 6, 18]) m.ringY('stiff' + y, [8.5, 8], 23, 0.6, y, y + 1, 'st');
    ladder(m, 'stair', 26, 6, -16, 24, 'z');
    P(m, 'pipes', [12, 24, -12], [14, 31, -10], 'ss');
    return m;
  }
  function acidSettler() {
    const m = mk('m8ref/sblue');
    for (const x of [-12, 22]) P(m, 'saddle' + x, [x, 0, 3], [x + 3, 4, 11], 'frame');
    drum(m, 'drum', 'x', [8, 7], 5.2, -14, 30, 'sb');
    P(m, 'acid_out', [-10, 0, 6.4], [-8.8, 3, 7.6], 'p_acid');
    P(m, 'hc_out', [24, 13.2, 6.4], [25.2, 14, 7.6], 'p_liq');
    P(m, 'acid_ret', [4, 12, -0.03], [5.2, 13.2, 2], 'p_acid');
    P(m, 'glass', [8, 5, 1.6], [14, 9, 1.8], 'vdark');
    return m;
  }
  function hfReactor() {
    const m = mk('m9chem/nickel');
    P(m, 'skirt', [8, 0, 8], [24, 4, 24], 'frame');
    m.cylY('shell', [16, 16], 11.6, 4, 26, 'ni');
    m.cylY('head', [16, 16], 8, 26, 31.97, 'ni', { cap: 'm9chem/nickel' });
    for (const y of [8, 18]) m.ringY('ring' + y, [16, 16], 12, 0.6, y, y + 1.4, 'gband');
    P(m, 'acid_in', [2, 22, 15], [4.4, 23.4, 17], 'p_acid');
    P(m, 'feed', [27.6, 10, 15], [30, 11.4, 17], 'p_liq');
    m.decal('tox', [14, 14, 4.2], [18, 17.6, 4.4], 'ni', 'north', 'parts.warning');
    return m;
  }
  function shoreWasher(on) {
    const m = mk('m10ops/orange');
    FUTK.skid(m, 'skid', [1, 2], [15, 14]);
    P(m, 'boiler', [2, 1.4, 3], [8, 10, 13], 'orng');
    P(m, 'flue', [4, 10, 7], [6, 13, 9], 'soot');
    P(m, 'pump', [9, 1.4, 4], [14, 6, 10], 'dk');
    P(m, 'reel', [9, 6, 10], [14, 11, 13], 'rubber');
    P(m, 'lance', [10, 11, 11], [15, 11.6, 11.6], 'st');
    lamp(m, 'on', 12, 8, 3.9, 'l_g', on, 0.5);
    return m;
  }

  // ---------------------------------------------------------------- GDD 15 renewables and storage
  function tidal() {
    const m = mk('material/steel');
    P(m, 'pylon', [4, -16, 6], [12, 0, 10], 'conc', { split: 'y' });
    m.prism('capsule', 'z', [8, 8], 8, -6, 26, 'st', { split: 'z' });
    m.prism('tail', 'z', [8, 8], 5, 26, 31, 'st', { split: '', ends: ['south'] });
    m.prism('nose_ring', 'z', [8, 8], 8.6, -8, -6, 'ci', { split: '' });
    P(m, 'cable', [7, -16, 18], [9, 0, 20], 'bk', { split: 'y' });
    return m;
  }
  function tidalRunner() {
    const m = mk('material/steel');
    m.prism('hub', 'z', [8, 8], 4, -16, -8, 'st', { split: '', ends: ['north', 'south'] });
    P(m, 'blade_up', [6.4, 12, -12], [9.6, 31, -10], 'st');
    for (const a of [45, -45]) P(m, 'blade' + a, [6.4, -15, -12], [9.6, 4, -10], 'st', { rot: { axis: 'z', angle: a, origin: [8, 8, -11] } });
    return m;
  }
  function heliostat() {
    const m = mk('disconnector/galvanised');
    P(m, 'pad', [4, 0, 4], [12, 1, 12], 'conc');
    m.cylY('post', [8, 8], 1.2, 1, 22, 'galv');
    P(m, 'drive', [6, 22, 6], [10, 25, 10], 'gb');
    return m;
  }
  function mirror() {
    const m = mk(D + 'mirror');
    P(m, 'frame', [-4, 25.6, 1], [20, 26, 15], 'galv');
    P(m, 'glass', [-4, 26, 1], [20, 26.4, 15], 'mir', { hide: ['down'] });
    P(m, 'trunnion', [6.6, 24.6, 7.4], [9.4, 25.6, 8.6], 'galv');
    return m;
  }
  function solarReceiver(hot) {
    const m = mk(D + 'receiver');
    m.cylY('tower_top', [8, 8], 10, -16, -4, 'conc');
    m.cylY('panels', [8, 8], 12, -4, 22, hot ? 'recvh' : 'recv');
    if (hot) m.cylY('glow_panels', [8, 8], 12.05, -2, 20, 'recvh');
    m.cylY('cap', [8, 8], 12.6, 22, 26, 'st', { cap: 'material/steel' });
    m.cylY('mast', [8, 8], 1, 26, 32, 'st', { cap: 'material/steel' });
    PLANT.railing(m, 'rail', -6, 22, -4, -6, 'x');
    return m;
  }
  function nas(state) {
    const m = mk(D + 'container');
    P(m, 'pad', [-16, 0, -16], [32, 1, 32], 'conc');
    for (const z of [-14, 10]) {
      P(m, 'cont' + z, [-14, 1, z], [30, 26, z + 18], 'cont', { split: 'x' });
      for (let x = -12; x < 28; x += 8) P(m, `vent_${z}_${x}`, [x, 18, z - 0.4], [x + 5, 23, z], state === 'normal' ? 'louv' : 'fglow');
      P(m, 'door' + z, [24, 2, z - 0.3], [29, 16, z], 'cont');
    }
    if (state === 'fire') for (const x of [-6, 10]) P(m, 'glow_flame' + x, [x, 26, -12], [x + 6, 30, 4], 'fglow');
    P(m, 'hvac', [-10, 26, -12], [-2, 30, -4], 'gb');
    return m;
  }

  const B = (model, cat, java) => ({ model, cat, java });
  const BUILDS = {
    district_heat_exchanger: B(districtHx, 'boilers'),
    compact_reactor_module: B(() => compactReactor(false), 'core'), compact_reactor_module_shutdown: B(() => compactReactor(true), 'core'),
    frac_pump: B(fracPump, 'drilling'), 'frac_pump/pump_crank': B(fracCrank, 'drilling', 'frac_pump/partial/pump_crank'),
    'top_drive/motor': B(topDrive, 'drilling', 'top_drive/partial/motor'),
    pond_liner: B(pondLiner, 'environment'), tailings_dam: B(() => tailingsDam(false), 'environment'), tailings_dam_breach: B(() => tailingsDam(true), 'environment'),
    fire_station: B(fireStation, 'fire'),
    gas_reheater: B(gasReheater, 'flue_gas'), 'gas_reheater/rotor': B(reheaterRotor, 'flue_gas', 'gas_reheater/partial/rotor'),
    detritiation_column: B(() => detrit('mid'), 'isotopes'), detritiation_column_bottom: B(() => detrit('bottom'), 'isotopes'), detritiation_column_top: B(() => detrit('top'), 'isotopes'),
    boring_mill: B(boringMill, 'machine_shop'), 'boring_mill/spindle': B(boringSpindle, 'machine_shop', 'boring_mill/partial/spindle'),
    cnc_machining_center: B(() => cnc(false), 'machine_shop'), cnc_machining_center_running: B(() => cnc(true), 'machine_shop'),
    'cnc_machining_center/spindle': B(cncSpindle, 'machine_shop', 'cnc_machining_center/partial/spindle'),
    bituminous_sand: B(cube(D + 'bitsand', 'bsand'), 'oil_geology'), oil_shale: B(cube(D + 'oilshale', 'oshale'), 'oil_geology'), ilmenite_sand: B(cube(D + 'ilmenite', 'ilm'), 'ores'),
    lng_tank: B(lngTank, 'oil_transport'), acid_settler: B(acidSettler, 'refinery'), hf_alkylation_reactor: B(hfReactor, 'refinery'),
    shore_washer: B(() => shoreWasher(false), 'spill'), shore_washer_on: B(() => shoreWasher(true), 'spill'),
    tidal_turbine: B(tidal, 'hydro'), 'tidal_turbine/runner': B(tidalRunner, 'hydro', 'tidal_turbine/partial/runner'),
    heliostat: B(heliostat, 'solar'), 'heliostat/mirror': B(mirror, 'solar', 'heliostat/partial/mirror'),
    solar_receiver: B(() => solarReceiver(false), 'solar'), solar_receiver_hot: B(() => solarReceiver(true), 'solar'),
    nas_battery: B(() => nas('normal'), 'storage'), nas_battery_hot: B(() => nas('hot'), 'storage'), nas_battery_fire: B(() => nas('fire'), 'storage'),
  };
  window.M15L = FUTK.machine(BUILDS, paint);
})();
