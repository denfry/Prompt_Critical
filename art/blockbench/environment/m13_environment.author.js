// M13 environment, flue gas cleaning and late-era plant equipment not yet in the mod, hand-authored (replaces the
// spec_environment.py, spec_ops.py, spec_oilgas.py, spec_petrochem.py and spec_content2.py placeholders): soot
// blower and lance, station record board, condenser ball cleaning strainer, cable-tool rig and derrick with its
// walking beam, cementing unit and pump crank, trade depot, air quality station and anemometer, withered leaves,
// baghouse, flue duct pieces, SCR reactor, radiometric level gauge, ESP control panel, prilling tower, urea
// reactor, pipe marker, bitumen oxidizer; draws the GDD 15 trash rack cleaner and rake, CAES bulkhead and the
// flywheel store with its rotor.
// Run headless: node art/tools/pcart_headless.js <repo> environment/m13_environment.author.js M13E --paint
//
// Front to the north. Pivots as the placeholders: soot blower lance along z through (8, 8); rig walking beam about
// z through (8, 22); cementing crank about x through (y 10, z 16); anemometer about y through (13.6, 12.5) on the
// mast top; trash rake in y; flywheel rotor about y through (8, 8).
(function () {
  const { rng, img, put, get, add, mix, rect, grain } = PCART;
  const { P, lamp, cabinet, vessel, drum, flange, motor, gauge, ladder } = FUTK;
  const D = 'm13env/', T = {};

  T.wood = () => { const im = img(16, 16), r = rng(41101); for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) put(im, x, y, add([120, 84, 52], Math.sin(y * 1.3 + Math.sin(x * 0.3)) * 8 + (r() - 0.5) * 10 + (x % 8 === 0 ? -20 : 0))); return im; };
  T.digits = () => { const im = img(32, 8); rect(im, 0, 0, 32, 8, [20, 20, 22]); for (let k = 0; k < 6; k++) { const x0 = 1 + k * 5; for (let y = 1; y < 7; y++) { put(im, x0, y, [230, 120, 40]); put(im, x0 + 3, y, [230, 120, 40]); } for (let x = x0; x < x0 + 4; x++) { put(im, x, 1, [230, 120, 40]); put(im, x, 6, [230, 120, 40]); } } return im; };
  T.leaves = () => { const im = img(16, 16), r = rng(41111); for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) put(im, x, y, r() < 0.15 ? [0, 0, 0, 0] : add([[118, 86, 44], [96, 70, 38], [140, 104, 52], [80, 60, 34]][Math.floor(r() * 4)], (r() - 0.5) * 10)); return im; };
  T.flue = () => { const im = img(16, 16), r = grain(im, [86, 84, 80], 41121, 4, 6, 4); for (let x = 0; x < 16; x++) { put(im, x, 0, [60, 58, 56]); put(im, x, 15, [60, 58, 56]); } for (let k = 0; k < 20; k++) put(im, r() * 16, r() * 16, [40, 38, 36]); return im; };
  T.white_blue = () => { const im = img(16, 16); for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) put(im, x, y, y < 10 ? [226, 228, 226] : [40, 80, 170]); return im; };
  T.prill = () => { const im = img(16, 16), r = rng(41131); for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) put(im, x, y, add([236, 236, 230], (r() - 0.5) * 20 - ((x + y) % 3 ? 0 : 20))); return im; };
  T.bitumen_streak = () => { const im = img(16, 16), r = grain(im, [90, 92, 94], 41141, 4, 6, 4); for (let k = 0; k < 7; k++) { const x = Math.floor(r() * 16), l = 4 + Math.floor(r() * 12); for (let y = 0; y < l; y++) put(im, x, y, [22, 20, 18]); } return im; };
  T.catalyst = () => { const im = img(16, 16); for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) put(im, x, y, x % 3 === 0 || y % 3 === 0 ? [120, 120, 100] : [176, 170, 120]); return im; };

  const MATX = {
    twood: { tex: D + 'wood', P: 16 }, leaves: { tex: D + 'leaves', P: 16 }, flue: { tex: D + 'flue', P: 16 }, wblue: { tex: D + 'white_blue', P: 16 },
    prill: { tex: D + 'prill', P: 16 }, bstreak: { tex: D + 'bitumen_streak', P: 16 }, cat: { tex: D + 'catalyst', P: 16 },
  };
  const mk = (p) => FUTK.mk(MATX, p);
  const paint = (root) => FUTK.paintSet(root, D, T);

  // ---------------------------------------------------------------- boilers and plant
  function sootBlower(blowing) {
    const m = mk('material/steel');
    P(m, 'wall_plate', [3, 3, 15], [13, 13, 16], 'st');
    m.prism('sleeve', 'z', [8, 8], 2, 6, 15, 'st', { split: '' });
    P(m, 'carriage', [4, 4, 10], [12, 12, 14], 'st');
    motor(m, 'motor', 'x', [12, 12], 1.6, 0, 6, 'blue', { feet: false });
    P(m, 'steam_in', [12, 6, 11], [15, 7.4, 12.4], 'p_steam');
    P(m, 'steam_band', [13, 5.8, 10.8], [13.6, 7.6, 12.6], 'red');
    lamp(m, 'blow', 10, 13, 10, 'l_a', blowing, 0.5);
    return m;
  }
  function lance() { const m = mk('material/steel'); m.prism('lance', 'z', [8, 8], 1, -13.03, 6.03, 'bright', { split: '', ends: ['north'] }); m.prism('nozzle', 'z', [8, 8], 1.4, -13.03, -11, 'st', { split: '', ends: ['north'] }); return m; }
  function recordBoard() {
    const m = mk(D + 'wood');
    P(m, 'frame', [0, 2, 15], [32, 14, 16], 'twood');
    P(m, 'back', [1, 3, 14.6], [31, 13, 15], 'bk');
    for (const y of [4, 8.4]) m.box('digits' + y, [2, y, 14.5], [30, y + 3.6, 14.6], 'bk', { split: '', face: { north: { tex: D + 'digits', uv: [0, 0, 16, 4] } } });
    P(m, 'glass', [1, 3, 14.2], [31, 13, 14.3], 'glass', { hide: ['south', 'east', 'west', 'up', 'down'] });
    return m;
  }
  function ballStrainer() {
    const m = mk('plant/pipe_water');
    m.prism('pipe', 'x', [9, 8], 3.4, -0.07, 16.07, 'p_water', { split: '' });
    m.prism('body', 'x', [9, 8], 4.6, 3, 13, 'st', { split: '' });
    for (const x of [3, 12.4]) m.ring('flange' + x, 'x', [9, 8], 5.4, 1.2, x, x + 0.6, 'ci');
    P(m, 'glass', [6, 13, 6], [10, 15, 10], 'glass');
    P(m, 'cap', [5.6, 15, 5.6], [10.4, 16.03, 10.4], 'ci');
    P(m, 'drive', [11, 12, 3], [14, 15, 6], 'blue');
    return m;
  }

  // ---------------------------------------------------------------- drilling relics and services
  function cableRig() {
    const m = mk(D + 'wood');
    P(m, 'sill_n', [-16, -16, -16], [32, -13, -13], 'twood'); P(m, 'sill_s', [-16, -16, 29], [32, -13, 32], 'twood');
    for (const z of [-13, 26]) P(m, 'floor' + z, [-16, -13, z], [32, -12, z + 3], 'twood');
    P(m, 'samson', [6, -13, 6], [10, 22, 10], 'twood', { split: 'y' });
    P(m, 'bull_wheel', [-14, -13, 4], [-10, -2, 12], 'twood');
    m.prism('band_wheel', 'z', [24, -4], 6, 2, 4, 'twood', { split: '', ends: ['north', 'south'] });
    P(m, 'engine_house', [18, -13, 14], [32, -2, 30], 'twood');
    P(m, 'pitman', [22, -4, 4], [24, 18, 6], 'twood');
    return m;
  }
  function rigDerrick() {
    const m = mk(D + 'wood');
    for (const x of [-6, 20]) for (const z of [-6, 20]) P(m, `leg_${x}_${z}`, [x, -16, z], [x + 2, 32, z + 2], 'twood', { split: 'y' });
    for (let y = -10; y < 32; y += 10) { P(m, 'girt_n' + y, [-6, y, -6], [22, y + 1, -5], 'twood'); P(m, 'girt_s' + y, [-6, y, 21], [22, y + 1, 22], 'twood'); P(m, 'girt_w' + y, [-6, y, -6], [-5, y + 1, 22], 'twood'); P(m, 'girt_e' + y, [21, y, -6], [22, y + 1, 22], 'twood'); }
    P(m, 'crown', [-6, 30, -6], [22, 32, 22], 'twood');
    return m;
  }
  function walkingBeam() { const m = mk(D + 'wood'); P(m, 'beam', [-16, 21, 6.5], [30, 23.4, 9.5], 'twood', { split: 'x' }); P(m, 'head', [-16, 18, 6.5], [-13, 26, 9.5], 'twood'); P(m, 'rope', [-15, 0, 7.6], [-14.4, 18, 8.4], 'rope'); return m; }
  function cementing() {
    const m = mk(D + 'white_blue');
    for (const x of [-12, 8, 24]) for (const z of [1, 27]) m.prism(`wheel_${x}_${z}`, 'z', [x + 2, 3], 3, z, z + 4, 'rubber', { split: '', ends: ['north', 'south'] });
    P(m, 'chassis', [-16, 3, 4], [32, 5, 28], 'frame');
    P(m, 'cab', [22, 5, 5], [32, 18, 27], 'wblue');
    P(m, 'tub', [-14, 5, 18], [-2, 14, 27], 'wblue');
    P(m, 'pump', [-6, 5, 8], [14, 13, 18], 'blue');
    P(m, 'engine', [2, 5, 19], [16, 15, 27], 'wblue');
    P(m, 'discharge', [-16, 6, 10], [-6, 8, 12], 'rigred');
    P(m, 'manifold', [-14, 5, 5], [-8, 8, 8], 'ci');
    return m;
  }
  function pumpCrank() { const m = mk('material/steel'); m.prism('shaft', 'x', [10, 16], 1.2, -6.03, 14.03, 'bright', { split: '', ends: ['west', 'east'] }); for (const x of [-2, 4, 10]) P(m, 'throw' + x, [x, 8.5, 14.5], [x + 1.4, 13, 17.5], 'st'); return m; }
  function tradeDepot(pending) {
    const m = mk('plant/concrete');
    P(m, 'block', [0, 0, 0], [16, 16, 16], 'conc');
    P(m, 'door', [2, 0, -0.2], [14, 12, 0], 'dk');
    for (let y = 1; y < 12; y += 2) P(m, 'slat' + y, [2, y, -0.3], [14, y + 0.4, -0.2], 'st');
    P(m, 'stripe', [0, 0, -0.2], [2, 2, 0], 'haz'); P(m, 'stripe2', [14, 0, -0.2], [16, 2, 0], 'haz');
    P(m, 'nozzle', [12, 13, -1.2], [14, 15, 0], 'ci');
    lamp(m, 'pending', 4, 14, 0, 'l_a', pending, 0.6);
    return m;
  }
  function aqStation(on) {
    const m = mk('m8power/louvre_white');
    P(m, 'plinth', [1, 0, 1], [15, 1, 15], 'conc');
    P(m, 'box', [2, 1, 2], [14, 14, 14], 'lwh');
    P(m, 'roof', [1.4, 14, 1.4], [14.6, 15, 14.6], 'wh');
    P(m, 'mast', [13, 15, 12], [14.2, 29, 13.2], 'galv');
    P(m, 'inlet', [5, 15, 5], [7, 20, 7], 'ss');
    P(m, 'inlet_hat', [4, 20, 4], [8, 21, 8], 'ss');
    lamp(m, 'on', 11, 12, 2, 'l_g', on, 0.5);
    return m;
  }
  function aqAnemometer() { const m = mk('grid_lines/alu'); m.cylY('hub', [13.6, 12.5], 0.5, 28.97, 30, 'alu', { cap: 'grid_lines/alu' }); for (const [x, z] of [[11.9, 12.5], [15.3, 12.5], [13.6, 11.9], [13.6, 13.1]]) m.cylY('cup' + x + z, [x, z], 0.4, 30, 31.03, 'alu', { cap: 'grid_lines/alu', bottom: true }); return m; }
  function withered() { const m = Object.assign(mk(D + 'leaves'), { renderType: 'minecraft:cutout' }); P(m, 'leaves', [0, 0, 0], [16, 16, 16], 'leaves'); return m; }

  // ---------------------------------------------------------------- flue gas
  function baghouse(top, pulsing) {
    const m = mk('disconnector/galvanised');
    if (!top) {
      for (const x of [-16, 30]) for (const z of [-16, 30]) P(m, `leg_${x}_${z}`, [x, 0, z], [x + 2, 14, z + 2], 'frame');
      P(m, 'hopper', [-14, 8, -14], [30, 14, 30], 'galv');
      P(m, 'hopper_low', [2, 2, 2], [14, 8, 14], 'galv');
      P(m, 'rotary', [5, 0, 5], [11, 2, 11], 'ci');
      P(m, 'casing', [-16, 14, -15.4], [32, 32, 32], 'galv', { split: 'xyz' });
      for (let x = -12; x < 30; x += 10) P(m, 'stiff' + x, [x, 14, -16], [x + 1, 32, -15.4], 'frame');
      P(m, 'inlet', [-16, 18, 2], [-14, 26, 14], 'flue');
    } else {
      P(m, 'casing', [-16, 0, -15.4], [32, 10, 32], 'galv', { split: 'xyz' });
      for (let x = -14; x < 30; x += 11) P(m, 'cover' + x, [x, 10, -14], [x + 10, 11, 30], 'galv');
      P(m, 'header', [-16, 11, 4], [32, 13, 7], 'p_air');
      for (let x = -12; x < 30; x += 8) { P(m, 'valve' + x, [x, 11, 8], [x + 2, 14, 10], 'ci'); if (pulsing) P(m, 'glow_valve' + x, [x + 0.4, 14, 8.4], [x + 1.6, 14.4, 9.6], 'l_a'); }
      P(m, 'outlet', [12, 10, 28], [20, 15, 32], 'flue');
    }
    return m;
  }
  function flueDuct(shape) {
    const m = mk(D + 'flue');
    if (shape === 'straight') { P(m, 'duct', [0.5, 0.5, 0], [15.5, 15.5, 16], 'flue', { hide: ['north', 'south'] }); for (const z of [0, 15]) P(m, 'rib' + z, [0, 0, z], [16, 16, z + 1], 'flue', { hide: ['north', 'south'] }); P(m, 'band', [0.4, 12, 6], [15.6, 13, 10], 'gband'); return m; }
    P(m, 'duct_n', [1, 1, 0], [15, 15, 15], 'flue', { hide: ['north'] });
    P(m, 'duct_e', [15, 1, 1], [16, 15, 15], 'flue', { hide: ['east'] });
    if (shape === 'tee') P(m, 'duct_s', [1, 1, 15], [15, 15, 16], 'flue', { hide: ['south'] });
    return m;
  }
  function scr(top, layers) {
    const m = mk('grid_lines/alu');
    P(m, 'casing', [-16, 0, -15.2], [32, 32, 32], 'alu', { split: 'xyz' });
    for (let y = 4; y < 32; y += 10) {
      P(m, 'hatch' + y, [-4, y, -16], [4, y + 4, -15.2], 'st');
      if (layers > (y - 4) / 10) P(m, 'layer' + y, [6, y + 0.6, -15.6], [26, y + 3.4, -15.2], 'cat');
    }
    if (top) { P(m, 'grid', [-14, 30, -14], [30, 32, 30], 'grat'); P(m, 'nh3', [-16, 26, 6], [-14, 28, 10], 'gband'); }
    return m;
  }
  function radiometric() {
    const m = mk('m12iso/lead');
    P(m, 'bracket', [4, 0, 15], [12, 16, 16], 'st');
    P(m, 'source', [5, 10, 9], [11, 15, 15], 'lead');
    P(m, 'shutter', [5, 13, 8.4], [11, 14, 9], 'ycase');
    m.box('tref', [6.5, 11, 8.8], [9.5, 13, 9], 'ycase', { split: '', face: { north: { tex: 'm12iso/trefoil', uv: [0, 0, 16, 16] } } });
    P(m, 'detector', [6, 1, 10], [10, 8, 14], 'ycase');
    P(m, 'cable', [7.4, 0, 14], [8.6, 1, 15], 'bk');
    return m;
  }
  function espPanel(on) {
    const m = mk('m3grid/tank');
    cabinet(m, 'cab', [1, 0, 1.4], [15, 31, 15], 'tank', { plate: true });
    m.box('screen', [3, 20, 1], [13, 27, 1.2], 'tank', { split: '', face: { north: { tex: 'electronics/screen', uv: [0, 0, 16, 12] } } });
    lamp(m, 'on', 4, 17, 1.2, 'l_g', on, 0.6);
    m.decal('kv', [9, 15, 1], [12, 18, 1.2], 'tank', 'north', 'parts.gauge');
    return m;
  }

  // ---------------------------------------------------------------- fertiliser and bitumen
  function prilling() {
    const m = mk('plant/concrete');
    m.cylY('tower', [8, 8], 19.6, -16, 26, 'conc', { cap: 'plant/concrete' });
    m.cylY('head', [8, 8], 12, 26, 31.97, 'conc', { cap: 'plant/concrete' });
    for (const y of [-6, 10]) m.ringY('ring' + y, [8, 8], 20, 0.6, y, y + 1, 'conc');
    P(m, 'conveyor', [-12, -16, -12], [28, -13, -6], 'prill');
    P(m, 'fan_house', [20, 26, 2], [26, 31.97, 14], 'st');
    P(m, 'melt_in', [6, 31, -2], [10, 31.97, 8], 'p_liq');
    return m;
  }
  function ureaReactor() {
    const m = mk('m9chem/forging');
    P(m, 'skirt', [3, -15.99, 3], [13, -10, 13], 'frame');
    m.cylY('shell', [8, 8], 5.4, -10, 26, 'forg');
    m.cylY('head', [8, 8], 4.4, 26, 30, 'forg', { cap: 'm9chem/forging' });
    m.cylY('top', [8, 8], 2, 30, 31.93, 'forg', { cap: 'm9chem/forging' });
    for (let y = -6; y < 26; y += 6) m.ringY('weld' + y, [8, 8], 5.6, 0.3, y, y + 0.4, 'st');
    P(m, 'co2_in', [12, -8, 7.4], [16.03, -6.8, 8.6], 'gband');
    P(m, 'nh3_in', [-0.03, -6, 7.4], [3, -4.8, 8.6], 'gband');
    P(m, 'out', [12, 22, 7.4], [16.03, 23.2, 8.6], 'p_liq');
    return m;
  }
  function pipeMarker(color) {
    const m = mk('material/steel');
    for (const x of [2, 12]) m.ring('ring' + x, 'x', [8, 8], 3.2, 0.6, x, x + 2, color);
    P(m, 'arrow', [5, 11, 7.4], [11, 11.4, 8.6], color);
    P(m, 'arrow_h', [10, 10.6, 6.8], [12, 11.4, 9.2], color);
    return m;
  }
  function oxidizer(top) {
    const m = mk(D + 'bitumen_streak');
    m.cylY('shell', [8, 8], 5.6, -0.03, top ? 26 : 31.99, 'bstreak', { cap: top ? D + 'bitumen_streak' : undefined });
    if (top) { m.cylY('head', [8, 8], 3, 26, 30, 'bstreak', { cap: D + 'bitumen_streak' }); P(m, 'vent', [7.4, 30, 7.4], [8.6, 31.93, 8.6], 'st'); P(m, 'air_in', [12, 4, 7.4], [16, 5.2, 8.6], 'p_air'); }
    else { P(m, 'bitumen_out', [12, 2, 7.4], [16, 3.2, 8.6], 'bk'); ladder(m, 'ladder', 6.6, 1.4, 0, 32, 'x'); }
    return m;
  }

  // ---------------------------------------------------------------- GDD 15: hydro and storage
  function rackCleaner() {
    const m = mk('plant/yellow');
    for (const x of [0, 14]) P(m, 'column' + x, [x, 0, 4], [x + 2, 30, 8], 'yel', { split: 'y' });
    P(m, 'beam', [0, 28, 3], [16, 32, 9], 'yel');
    m.prism('drum', 'x', [30, 12], 2, 3, 13, 'ci', { split: '' });
    P(m, 'motor', [12, 28, 10], [16, 32, 15], 'blue');
    for (const x of [4, 11]) P(m, 'rope' + x, [x, 0, 6], [x + 0.4, 28, 6.4], 'rope');
    return m;
  }
  function rake() { const m = mk('plant/yellow'); P(m, 'head', [2, 10, 4], [14, 13, 7], 'yel'); for (let x = 2.6; x < 14; x += 1.6) P(m, 'tine' + x, [x, 8, 2], [x + 0.6, 10, 4], 'st'); P(m, 'trough', [2, 13, 5], [14, 14, 8], 'yel'); return m; }
  function caesBulkhead(state) {
    const m = mk('plant/concrete');
    P(m, 'plug', [0, 0, 4], [16, 16, 12], 'conc');
    P(m, 'door', [3, 1, 3], [13, 13, 4], 'st');
    m.wheelY('wheel', 8, 7, 2.6, 2.4, 'red');
    for (const y of [2, 12]) P(m, 'hinge' + y, [2.4, y, 3], [3.2, y + 1, 4], 'ci');
    if (state === 'leaking') P(m, 'frost_seam', [3, 1, 2.8], [13, 2, 3], 'frost');
    lamp(m, 'seal', 14, 14, 4, state === 'sealed' ? 'l_g' : 'l_r', true, 0.5);
    return m;
  }
  function flywheelStore() {
    const m = mk('material/steel');
    P(m, 'pit', [-16, 0, -16], [32, 2, 32], 'conc');
    m.cylY('housing', [8, 8], 20, 2, 26, 'st');
    m.cylY('lid', [8, 8], 20, 26, 28, 'st', { cap: 'material/steel' });
    for (let a = 0; a < 360; a += 45) m.postY('bolt' + a, [8, 8], 19, a, 1, 28, 29, 'ci');
    m.cylY('motor', [8, 8], 4, 28, 32, 'blue', { cap: 'plant/blue' });
    P(m, 'vac_pump', [22, 2, -14], [30, 8, -8], 'gb');
    P(m, 'vac_line', [24, 8, -10], [26, 16, -8], 'st');
    return m;
  }
  function flywheelRotor() { const m = mk('material/steel'); m.cylY('rotor', [8, 8], 17, 6, 20, 'bright', { cap: 'material/bright_steel', bottom: true }); m.cylY('shaft', [8, 8], 1.6, 2, 26, 'st'); return m; }

  const B = (model, cat, java) => ({ model, cat, java });
  const BUILDS = {
    soot_blower: B(() => sootBlower(false), 'boilers'), soot_blower_blowing: B(() => sootBlower(true), 'boilers'),
    'soot_blower/lance': B(lance, 'boilers', 'soot_blower/partial/lance'),
    station_record_board: B(recordBoard, 'control'), condenser_ball_cleaning: B(ballStrainer, 'cooling'),
    cable_tool_rig: B(cableRig, 'drilling'), cable_tool_rig_derrick: B(rigDerrick, 'drilling'),
    'cable_tool_rig/walking_beam': B(walkingBeam, 'drilling', 'cable_tool_rig/partial/walking_beam'),
    cementing_unit: B(cementing, 'drilling'), 'cementing_unit/pump_crank': B(pumpCrank, 'drilling', 'cementing_unit/partial/pump_crank'),
    trade_depot: B(() => tradeDepot(false), 'economy'), trade_depot_pending: B(() => tradeDepot(true), 'economy'),
    air_quality_station: B(() => aqStation(false), 'environment'), air_quality_station_on: B(() => aqStation(true), 'environment'),
    'air_quality_station/anemometer': B(aqAnemometer, 'environment', 'air_quality_station/partial/anemometer'),
    withered_leaves: B(withered, 'environment'),
    baghouse_filter: B(() => baghouse(false, false), 'flue_gas'), baghouse_filter_top: B(() => baghouse(true, false), 'flue_gas'),
    baghouse_filter_top_pulsing: B(() => baghouse(true, true), 'flue_gas'),
    flue_duct: B(() => flueDuct('straight'), 'flue_gas'), flue_duct_elbow: B(() => flueDuct('elbow'), 'flue_gas'), flue_duct_tee: B(() => flueDuct('tee'), 'flue_gas'),
    scr_reactor: B(() => scr(false, 3), 'flue_gas'), scr_reactor_empty: B(() => scr(false, 0), 'flue_gas'), scr_reactor_top: B(() => scr(true, 3), 'flue_gas'),
    level_gauge_radiometric: B(radiometric, 'instruments'),
    esp_control_panel: B(() => espPanel(false), 'oil_production'), esp_control_panel_on: B(() => espPanel(true), 'oil_production'),
    prilling_tower: B(prilling, 'petrochem'), urea_reactor: B(ureaReactor, 'petrochem'),
    pipe_marker: B(() => pipeMarker('gband'), 'piping'),
    bitumen_oxidizer: B(() => oxidizer(false), 'refinery'), bitumen_oxidizer_top: B(() => oxidizer(true), 'refinery'),
    trash_rack_cleaner: B(rackCleaner, 'hydro'), 'trash_rack_cleaner/rake': B(rake, 'hydro', 'trash_rack_cleaner/partial/rake'),
    caes_bulkhead: B(() => caesBulkhead('idle'), 'storage'), caes_bulkhead_sealed: B(() => caesBulkhead('sealed'), 'storage'),
    caes_bulkhead_leaking: B(() => caesBulkhead('leaking'), 'storage'),
    flywheel_storage: B(flywheelStore, 'storage'), 'flywheel_storage/rotor': B(flywheelRotor, 'storage', 'flywheel_storage/partial/rotor'),
  };
  window.M13E = FUTK.machine(BUILDS, paint);
})();
