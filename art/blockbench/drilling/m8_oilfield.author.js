// M8 oil and gas field equipment not yet in the mod, hand-authored (replaces the spec_oilgas.py placeholders):
// BOP accumulator and its levers, capping stack, crown block and sheaves, drill floor tile, kill manifold, mud
// degasser and mud-gas separator, shale shaker and its screen deck, cold box, turboexpander and rotor, core
// analysis bench, logging truck and cable drum, seismic vibrator and its eccentric rotor, desalter, pumpjack frame
// and the samson post and horse head segments, centrifugal and reciprocating compressors with their partials,
// cathodic protection rectifier and test post, gas bullet, odorizer, tank radar gauge with the floating roof, tank
// roof and shell pieces, and the natural blocks (oil slick, rock salt, gas seep, natural bitumen, hot spring vent).
// Run headless: node art/tools/pcart_headless.js <repo> drilling/m8_oilfield.author.js M8F --paint
//
// Front to the north. Partial pivots as the placeholders: accumulator levers on the panel at z 0..1; crown sheaves
// about x through (y 8, z 8); shaker deck x 3..25 at y 8; turboexpander shaft about x through (8, 8); logging drum
// about x through (y 15, z 16); vibrator rotor about x through (y 9.9, z 16); centrifugal compressor coupling about
// x through (y 12, z 16) at x -16..-10; reciprocating crankshaft about x through (y 8, z 16), pistons along z at
// x 13..15, y 10..12; floating roof moves in y.
(function () {
  const { rng, img, put, get, add, mix, rect, grain, vnoise } = PCART;
  const { P, lamp, cabinet, vessel, drum, flange, motor, gauge, ladder } = FUTK;
  const D = 'm8field/', T = {};

  T.tank_silver = () => { const im = img(32, 32), r = grain(im, [176, 180, 182], 36101, 3, 5, 8); for (let x = 0; x < 32; x++) { put(im, x, 15, [140, 144, 146]); put(im, x, 31, [140, 144, 146]); } for (const x of [7, 23]) for (let y = 0; y < 32; y++) put(im, x, y, add(get(im, x, y), -14)); return im; };
  T.white = FUTK.enamel([226, 226, 220], 36102, 2);
  T.gasband = () => { const im = img(16, 16); for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) put(im, x, y, y >= 6 && y < 10 ? [200, 40, 34] : [222, 184, 40]); return im; };
  T.yellow_post = FUTK.enamel([226, 188, 40], 36103, 1);
  T.greygreen = FUTK.enamel([104, 120, 108], 36104, 3);
  T.greyblue = FUTK.enamel([112, 130, 146], 36105, 3);
  T.travertine = () => { const im = img(16, 16), r = grain(im, [214, 200, 172], 36111, 6, 8, 4); for (let y = 2; y < 16; y += 4) for (let x = 0; x < 16; x++) if (r() < 0.6) put(im, x, y, [180, 164, 134]); return im; };
  T.spring_water = () => { const im = img(16, 16), r = rng(36112); for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) put(im, x, y, add([60, 190, 196], Math.sin(x * 0.7 + y * 0.5) * 10 + (r() - 0.5) * 10)); return im; };
  T.cracked = () => { const im = img(16, 16), r = grain(im, [118, 110, 100], 36121, 8, 10, 4); for (let k = 0; k < 4; k++) { let x = r() * 16, y = r() * 16; for (let i = 0; i < 12; i++) { put(im, x, y, [36, 32, 30]); x += r() * 2 - 1; y += r() * 2 - 1; } } return im; };
  T.cracked_hot = () => { const im = img(16, 16), r = grain(im, [118, 110, 100], 36121, 8, 10, 4); for (let k = 0; k < 4; k++) { let x = r() * 16, y = r() * 16; for (let i = 0; i < 12; i++) { put(im, x, y, [255, 170, 60]); x += r() * 2 - 1; y += r() * 2 - 1; } } return im; };
  T.bitumen = () => { const im = img(16, 16), r = rng(36131); for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) put(im, x, y, add([20, 18, 16], (r() - 0.5) * 6)); for (let k = 0; k < 7; k++) { const x = r() * 14, y = r() * 14; put(im, x, y, [80, 76, 70]); put(im, x + 1, y, [40, 38, 34]); put(im, x, y + 1, [40, 38, 34]); } return im; };
  T.salt = () => { const im = img(16, 16), r = rng(36141); for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) { const cell = (Math.floor(x / 4) * 7 + Math.floor(y / 4) * 3) % 5; put(im, x, y, add(mix([236, 220, 216], [214, 176, 172], cell / 5), (x % 4 === 0 || y % 4 === 0 ? -16 : 0) + (r() - 0.5) * 8)); } return im; };
  const slick = (a) => () => { const im = img(16, 16), r = rng(36151); for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) { const t = (Math.sin(x * 0.5 + y * 0.3) + 1) / 2; const c = t < 0.33 ? [120, 60, 150] : t < 0.66 ? [40, 140, 120] : [170, 140, 40]; put(im, x, y, [c[0], c[1], c[2], a]); } return im; };
  T.slick0 = slick(170); T.slick1 = slick(130); T.slick2 = slick(90); T.slick3 = slick(50);
  T.soot_red = () => { const im = img(16, 16), r = rng(36161); grain(im, [70, 30, 26], 36162, 6, 10, 4); for (let k = 0; k < 40; k++) put(im, r() * 16, r() * 16, [20, 18, 16]); return im; };
  T.cryo = () => { const im = img(16, 16); rect(im, 0, 0, 16, 16, [40, 90, 170]); for (let k = -5; k <= 5; k++) { put(im, 8 + k, 8, [240, 240, 240]); put(im, 8, 8 + k, [240, 240, 240]); put(im, 8 + k, 8 + k, [240, 240, 240]); put(im, 8 + k, 8 - k, [240, 240, 240]); } return im; };
  T.he = FUTK.enamel([150, 120, 80], 36171, 0);

  const MATX = {
    silver: { tex: D + 'tank_silver', P: 16 }, wbul: { tex: D + 'white', P: 16 }, gband: { tex: D + 'gasband', P: 16 }, ypost: { tex: D + 'yellow_post', P: 16 },
    gg: { tex: D + 'greygreen', P: 16 }, gb: { tex: D + 'greyblue', P: 16 }, trav: { tex: D + 'travertine', P: 16 }, swat: { tex: D + 'spring_water', P: 16 },
    crack: { tex: D + 'cracked', P: 16 }, crackhot: { tex: D + 'cracked_hot', P: 16 }, bitu: { tex: D + 'bitumen', P: 16 }, salt: { tex: D + 'salt', P: 16 },
    soot_red: { tex: D + 'soot_red', P: 16 }, he: { tex: D + 'he', P: 16 },
    rigred: { tex: 'oil/rig_red', P: 16 }, pump: { tex: 'oil/pump_paint', P: 16 }, rope: { tex: 'oil/rope', P: 16 }, mud: { tex: 'oil/mud', P: 16 },
    frost: { tex: 'm8ref/frost', P: 16 },
  };
  const mk = (p) => FUTK.mk(MATX, p);
  const paint = (root) => FUTK.paintSet(root, D, T);
  const wheel = (m, n, x, y, z, r, w) => m.prism(n, 'z', [x, y], r, z, z + w, 'rubber', { split: '', ends: ['north', 'south'] });

  // ---------------------------------------------------------------- drilling
  function accumulator(charged) {
    const m = mk('oil/rig_red');
    FUTK.skid(m, 'skid', [0, 1], [32, 15]);
    for (let i = 0; i < 6; i++) for (const z of [9.6, 12.8]) m.cylY(`bottle_${i}_${z}`, [2.4 + i * 2.8, z], 1.3, 1.4, 13, 'rigred', { cap: 'oil/rig_red' });
    P(m, 'manifold', [1, 13, 8.6], [18, 14, 14], 'st');
    motor(m, 'pump', 'x', [4, 4.6], 2.2, 1, 10, 'blue', { feet: false });
    cabinet(m, 'panel', [18, 1.4, 1], [31, 15, 6], 'rigred', { plate: false, inset: 0.6 });
    for (const x of [20.6, 23.6, 26.6]) m.decal('g' + x, [x - 1.2, 10, 0.6], [x + 1.2, 12.4, 0.8], 'rigred', 'north', 'parts.gauge');
    lamp(m, 'charged', 29.4, 11, 0.8, 'l_g', charged, 0.5);
    for (const x of [20.5, 22.7, 24.9, 27.1]) P(m, 'lever_base' + x, [x - 0.6, 4.4, 0.6], [x + 0.6, 5.6, 1], 'ci');
    return m;
  }
  function levers() {
    const m = mk('material/bright_steel');
    for (const x of [20.5, 22.7, 24.9, 27.1]) { P(m, 'arm' + x, [x - 0.3, 5, 0.2], [x + 0.3, 8, 0.8], 'bright'); P(m, 'knob' + x, [x - 0.6, 7.4, 0], [x + 0.6, 8, 1], 'red'); }
    return m;
  }
  function capping(state) {
    const m = mk(state === 'sooty' ? D + 'soot_red' : 'oil/rig_red');
    const mat = state === 'sooty' ? 'soot_red' : 'rigred', w = state === 'closed' ? 1 : 2;
    m.cylY('connector', [8, 8], 5, -0.03, 6, mat, { cap: 'oil/rig_red' });
    for (const [y, n] of [[6, 'lower'], [14, 'upper']]) {
      P(m, 'ram_' + n, [3, y, 3], [13, y + 7, 13], mat);
      for (const s of [-1, 1]) m.prism(`bonnet_${n}_${s}`, 'x', [y + 3.5, 8], 2.8, s < 0 ? -w : 13, s < 0 ? 3 : 16 + w, mat, { split: '', ends: [s < 0 ? 'west' : 'east'] });
      P(m, 'flange_' + n, [2.4, y - 0.6, 2.4], [13.6, y, 13.6], 'ci');
    }
    m.cylY('mandrel', [8, 8], 3.6, 21, 28, mat, { cap: 'oil/rig_red' });
    for (const s of [-1, 1]) P(m, 'choke' + s, [s < 0 ? 0 : 12, 18, 7], [s < 0 ? 4 : 16, 20, 9], 'ci');
    for (const x of [3, 12]) P(m, 'lug' + x, [x, 28, 7], [x + 1, 31, 9], 'yel');
    P(m, 'lug_bar', [3, 30, 7.4], [13, 31, 8.6], 'yel');
    return m;
  }
  function crown() {
    const m = mk('plant/yellow');
    for (const z of [0, 13]) P(m, 'main' + z, [-16, 0, z], [32, 4, z + 3], 'yel');
    for (const x of [-16, 29]) P(m, 'cross' + x, [x, 0, -16], [x + 3, 4, 24], 'yel');
    P(m, 'deck_n', [-13, 3, -16], [29, 3.6, 0], 'chequer'); P(m, 'deck_s', [-13, 3, 16], [29, 3.6, 24], 'chequer');
    for (const x of [-12.6, 27.4]) P(m, 'bearing' + x, [x, 4, 5], [x + 1.2, 11, 11], 'ci');
    P(m, 'gin_pole', [6, 4, 22], [8, 32, 24], 'yel', { split: 'y' });
    P(m, 'gin_top', [6, 30, 10], [8, 32, 24], 'yel');
    PLANT.railing(m, 'rail_n', -16, 32, 4, -16, 'x');
    return m;
  }
  function sheaves() {
    const m = mk('material/steel');
    m.prism('shaft', 'x', [8, 8], 1, -12.03, 28.03, 'bright', { split: '', ends: ['west', 'east'] });
    for (let x = -11, i = 0; x < 27; x += 6.4, i++) {
      m.prism('sheave' + i, 'x', [8, 8], 5, x, x + 2.6, 'st', { split: '', ends: ['west', 'east'] });
      m.prism('rope' + i, 'x', [8, 8], 4.4, x + 0.8, x + 1.8, 'rope', { split: '' });
    }
    return m;
  }
  function floorTile(formed) {
    const m = mk('material/chequer');
    P(m, 'deck', [0, 15, 0], [16, 16, 16], 'chequer');
    for (const z of [1, 13]) P(m, 'beam' + z, [0, 12, z], [16, 15, z + 2], 'yel');
    if (formed) {
      for (const [x, z] of [[1, 1], [13, 1], [1, 13], [13, 13]]) P(m, `leg_${x}_${z}`, [x, 0, z], [x + 2, 12, z + 2], 'yel');
      P(m, 'toe', [0, 16, 0], [16, 17, 0.6], 'yel');
    }
    return m;
  }
  function killManifold() {
    const m = mk('material/steel');
    FUTK.skid(m, 'skid', [0, 1], [16, 15]);
    m.prism('header', 'x', [6, 8], 1.6, -0.07, 16.07, 'st', { split: '' });
    for (const x of [3.4, 8, 12.6]) {
      P(m, 'valve' + x, [x - 1.4, 4, 6.6], [x + 1.4, 8, 9.4], 'rigred');
      m.wheelY('hw' + x, x, 10.4, 8, 1.6, 'ci');
      P(m, 'stem' + x, [x - 0.2, 8, 7.8], [x + 0.2, 10.4, 8.2], 'bright');
    }
    P(m, 'check', [5.2, 4.4, 9.4], [7, 7.6, 13], 'rigred');
    P(m, 'gauge_t', [10.4, 7.6, 3], [11.2, 10, 3.8], 'br');
    gauge(m, 'gauge', 10.8, 11.4, 3, 1.2);
    return m;
  }
  function degasser() {
    const m = mk('m3grid/tank');
    FUTK.skid(m, 'skid', [2, 1], [16, 14]);
    vessel(m, 'tank', [9, 8], 5, 2, 20, 'tank', { bottom: false });
    motor(m, 'vac', 'z', [4.4, 4.4], 1.8, 2, 8, 'blue', { feet: false });
    P(m, 'vent', [8.4, 22, 7.4], [9.6, 28.07, 8.6], 'p_gas');
    P(m, 'vent_ring', [8.2, 25, 7.2], [9.8, 25.6, 8.8], 'red');
    P(m, 'inlet', [13, 15, 7.4], [16.03, 16.2, 8.6], 'mud');
    P(m, 'outlet', [13, 3, 7.4], [16.03, 4.2, 8.6], 'mud');
    gauge(m, 'vac_g', 9, 13, 2.8, 1);
    return m;
  }
  function mudGasSep() {
    const m = mk('material/steel');
    for (const [x, z] of [[4, 2], [11, 2], [4, 10], [11, 10]]) P(m, `leg_${x}_${z}`, [x, -15.95, z], [x + 1, -10, z + 1], 'frame');
    vessel(m, 'shell', [8, 6.5], 4.6, -10, 18, 'st', { bottom: true });
    P(m, 'vent', [7.4, 20, 5.9], [8.6, 31.93, 7.1], 'gband');
    P(m, 'inlet', [12, 8, 5.9], [13, 9.2, 7.1], 'mud');
    P(m, 'u_tube', [7.4, -15, 0], [8.6, -13.8, 2], 'mud');
    P(m, 'u_leg', [7.4, -15, -0.03], [8.6, -9, 1.2], 'mud');
    ladder(m, 'ladder', 11.6, 10.6, -15.95, 22, 'z');
    return m;
  }
  function shaker() {
    const m = mk('plant/blue');
    for (const x of [1, 27]) for (const z of [1, 12]) { P(m, `foot_${x}_${z}`, [x, 0, z], [x + 3, 3, z + 2], 'frame'); P(m, `spring_${x}_${z}`, [x + 0.6, 3, z + 0.4], [x + 2.4, 6, z + 1.6], 'bright'); }
    for (const z of [0.6, 13.4]) P(m, 'basket' + z, [3, 6, z], [26, 12, z + 1], 'blue');
    P(m, 'basket_floor', [3, 6, 1.6], [26, 6.6, 13.4], 'blue');
    P(m, 'feeder', [0, 6, 1], [3, 16, 15], 'blue');
    P(m, 'flowline', [0, 13, 6], [1, 16, 10], 'mud');
    P(m, 'chute', [26, 3, 2], [32, 6, 14], 'st', { rot: { axis: 'z', angle: -22.5, origin: [26, 6, 8] } });
    for (const z of [0, 13]) motor(m, 'vib' + z, 'x', [14, z + 1], 1.6, 12, 18, 'blue', { feet: false });
    P(m, 'tank_lip', [0, 0, 0], [32, 1, 15], 'st');
    return m;
  }
  function screenDeck() { const m = mk('plant/blue'); P(m, 'screen', [3, 8, 2], [25, 9, 14], 'grat'); P(m, 'mud_on', [3, 9, 3], [14, 9.2, 13], 'mud', { hide: ['down'] }); return m; }

  // ---------------------------------------------------------------- gas processing
  function coldBox(cold) {
    const m = mk('material/steel');
    P(m, 'box', [-14, -16, -14], [30, 30, 30], 'st', { split: 'xyz' });
    for (let x = -14; x < 30; x += 11) for (const z of [-14.6, 30]) P(m, `stiff_${x}_${z}`, [x, -16, z], [x + 1, 30, z + 0.6], 'frame');
    P(m, 'roof', [-15, 30, -15], [31, 32, 31], 'st');
    for (const [y, i] of [[-8, 0], [4, 1], [16, 2]]) {
      P(m, 'nozzle' + i, [-16, y, 4], [-14, y + 3, 7], cold ? 'frost' : 'st');
      P(m, 'nozzle_e' + i, [30, y + 2, 9], [32, y + 5, 12], cold ? 'frost' : 'st');
    }
    m.box('cryo', [2, 6, -14.7], [8, 12, -14.6], 'st', { split: '', face: { north: { tex: D + 'cryo', uv: [0, 0, 16, 16] } } });
    m.decal('plate', [10, 8, -14.7], [14, 10, -14.6], 'st', 'north', 'parts.plate');
    ladder(m, 'ladder', 24, -15.4, -16, 30, 'x');
    return m;
  }
  function turboexpander() {
    const m = mk('m8field/greyblue');
    P(m, 'base', [0, 0, 1], [32, 2, 15], 'frame');
    m.prism('volute', 'x', [8, 8], 6, 0.6, 7, 'silver', { split: '' });
    P(m, 'inlet', [2, 13, 6], [6, 16.03, 10], 'frost');
    m.prism('outlet', 'x', [8, 8], 3, -0.03, 0.6, 'frost', { split: '', ends: ['west'] });
    P(m, 'bearing', [7, 2, 4], [14, 12, 12], 'gb');
    m.prism('comp', 'x', [8, 8], 5, 14, 22, 'gb', { split: '' });
    P(m, 'comp_out', [17, 12, 6.6], [19, 16, 9.4], 'p_gas');
    P(m, 'lube', [22, 2, 2], [30, 8, 14], 'gb');
    P(m, 'lube_line', [14, 12, 2], [22, 13, 3], 'p_liq');
    return m;
  }
  function turboRotor() { const m = mk('material/bright_steel'); m.prism('shaft', 'x', [8, 8], 1, -0.04, 28.03, 'bright', { split: '', ends: ['west', 'east'] }); return m; }

  // ---------------------------------------------------------------- exploration
  function coreBench(busy) {
    const m = mk('m6auto/white');
    for (const x of [0.6, 30.4]) P(m, 'leg' + x, [x, 0, 2], [x + 1, 11, 15], 'dk');
    P(m, 'top', [0, 11, 2], [32, 12, 15], 'wh');
    P(m, 'porosimeter', [2, 12, 4], [10, 18, 12], 'ss');
    m.decal('poro_g', [4.6, 14, 3.4], [7.4, 16.8, 4], 'ss', 'north', 'parts.gauge');
    P(m, 'permeameter', [12, 12, 5], [20, 15, 11], 'ss');
    m.prism('holder', 'x', [17, 8], 1.4, 13, 19, 'bright', { split: '' });
    m.cylY('core', [23, 6], 1, 12, 16, 'mud', { cap: 'oil/mud' });
    m.cylY('he', [28, 11], 1.8, 0, 24, 'he', { cap: D + 'he' });
    m.cylY('he_valve', [28, 11], 0.7, 24, 26.03, 'br');
    P(m, 'he_line', [21, 24.6, 10.6], [28, 25.2, 11.2], 'br');
    lamp(m, 'busy', 30, 13.4, 2, 'l_g', busy, 0.5);
    return m;
  }
  function loggingTruck() {
    const m = mk('plant/blue');
    for (const x of [-12, 4, 24]) for (const z of [1.5, 26]) wheel(m, `wheel_${x}_${z}`, x + 2, 3, z, 3, 4.4);
    P(m, 'chassis', [-16, 3, 4], [32, 5, 28], 'frame');
    P(m, 'cab', [20, 5, 5], [32, 22, 27], 'blue');
    P(m, 'windscreen', [31.8, 13, 7], [32, 20, 25], 'glass');
    for (const [f, t] of [[[-14, 5, 3.5], [20, 28, 5]], [[-14, 5, 27], [20, 28, 28.5]], [[-14, 26, 5], [20, 28, 27]], [[12, 5, 5], [20, 26, 27]]]) P(m, 'van', f, t, 'blue');
    P(m, 'floor', [-16, 5, 5], [12, 6, 27], 'chequer');
    for (const x of [-12.6, 6]) P(m, 'drum_stand' + x, [x, 6, 13], [x + 1.6, 16, 19], 'frame');
    P(m, 'console', [8, 6, 20], [12, 14, 26], 'dk');
    m.box('counter', [8.6, 11, 19.8], [11.4, 13, 20], 'dk', { split: '', face: { north: { tex: 'electronics/screen', uv: [0, 0, 16, 8] } } });
    P(m, 'boom', [-16, 6, 15], [-14, 22, 17], 'yel');
    m.prism('depth_wheel', 'z', [-14, 22], 1.8, 14, 18, 'st', { split: '' });
    return m;
  }
  function cableDrum() {
    const m = mk('m6auto/black');
    m.prism('drum', 'x', [15, 16], 5.6, -10, 6, 'cable', { split: 'x' });
    for (const x of [-12, 6]) m.prism('flange' + x, 'x', [15, 16], 7.2, x, x + 2, 'st', { split: '', ends: ['west', 'east'] });
    return m;
  }
  function vibrator() {
    const m = mk(D + 'yellow_post');
    for (const x of [2, 26]) for (const z of [0, 27]) wheel(m, `wheel_${x}_${z}`, x + 2, 4, z, 4, 5);
    P(m, 'frame', [0, 5, 5], [32, 8, 27], 'ypost');
    P(m, 'cab', [24, 8, 6], [32, 16, 26], 'ypost');
    P(m, 'glass', [31.8, 10, 8], [32, 15, 24], 'glass');
    P(m, 'plate', [6, 0, 8], [22, 1.4, 24], 'st');
    for (const x of [7, 19]) P(m, 'lift' + x, [x, 1.4, 14], [x + 2, 12, 18], 'bright');
    P(m, 'mass', [4, 2.4, 12], [24, 6.4, 20], 'br');
    P(m, 'engine', [0, 8, 8], [8, 14, 24], 'ypost');
    P(m, 'exhaust', [2, 14, 20], [3, 16, 21], 'st');
    return m;
  }
  function eccRotor() {
    const m = mk('material/steel');
    m.prism('shaft', 'x', [9.9, 16], 0.8, -0.03, 31.95, 'bright', { split: '', ends: ['west', 'east'] });
    for (const x of [8, 18]) P(m, 'ecc' + x, [x, 9.9, 13], [x + 4, 13, 19], 'st');
    return m;
  }

  // ---------------------------------------------------------------- production and treating
  function desalter(powered) {
    const m = mk('m3grid/tank');
    for (const x of [-12, 20]) P(m, 'saddle' + x, [x, 0, 9], [x + 4, 5, 23], 'frame');
    drum(m, 'vessel', 'x', [12, 16], 8, -13, 29, 'tank');
    P(m, 'tx', [6, 20, 10], [14, 27, 22], 'tank');
    for (const z of [12, 20]) FUTK.insulatorY(m, 'bush' + z, [10, z], 1.2, 27.4, 3.6, 'porc', { pitch: 1 });
    m.decal('hv', [7, 23, 9.8], [10, 26, 10], 'tank', 'north', 'parts.warning');
    lamp(m, 'pwr', 12, 25, 10, 'l_r', powered, 0.5);
    for (const x of [-8, 24]) P(m, 'noz' + x, [x, 20, 15], [x + 1.6, 23, 17], 'p_liq');
    P(m, 'water_out', [6, 0, 15], [8, 4, 17], 'p_water');
    ladder(m, 'ladder', 26, 5, 0, 24, 'x');
    return m;
  }
  function pjFrame(formed) {
    const m = mk('oil/pump_paint');
    if (!formed) { for (const z of [2, 11]) P(m, 'channel' + z, [0, 0, z], [16, 4, z + 3], 'pump'); return m; }
    for (const z of [0, 13]) P(m, 'rail' + z, [0, 0, z], [16, 5, z + 3], 'pump');
    P(m, 'cross', [6, 0, 3], [10, 4, 13], 'pump');
    P(m, 'pad', [0, 0, 0], [16, 0.01, 16], 'conc', { hide: ['down'] });
    return m;
  }
  function pjSamson() {
    const m = mk('oil/pump_paint');
    for (const z of [-10, 22]) P(m, 'leg' + z, [2, -16, z], [6, 22, z + 4], 'pump', { split: 'y', rot: { axis: 'x', angle: z < 0 ? -22.5 : 22.5, origin: [4, -16, z + 2] } });
    P(m, 'saddle', [2, 24, 4], [10, 28, 12], 'pump');
    m.prism('pin', 'z', [6, 28], 1.2, 2, 14, 'bright', { split: '', ends: ['north', 'south'] });
    ladder(m, 'ladder', 8, 6, -16, 22, 'z');
    return m;
  }
  function pjHead() {
    const m = mk('oil/pump_paint');
    for (const [y0, y1, x0] of [[-16, -8, -16], [-8, 8, -14], [8, 16, -12]]) P(m, 'arc' + y0, [x0, y0, 3], [x0 + 3, y1, 13], 'pump');
    P(m, 'web', [-13, -12, 6], [-6, 14, 10], 'pump');
    P(m, 'bridle', [-16.5 + 0.5, -16, 7], [-15, 12, 9], 'rope');
    return m;
  }
  function centrifugal() {
    const m = mk(D + 'greyblue');
    P(m, 'base', [-16, 0, 4], [32, 3, 28], 'frame');
    m.prism('casing', 'x', [12, 16], 8, -6, 20, 'gb', { split: 'x' });
    P(m, 'split_flange', [-6, 11.6, 7.4], [20, 12.4, 24.6], 'gb');
    for (const x of [-10, 20]) m.prism('bearing' + x, 'x', [12, 16], 4, x, x + 4, 'gb', { split: '', ends: x < 0 ? ['west'] : ['east'] });
    P(m, 'guard', [-16, 8, 12], [-10, 16, 20], 'yel');
    for (const [x, n] of [[-2, 'suction'], [12, 'discharge']]) {
      P(m, n, [x, 19, 13], [x + 6, 31.93, 19], 'gband');
      P(m, n + '_fl', [x - 0.6, 26, 12.4], [x + 6.6, 27, 19.6], 'ci');
    }
    P(m, 'lube', [22, 3, 6], [32, 10, 26], 'gb');
    m.decal('plate', [4, 6, 7.8], [8, 8, 7.9], 'gb', 'north', 'parts.plate');
    return m;
  }
  function centRotor() { const m = mk('material/steel'); m.prism('shaft', 'x', [12, 16], 1, -15.95, -9.97, 'bright', { split: '', ends: ['west', 'east'] }); m.prism('hub', 'x', [12, 16], 2.4, -14, -12, 'st', { split: '', ends: ['west', 'east'] }); return m; }
  function reciprocating() {
    const m = mk(D + 'greygreen');
    P(m, 'skid', [0, 0, 0], [32, 2, 32], 'frame');
    P(m, 'crankcase', [2, 2, 10], [30, 14, 22], 'gg');
    for (const s of [-1, 1]) {
      const z0 = s < 0 ? 0 : 22, z1 = s < 0 ? 10 : 32;
      P(m, 'distance' + s, [12, 8, s < 0 ? 6 : 22], [16, 14, s < 0 ? 10 : 26], 'gg');
      m.prism('cyl' + s, 'z', [14, 11], 4, s < 0 ? 0 : 26, s < 0 ? 6 : 32, 'gg', { split: '', ends: [s < 0 ? 'north' : 'south'] });
      vessel(m, 'bottle' + s, [14, s < 0 ? 3 : 29], 2.6, 16, 27, 'gg');
      P(m, 'bottle_leg' + s, [13.4, 14, s < 0 ? 2.4 : 28.4], [14.6, 16, s < 0 ? 3.6 : 29.6], 'gg');
      P(m, 'valve_cover' + s, [9.4, 13, z0 + (s < 0 ? 1 : 2)], [10.6, 15, z0 + (s < 0 ? 4 : 5)], 'ci');
    }
    motor(m, 'motor', 'x', [8, 16], 5, 20, 30, 'gg', { feet: false });
    return m;
  }
  function crankshaft() {
    const m = mk('material/cast_iron');
    m.prism('shaft', 'x', [8, 16], 1.2, -0.07, 31.95, 'bright', { split: '', ends: ['west', 'east'] });
    for (const x of [11, 15.4]) P(m, 'web' + x, [x, 2, 13], [x + 1.6, 14, 19], 'ci');
    m.prism('flywheel', 'x', [8, 16], 6, 2, 4, 'ci', { split: '', ends: ['west', 'east'] });
    return m;
  }
  function pistons() { const m = mk('material/bright_steel'); P(m, 'rod_n', [13, 10, 3.97], [15, 12, 13], 'bright'); P(m, 'rod_s', [13, 10, 19], [15, 12, 28.03], 'bright'); return m; }

  // ---------------------------------------------------------------- transport and storage
  function cpRectifier(on) {
    const m = mk('m3grid/tank');
    for (const x of [2, 13]) P(m, 'leg' + x, [x, 0, 6], [x + 1, 12, 8], 'galv');
    cabinet(m, 'cab', [1, 12, 2.4], [15, 30, 15], 'tank', { plate: true });
    m.decal('meter', [3, 22, 2.05], [7, 26, 2.2], 'tank', 'north', 'parts.gauge');
    lamp(m, 'on', 11, 24, 2.2, 'l_g', on, 0.6);
    P(m, 'cable', [7.4, 0, 9], [8.6, 12, 10.2], 'bk');
    return m;
  }
  function testPost() {
    const m = mk(D + 'yellow_post');
    P(m, 'post', [6, 0, 6], [10, 12, 10], 'ypost');
    P(m, 'cap', [5.5, 12, 5.5], [10.5, 13, 10.5], 'ypost');
    P(m, 'plate', [6.4, 8, 5.8], [9.6, 10.6, 6], 'wh');
    P(m, 'digit', [7.4, 8.6, 5.7], [8.6, 10, 5.8], 'bk');
    return m;
  }
  function bullet() {
    const m = mk(D + 'white');
    for (const x of [-12, 22]) P(m, 'saddle' + x, [x, 0, 9], [x + 4, 7, 23], 'conc');
    drum(m, 'shell', 'x', [14, 16], 8, -13, 29, 'wbul');
    m.ring('band', 'x', [14, 16], 8.3, 0.5, 6, 9, 'gband');
    for (const x of [-2, 18]) { P(m, 'psv' + x, [x, 22, 15], [x + 2, 25, 17], 'red'); P(m, 'psv_out' + x, [x + 0.4, 25, 15.4], [x + 1.6, 26.03, 16.6], 'st'); }
    P(m, 'platform', [-6, 22, 7], [22, 23, 11], 'grat');
    ladder(m, 'ladder', 24, 7.6, 0, 23, 'x');
    return m;
  }
  function odorizer(running) {
    const m = mk('m3grid/tank');
    cabinet(m, 'cab', [1, 0, 0.2], [9, 14, 15], 'tank', { plate: false });
    m.cylY('tank', [12, 8], 2.6, 0, 11, 'tank', { cap: 'm3grid/tank' });
    m.ringY('ring', [12, 8], 2.8, 0.4, 7, 8, 'gband');
    P(m, 'pump_line', [9, 3, 7.4], [10, 4, 8.6], 'br');
    P(m, 'gas_line', [9, 12, 2], [15, 13, 3.2], 'p_gas');
    lamp(m, 'run', 7, 12, 0.2, 'l_g', running, 0.5);
    m.decal('tox', [2, 8, -0.05], [5, 10.6, 0], 'tank', 'north', 'parts.warning');
    return m;
  }
  function tankGauge() {
    const m = mk('m6auto/dark');
    m.cylY('nozzle', [8.5, 8], 1.6, 0, 2, 'silver');
    m.cylY('flange', [8.5, 8], 2.2, 2, 2.6, 'ci', { cap: 'material/cast_iron' });
    P(m, 'head', [6, 2.6, 5.6], [11, 6, 10.4], 'dk');
    P(m, 'cover', [6.4, 6, 6], [10.6, 7, 10], 'dk');
    P(m, 'cable', [11, 3, 7.4], [12, 4, 8.6], 'bk');
    return m;
  }
  function floatingRoof() {
    const m = mk(D + 'tank_silver');
    P(m, 'deck', [0, 0.6, 0], [16, 1.6, 16], 'grat');
    P(m, 'pontoon', [0, 0, 0], [16, 2.5, 3], 'silver');
    P(m, 'seal', [0, 1.4, -0.01], [16, 2.5, 0.6], 'rubber');
    return m;
  }
  function tankRoof(kind) {
    const m = mk(D + 'tank_silver');
    P(m, 'plate', [0, 0, 0], [16, kind === 'formed' ? 1 : 2, 16], 'silver');
    if (kind === 'formed') for (const x of [3, 11]) P(m, 'rafter' + x, [x, 1, 0], [x + 1, 3, 16], 'silver');
    if (kind === 'vent') {
      m.cylY('neck', [8, 8], 1.6, 2, 4, 'silver');
      P(m, 'valve', [5.6, 4, 5.6], [10.4, 6.4, 10.4], 'gb');
      P(m, 'hood', [5, 6.4, 5], [11, 7.5, 11], 'gb');
    }
    return m;
  }
  function tankShell(kind) {
    const m = mk(D + 'tank_silver');
    P(m, 'wall', [0, 0, kind === 'formed' ? 12 : 10], [16, 16, 16], 'silver');
    if (kind === 'edge') P(m, 'curb', [0, 14.6, 8.6], [16, 16, 10], 'silver');
    if (kind === 'ladder') {
      for (let i = 0; i < 4; i++) P(m, 'tread' + i, [i * 4, i * 4, 6.6], [i * 4 + 4, i * 4 + 0.6, 10], 'grat');
      P(m, 'stringer', [0, 0, 6.4], [16, 1, 6.8], 'yel', { rot: { axis: 'z', angle: 45, origin: [0, 0, 6.6] } });
      for (let x = 2; x < 16; x += 6) P(m, 'post' + x, [x, x, 6.4], [x + 0.6, x + 6, 6.8], 'yel');
    }
    return m;
  }

  // ---------------------------------------------------------------- natural blocks
  const block = (mat, tex) => () => { const m = mk(tex); P(m, 'block', [0, 0, 0], [16, 16, 16], mat); return m; };
  function slickModel(age) { const m = Object.assign(mk(D + 'slick' + age), { renderType: 'minecraft:translucent' }); m.mat.sl = { tex: D + 'slick' + age, P: 16 }; P(m, 'film', [0, 0, 0], [16, 0.5, 16], 'sl', { hide: ['down', 'north', 'south', 'east', 'west'] }); return m; }
  function seep(burning) { const m = mk(D + 'cracked'); P(m, 'block', [0, 0, 0], [16, 16, 16], 'crack', { face: burning ? { up: 'crackhot' } : {} }); if (burning) P(m, 'glow_vent', [6, 16, 6], [10, 16.1, 10], 'fglow', { hide: ['down'] }); return m; }
  function hotSpring() {
    const m = mk(D + 'travertine');
    P(m, 'base', [0, 0, 0], [16, 12, 16], 'trav');
    for (const [f, t] of [[[0, 12, 0], [16, 16, 2]], [[0, 12, 14], [16, 16, 16]], [[0, 12, 2], [2, 16, 14]], [[14, 12, 2], [16, 16, 14]]]) P(m, 'rim', f, t, 'trav');
    P(m, 'pool', [2, 12, 2], [14, 15, 14], 'swat', { hide: ['down', 'north', 'south', 'east', 'west'] });
    return m;
  }

  const B = (model, cat, java) => ({ model, cat, java });
  const BUILDS = {
    bop_accumulator: B(() => accumulator(false), 'drilling'), bop_accumulator_charged: B(() => accumulator(true), 'drilling'),
    'bop_accumulator/levers': B(levers, 'drilling', 'bop_accumulator/partial/levers'),
    capping_stack: B(() => capping('open'), 'drilling'), capping_stack_closed: B(() => capping('closed'), 'drilling'), capping_stack_sooty: B(() => capping('sooty'), 'drilling'),
    crown_block: B(crown, 'drilling'), 'crown_block/sheaves': B(sheaves, 'drilling', 'crown_block/partial/sheaves'),
    drill_floor: B(() => floorTile(false), 'drilling'), drill_floor_formed: B(() => floorTile(true), 'drilling'),
    kill_manifold: B(killManifold, 'drilling'), mud_degasser: B(degasser, 'drilling'), mud_gas_separator: B(mudGasSep, 'drilling'),
    shale_shaker: B(shaker, 'drilling'), 'shale_shaker/screen_deck': B(screenDeck, 'drilling', 'shale_shaker/partial/screen_deck'),
    cold_box: B(() => coldBox(false), 'gas_processing'), cold_box_cold: B(() => coldBox(true), 'gas_processing'),
    turboexpander: B(turboexpander, 'gas_processing'), 'turboexpander/rotor': B(turboRotor, 'gas_processing', 'turboexpander/partial/rotor'),
    core_analysis_station: B(() => coreBench(false), 'oil_exploration'), core_analysis_station_busy: B(() => coreBench(true), 'oil_exploration'),
    logging_unit: B(loggingTruck, 'oil_exploration'), 'logging_unit/cable_drum': B(cableDrum, 'oil_exploration', 'logging_unit/partial/cable_drum'),
    seismic_vibrator: B(vibrator, 'oil_exploration'), 'seismic_vibrator/eccentric_rotor': B(eccRotor, 'oil_exploration', 'seismic_vibrator/partial/eccentric_rotor'),
    electrostatic_desalter: B(() => desalter(false), 'oil_production'), electrostatic_desalter_powered: B(() => desalter(true), 'oil_production'),
    pumpjack_frame: B(() => pjFrame(false), 'oil_production'), pumpjack_frame_formed: B(() => pjFrame(true), 'oil_production'),
    pumpjack_gearbox_samson: B(pjSamson, 'oil_production'), pumpjack_gearbox_head: B(pjHead, 'oil_production'),
    centrifugal_compressor: B(centrifugal, 'oil_transport'), 'centrifugal_compressor/rotor': B(centRotor, 'oil_transport', 'centrifugal_compressor/partial/rotor'),
    reciprocating_compressor: B(reciprocating, 'oil_transport'), 'reciprocating_compressor/crankshaft': B(crankshaft, 'oil_transport', 'reciprocating_compressor/partial/crankshaft'),
    'reciprocating_compressor/pistons': B(pistons, 'oil_transport', 'reciprocating_compressor/partial/pistons'),
    cp_rectifier: B(() => cpRectifier(false), 'oil_transport'), cp_rectifier_on: B(() => cpRectifier(true), 'oil_transport'),
    cp_test_post: B(testPost, 'oil_transport'), gas_storage_bullet: B(bullet, 'oil_transport'),
    odorizer: B(() => odorizer(false), 'oil_transport'), odorizer_running: B(() => odorizer(true), 'oil_transport'),
    tank_gauge: B(tankGauge, 'oil_transport'), 'tank_gauge/floating_roof': B(floatingRoof, 'oil_transport', 'tank_gauge/partial/floating_roof'),
    tank_roof: B(() => tankRoof('plain'), 'oil_transport'), tank_roof_formed: B(() => tankRoof('formed'), 'oil_transport'), tank_roof_vent: B(() => tankRoof('vent'), 'oil_transport'),
    tank_shell: B(() => tankShell('plain'), 'oil_transport'), tank_shell_edge: B(() => tankShell('edge'), 'oil_transport'),
    tank_shell_formed: B(() => tankShell('formed'), 'oil_transport'), tank_shell_ladder: B(() => tankShell('ladder'), 'oil_transport'),
    oil_slick: B(() => slickModel(0), 'oil_geology'), oil_slick_age1: B(() => slickModel(1), 'oil_geology'),
    oil_slick_age2: B(() => slickModel(2), 'oil_geology'), oil_slick_age3: B(() => slickModel(3), 'oil_geology'),
    rock_salt: B(block('salt', D + 'salt'), 'oil_geology'),
    gas_seep_vent: B(() => seep(false), 'world'), gas_seep_vent_burning: B(() => seep(true), 'world'),
    natural_bitumen: B(block('bitu', D + 'bitumen'), 'world'), hot_spring_vent: B(hotSpring, 'world'),
  };
  window.M8F = FUTK.machine(BUILDS, paint);
})();
