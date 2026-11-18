// HV grid equipment not yet in the mod, hand-authored (replaces the spec_grid.py / spec_grid2.py placeholders):
// DC busbar pack, turbogenerator stator segment, end shield and rotor, FE grid interface cabinet, earth electrode,
// 110 kV circuit breakers (minimum-oil and SF6), power transformers 35, 110 and 220 kV with radiators, ODC
// coolers and the cooling fan, surge arresters (ZnO, valve, failed) and the 110 kV lattice tower.
// Run headless: node art/tools/pcart_headless.js <repo> grid/hv_grid.author.js HVG --paint
// (or in Blockbench after pcart.js, pcround.js, plantkit.js, futurekit.js: HVG.paint(root); HVG.exportAll(root)).
//
// Front to the north. Footprints as the GDD rows and the placeholders: busbar node 1x1 with its north arm
// (busbar_dc_side, multipart); generator segments along x with the rotor partial about x through (y 8, z 8);
// breaker one 3x3x1 segment about the middle block; transformer tank segments about the middle block of 3x3x3;
// fan partial a disc at z -0.5..1.5 about (8, 8); tower segments 3x3x3 about the middle block.
(function () {
  const { rng, img, put, get, add, mix, rect, grain, vnoise } = PCART;
  const { P, lamp, cabinet, flange, motor, insulatorY, drum } = FUTK;
  const D = 'm3grid/', T = {};

  T.tank = FUTK.enamel([146, 146, 134], 31101, 2);         // RAL 7032 pebble grey
  T.gen = FUTK.enamel([196, 196, 186], 31102, 0);          // RAL 7035 light grey
  T.bushing = () => {                                      // brown glazed porcelain, glossy streak
    const im = img(16, 16), r = rng(31111);
    for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) put(im, x, y, add(mix([96, 50, 32], [150, 92, 62], x === 5 || x === 6 ? 0.8 : 0), (r() - 0.5) * 8));
    return im;
  };
  T.polymer = () => { const im = img(16, 16); grain(im, [118, 124, 128], 31121, 3, 4, 8); return im; };
  T.glass = () => {                                        // toughened glass discs, green
    const im = img(16, 16), r = rng(31131);
    for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) put(im, x, y, add(mix([60, 120, 104], [140, 200, 180], x % 8 === 2 ? 0.7 : 0), (r() - 0.5) * 10));
    return im;
  };
  // Lattice face of a tower panel: corner angles, X bracing and a strut, transparent between.
  T.lattice = () => {
    const im = img(32, 32), r = rng(31141), c = () => add([150, 154, 156], (r() - 0.5) * 16);
    for (let k = 0; k < 32; k++) {
      for (const w of [0, 1]) { put(im, k + w, k, c()); put(im, 31 - k - w, k, c()); }
      put(im, k, 0, c()); put(im, k, 1, add([110, 114, 116], 0)); put(im, k, 16, c());
    }
    for (let k = 0; k < 16; k++) { put(im, 8 + k / 2, 16 + k, c()); put(im, 23 - k / 2, 16 + k, c()); }
    return im;
  };
  T.lamp_blue = () => { const im = img(16, 16); for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) put(im, x, y, mix([170, 210, 255], [40, 90, 200], Math.hypot(x - 7.5, y - 7.5) / 9)); return im; };
  T.earth = () => { const im = img(16, 16); for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) put(im, x, y, ((x + y) >> 2) % 2 ? [220, 190, 40] : [50, 140, 60]); return im; };
  T.rotor = () => {                                        // rotor body: slot wedges along the barrel
    const im = img(16, 16), r = rng(31151);
    for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) put(im, x, y, add(y % 4 === 0 ? [150, 120, 70] : [72, 74, 78], (r() - 0.5) * 8));
    return im;
  };
  T.soot = () => { const im = img(16, 16), r = rng(31161); grain(im, [36, 34, 32], 31162, 6, 8, 4); for (let k = 0; k < 20; k++) put(im, r() * 16, r() * 16, [90, 84, 78]); return im; };

  const MATX = {
    tank: { tex: D + 'tank', P: 16 }, gen: { tex: D + 'gen', P: 16 }, bush: { tex: D + 'bushing', P: 16 }, poly: { tex: D + 'polymer', P: 16 },
    gdisc: { tex: D + 'glass', P: 16 }, lat: { tex: D + 'lattice', P: 16 }, l_b: { tex: D + 'lamp_blue', P: 16 }, earth: { tex: D + 'earth', P: 8 },
    rotor: { tex: D + 'rotor', P: 16 }, sootg: { tex: D + 'soot', P: 16 },
  };
  const mk = (p) => FUTK.mk(MATX, p);
  const paint = (root) => FUTK.paintSet(root, D, T);

  // ---------------------------------------------------------------- DC busbar
  // Four aluminium plates bolted into a pack on an insulating block over a concrete pier; the side arm runs the
  // pack north to the next node.
  function busbar() {
    const m = mk('grid_lines/alu');
    P(m, 'pier', [5, 0, 5], [11, 3, 11], 'conc');
    P(m, 'insul', [5.4, 3, 5.4], [10.6, 4.4, 10.6], 'rubber');
    for (const x of [3.6, 5.8, 8, 10.2]) P(m, 'plate' + x, [x, 4.4, 4], [x + 1.6, 12, 12], 'alu');
    for (const y of [6, 10.4]) for (const z of [5.4, 10]) P(m, `bolt_${y}_${z}`, [3, y, z], [13, y + 0.8, z + 0.8], 'st');
    P(m, 'clamp', [3.2, 4.2, 7.2], [12.8, 4.6, 8.8], 'st');
    return m;
  }
  function busbarSide() {
    const m = mk('grid_lines/alu');
    for (const x of [3.6, 5.8, 8, 10.2]) P(m, 'plate' + x, [x, 4.4, 0], [x + 1.6, 12, 4], 'alu');
    return m;
  }

  // ---------------------------------------------------------------- turbogenerator
  // Stator segment: round frame about x with end rings, feet, the gas cooler housing on top, a nameplate.
  function genStator() {
    const m = mk(D + 'gen');
    m.prism('frame', 'x', [8, 8], 7.2, 0, 16, 'gen', { split: 'x', ends: ['west', 'east'] });
    for (const x of [0, 7.6, 15.2]) m.ring('rib' + x, 'x', [8, 8], 7.8, 0.8, x, x + 0.8, 'gen');
    for (const z of [0.4, 12.6]) P(m, 'foot' + z, [0, 0, z], [16, 2.4, z + 3], 'frame');
    P(m, 'cooler', [2, 13.6, 4], [14, 16, 12], 'gen');
    for (const x of [3, 12]) P(m, 'cooler_pipe' + x, [x, 14.6, 3], [x + 1, 15.6, 4], 'p_water');
    m.decal('plate', [6, 9, 0.5], [10, 11, 0.7], 'gen', 'north', 'parts.plate');
    return m;
  }
  // End segment: the last stator ring, the end shield dome and the bearing pedestal with its oil pipes.
  function genEnd() {
    const m = mk(D + 'gen');
    m.prism('frame', 'x', [8, 8], 7.2, 8, 16, 'gen', { split: 'x', ends: ['west', 'east'] });
    m.ring('rib', 'x', [8, 8], 7.8, 0.8, 8, 8.8, 'gen');
    m.prism('shield', 'x', [8, 8], 6, 5, 8, 'gen', { split: '', ends: ['west'] });
    m.prism('shield2', 'x', [8, 8], 4, 3.6, 5, 'gen', { split: '', ends: ['west'] });
    P(m, 'pedestal', [0, 0, 4.6], [4, 6.4, 11.4], 'ci');
    P(m, 'bearing_cap', [0.4, 6.4, 5.4], [3.6, 9.6, 10.6], 'ci');
    P(m, 'oil_in', [1.4, 9.6, 7.6], [2.2, 12, 8.4], 'p_liq');
    P(m, 'oil_out', [0.4, 1, 11.4], [1.2, 2, 14], 'p_liq');
    for (const z of [0.4, 12.6]) P(m, 'foot' + z, [8, 0, z], [16, 2.4, z + 3], 'frame');
    return m;
  }
  function genRotor() {
    const m = mk(D + 'rotor');
    m.prism('shaft', 'x', [8, 8], 1.2, -1, 17, 'bright', { split: '', ends: ['west', 'east'] });
    m.prism('barrel', 'x', [8, 8], 2.75, 1, 15, 'rotor', { split: 'x' });
    for (const x of [0, 15]) m.prism('ring' + x, 'x', [8, 8], 2.7, x, x + 1, 'br', { split: '', ends: ['west', 'east'] });
    return m;
  }

  // ---------------------------------------------------------------- FE interface
  function feInterface(mode) {
    const m = mk('switchgear_cell/enamel');
    cabinet(m, 'cab', [1, 0, 1.4], [15, 16, 15], 'en', { plate: true });
    for (const y of [2, 12.6]) P(m, 'louvre' + y, [3, y, 1], [10, y + 1.8, 1.2], 'louv');
    P(m, 'disp', [3, 8.4, 1], [8, 11, 1.2], 'disp');
    const lampMat = { import: 'l_b', export: 'l_a', off: 'l_off' }[mode];
    lamp(m, 'dir', 11, 9.6, 1.2, lampMat, mode !== 'off', 0.9);
    P(m, 'arrow', mode === 'export' ? [9.6, 7.6, 1] : [11.4, 7.6, 1], mode === 'export' ? [11, 8, 1.2] : [12.8, 8, 1.2], 'yel');
    for (const x of [4, 7, 10]) P(m, 'bus' + x, [x, 16, 7], [x + 1.2, 16.5, 11], 'cu');
    m.decal('hv', [11.6, 12.6, 1], [13.4, 14.4, 1.2], 'en', 'north', 'parts.warning');
    return m;
  }

  // ---------------------------------------------------------------- earth electrode
  function groundElectrode() {
    const m = mk('disconnector/copper');
    for (const [f, t] of [[[4, 0, 4], [12, 1.4, 5]], [[4, 0, 11], [12, 1.4, 12]], [[4, 0, 5], [5, 1.4, 11]], [[11, 0, 5], [12, 1.4, 11]]]) P(m, 'well', f, t, 'conc');
    P(m, 'gravel', [5, 0, 5], [11, 0.6, 11], 'gravel');
    m.cylY('rod', [8, 8], 0.6, 0.6, 10, 'cu', { cap: 'disconnector/copper' });
    P(m, 'clamp', [7.1, 7.6, 7.1], [8.9, 9.2, 8.9], 'br');
    P(m, 'clamp_bolt', [8.9, 8, 7.7], [9.6, 8.8, 8.3], 'st');
    P(m, 'conductor', [8.9, 8.2, 7.6], [16, 8.8, 8.4], 'earth');
    P(m, 'sign', [5, 1.4, 4.2], [8, 4, 4.4], 'light');
    return m;
  }

  // ---------------------------------------------------------------- 110 kV breaker
  // Three poles on a common frame over two galvanised posts, the spring drive cabinet low on the west, a
  // position indicator (red closed, green open) on the cabinet and the flag on the frame.
  function breaker(kind, closed) {
    const m = mk('disconnector/galvanised');
    for (const x of [-13, 27]) {
      P(m, 'post' + x, [x, -16, 4.6], [x + 2, -2, 8.6], 'galv');
      P(m, 'post_foot' + x, [x - 1, -16, 3.6], [x + 3, -15.4, 9.6], 'galv');
    }
    P(m, 'beam', [-15, -2, 3.6], [31, 0, 9.6], 'galv');
    P(m, 'rod_shaft', [-13.6, 0, 9.6], [29.6, 1, 10.6], 'st');
    for (const x of [-8, 8, 24]) {
      const c = [x, 6.6];
      if (kind === 'oil') {
        insulatorY(m, 'sup' + x, c, 2, 0.6, 8.4, 'porc', { pitch: 1.4 });
        m.cylY('tank' + x, c, 2.6, 9.6, 13.4, 'tank', { cap: D + 'tank', bottom: true });
        insulatorY(m, 'arc' + x, c, 1.8, 14, 6, 'porc', { pitch: 1.3 });
        m.cylY('head' + x, c, 1.6, 20.6, 21, 'alu', { cap: 'grid_lines/alu' });
        P(m, 'pad_w' + x, [x - 3, 19.4, 6], [x - 1.4, 20.6, 7.2], 'alu');
        P(m, 'pad_e' + x, [x + 1.4, 11, 6], [x + 3.4, 12, 7.2], 'alu');
        P(m, 'gauge' + x, [x - 0.6, 11, 3.8], [x + 0.6, 12.4, 4.2], 'glass');
      } else {
        P(m, 'base' + x, [x - 2.4, 0, 4.2], [x + 2.4, 2.4, 9], 'galv');
        insulatorY(m, 'col' + x, c, 2, 3, 18, 'poly', { pitch: 1.2, alt: true, cap: 'alu' });
        m.cylY('head' + x, c, 1.8, 21.6, 23, 'alu', { cap: 'grid_lines/alu' });
        P(m, 'pad' + x, [x + 1.6, 22, 6], [x + 3.6, 23, 7.2], 'alu');
        P(m, 'density' + x, [x - 0.8, 0.6, 3.8], [x + 0.8, 2, 4.2], 'glass');
      }
    }
    cabinet(m, 'drive', [-14, -14, 0.4], [-6, -4, 4], 'en', { plate: true });
    P(m, 'drive_rod', [-10.6, -4, 2], [-9.4, 0, 3.2], 'st');
    lamp(m, 'pos', -7.6, -6, 0.2, closed ? 'l_r' : 'l_g', true, 0.6);
    P(m, 'flag_hub', [-15, -1.4, 2.6], [-14, 0.4, 3.6], 'ci');
    P(m, 'flag', [-17 + 2, -0.9, 2.2], [-15 + 2 - 0.4, -0.1, 2.6], closed ? 'red' : 'green', { rot: { axis: 'z', angle: closed ? 0 : 45, origin: [-14.5, -0.5, 2.4] } });
    m.decal('haz', [-12.9, -10, 4.5], [-11.1, -8.2, 4.6], 'galv', 'north', 'parts.warning');
    return m;
  }

  // ---------------------------------------------------------------- power transformers
  // Bushing with its turret on the cover.
  function bushing(m, n, x, z, y0, h, r, mat) {
    m.cylY(n + '_turret', [x, z], r + 0.4, y0, y0 + 1.6, 'tank', { cap: D + 'tank' });
    insulatorY(m, n, [x, z], r, y0 + 2.2, h, mat, { pitch: r > 2 ? 1.4 : 1.1, alt: r > 2 });
    m.cylY(n + '_term', [x, z], 0.5, y0 + 2.8 + h, y0 + 4 + h, 'alu', { cap: 'grid_lines/alu' });
  }
  function tankBody(m, f, t, mat, ribs) {
    P(m, 'tank', f, t, mat);
    if (ribs) for (let x = f[0] + 4; x < t[0] - 2; x += 8) for (const [z0, z1] of [[f[2] - 0.6, f[2]], [t[2], t[2] + 0.6]]) P(m, `rib_${x}_${z0}`, [x, f[1] + 1, z0], [x + 1, t[1] - 1, z1], mat);
    P(m, 'cover', [f[0] - 0.6, t[1], f[2] - 0.6], [t[0] + 0.6, t[1] + 1, t[2] + 0.6], 'tank');
    P(m, 'base_frame', [f[0] - 0.4, f[1] - 1.4, f[2] - 0.4], [t[0] + 0.4, f[1], t[2] + 0.4], 'frame');
    for (const z of [f[2] + 1, t[2] - 3]) P(m, 'rail' + z, [f[0] - 2, -16, z], [t[0] + 2, f[1] - 1.4, z + 2], 'frame');
  }
  function conservator(m, y, z, r, x0, x1, brackets) {
    drum(m, 'cons', 'x', [y, z], r, x0, x1, 'tank');
    for (const x of brackets) P(m, 'cons_br' + x, [x, brackets.y0, z - 0.8], [x + 1.2, y - r + 0.4, z + 0.8], 'frame');
    m.decal('oil_gauge', [x0 - r * 0.2 - 0.3, y - 1.2, z - 1.2], [x0 - r * 0.2, y + 1.2, z + 1.2], 'tank', 'west', 'parts.gauge');
    P(m, 'breather', [x1 - 3, y - r - 4, z + r - 0.4], [x1 - 1.6, y - r - 0.6, z + r + 1], 'glass');
  }
  function tr110() {
    const m = mk(D + 'tank');
    tankBody(m, [-12, -14.6, -6], [28, 10, 22], 'tank', true);
    P(m, 'oltc', [-14.4, -10, 0], [-12, 8, 16], 'tank');
    cabinet(m, 'oltc_drive', [-15.4, -13, 3.6], [-14.4, -4, 10.4], 'tank', { plate: false, inset: 0.3 });
    for (const x of [-4, 8, 20]) bushing(m, 'hv' + x, x, 2, 11, 13, 2.2, 'bush');
    bushing(m, 'n', 26, 2, 11, 5, 1.2, 'bush');
    for (const x of [0, 8, 16]) bushing(m, 'mv' + x, x, 15, 11, 4, 1.2, 'porc');
    const br = [-6, 10]; br.y0 = 11;
    conservator(m, 21, 19.6, 3, -10, 14, br);
    P(m, 'buchholz_h', [14.6, 17.6, 19], [22, 18.6, 20], 'tank');
    P(m, 'buchholz_relay', [17, 17.2, 18.6], [19, 19, 20.4], 'ci');
    P(m, 'buchholz_v', [21, 11, 19], [22, 17.6, 20], 'tank');
    m.decal('plate', [6, 0, -6.7], [10, 2, -6.6], 'tank', 'north', 'parts.plate');
    m.decal('haz', [12, 0, -6.7], [15, 2.6, -6.6], 'tank', 'north', 'parts.warning');
    for (const [x, z] of [[-11.6, -5.4], [27, -5.4]]) P(m, 'earth' + x, [x, -14, z - 0.6], [x + 0.6, -12, z], 'earth');
    return m;
  }
  // Radiator bank: panels across the tank wall on top and bottom headers, the fan brackets under them.
  function radiators() {
    const m = mk('distribution_transformer/fins');
    for (let x = -13, i = 0; x < 29; x += 3.5, i++) P(m, 'panel' + i, [x, -12, -4], [x + 1.2, 6, 20], 'fin');
    for (const y of [-13.4, 6]) P(m, 'header' + y, [-14, y, 20], [30, y + 1.6, 22.4], 'tank');
    for (const y of [-13.4, 6]) for (const x of [-6, 8, 22]) P(m, `valve_${y}_${x}`, [x, y, 22.4], [x + 1.6, y + 1.6, 24], 'ci');
    for (const x of [-10, 6, 22]) {
      P(m, 'fan_br' + x, [x, -14, -5.6], [x + 0.8, -2, -4], 'frame');
      m.ring('fan_guard' + x, 'z', [x + 4.4, -8], 4.4, 0.6, -6.2, -5.6, 'frame');
    }
    P(m, 'stand', [-14, -16, 6], [30, -13.4, 8], 'frame');
    return m;
  }
  function fan() {
    const m = mk('plant/structure');
    m.prism('hub', 'z', [8, 8], 1.4, -0.5, 1.5, 'ci', { split: '', ends: ['north', 'south'] });
    for (const a of [45, -45]) P(m, 'blade' + a, [3.2, 7.1, 0], [12.8, 8.9, 0.6], 'galv', { rot: { axis: 'z', angle: a, origin: [8, 8, 8] } });
    return m;
  }
  function tr35() {
    const m = mk(D + 'tank');
    P(m, 'tank_core', [-8, -14, -4], [24, 6, 20], 'tank');
    for (const [f, t] of [[[-10, -13, -6], [26, 5, -4]], [[-10, -13, 20], [26, 5, 22]], [[-10, -13, -4], [-8, 5, 20]], [[24, -13, -4], [26, 5, 20]]]) P(m, 'corr', f, t, 'fin');
    P(m, 'cover', [-10.6, 6, -6.6], [26.6, 7, 22.6], 'tank');
    P(m, 'base', [-10.4, -15, -6.4], [26.4, -14, 22.4], 'frame');
    for (const z of [-4, 18]) P(m, 'skid' + z, [-12, -16, z], [28, -15, z + 2], 'frame');
    for (const x of [-2, 8, 18]) bushing(m, 'hv' + x, x, 3, 7, 9, 1.6, 'porc');
    for (const x of [0, 6, 12, 18]) bushing(m, 'lv' + x, x, 15, 7, 2.4, 1, 'porc');
    const br = [-6, 8]; br.y0 = 7;
    conservator(m, 16, 19, 2.4, -8, 12, br);
    P(m, 'oltc_drive', [-12, -12, 2], [-10, -5, 8], 'tank');
    m.decal('plate', [6, -4, -6.1], [10, -2, -6], 'fin', 'north', 'parts.plate');
    return m;
  }
  function tr220() {
    const m = mk(D + 'tank');
    tankBody(m, [-14, -14, -10], [30, 12, 26], 'tank', true);
    for (const x of [-6, 8, 22]) bushing(m, 'hv' + x, x, -2, 13, 13.5, 2.6, 'bush');
    for (const x of [-4, 8, 20]) bushing(m, 'mv' + x, x, 12, 13, 7, 1.8, 'bush');
    for (const x of [-10, 26]) bushing(m, 'lv' + x, x, 20, 13, 3, 1.1, 'porc');
    const br = [-8, 14]; br.y0 = 13;
    conservator(m, 24, 23.4, 3.2, -12, 20, br);
    P(m, 'oltc', [-16, -10, 0], [-14, 10, 16], 'tank');
    m.decal('plate', [6, 0, -10.7], [10, 2, -10.6], 'tank', 'north', 'parts.plate');
    m.decal('haz', [12, 0, -10.7], [15, 2.6, -10.6], 'tank', 'north', 'parts.warning');
    return m;
  }
  // ODC cooler segment: three forced-oil coolers with fan cowls on the front, pumps under, headers behind.
  function cooler() {
    const m = mk('distribution_transformer/fins');
    for (const [x0, i] of [[-12, 0], [3, 1], [18, 2]]) {
      P(m, 'core' + i, [x0, -10, -2], [x0 + 10, 10, 12], 'fin');
      P(m, 'top' + i, [x0 - 0.4, 10, -2.4], [x0 + 10.4, 11, 12.4], 'tank');
      m.prism('cowl' + i, 'z', [x0 + 5, 0], 4.4, -4, -2, 'tank', { split: '' });
      m.ring('guard' + i, 'z', [x0 + 5, 0], 4.6, 0.5, -4.4, -4, 'frame');
      motor(m, 'pump' + i, 'x', [-13, 8], 1.8, x0 + 1, x0 + 7, 'blue', { feet: false });
      P(m, 'leg' + i, [x0 + 0.4, -16, 0], [x0 + 1.4, -10, 1], 'frame'); P(m, 'leg_b' + i, [x0 + 8.6, -16, 0], [x0 + 9.6, -10, 1], 'frame');
    }
    for (const y of [-12, 8]) P(m, 'header' + y, [-14, y, 14], [30, y + 2, 16.6], 'tank');
    for (const x of [-8, 7, 22]) P(m, 'pipe' + x, [x, -12, 12], [x + 1.6, 10, 14], 'tank');
    P(m, 'cab', [26, -12, -4], [30, -2, 0], 'en');
    return m;
  }

  // ---------------------------------------------------------------- surge arrester
  function arrester(kind) {
    const m = mk(kind === 'valve' ? 'disconnector/porcelain' : D + 'polymer');
    P(m, 'base', [4, 0, 4], [14, 1, 12], 'galv');
    for (const [x, z] of [[6.4, 6.4], [10.6, 6.4], [6.4, 9.6], [10.6, 9.6]]) P(m, `foot_${x}_${z}`, [x - 0.6, 1, z - 0.6], [x + 0.6, 2.4, z + 0.6], 'rubber');
    P(m, 'plinth', [6, 2.4, 6], [12, 3.4, 10], 'galv');
    P(m, 'counter', [5, 1, 4], [8, 4, 5.6], 'ci');
    P(m, 'counter_glass', [5.6, 2, 3.8], [7.4, 3.4, 4], 'glass');
    P(m, 'lead', [7.6, 1.6, 5.6], [8.4, 3, 7], 'cu');
    const mat = kind === 'valve' ? 'porc' : 'poly';
    if (kind === 'failed') {
      insulatorY(m, 'body', [9, 8], 2.6, 3.4, 5, mat, { pitch: 1.2, alt: true });
      m.cylY('rupture', [9, 8], 1.8, 8.4, 10, 'sootg', { cap: D + 'soot' });
      P(m, 'shard', [10.6, 1, 11], [12.6, 1.6, 13], 'poly', { rot: { axis: 'y', angle: 22.5, origin: [11.6, 1, 12] } });
      P(m, 'disc_lead', [9.4, 3.6, 11.6], [10, 9, 12.2], 'cu', { rot: { axis: 'x', angle: -22.5, origin: [9.7, 9, 12] } });
      P(m, 'scorch', [6.6, 10, 5.6], [11.4, 10.2, 10.4], 'sootg');
    } else {
      insulatorY(m, 'body', [9, 8], kind === 'valve' ? 2.8 : 2.6, 3.4, 10.4, mat, { pitch: kind === 'valve' ? 1.6 : 1.1, alt: true });
      P(m, 'top', [7, 14.4, 6], [11, 15, 10], 'alu');
      P(m, 'term', [8.4, 15, 7.4], [9.6, 16, 8.6], 'alu');
      if (kind === 'valve') m.ring('grading', 'z', [9, 13.6], 1.6, 0.3, 10.4, 10.8, 'alu');
    }
    return m;
  }

  // ---------------------------------------------------------------- 110 kV lattice tower
  // A lattice panel face per block of height: the lattice texture over the whole panel, both sides shown.
  function panel(m, n, axis, a0, a1, at, y0, y1) {
    for (let y = y0; y < y1; y += 16) {
      const yt = Math.min(y + 16, y1), uv = [0, 16 - (yt - y), 16, 16];
      const face = axis === 'x' ? { north: { tex: D + 'lattice', uv }, south: { tex: D + 'lattice', uv } } : { east: { tex: D + 'lattice', uv }, west: { tex: D + 'lattice', uv } };
      const f = axis === 'x' ? [a0, y, at] : [at, y, a0], t = axis === 'x' ? [a1, yt, at + 0.02] : [at + 0.02, yt, a1];
      m.box(`${n}_${y}`, f, t, 'lat', { split: '', face, hide: axis === 'x' ? ['east', 'west', 'up', 'down'] : ['north', 'south', 'up', 'down'] });
    }
  }
  function towerBody(m, lo, hi, y0, y1) {
    for (const x of [lo, hi - 1]) for (const z of [lo, hi - 1]) P(m, `leg_${x}_${z}_${y0}`, [x, y0, z], [x + 1, y1, z + 1], 'galv', { split: 'y' });
    panel(m, 'pn' + y0, 'x', lo + 1, hi - 1, lo + 0.5, y0, y1);
    panel(m, 'ps' + y0, 'x', lo + 1, hi - 1, hi - 0.5, y0, y1);
    panel(m, 'pw' + y0, 'z', lo + 1, hi - 1, lo + 0.5, y0, y1);
    panel(m, 'pe' + y0, 'z', lo + 1, hi - 1, hi - 0.5, y0, y1);
  }
  const lat = (m) => Object.assign(m, { renderType: 'minecraft:cutout' });
  function towerMid() { const m = lat(mk('disconnector/galvanised')); towerBody(m, -6, 22, -16, 32); return m; }
  function towerBase() {
    const m = lat(mk('disconnector/galvanised'));
    for (const x of [-8, 20]) for (const z of [-8, 20]) {
      P(m, `found_${x}_${z}`, [x - 0.5, -16, z - 0.5], [x + 4.5, -13, z + 4.5], 'conc');
      P(m, `stub_${x}_${z}`, [x + 1, -13, z + 1], [x + 3, -11, z + 3], 'galv');
    }
    towerBody(m, -8, 24, -11, 8);
    towerBody(m, -6, 22, 8, 32);
    P(m, 'sign', [6, -4, -8.2], [10, 0, -8], 'light');
    m.decal('haz', [6.4, -3.6, -8.3], [9.6, -0.6, -8.2], 'light', 'north', 'parts.warning');
    return m;
  }
  function insulatorString(m, n, x, z, yTop, len) {
    P(m, n + '_link', [x - 0.2, yTop - 1, z - 0.2], [x + 0.2, yTop, z + 0.2], 'st');
    for (let y = yTop - 1.6, i = 0; y > yTop - len; y -= 1, i++) m.cylY(`${n}_d${i}`, [x, z], 1.3, y, y + 0.35, 'gdisc', { cap: D + 'glass', bottom: true });
    P(m, n + '_clamp', [x - 0.6, yTop - len - 1, z - 1.4], [x + 0.6, yTop - len, z + 1.4], 'alu');
  }
  function towerTop() {
    const m = lat(mk('disconnector/galvanised'));
    towerBody(m, -6, 22, -16, 16);
    towerBody(m, -2, 18, 16, 28);
    for (const [y, x0, x1] of [[0, -16, 32], [14, -12, 28]]) {
      P(m, 'arm_lo' + y, [x0, y, 4], [x1, y + 1, 5], 'galv'); P(m, 'arm_lo2' + y, [x0, y, 11], [x1, y + 1, 12], 'galv');
      P(m, 'arm_top' + y, [x0 + 2, y + 2.4, 7.4], [x1 - 2, y + 3.2, 8.6], 'galv');
      panel(m, 'arm_pn' + y, 'x', x0 + 1, x1 - 1, 4.4, y, y + 3);
    }
    for (const x of [-14.6, 30.6]) insulatorString(m, 'str' + x, x, 8, 0, 8);
    insulatorString(m, 'str_m', 27, 8, 14, 8);
    P(m, 'peak', [7, 28, 7], [9, 32, 9], 'galv');
    P(m, 'gw_clamp', [6.4, 31, 6], [9.6, 32, 10], 'alu');
    return m;
  }

  const B = (model, java) => ({ model, cat: 'grid', java });
  const BUILDS = {
    busbar_dc: B(busbar), busbar_dc_side: B(busbarSide),
    electric_generator: B(genStator), electric_generator_end: B(genEnd),
    'electric_generator/rotor': B(genRotor, 'electric_generator/partial/rotor'),
    grid_fe_interface: B(() => feInterface('off')), grid_fe_interface_import: B(() => feInterface('import')),
    grid_fe_interface_export: B(() => feInterface('export')),
    ground_electrode: B(groundElectrode),
    hv_circuit_breaker: B(() => breaker('oil', true)), hv_circuit_breaker_open: B(() => breaker('oil', false)),
    hv_circuit_breaker_sf6: B(() => breaker('sf6', true)), hv_circuit_breaker_sf6_open: B(() => breaker('sf6', false)),
    power_transformer: B(tr110), power_transformer_radiators: B(radiators), 'power_transformer/fan': B(fan, 'power_transformer/partial/fan'),
    power_transformer_35: B(tr35), power_transformer_220: B(tr220), power_transformer_220_cooler: B(cooler),
    'power_transformer_220/fan': B(fan, 'power_transformer_220/partial/fan'),
    surge_arrester: B(() => arrester('zno')), surge_arrester_valve: B(() => arrester('valve')), surge_arrester_failed: B(() => arrester('failed')),
    transmission_tower: B(towerMid), transmission_tower_base: B(towerBase), transmission_tower_top: B(towerTop),
  };
  window.HVG = FUTK.machine(BUILDS, paint);
})();
