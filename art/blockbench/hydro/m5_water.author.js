// M5 water and hydro equipment not yet in the mod, hand-authored. Replaces the spec_grid2.py placeholders of the
// cooling tower (basin, fill, shell profiles), spray pond nozzle, water intake, battery charger, fuel day tank and
// the 07 penstock pipe; draws from nothing the GDD 15 hydro set (clay core, dam concrete, dam control board,
// umbrella hydro generator with its rotor, governors and flyballs, intake gate and leaf, staff gauge, large
// penstock sections, Tainter spillway gate and leaf, surge tank, trash rack) and the M5 weather instruments
// (anemometer and cups, wind vane, reservoir level gauge).
// Run headless: node art/tools/pcart_headless.js <repo> hydro/m5_water.author.js M5W --paint
//
// Front to the north. Sizes over one block follow the README: size 2 runs 0..32 from the anchor, size 3 is
// centred on the middle block (-16..32). Pivots: generator rotor about y through (8, 8); flyballs about y through
// (8, 8); anemometer cups and wind vane about y through (8, 8); intake gate leaf moves in y; spillway leaf turns
// about x through the trunnion (y 26, z 14).
(function () {
  const { rng, img, put, get, add, mix, rect, grain, vnoise } = PCART;
  const { P, lamp, cabinet, vessel, drum, flange, motor, gauge, ladder } = FUTK;
  const D = 'm5water/', T = {};

  T.clay = () => { const im = img(32, 32), r = grain(im, [112, 100, 86], 34101, 5, 9, 8); for (let k = 0; k < 30; k++) put(im, r() * 32, r() * 32, [88, 78, 66]); for (let k = 0; k < 10; k++) put(im, r() * 32, r() * 32, [140, 130, 112]); return im; };
  // Hydraulic concrete: board-formed lifts with a lift joint, tie holes; crack overlays painted in.
  const damConc = (cracks) => () => {
    const im = img(32, 32), r = grain(im, [150, 148, 138], 34111, 5, 6, 8);
    for (let y = 3; y < 32; y += 4) for (let x = 0; x < 32; x++) put(im, x, y, add(get(im, x, y), -8));
    for (let x = 0; x < 32; x++) { put(im, x, 15, [104, 102, 96]); put(im, x, 31, [104, 102, 96]); }
    for (const [x, y] of [[6, 7], [22, 7], [6, 23], [22, 23]]) rect(im, x, y, 2, 2, [92, 90, 86]);
    for (let k = 0; k < cracks * 3; k++) {
      let x = 4 + r() * 24, y = r() * 8;
      for (let i = 0; i < 24 + cracks * 8; i++) { put(im, x, y, [52, 50, 48]); if (r() < 0.4) put(im, x + 1, y, [80, 78, 74]); x += (r() - 0.5) * 2; y += r() * 1.4; }
    }
    if (cracks === 2) for (let k = 0; k < 20; k++) put(im, r() * 32, 26 + r() * 6, [96, 112, 96]);
    return im;
  };
  T.dam = damConc(0); T.dam_crack1 = damConc(1); T.dam_crack2 = damConc(2);
  T.staff = () => {
    const im = img(16, 16);
    for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
      const band = Math.floor(y / 2) % 2, m = Math.floor(y / 8) % 2;
      let c = [236, 236, 230];
      if (x >= 3 && x < 8 && band === 0) c = m ? [30, 30, 30] : [200, 36, 30];
      if (x >= 8 && x < 13 && y % 8 === 0) c = m ? [30, 30, 30] : [200, 36, 30];
      if (x === 0 || x === 15) c = [150, 150, 146];
      put(im, x, y, c);
    }
    return im;
  };
  T.gen_red = FUTK.enamel([128, 58, 44], 34121, 2);
  T.ral6005 = FUTK.enamel([40, 74, 56], 34122, 5);
  T.leaves = () => {
    const im = img(16, 16), r = rng(34131);
    for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) put(im, x, y, [0, 0, 0, 0]);
    for (let k = 0; k < 70; k++) { const x = r() * 15, y = r() * 15, c = [[90, 110, 40], [130, 100, 40], [70, 80, 36], [110, 70, 30]][Math.floor(r() * 4)]; put(im, x, y, c); put(im, x + 1, y, add(c, -16)); put(im, x, y + 1, add(c, 10)); }
    return im;
  };
  T.tower = () => { const im = img(32, 32), r = grain(im, [168, 166, 158], 34141, 4, 7, 8); for (let y = 7; y < 32; y += 8) for (let x = 0; x < 32; x++) put(im, x, y, add(get(im, x, y), -10)); for (let k = 0; k < 6; k++) { const x = Math.floor(r() * 32); for (let y = 0; y < 32; y++) if (r() < 0.7) put(im, x, y, add(get(im, x, y), -12)); } return im; };
  T.tower_band = () => { const im = img(32, 32), r = rng(34142); for (let y = 0; y < 32; y++) for (let x = 0; x < 32; x++) put(im, x, y, add(Math.floor(y / 8) % 2 ? [236, 234, 228] : [190, 44, 36], (r() - 0.5) * 8)); return im; };
  T.fill = () => { const im = img(16, 16); for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) { const hx = (x + (Math.floor(y / 3) % 2) * 2) % 4; put(im, x, y, hx === 0 || y % 3 === 0 ? [40, 46, 44] : [96, 112, 104]); } return im; };
  T.green_water = () => { const im = img(16, 16), r = rng(34151); for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) put(im, x, y, add([44, 96, 80], Math.sin(x * 0.7 + y * 0.3) * 8 + (r() - 0.5) * 8)); return im; };
  T.dial = () => {
    const im = img(32, 32), r = rng(34161); rect(im, 0, 0, 32, 32, [24, 24, 24]);
    rect(im, 4, 3, 24, 26, [230, 226, 210]);
    for (let y = 5; y < 27; y += 2) rect(im, 6, y, y % 10 === 5 ? 6 : 3, 1, [30, 30, 30]);
    rect(im, 15, 5, 2, 22, [60, 120, 190]);
    for (let x = 20; x < 26; x++) if (r() < 0.6) put(im, x, 6, [30, 30, 30]);
    return im;
  };
  T.brass_plate = () => { const im = img(16, 8), r = rng(34171); rect(im, 0, 0, 16, 8, [176, 140, 74]); for (let x = 2; x < 14; x++) if (r() < 0.6) put(im, x, 3, [70, 50, 30]); for (let x = 2; x < 10; x++) if (r() < 0.6) put(im, x, 5, [70, 50, 30]); rect(im, 0, 0, 16, 1, [210, 180, 110]); return im; };

  const MATX = {
    clay: { tex: D + 'clay', P: 16 }, dam: { tex: D + 'dam', P: 16 }, dam1: { tex: D + 'dam_crack1', P: 16 }, dam2: { tex: D + 'dam_crack2', P: 16 },
    staff: { tex: D + 'staff', P: 16 }, gred: { tex: D + 'gen_red', P: 16 }, r6005: { tex: D + 'ral6005', P: 16 }, leaves: { tex: D + 'leaves', P: 16 },
    tower: { tex: D + 'tower', P: 16 }, band: { tex: D + 'tower_band', P: 16 }, fillm: { tex: D + 'fill', P: 8 }, gwat: { tex: D + 'green_water', P: 16 },
  };
  const mk = (p) => FUTK.mk(MATX, p);
  const paint = (root) => FUTK.paintSet(root, D, T);
  const cube = (tex, mat) => () => { const m = mk(tex); P(m, 'block', [0, 0, 0], [16, 16, 16], mat, { split: 'xyz' }); return m; };

  // ---------------------------------------------------------------- cooling
  function basin(level) {
    const m = mk('plant/concrete');
    P(m, 'floor', [0, 0, 0], [16, 2, 16], 'conc');
    P(m, 'rim', [0, 2, 14], [16, 16, 16], 'conc');
    if (level) P(m, 'water', [0, 2, 0], [16, level, 14], 'gwat', { hide: ['down'] });
    P(m, 'curb', [0, 15.4, 13.6], [16, 16, 14], 'conc');
    return m;
  }
  function fill() {
    const m = mk(D + 'fill');
    P(m, 'pack', [0, 2, 0], [16, 14, 16], 'fillm');
    for (const x of [1, 14]) P(m, 'beam' + x, [x, 0, 0], [x + 1, 2, 16], 'conc');
    for (const z of [1, 14]) P(m, 'hanger' + z, [0, 14, z], [16, 16, z + 1], 'conc');
    return m;
  }
  // Shell panel: wall 4 thick at the south (outer) face, tilted inwards (in) or outwards (out) by 22.5 degrees,
  // steep variants by 45; the top course carries the red and white band.
  function shell(tilt, top) {
    const m = mk(D + 'tower');
    const a = { none: 0, in: -22.5, out: 22.5, in_steep: -45, out_steep: 45 }[tilt];
    const rot = a ? { axis: 'x', angle: a, origin: [8, 8, 14] } : undefined;
    P(m, 'wall', [0, 0, 12], [16, 16, 16], top ? 'band' : 'tower', { rot, split: '' });
    if (!top) P(m, 'rib', [7.4, 0, 11.4], [8.6, 16, 12], 'tower', { rot, split: '' });
    return m;
  }
  function sprayNozzle() {
    const m = mk('plant/concrete');
    P(m, 'pad', [4, 0, 4], [12, 1, 12], 'conc');
    m.cylY('riser', [8, 8], 1, 1, 10, 'p_water');
    m.cylY('flange', [8, 8], 1.6, 9, 9.6, 'ci', { cap: 'material/cast_iron' });
    m.cylY('nozzle', [8, 8], 1.4, 9.6, 12, 'br');
    m.cylY('tip', [8, 8], 0.6, 12, 13.4, 'br', { cap: 'material/brass' });
    for (const a of [0, 90]) m.postY('tang' + a, [8, 8], 2, a, 0.6, 11.4, 12, 'br', { split: '' });
    return m;
  }
  function intake(fouled) {
    const m = mk('plant/concrete');
    P(m, 'base', [0, 0, 0], [16, 4, 16], 'conc');
    for (const x of [0, 13]) P(m, 'pier' + x, [x, 4, 0], [x + 3, 32, 16], 'conc');
    for (let x = 3.6; x < 13; x += 1.2) P(m, 'bar' + x, [x, 4, 1.6], [x + 0.4, 22, 2.2], 'st');
    P(m, 'rack_beam', [3, 21, 1.2], [13, 22.6, 2.6], 'st');
    if (fouled) P(m, 'debris', [3, 4, 1.2], [13, 14, 1.6], 'leaves', { hide: ['up', 'down', 'east', 'west', 'south'] });
    P(m, 'deck', [3, 28, 0], [13, 29, 16], 'grat');
    P(m, 'pipe', [6, 6, 8], [10, 10, 16], 'p_water');
    P(m, 'pipe_band', [5.6, 7.4, 13], [10.4, 8.6, 13.6], 'p_water');
    P(m, 'screen_motor', [5, 29, 10], [11, 32, 14], 'green');
    P(m, 'marker', [3.4, 24, 0.9], [12.6, 25.4, 1], 'p_water');
    return m;
  }

  // ---------------------------------------------------------------- station supplies
  function charger(state) {
    const m = mk('switchgear_cell/enamel');
    cabinet(m, 'cab', [1, 0, 2.4], [15, 16, 15], 'en', { plate: true });
    m.decal('ammeter', [3, 9, 2.05], [7, 13, 2.2], 'en', 'north', 'parts.gauge');
    m.decal('voltmeter', [8, 9, 2.05], [12, 13, 2.2], 'en', 'north', 'parts.gauge');
    lamp(m, 'charge', 4, 6.6, 2.2, state === 'charging' ? 'l_a' : 'l_off', state === 'charging', 0.6);
    lamp(m, 'float', 6.4, 6.6, 2.2, state === 'float' ? 'l_g' : 'l_off', state === 'float', 0.6);
    P(m, 'switch', [9, 5.6, 1.6], [11, 7.6, 2.2], 'ci');
    P(m, 'switch_h', [9.6, 6.4, 1], [10.4, 8.8, 1.6], 'bk');
    m.box('vent', [3, 1.6, 2.1], [12, 4.4, 2.2], 'en', { split: '', face: { north: { tex: 'grid_lines/louvre', uv: [0, 0, 16, 6] } } });
    return m;
  }
  function dayTank() {
    const m = mk('plant/pipe_liquid');
    for (const [x, z] of [[2.4, 2.4], [12.4, 2.4], [2.4, 12.4], [12.4, 12.4]]) P(m, `leg_${x}_${z}`, [x, 0, z], [x + 1.2, 4, z + 1.2], 'frame');
    P(m, 'tray', [1.6, 0, 1.6], [14.4, 1, 14.4], 'st');
    vessel(m, 'tank', [8, 8], 6.2, 4, 28, 'st', { bottom: true });
    m.ringY('band', [8, 8], 6.6, 0.4, 20, 21.4, 'p_liq');
    P(m, 'gauge_glass', [7.4, 6, 1.4], [8.6, 26, 1.8], 'glass');
    for (const y of [6, 26]) P(m, 'gauge_cock' + y, [7.2, y - 0.6, 1.4], [8.8, y + 0.6, 2.2], 'br');
    P(m, 'vent', [10, 29.6, 7.6], [10.8, 32, 8.4], 'st');
    P(m, 'vent_cap', [9.6, 31.4, 7.2], [11.2, 31.95, 8.8], 'st');
    P(m, 'fill', [4, 29, 4], [5.6, 31, 5.6], 'p_liq');
    P(m, 'outlet', [13.4, 5, 7.4], [16, 6.2, 8.6], 'p_liq');
    P(m, 'valve', [14, 4.4, 6.8], [15.2, 6.8, 9.2], 'red');
    return m;
  }
  // 07 penstock pipe node along z and its side stub (connections like a pipe).
  function penstockNode() { const m = mk('plant/pipe_water'); m.prism('pipe', 'z', [8, 8], 6, 0, 16, 'p_water', { split: '', ends: [] }); for (const z of [0, 15]) m.ring('flange' + z, 'z', [8, 8], 6.6, 1.2, z, z + 1, 'p_water'); return m; }
  function penstockSide() { const m = mk('plant/pipe_water'); m.prism('pipe', 'x', [8, 8], 5, 0, 8, 'p_water', { split: '', ends: ['west'] }); return m; }

  // ---------------------------------------------------------------- dam
  function damBlock(cracks) { return cube(D + 'dam', ['dam', 'dam1', 'dam2'][cracks])(); }
  function damCore(state) {
    const m = mk(D + 'dam');
    P(m, 'plinth', [1, 0, 6], [15, 2, 15], 'dam');
    cabinet(m, 'board', [1.6, 2, 7], [14.4, 16, 14.6], 'en', { plate: false });
    m.box('plate', [4, 12, 6.7], [12, 14, 6.8], 'br', { split: '', face: { north: { tex: D + 'brass_plate', uv: [0, 0, 16, 8] } } });
    const mat = { survey: 'l_w', ok: 'l_g', warning: 'l_a', breach: 'l_r' }[state];
    lamp(m, 'state', 8, 9, 6.8, mat, true, 1);
    m.decal('level', [4, 4, 6.7], [7, 7, 6.8], 'en', 'north', 'parts.gauge');
    m.decal('flow', [9, 4, 6.7], [12, 7, 6.8], 'en', 'north', 'parts.gauge');
    return m;
  }
  function staffGauge() {
    const m = mk(D + 'staff');
    P(m, 'post', [7, -16, 10], [9, 32, 12], 'st', { split: 'y' });
    P(m, 'board', [5, -14, 9], [11, 30, 10], 'staff', { split: 'y', face: { south: 'st', east: 'st', west: 'st', up: 'st', down: 'st' } });
    P(m, 'cap', [6.6, 31, 9.6], [9.4, 32, 12.4], 'st');
    return m;
  }
  // Intake gate frame: two guide piers with steel guides, the hoist on the bridge; the leaf is the partial.
  function intakeGate() {
    const m = mk('plant/concrete');
    for (const x of [0, 13]) { P(m, 'pier' + x, [x, 0, 2], [x + 3, 32, 14], 'conc'); P(m, 'guide' + x, [x === 0 ? 3 : 12.4, 0, 6.6], [x === 0 ? 3.6 : 13, 28, 9.4], 'st'); }
    P(m, 'sill', [3, 0, 6], [13, 1, 10], 'st');
    P(m, 'bridge', [0, 28, 2], [16, 30, 14], 'conc');
    P(m, 'hoist', [4, 30, 4], [12, 32, 12], 'r6005');
    P(m, 'drum', [5, 30.4, 3.4], [11, 31.6, 4], 'ci');
    lamp(m, 'pos', 13.4, 31, 2, 'l_g', true, 0.5);
    return m;
  }
  function gateLeaf() {
    const m = mk(D + 'ral6005');
    P(m, 'skin', [3.6, 1, 7.4], [12.4, 15, 8.6], 'r6005');
    for (const y of [3, 8, 13]) P(m, 'girder' + y, [3.6, y, 8.6], [12.4, y + 1, 10], 'r6005');
    P(m, 'seal_bottom', [3.6, 0, 7.4], [12.4, 1, 8.6], 'rubber');
    for (const x of [3.6, 12]) P(m, 'seal' + x, [x, 1, 7], [x + 0.4, 15, 7.4], 'rubber');
    P(m, 'lug', [7, 15, 7.6], [9, 16.6, 8.4], 'st');
    return m;
  }
  // Tainter gate segment 3x2x1: two piers with the trunnion seats; the leaf (skin plate on radial arms) turns
  // about x through the trunnions at (y 26, z 14).
  function spillway() {
    const m = mk(D + 'dam');
    for (const x of [-16, 28]) P(m, 'pier' + x, [x, 0, 0], [x + 4, 32, 16], 'dam', { split: 'y' });
    P(m, 'ogee', [-12, 0, 0], [28, 3, 16], 'dam');
    P(m, 'ogee_lip', [-12, 3, 0], [28, 4, 6], 'dam');
    for (const x of [-12.6, 28]) m.prism('seat' + x, 'x', [26, 14], 1.6, x, x + 0.6, 'ci', { split: '', ends: ['west', 'east'] });
    P(m, 'bridge', [-16, 30, 0], [32, 32, 6], 'dam');
    return m;
  }
  function spillwayLeaf() {
    const m = mk(D + 'ral6005');
    const o = [8, 26, 14];
    for (const [a, y0, y1] of [[0, 2, 10], [22.5, 10, 18]]) P(m, 'skin' + a, [-12, y0, 1.6], [28, y1, 2.6], 'r6005', { split: 'x', rot: a ? { axis: 'x', angle: a, origin: [8, 10, 2] } : undefined });
    for (const x of [-11, 26]) for (const a of [-22.5, 0]) P(m, `arm_${x}_${a}`, [x, a ? 24.6 : 4, 2.6], [x + 1.6, a ? 25.6 : 25, a ? 14 : 3.6], 'r6005', { rot: a ? { axis: 'x', angle: a, origin: o } : undefined });
    for (const x of [-11, 26]) P(m, 'strut' + x, [x, 6, 2.6], [x + 1.6, 7, 13], 'r6005', { rot: { axis: 'x', angle: 45, origin: [x, 6.5, 2.6] } });
    for (const x of [-12, 26.4]) m.prism('trunnion' + x, 'x', [26, 14], 1.2, x, x + 1.6, 'bright', { split: '', ends: ['west', 'east'] });
    P(m, 'seal', [-12, 1, 1.6], [28, 2, 2.6], 'rubber');
    return m;
  }
  function surge(top) {
    const m = mk(D + 'dam');
    if (!top) {
      m.cylY('shaft', [8, 8], 22, -16, 32, 'dam');
      for (const y of [-12, 4, 20]) m.ringY('ring' + y, [8, 8], 22.6, 0.8, y, y + 1.2, 'st');
      ladder(m, 'ladder', 6.6, -14.4, -16, 32, 'x');
    } else {
      m.cylY('shaft', [8, 8], 22, -16, 16, 'dam');
      m.cylY('roof', [8, 8], 22, 16, 18, 'dam', { cap: D + 'dam' });
      m.cylY('vent', [8, 8], 4, 18, 24, 'st', { cap: 'material/steel' });
      m.cylY('vent_hat', [8, 8], 6, 24, 25, 'st', { cap: 'material/steel' });
      P(m, 'gauge_glass', [7, -10, -14.4], [9, 14, -14], 'glass');
      P(m, 'glow_level', [7.4, -10, -14.6], [8.6, 2, -14.4], 'l_b');
      for (const y of [-10.6, 14]) P(m, 'gauge_cock' + y, [6.6, y, -14.6], [9.4, y + 0.6, -13.6], 'br');
      ladder(m, 'ladder', 0, -14.4, -16, 18, 'x');
    }
    return m;
  }
  function trashRack(clog) {
    const m = mk('material/steel');
    for (let x = 0.6; x < 16; x += 1.6) P(m, 'bar' + x, [x, 0, 7.4], [x + 0.5, 16, 9], 'st');
    for (const y of [2, 14]) P(m, 'tie' + y, [0, y, 9], [16, y + 1, 9.6], 'st');
    if (clog) P(m, 'leaves', [0, 0, 6.8], [16, [0, 5, 10, 16][clog], 7.4], 'leaves', { hide: ['up', 'down', 'east', 'west'] });
    if (clog >= 2) P(m, 'mat', [0, 0, 6.2], [16, clog === 3 ? 12 : 6, 6.8], 'leaves', { hide: ['up', 'down', 'east', 'west', 'south'] });
    return m;
  }

  // ---------------------------------------------------------------- hydro generator
  // Umbrella hydro generator, 5x3x5: the core 3x1x3 carries the upper bracket (spider) with its arms, the thrust
  // bearing housing and the exciter; the stator casing pieces stand round it; the rotor is the partial.
  function genCore() {
    const m = mk(D + 'gen_red');
    m.cylY('cover', [8, 8], 12, 10, 12, 'gred', { cap: D + 'gen_red' });
    for (const a of [0, 45, 90, 135, 180, 225, 270, 315]) m.postY('arm' + a, [8, 8], 17, a, 3, 10, 13, 'gred', { split: '' });
    m.cylY('bearing', [8, 8], 5, 12, 15, 'gred', { cap: D + 'gen_red' });
    m.cylY('exciter', [8, 8], 3, 15, 16, 'ci', { cap: 'material/cast_iron' });
    m.decal('plate', [5, 11, -4.1], [11, 12.6, -4], 'gred', 'north', 'parts.plate');
    return m;
  }
  function genStator() {
    const m = mk(D + 'gen_red');
    P(m, 'casing', [0, 0, 8], [16, 16, 16], 'gred');
    for (const x of [0, 7.4, 14.8]) P(m, 'rib' + x, [x, 0, 7.4], [x + 1.2, 16, 8], 'gred');
    m.box('vent', [2, 4, 7.9], [6, 12, 8], 'gred', { split: '', face: { north: { tex: 'grid_lines/louvre', uv: [0, 0, 8, 16] } } });
    m.box('vent2', [9, 4, 7.9], [13, 12, 8], 'gred', { split: '', face: { north: { tex: 'grid_lines/louvre', uv: [0, 0, 8, 16] } } });
    P(m, 'cooler_pipe', [0, 13, 7], [16, 14, 7.6], 'p_water');
    return m;
  }
  function genRotor() {
    const m = mk('material/steel');
    m.cylY('shaft', [8, 8], 2.4, 0, 16, 'bright', { cap: 'material/bright_steel' });
    m.ringY('rim', [8, 8], 21, 3, 1, 9, 'st');
    for (let a = 0; a < 360; a += 45) m.postY('spider' + a, [8, 8], 10, a, 1.6, 3, 7, 'st', { split: '' });
    for (let a = 0; a < 360; a += 22.5) m.postY('pole' + a, [8, 8], 22.4, a, 1.8, 2, 8, 'cu', { split: '' });
    return m;
  }
  // Governors: the mechanical flyball governor column (flyballs are the partial) and the electro-hydraulic cabinet.
  function governor(kind) {
    const m = mk('material/cast_iron');
    if (kind === 'mechanical') {
      P(m, 'base', [2, 0, 2], [14, 3, 14], 'ci');
      P(m, 'column', [4, 3, 4], [12, 20, 12], 'ci', { split: 'y' });
      P(m, 'servo', [3, 8, 2.6], [8, 13, 4], 'br');
      m.wheelY('hand', 8, 21, 8, 3, 'red');
      P(m, 'head', [6, 20, 6], [10, 22, 10], 'br');
      P(m, 'oil_pipe', [12, 4, 7.4], [16, 5.2, 8.6], 'p_liq');
      m.decal('gauge', [9, 14, 3.4], [11.4, 16.4, 4], 'ci', 'north', 'parts.gauge');
    } else {
      cabinet(m, 'cab', [1, 0, 1], [15, 30, 15], 'en', { plate: true });
      m.box('screen', [3, 20, 0.6], [10, 25, 0.8], 'en', { split: '', face: { north: { tex: 'electronics/screen', uv: [0, 0, 16, 12] } } });
      for (let i = 0; i < 3; i++) lamp(m, 'lamp' + i, 4 + i * 2.4, 17, 0.8, ['l_g', 'l_a', 'l_off'][i], i < 1, 0.5);
      P(m, 'acc', [11.6, 0, 3], [14.4, 12, 6], 'r6005');
    }
    return m;
  }
  function flyballs() {
    const m = mk('material/brass');
    m.cylY('spindle', [8, 8], 0.5, 22, 30, 'bright', { cap: 'material/bright_steel' });
    for (const s of [-1, 1]) {
      P(m, 'arm' + s, [8 + s * 0.3 - 0.3, 25, 7.7], [8 + s * 3.4 + 0.3 * -s, 29, 8.3], 'bright', { rot: { axis: 'z', angle: s * 22.5, origin: [8, 29, 8] } });
      m.cylY('ball' + s, [8 + s * 3.6, 8], 1.3, 24.4, 26.8, 'br', { cap: 'material/brass', bottom: true });
    }
    P(m, 'collar', [7, 23.6, 7], [9, 24.4, 9], 'br');
    return m;
  }

  // ---------------------------------------------------------------- penstock sections (GDD 15)
  function bigPenstock(n) {
    const m = mk('plant/pipe_water');
    const c = n === 2 ? 16 : 8, r = n === 2 ? 15 : 23;
    m.prism('shell', 'z', [c, c], r, 0, 16, 'p_water', { split: 'z' });
    m.ring('joint', 'z', [c, c], r + 0.6, 1.2, 0, 1, 'st');
    for (const s of [-1, 1]) P(m, 'saddle' + s, [c + s * r * 0.5 - 1, n === 2 ? 0 : -16, 6], [c + s * r * 0.5 + 1, c - r * 0.8, 10], 'conc');
    return m;
  }

  // ---------------------------------------------------------------- weather instruments
  function mastTop(m) {
    m.cylY('mast', [8, 8], 0.8, 0, 10, 'galv', { cap: 'disconnector/galvanised' });
    P(m, 'bracket', [6.6, 0, 6.6], [9.4, 1, 9.4], 'galv');
  }
  function anemometer() { const m = mk('disconnector/galvanised'); mastTop(m); m.cylY('body', [8, 8], 1.4, 10, 13, 'light', { cap: 'plant/light' }); return m; }
  function cups() {
    const m = mk('grid_lines/alu');
    m.cylY('hub', [8, 8], 0.8, 13, 14.4, 'alu', { cap: 'grid_lines/alu' });
    for (const a of [0, 90, 180, 270]) {
      m.postY('arm' + a, [8, 8], 2.2, a, 0.3, 13.6, 13.9, 'alu', { split: '' });
      const rad = a * Math.PI / 180, x = 8 + Math.cos(rad) * 4.4, z = 8 - Math.sin(rad) * 4.4;
      m.cylY('cup' + a, [x, z], 1, 13, 14.6, 'alu', { cap: 'grid_lines/alu', bottom: true });
    }
    return m;
  }
  function windVane() { const m = mk('disconnector/galvanised'); mastTop(m); for (const [d, x, z] of [[0, 8, 3.6], [1, 12.4, 8], [2, 8, 12.4], [3, 3.6, 8]]) P(m, 'dir' + d, [x - 0.2, 9, z - 0.2], [x + 0.2, 9.4, z + 0.2], 'bk'); for (const a of [0, 90]) m.postY('cross' + a, [8, 8], 2.2, a, 0.3, 9, 9.3, 'galv', { split: '' }); return m; }
  function vane() {
    const m = mk('material/paint_red');
    m.cylY('pivot', [8, 8], 0.5, 10, 12, 'bright', { cap: 'material/bright_steel' });
    P(m, 'rod', [7.8, 11.4, 2], [8.2, 11.8, 14], 'st');
    P(m, 'tail', [7.9, 10.4, 10.4], [8.1, 14, 14.6], 'red');
    P(m, 'weight', [7.4, 11, 1.4], [8.6, 12.2, 2.6], 'ci');
    return m;
  }
  function reservoirGauge() {
    const m = mk('material/soot');
    P(m, 'case', [1, 1, 13], [15, 15, 16], 'bk');
    m.box('dial', [2, 2, 12.6], [14, 14, 13], 'bk', { split: '', face: { north: { tex: D + 'dial', uv: [0, 0, 16, 16] } } });
    P(m, 'bezel_t', [1, 14, 12.4], [15, 15, 13], 'bk'); P(m, 'bezel_b', [1, 1, 12.4], [15, 2, 13], 'bk');
    P(m, 'conduit', [7.4, 0, 13.6], [8.6, 1, 14.8], 'st');
    return m;
  }

  const B = (model, cat, java) => ({ model, cat, java });
  const BUILDS = {
    cooling_tower_basin: B(() => basin(13), 'cooling'), cooling_tower_basin_low: B(() => basin(6), 'cooling'), cooling_tower_basin_empty: B(() => basin(0), 'cooling'),
    cooling_tower_fill: B(fill, 'cooling'),
    cooling_tower_shell: B(() => shell('none', false), 'cooling'), cooling_tower_shell_top: B(() => shell('none', true), 'cooling'),
    cooling_tower_shell_in: B(() => shell('in', false), 'cooling'), cooling_tower_shell_in_top: B(() => shell('in', true), 'cooling'),
    cooling_tower_shell_out: B(() => shell('out', false), 'cooling'), cooling_tower_shell_out_top: B(() => shell('out', true), 'cooling'),
    cooling_tower_shell_in_steep: B(() => shell('in_steep', false), 'cooling'), cooling_tower_shell_in_steep_top: B(() => shell('in_steep', true), 'cooling'),
    cooling_tower_shell_out_steep: B(() => shell('out_steep', false), 'cooling'), cooling_tower_shell_out_steep_top: B(() => shell('out_steep', true), 'cooling'),
    spray_pond_nozzle: B(sprayNozzle, 'cooling'), water_intake: B(() => intake(false), 'cooling'), water_intake_fouled: B(() => intake(true), 'cooling'),
    battery_charger: B(() => charger('off'), 'grid'), battery_charger_charging: B(() => charger('charging'), 'grid'), battery_charger_float: B(() => charger('float'), 'grid'),
    fuel_day_tank: B(dayTank, 'grid'),
    penstock: B(penstockNode, 'hydro'), penstock_side: B(penstockSide, 'hydro'),
    penstock_2x2: B(() => bigPenstock(2), 'hydro'), penstock_3x3: B(() => bigPenstock(3), 'hydro'),
    dam_clay_core: B(cube(D + 'clay', 'clay'), 'hydro'),
    dam_concrete_block: B(() => damBlock(0), 'hydro'), dam_concrete_block_cracked1: B(() => damBlock(1), 'hydro'), dam_concrete_block_cracked2: B(() => damBlock(2), 'hydro'),
    dam_core: B(() => damCore('survey'), 'hydro'), dam_core_ok: B(() => damCore('ok'), 'hydro'), dam_core_warning: B(() => damCore('warning'), 'hydro'), dam_core_breach: B(() => damCore('breach'), 'hydro'),
    level_gauge_staff: B(staffGauge, 'hydro'),
    intake_gate: B(intakeGate, 'hydro'), 'intake_gate/leaf': B(gateLeaf, 'hydro', 'intake_gate/partial/leaf'),
    spillway_gate: B(spillway, 'hydro'), 'spillway_gate/leaf': B(spillwayLeaf, 'hydro', 'spillway_gate/partial/leaf'),
    surge_tank: B(() => surge(false), 'hydro'), surge_tank_top: B(() => surge(true), 'hydro'),
    trash_rack: B(() => trashRack(0), 'hydro'), trash_rack_clogged1: B(() => trashRack(1), 'hydro'), trash_rack_clogged2: B(() => trashRack(2), 'hydro'), trash_rack_clogged3: B(() => trashRack(3), 'hydro'),
    hydro_generator: B(genCore, 'hydro'), hydro_generator_stator: B(genStator, 'hydro'), 'hydro_generator/rotor': B(genRotor, 'hydro', 'hydro_generator/partial/rotor'),
    hydro_governor: B(() => governor('mechanical'), 'hydro'), hydro_governor_electrohydraulic: B(() => governor('electrohydraulic'), 'hydro'),
    'hydro_governor/flyballs': B(flyballs, 'hydro', 'hydro_governor/partial/flyballs'),
    anemometer: B(anemometer, 'instruments'), 'anemometer/cups': B(cups, 'instruments', 'anemometer/partial/cups'),
    wind_vane: B(windVane, 'instruments'), 'wind_vane/vane': B(vane, 'instruments', 'wind_vane/partial/vane'),
    instrument_reservoir_level: B(reservoirGauge, 'instruments'),
  };
  window.M5W = FUTK.machine(BUILDS, paint);
})();
