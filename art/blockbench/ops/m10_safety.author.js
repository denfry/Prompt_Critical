// M10 operations, fire water, flare and spill response equipment not yet in the mod, hand-authored (replaces the
// spec_ops.py, spec_environment.py, spec_oilgas.py and spec_petrochem.py placeholders): caustic scrubber and fan,
// oiled gravel and sand, fire hydrant, fire water pumps and impeller, foam chamber and proportioner, flare
// ignition panel, knockout and seal drums, manway and cover, patrol checkpoint, permit board,
// portable ventilator and fan, rescue tripod and winch drum, valve test bench, hazard signs, UN
// placard, boom anchor, oil boom pieces, oil skimmer and drum.
// Run headless: node art/tools/pcart_headless.js <repo> ops/m10_safety.author.js M10S --paint
//
// Front to the north; wall pieces hang on the south face (backs at z 16). Pivots as the placeholders: scrubber fan
// about y through (8, 8) at y 28..30; pump impeller about z through (8, 6.5); manway cover on its hinge at x 1,
// about y; ventilator fan about z through (8, 8); tripod drum about x through (y 17, z 12.5); skimmer drum about x
// through (y 4, z 5).
(function () {
  const { rng, img, put, get, add, mix, rect, grain } = PCART;
  const { P, lamp, cabinet, vessel, drum, flange, motor, gauge, ladder } = FUTK;
  const D = 'm10ops/', T = {};

  T.fire_red = FUTK.enamel([186, 32, 28], 39101, 2);
  T.orange = FUTK.enamel([232, 110, 30], 39102, 1);
  T.pvc_blue = () => { const im = img(16, 16); grain(im, [110, 150, 190], 39103, 2, 3, 8); return im; };
  const oiled = (base, seed, age) => () => {
    const im = img(16, 16), r = grain(im, base, seed, 8, 8, 4);
    for (let k = 0; k < 40 - age * 12; k++) { const x = r() * 15, y = r() * 15; put(im, x, y, [20, 18, 16]); put(im, x + 1, y, [44, 40, 34]); if (age === 0 && r() < 0.3) put(im, x, y + 1, [90, 60, 120]); }
    return im;
  };
  T.gravel0 = oiled([128, 124, 118], 39111, 0); T.gravel1 = oiled([128, 124, 118], 39111, 1); T.gravel2 = oiled([128, 124, 118], 39111, 2);
  T.sand0 = oiled([206, 190, 140], 39112, 0); T.sand1 = oiled([206, 190, 140], 39112, 1); T.sand2 = oiled([206, 190, 140], 39112, 2);
  T.cork = () => { const im = img(16, 16), r = rng(39121); for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) put(im, x, y, add([170, 128, 84], (r() - 0.5) * 40)); return im; };
  T.permit = () => { const im = img(16, 16), r = rng(39122); rect(im, 0, 0, 16, 16, [0, 0, 0, 0]); for (const x0 of [1, 9]) { rect(im, x0, 2, 6, 12, [230, 230, 220]); rect(im, x0, 2, 6, 2, [200, 40, 34]); for (let y = 6; y < 13; y += 2) for (let x = x0 + 1; x < x0 + 5; x++) if (r() < 0.7) put(im, x, y, [40, 40, 40]); } return im; };
  T.diamond = () => {
    const im = img(16, 16);
    for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
      const dx = x + 0.5 - 8, dy = y + 0.5 - 8;
      if (Math.abs(dx) + Math.abs(dy) > 8) { put(im, x, y, [0, 0, 0, 0]); continue; }
      let c = dy < 0 && Math.abs(dx) < -dy ? [200, 34, 30] : dy > 0 && Math.abs(dx) < dy ? [240, 240, 236] : dx < 0 ? [40, 80, 190] : [236, 200, 30];
      if (Math.abs(Math.abs(dx) + Math.abs(dy) - 7.6) < 0.6 || Math.abs(Math.abs(dx) - Math.abs(dy)) < 0.5) c = [20, 20, 20];
      put(im, x, y, c);
    }
    return im;
  };
  T.un = () => { const im = img(16, 8); rect(im, 0, 0, 16, 8, [232, 120, 30]); for (let x = 0; x < 16; x++) { put(im, x, 0, [20, 20, 20]); put(im, x, 7, [20, 20, 20]); put(im, x, 4, [20, 20, 20]); } for (let y = 0; y < 8; y++) { put(im, 0, y, [20, 20, 20]); put(im, 15, y, [20, 20, 20]); } return im; };
  const sign = (kind) => () => {
    const im = img(32, 32);
    rect(im, 0, 0, 32, 32, [244, 244, 240]);
    for (let y = 0; y < 32; y++) for (let x = 0; x < 32; x++) {
      const dx = x + 0.5 - 16, dy = y + 0.5 - 16, d = Math.hypot(dx, dy);
      if (kind === 'warning') { const inside = y >= 3 && y <= 28 && Math.abs(dx) <= (y - 3) * 0.6; if (inside) put(im, x, y, Math.abs(dx) > (y - 3) * 0.6 - 2.4 || y > 26 ? [20, 20, 20] : [240, 200, 30]); }
      if (kind === 'forbid') { if (d < 13 && d > 10) put(im, x, y, [210, 30, 28]); if (Math.abs(dx - dy) < 1.8 && d < 11) put(im, x, y, [210, 30, 28]); }
      if (kind === 'mandatory') { if (d < 13) put(im, x, y, [30, 80, 180]); if (d < 9 && Math.abs(dx) < 1.6) put(im, x, y, [244, 244, 240]); }
      if (kind === 'ghs') { if (Math.abs(dx) + Math.abs(dy) < 14 && Math.abs(dx) + Math.abs(dy) > 11.4) put(im, x, y, [210, 30, 28]); if (d < 4) put(im, x, y, [20, 20, 20]); }
    }
    if (kind === 'warning') for (let y = 11; y < 22; y++) if (y !== 19) { put(im, 15, y, [20, 20, 20]); put(im, 16, y, [20, 20, 20]); }
    return im;
  };
  T.sign_warning = sign('warning'); T.sign_forbid = sign('forbid'); T.sign_mandatory = sign('mandatory'); T.sign_ghs = sign('ghs');
  T.mesh = () => { const im = img(16, 16); for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) put(im, x, y, (x + y) % 2 ? [40, 42, 44] : [90, 92, 94]); return im; };

  const MATX = {
    fred: { tex: D + 'fire_red', P: 16 }, orng: { tex: D + 'orange', P: 16 }, pvcb: { tex: D + 'pvc_blue', P: 16 }, cork: { tex: D + 'cork', P: 16 },
    mesh: { tex: D + 'mesh', P: 8 }, rope: { tex: 'oil/rope', P: 16 }, gb: { tex: 'm8field/greyblue', P: 16 }, pls: { tex: 'm9chem/plastic', P: 16 },
  };
  const mk = (p) => FUTK.mk(MATX, p);
  const paint = (root) => FUTK.paintSet(root, D, T);
  const plate = (m, n, f, t, tex, uv) => m.box(n, f, t, 'light', { split: '', face: { north: { tex: D + tex, uv: uv || [0, 0, 16, 16] } } });

  // ---------------------------------------------------------------- caustic scrubber
  function scrubber(active) {
    const m = mk(D + 'pvc_blue');
    P(m, 'sump', [1, -15.99, 1.5], [14.5, -8, 14.5], 'pvcb');
    m.cylY('tower', [7.75, 8], 5.6, -8, 24, 'pvcb');
    for (const y of [-2, 10, 22]) m.ringY('flange' + y, [7.75, 8], 6, 0.5, y, y + 0.8, 'pvcb');
    m.cylY('fan_housing', [7.75, 8], 5.6, 24, 28, 'pvcb');
    m.cylY('stack', [7.75, 8], 2, 30, 31.97, 'pvcb', { cap: D + 'pvc_blue' });
    P(m, 'gas_in', [-0.03, -6, 6], [2.4, -2, 10], 'pvcb');
    P(m, 'caustic', [12, -12, 7.4], [14.5, 20, 8.6], 'p_alk');
    P(m, 'pump', [8, -15.99, 0.6], [12, -12, 4], 'blue');
    lamp(m, 'active', 3.4, 26, 2.2, 'l_g', active, 0.5);
    return m;
  }
  function scrubFan() { const m = mk(D + 'pvc_blue'); m.cylY('hub', [8, 8], 1.2, 28.22, 29.78, 'pvcb', { cap: D + 'pvc_blue' }); for (const a of [0, 45, -45, 90]) P(m, 'blade' + a, a === 90 ? [7.4, 28.6, 3] : [3, 28.6, 7.4], a === 90 ? [8.6, 29.4, 13] : [13, 29.4, 8.6], 'pvcb', { rot: a && a !== 90 ? { axis: 'y', angle: a, origin: [8, 8, 8] } : undefined }); return m; }

  // ---------------------------------------------------------------- fire water
  function hydrant() {
    const m = mk(D + 'fire_red');
    P(m, 'flange', [5, 0, 5], [11, 1, 11], 'fred');
    m.cylY('barrel', [8, 8], 2.4, 1, 11, 'fred');
    m.cylY('bonnet', [8, 8], 2, 11, 13, 'fred', { cap: D + 'fire_red' });
    P(m, 'nut', [7.2, 13, 7.2], [8.8, 15, 8.8], 'br');
    for (const s of [-1, 1]) m.prism('outlet' + s, 'x', [8, 8], 1.2, s < 0 ? 3 : 10.4, s < 0 ? 5.6 : 13, 'fred', { split: '', ends: [s < 0 ? 'west' : 'east'] });
    m.prism('pumper', 'z', [8, 7], 1.6, 3.6, 5.6, 'fred', { split: '', ends: ['north'] });
    for (const s of [-1, 1]) P(m, 'cap' + s, [s < 0 ? 2.6 : 13, 7.2, 7.2], [s < 0 ? 3 : 13.4, 8.8, 8.8], 'br');
    return m;
  }
  function firePump(jockey) {
    const m = mk(D + 'fire_red');
    FUTK.skid(m, 'skid', [0, 2], [32, 14]);
    const r = jockey ? 2.6 : 3.6;
    m.prism('volute', 'z', [8, 6.5], r + 1, 3, 10, 'fred', { split: '' });
    P(m, 'suction', [6.4, 2, -0.03], [9.6, 5 + r, 3], 'fred');
    P(m, 'discharge', [6.6, 6.5 + r, 6], [9.4, jockey ? 9.3 : 14.5, 9], 'fred');
    P(m, 'bearing', [6, 2, 10], [10, 6, 13], 'fred');
    motor(m, 'motor', 'x', [6.5, 8], jockey ? 2.4 : 3.4, 14, jockey ? 24 : 31, 'fred', { feet: false });
    P(m, 'coupling', [12, 4, 6.6], [14, 9, 9.4], 'yel');
    if (!jockey) { gauge(m, 'p_out', 12.4, 12.4, 6, 1); P(m, 'g_stem', [10, 12, 7], [12, 12.6, 7.6], 'br'); }
    return m;
  }
  function impeller() { const m = mk('material/brass'); m.prism('shaft', 'z', [8, 6.5], 0.8, -0.03, 16.03, 'bright', { split: '', ends: ['north', 'south'] }); m.prism('wheel', 'z', [8, 6.5], 3.4, 4, 6, 'br', { split: '', ends: ['north', 'south'] }); return m; }
  function foamChamber() {
    const m = mk(D + 'fire_red');
    P(m, 'mount', [2, 4, 15], [12, 12, 16], 'fred');
    P(m, 'body', [3, 5, 9], [11, 12, 15], 'fred');
    P(m, 'glass_seal', [5, 7, 8.6], [9, 10, 9], 'glass');
    m.prism('inlet', 'x', [6, 12], 1.4, -0.03, 3, 'fred', { split: '', ends: ['west'] });
    P(m, 'deflector', [3.6, 3, 10], [10.4, 5, 14], 'fred');
    P(m, 'top', [5, 12, 10], [9, 13, 14], 'fred');
    return m;
  }
  function proportioner(six) {
    const m = mk(D + 'fire_red');
    for (const [x, z] of [[2, 2], [12, 2], [2, 12], [12, 12]]) P(m, `leg_${x}_${z}`, [x, 0, z], [x + 2, 4, z + 2], 'frame');
    vessel(m, 'tank', [8, 8], 6, 4, 22, 'fred', { bottom: true });
    P(m, 'label', [5, 14, 1.8], [11, 17, 2], 'wh');
    P(m, 'ratio', [6, 14.6, 1.7], [six ? 10 : 8, 15.6, 1.8], 'bk');
    P(m, 'inductor', [13, 6, 6], [16, 9, 10], 'br');
    P(m, 'water_in', [13, 6.6, 0], [15, 8.6, 6], 'p_water');
    P(m, 'foam_out', [13, 6.6, 10], [15, 8.6, 15], 'fred');
    P(m, 'fill', [7, 26, 7], [9, 28.07, 9], 'br');
    return m;
  }

  // ---------------------------------------------------------------- flare
  function ignitionPanel(pilots) {
    const m = mk('m3grid/tank');
    cabinet(m, 'cab', [1, 0, 3.4], [15, 16, 15], 'tank', { plate: true });
    for (let i = 0; i < 3; i++) lamp(m, 'pilot' + i, 4 + i * 3, 11, 3.2, 'l_a', i < pilots, 0.7);
    m.cylY('ignite', [8, 2.4], 1.2, 5, 5.01, 'red');
    P(m, 'ignite_btn', [6.8, 5.4, 2.2], [9.2, 7.8, 3.2], 'red');
    P(m, 'gas_line', [13, 0, 12], [14, 16, 13], 'p_gas');
    return m;
  }
  function koDrum(level) {
    const m = mk('m3grid/tank');
    for (const x of [-12, 24]) P(m, 'saddle' + x, [x, 0, 3], [x + 4, 4, 13], 'frame');
    drum(m, 'drum', 'x', [8.6, 8], 5.2, -14.4, 30.4, 'tank');
    P(m, 'glass', [4, 5, 2.6], [12, 12, 2.8], 'vdark');
    if (level) P(m, 'level', [4.4, 5.4, 2.5], [11.6, 5.4 + level * 1.6, 2.6], 'p_liq');
    P(m, 'inlet', [-12, 13.6, 7], [-9, 14, 9], 'p_gas');
    P(m, 'outlet', [24, 13.6, 7], [28, 14, 9], 'p_gas');
    P(m, 'drain', [7, 0, 7], [9, 3.4, 9], 'p_liq');
    return m;
  }
  function sealDrum() {
    const m = mk('m3grid/tank');
    for (const a of [45, 135, 225, 315]) m.postY('leg' + a, [8, 8], 5.4, a, 1.2, 0, 4, 'frame');
    vessel(m, 'drum', [8, 8], 6, 4, 26, 'tank', { bottom: true });
    P(m, 'glass', [7.2, 6, 1.8], [8.8, 22, 2.1], 'vdark');
    for (const y of [6, 22]) P(m, 'cock' + y, [6.8, y - 0.6, 1.4], [9.2, y + 0.6, 2.2], 'br');
    P(m, 'inlet', [-0.03, 20, 7], [2, 22, 9], 'p_gas');
    P(m, 'outlet', [7, 28, 7], [9, 30.07, 9], 'p_gas');
    P(m, 'water', [14, 6, 7], [16.03, 7.4, 8.4], 'p_water');
    return m;
  }

  // ---------------------------------------------------------------- operations
  function manway() {
    const m = mk('material/steel');
    m.prism('neck', 'z', [8, 8], 6.4, 12, 16.07, 'st', { split: '' });
    m.ring('flange', 'z', [8, 8], 7, 1.6, 11.4, 12, 'st');
    P(m, 'hinge', [0.6, 7, 11], [2, 9, 12], 'st');
    P(m, 'davit', [0.6, 9, 11.4], [1.4, 15, 12], 'st');
    return m;
  }
  function manwayCover() {
    const m = mk('material/steel');
    m.prism('cover', 'z', [8, 8], 6.6, 10, 11.4, 'st', { split: '', ends: ['north'] });
    for (let a = 0; a < 360; a += 45) { const r = a * Math.PI / 180; P(m, 'bolt' + a, [8 + Math.cos(r) * 5.8 - 0.4, 8 + Math.sin(r) * 5.8 - 0.4, 9.5], [8 + Math.cos(r) * 5.8 + 0.4, 8 + Math.sin(r) * 5.8 + 0.4, 10], 'bright'); }
    P(m, 'handle', [6, 10.6, 9.5], [10, 11.4, 10], 'st');
    P(m, 'arm', [1, 7.4, 10.4], [4, 8.6, 11.4], 'st');
    return m;
  }
  function checkpoint() { const m = mk('m8field/yellow_post'); P(m, 'tag', [6, 6, 15], [10, 10, 16], 'ypost'); P(m, 'num', [7, 7, 14.9], [9, 9, 15], 'bk'); return m; }
  function permitBoard() {
    const m = mk(D + 'cork');
    P(m, 'frame', [0, 2, 15.2], [32, 14, 16], 'wood');
    P(m, 'cork', [0.6, 2.6, 14.8], [31.4, 13.4, 15.2], 'cork');
    for (const x of [2, 10, 18, 25]) m.box('pocket' + x, [x, 3.4, 14.6], [x + 6, 12, 14.8], 'cork', { split: '', face: { north: { tex: D + 'permit', uv: [0, 0, 16, 16] } } });
    P(m, 'title', [10, 12.4, 14.7], [22, 13.2, 14.8], 'red');
    return m;
  }
  function ventilator(on) {
    const m = mk(D + 'orange');
    m.prism('housing', 'z', [8, 8], 6.4, 2, 12, 'orng', { split: '' });
    m.ring('guard', 'z', [8, 8], 6.6, 0.6, 0.4, 2, 'orng');
    for (const a of [0, 90]) P(m, 'grid' + a, a ? [7.7, 2, 1.2] : [2, 7.7, 1.2], a ? [8.3, 14, 1.6] : [14, 8.3, 1.6], 'st');
    P(m, 'frame_l', [1.5, 0, 6], [2.5, 2, 12], 'st'); P(m, 'frame_r', [13.5, 0, 6], [14.5, 2, 12], 'st');
    m.prism('duct', 'z', [8, 8], 5, 12, 16, 'rubber', { split: '', ends: ['south'] });
    P(m, 'switch', [12, 12, 4], [14, 14, 6], 'bk');
    lamp(m, 'on', 13, 13, 3.8, 'l_g', on, 0.4);
    return m;
  }
  function ventFan() { const m = mk('material/steel'); m.prism('hub', 'z', [8, 8], 1, 3.22, 4.78, 'st', { split: '', ends: ['north', 'south'] }); for (const a of [0, 45, -45, 90]) P(m, 'blade' + a, a === 90 ? [7.4, 3, 3.6] : [3, 7.4, 3.6], a === 90 ? [8.6, 13, 4.4] : [13, 8.6, 4.4], 'st', { rot: a && a !== 90 ? { axis: 'z', angle: a, origin: [8, 8, 4] } : undefined }); return m; }
  function tripod() {
    const m = mk('grid_lines/alu');
    for (const [a, x, z] of [[22.5, 4, 5], [-22.5, 11, 5]]) P(m, 'leg' + a, [x, 0, z], [x + 1, 31, z + 1], 'alu', { rot: { axis: 'z', angle: a, origin: [x + 0.5, 31, z + 0.5] }, split: 'y' });
    P(m, 'leg_s', [7.5, 0, 13], [8.5, 31, 14], 'alu', { rot: { axis: 'x', angle: 22.5, origin: [8, 31, 8] }, split: 'y' });
    P(m, 'head', [6, 30, 6], [10, 32, 10], 'alu');
    P(m, 'pulley', [7.4, 28, 7.4], [8.6, 30, 8.6], 'st');
    P(m, 'winch_body', [5, 15, 12], [11, 19, 15.6], 'yel');
    P(m, 'cable', [7.8, 4, 7.8], [8.2, 28, 8.2], 'cable');
    P(m, 'hook', [7.4, 3, 7.4], [8.6, 4, 8.6], 'st');
    return m;
  }
  function winchDrum() { const m = mk('m6auto/black'); m.prism('drum', 'x', [17, 12.5], 2, 6, 10, 'cable', { split: '' }); for (const x of [4.97, 10]) m.prism('fl' + x, 'x', [17, 12.5], 3, x, x + 1.03, 'yel', { split: '', ends: ['west', 'east'] }); return m; }
  function testBench(occupied) {
    const m = mk('material/steel');
    for (const x of [1, 29]) P(m, 'leg' + x, [x, 0, 2], [x + 2, 12, 14], 'frame');
    P(m, 'top', [0, 12, 1], [32, 13, 15], 'st');
    P(m, 'clamp_base', [10, 13, 4], [22, 15, 12], 'blue');
    for (const x of [10, 21]) P(m, 'clamp_post' + x, [x, 15, 7], [x + 1, 21, 9], 'bright');
    P(m, 'clamp_bar', [10, 20, 6.6], [22, 21, 9.4], 'blue');
    m.decal('gauge', [24, 14, 1.4], [28, 18, 1.8], 'st', 'north', 'parts.gauge');
    P(m, 'gauge_box', [24, 13, 1.8], [28, 18, 4], 'dk');
    P(m, 'pump', [3, 13, 3], [8, 17, 9], 'red');
    P(m, 'hose', [8, 13.4, 5], [10, 14.4, 6], 'rubber');
    if (occupied) {
      P(m, 'valve_body', [13, 15, 5.6], [19, 19, 10.4], 'green');
      P(m, 'valve_bonnet', [15, 19, 7], [17, 20, 9], 'green');
      m.wheelY('valve_hw', 16, 20.6, 8, 2, 'red');
    }
    return m;
  }
  function hazardDiamond() { const m = mk(D + 'diamond'); P(m, 'diamond', [3, 3, 15.5], [13, 13, 16], 'light', { face: { north: { tex: D + 'diamond', uv: [0, 0, 16, 16] } }, hide: ['east', 'west', 'up', 'down'] }); return Object.assign(m, { renderType: 'minecraft:cutout' }); }
  function hazardSign(kind) { const m = mk(D + 'sign_' + kind); plate(m, 'sign', [1, 1, 15], [15, 15, 16], 'sign_' + kind); return m; }
  function unPlacard() { const m = mk(D + 'un'); plate(m, 'placard', [0, 4, 15], [16, 12, 16], 'un'); return m; }
  function oiledBlock(base, age) { const m = mk(D + base + age); P(m, 'block', [0, 0, 0], [16, 16, 16], 'light', { face: Object.fromEntries(['north', 'south', 'east', 'west', 'up', 'down'].map((d) => [d, { tex: D + base + age, uv: [0, 0, 16, 16] }])) }); return m; }

  // ---------------------------------------------------------------- spill response
  function boomAnchor() {
    const m = mk('plant/concrete');
    P(m, 'block', [3, 0, 3], [13, 6, 13], 'conc');
    P(m, 'eye', [7, 6, 7], [9, 8, 9], 'st');
    P(m, 'rope', [7.6, 8, 7.6], [8.4, 16, 8.4], 'rope');
    m.cylY('buoy', [8, 8], 2.4, 12, 16, 'orng', { cap: D + 'orange' });
    return m;
  }
  function boom(kind) {
    const m = mk(D + 'orange');
    if (kind === 'x') { m.prism('float', 'x', [2, 8], 3, 0, 16, 'orng', { split: '', ends: [] }); P(m, 'skirt', [0, -6, 7.6], [16, 0, 8.4], 'orng'); P(m, 'chain', [0, -6.4, 7.6], [16, -6, 8.4], 'st'); }
    else {
      m.prism('float', 'z', [8, 2], 3, 0, kind === 'corner' ? 11 : 16, 'orng', { split: '' });
      P(m, 'skirt', [7.6, -6, 0], [8.4, 0, kind === 'corner' ? 11 : 16], 'orng');
      if (kind === 'corner') { m.prism('float_e', 'x', [2, 8], 3, 8, 16, 'orng', { split: '' }); P(m, 'skirt_e', [8, -6, 7.6], [16, 0, 8.4], 'orng'); }
    }
    return m;
  }
  function skimmer(on) {
    const m = mk(D + 'orange');
    for (const x of [0, 13]) P(m, 'pontoon' + x, [x, 0, 1], [x + 3, 5, 15], 'orng');
    P(m, 'deck', [3, 4, 9], [13, 5, 15], 'orng');
    P(m, 'hopper', [3, 1, 8], [13, 4, 9], 'orng');
    P(m, 'pump', [5, 5, 10], [11, 8, 14], 'dk');
    P(m, 'hose', [11, 5.6, 11.4], [16, 6.6, 12.4], 'rubber');
    lamp(m, 'on', 4, 7, 10, 'l_g', on, 0.5);
    return m;
  }
  function skimDrum() { const m = mk('m6auto/black'); m.prism('drum', 'x', [4, 5], 2.8, 2, 14, 'bk', { split: '', ends: ['west', 'east'] }); m.prism('axle', 'x', [4, 5], 0.6, 1.97, 14.03, 'st', { split: '', ends: ['west', 'east'] }); return m; }

  const B = (model, cat, java) => ({ model, cat, java });
  const BUILDS = {
    caustic_scrubber: B(() => scrubber(false), 'chlor_alkali'), caustic_scrubber_active: B(() => scrubber(true), 'chlor_alkali'),
    'caustic_scrubber/fan': B(scrubFan, 'chlor_alkali', 'caustic_scrubber/partial/fan'),
    oiled_gravel: B(() => oiledBlock('gravel', 0), 'environment'), oiled_gravel_1: B(() => oiledBlock('gravel', 1), 'environment'), oiled_gravel_2: B(() => oiledBlock('gravel', 2), 'environment'),
    oiled_sand: B(() => oiledBlock('sand', 0), 'environment'), oiled_sand_1: B(() => oiledBlock('sand', 1), 'environment'), oiled_sand_2: B(() => oiledBlock('sand', 2), 'environment'),
    fire_hydrant: B(hydrant, 'fire'), fire_water_pump: B(() => firePump(false), 'fire'), fire_water_pump_jockey: B(() => firePump(true), 'fire'),
    'fire_water_pump/impeller': B(impeller, 'fire', 'fire_water_pump/partial/impeller'),
    foam_chamber: B(foamChamber, 'fire'), foam_proportioner: B(() => proportioner(false), 'fire'), foam_proportioner_6: B(() => proportioner(true), 'fire'),
    flare_ignition_panel: B(() => ignitionPanel(0), 'flare'), flare_ignition_panel_pilots1: B(() => ignitionPanel(1), 'flare'),
    flare_ignition_panel_pilots2: B(() => ignitionPanel(2), 'flare'), flare_ignition_panel_pilots3: B(() => ignitionPanel(3), 'flare'),
    flare_knockout_drum: B(() => koDrum(0), 'flare'), flare_knockout_drum_level2: B(() => koDrum(2), 'flare'), flare_knockout_drum_level4: B(() => koDrum(4), 'flare'),
    flare_seal_drum: B(sealDrum, 'flare'),
    manway: B(manway, 'ops'), 'manway/cover': B(manwayCover, 'ops', 'manway/partial/cover'),
    patrol_checkpoint: B(checkpoint, 'ops'), permit_board: B(permitBoard, 'ops'),
    portable_ventilator: B(() => ventilator(false), 'ops'), portable_ventilator_on: B(() => ventilator(true), 'ops'),
    'portable_ventilator/fan': B(ventFan, 'ops', 'portable_ventilator/partial/fan'),
    rescue_tripod: B(tripod, 'ops'), 'rescue_tripod/winch_drum': B(winchDrum, 'ops', 'rescue_tripod/partial/winch_drum'),
    valve_test_bench: B(() => testBench(false), 'ops'), valve_test_bench_occupied: B(() => testBench(true), 'ops'),
    hazard_diamond: B(hazardDiamond, 'signs'), un_placard: B(unPlacard, 'signs'),
    hazard_sign: B(() => hazardSign('warning'), 'signs'), hazard_sign_forbid: B(() => hazardSign('forbid'), 'signs'),
    hazard_sign_mandatory: B(() => hazardSign('mandatory'), 'signs'), hazard_sign_ghs: B(() => hazardSign('ghs'), 'signs'),
    boom_anchor: B(boomAnchor, 'spill'), oil_boom: B(() => boom('z'), 'spill'), oil_boom_x: B(() => boom('x'), 'spill'), oil_boom_corner: B(() => boom('corner'), 'spill'),
    oil_skimmer: B(() => skimmer(false), 'spill'), oil_skimmer_on: B(() => skimmer(true), 'spill'), 'oil_skimmer/drum': B(skimDrum, 'spill', 'oil_skimmer/partial/drum'),
  };
  window.M10S = FUTK.machine(BUILDS, paint);
})();
