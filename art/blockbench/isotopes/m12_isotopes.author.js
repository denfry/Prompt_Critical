// M12 isotope and irradiation equipment not yet in the mod, hand-authored (replaces the spec_isotopes.py
// placeholders): reflector irradiation port, transient rod drive and rod, tritium lamp signs, gamma irradiator
// cell wall, door and pool with their partials, NTD silicon rig and ingot, pneumatic rabbit terminal, hatch and
// tube pieces, Stirling RTG and shaft, type A and type B source containers.
// Run headless: node art/tools/pcart_headless.js <repo> isotopes/m12_isotopes.author.js M12I --paint
//
// Front to the north; wall signs hang on the south face. Pivots as the placeholders: transient rod in y about
// (8, 8) below the drive; irradiator door slides in x; source rack in y; NTD ingot about y through (8, 8); rabbit
// hatch about x at its top edge (y 14, z 2.4..9); RTG shaft about z through (8, 8) at the north end; type A lid
// about x at its back edge.
(function () {
  const { rng, img, put, get, add, mix, rect, grain } = PCART;
  const { P, lamp, cabinet, vessel, drum, flange, motor, gauge, ladder } = FUTK;
  const D = 'm12iso/', T = {};

  T.heavy = () => { const im = img(32, 32), r = grain(im, [122, 120, 114], 40101, 5, 6, 8); for (let k = 0; k < 60; k++) put(im, r() * 32, r() * 32, r() < 0.5 ? [86, 84, 80] : [150, 146, 136]); return im; };
  T.trefoil = () => {
    const im = img(16, 16);
    for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
      const dx = x + 0.5 - 8, dy = y + 0.5 - 8, d = Math.hypot(dx, dy), a = (Math.atan2(dy, dx) * 180 / Math.PI + 450) % 120;
      put(im, x, y, d < 1.4 || (d > 2.2 && d < 6.2 && a > 30 && a < 90) ? [150, 40, 140] : [240, 210, 40]);
    }
    return im;
  };
  T.heavy_sign = () => { const im = T.heavy(), s = T.trefoil(); for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) { const i = (y * 16 + x) * 4; put(im, 8 + x, 8 + y, [s.d[i], s.d[i + 1], s.d[i + 2]]); } return im; };
  T.liner = () => { const im = img(16, 16), r = rng(40111); for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) put(im, x, y, x % 8 === 0 || y % 8 === 0 ? [120, 124, 126] : add([176, 180, 182], (r() - 0.5) * 8)); return im; };
  T.cherenkov = () => { const im = img(16, 16), r = rng(40112); for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) put(im, x, y, add([60, 140, 255], Math.sin(x * 0.6 + y * 0.4) * 20 + (r() - 0.5) * 20)); return im; };
  T.pool_water = () => { const im = img(16, 16), r = rng(40113); for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) put(im, x, y, add([40, 90, 130], Math.sin(x * 0.6 + y * 0.4) * 8 + (r() - 0.5) * 8)); return im; };
  const tritium = (kind, on) => () => {
    const im = img(16, 8), g = on ? [120, 255, 140] : [60, 110, 70], bg = on ? [30, 120, 50] : [26, 50, 30];
    rect(im, 0, 0, 16, 8, bg);
    if (kind === 'exit') for (const [x0, w] of [[2, 3], [6, 3], [10, 1], [12, 3]]) { for (let x = x0; x < x0 + w; x++) { put(im, x, 2, g); put(im, x, 5, g); } put(im, x0, 3, g); put(im, x0, 4, g); }
    if (kind === 'arrow') { for (let x = 3; x < 12; x++) put(im, x, 4, g); for (let k = 0; k < 3; k++) { put(im, 11 - k, 4 - k, g); put(im, 11 - k, 4 + k, g); } }
    for (let x = 0; x < 16; x++) { put(im, x, 0, [200, 200, 196]); put(im, x, 7, [200, 200, 196]); }
    return im;
  };
  T.tritium_exit = tritium('exit', true); T.tritium_arrow = tritium('arrow', true); T.tritium_blank = tritium('blank', true); T.tritium_dim = tritium('exit', false);
  T.silicon = () => { const im = img(16, 16), r = rng(40121); for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) put(im, x, y, add([112, 118, 128], (x % 5 === 0 ? 30 : 0) + (r() - 0.5) * 10)); return im; };
  T.rtg_fins = () => { const im = img(16, 16); for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) put(im, x, y, x % 2 ? [180, 184, 188] : [110, 114, 118]); return im; };
  T.yellow_case = FUTK.enamel([232, 194, 40], 40131, 1);
  T.lead = () => { const im = img(16, 16); grain(im, [96, 100, 108], 40132, 3, 5, 8); return im; };

  const MATX = {
    heavy: { tex: D + 'heavy', P: 16 }, hsign: { tex: D + 'heavy_sign', P: 16 }, liner: { tex: D + 'liner', P: 16 }, cher: { tex: D + 'cherenkov', P: 16 },
    pwat: { tex: D + 'pool_water', P: 16 }, si: { tex: D + 'silicon', P: 16 }, rfin: { tex: D + 'rtg_fins', P: 8 }, ycase: { tex: D + 'yellow_case', P: 16 },
    lead: { tex: D + 'lead', P: 16 }, tref: { tex: D + 'trefoil', P: 16 },
  };
  const mk = (p) => FUTK.mk(MATX, p);
  const paint = (root) => FUTK.paintSet(root, D, T);
  const sign = (m, n, f, t, dir) => m.box(n, f, t, 'ycase', { split: '', face: { [dir || 'north']: { tex: D + 'trefoil', uv: [0, 0, 16, 16] } } });

  function reflectorPort(loaded) {
    const m = mk('steelmaking/graphite');
    P(m, 'block', [0, 0, 0], [16, 16, 16], 'graph', { split: 'xyz' });
    m.cylY('sleeve', [8, 8], 3, 15.99, 16.5, 'alu', { cap: 'grid_lines/alu' });
    m.ringY('rim', [8, 8], 3.6, 0.6, 15.99, 16.4, 'ycase');
    if (loaded) m.cylY('plug', [8, 8], 1.8, 16.5, 17.5, 'ss', { cap: 'electronics/ss' });
    return m;
  }
  function transientDrive(state) {
    const m = mk('electronics/ss');
    P(m, 'flange', [2, 0, 2], [14, 2, 14], 'ss');
    m.cylY('cylinder', [8, 8], 4, 2, 22, 'ss', { cap: 'electronics/ss' });
    for (const y of [6, 18]) m.ringY('band' + y, [8, 8], 4.3, 0.4, y, y + 1, 'red');
    P(m, 'accumulator', [11, 4, 2], [14, 18, 5], 'ss');
    P(m, 'valve', [11, 18, 2], [14, 22, 5], 'ci');
    P(m, 'tag', [6, 12, 3.6], [10, 15, 3.8], 'red');
    m.cylY('top', [8, 8], 2, 22, 29, 'ss', { cap: 'electronics/ss' });
    lamp(m, 'state', 8, 24, 5.8, state === 'fired' ? 'l_r' : state === 'armed' ? 'l_a' : 'l_off', state !== 'safe', 0.6);
    return m;
  }
  function transientRod() { const m = mk('steelmaking/graphite'); m.cylY('rod', [8, 8], 1.5, -15.95, 3.03, 'bright', { cap: 'material/bright_steel', bottom: true }); m.cylY('absorber', [8, 8], 1.4, -15.95, -6, 'graph'); return m; }
  function tritiumLamp(kind) { const m = mk(D + 'tritium_' + kind); m.box(kind === 'dim' ? 'plate' : 'glow_plate', [3, 5, 15.5], [13, 11, 16], 'alu', { split: '', face: { north: { tex: D + 'tritium_' + kind, uv: [0, 0, 16, 8] } } }); return m; }
  function cellWall(signed) { const m = mk(D + (signed ? 'heavy_sign' : 'heavy')); P(m, 'block', [0, 0, 0], [16, 16, 16], 'heavy', { face: signed ? { north: { tex: D + 'heavy_sign', uv: [0, 0, 16, 16] } } : {} }); return m; }
  function irrDoor(state) {
    const m = mk(D + 'heavy');
    for (const x of [0, 14.5]) P(m, 'jamb' + x, [x, 0, 9.4], [x + 1.5, 32, 16], 'heavy', { split: 'y' });
    P(m, 'lintel', [1.5, 28, 9.4], [14.5, 32, 16], 'heavy');
    P(m, 'track', [0, 28, 9.8], [16, 28.6, 10.4], 'st');
    for (const x of [0, 14.5]) P(m, 'stripe' + x, [x, 0, 9.2], [x + 1.5, 28, 9.4], 'haz');
    lamp(m, 'state', 8, 30, 9.4, state === 'open' ? 'l_r' : state === 'interlocked' ? 'l_g' : 'l_a', true, 0.7);
    return m;
  }
  function irrDoorLeaf() { const m = mk(D + 'heavy'); P(m, 'leaf', [1.5, 0, 10.5], [14.5, 28, 15.5], 'heavy', { split: 'y' }); P(m, 'edge', [1.5, 0, 10.3], [2.1, 28, 10.5], 'haz'); P(m, 'handle', [11, 12, 10], [12, 16, 10.5], 'st'); return m; }
  function pool(raised) {
    const m = mk(D + 'liner');
    P(m, 'floor', [-16, -16, -16], [32, -14, 32], 'liner');
    for (const [f, t] of [[[-16, -14, -16], [32, 28, -14]], [[-16, -14, 30], [32, 28, 32]], [[-16, -14, -14], [-14, 28, 30]], [[30, -14, -14], [32, 28, 30]]]) P(m, 'wall', f, t, 'liner', { split: 'y' });
    P(m, raised ? 'water' : 'glow_water', [-14, 26, -14], [30, 26.4, 30], raised ? 'pwat' : 'cher', { hide: ['down', 'north', 'south', 'east', 'west'] });
    P(m, 'coping', [-16, 28, -16], [32, 30, -14], 'heavy');
    PLANT.railing(m, 'rail_n', -16, 32, 22, -15.4, 'x');
    for (const x of [-4, 20]) P(m, 'guide' + x, [x, -14, 6], [x + 1, 26, 10], 'ss');
    return m;
  }
  function sourceRack() { const m = mk('electronics/ss'); P(m, 'frame', [2, -12, 6], [14, 4, 10], 'ss'); for (let x = 3.4; x < 13; x += 2.2) P(m, 'pencil' + x, [x, -11, 7.4], [x + 1, 2, 8.6], 'bright'); return m; }
  function ntdRig() {
    const m = mk('grid_lines/alu');
    P(m, 'base', [2, 0, 2], [14, 3, 14], 'alu');
    for (const [x, z] of [[2.4, 2.4], [12.6, 2.4], [2.4, 12.6], [12.6, 12.6]]) P(m, `rod_${x}_${z}`, [x, 3, z], [x + 1, 28, z + 1], 'alu');
    P(m, 'top', [2, 28, 2], [14, 30, 14], 'alu');
    P(m, 'drive', [5, 30, 5], [11, 32, 11], 'alu');
    return m;
  }
  function ingot() { const m = mk(D + 'silicon'); m.cylY('ingot', [8, 8], 2.8, 5, 26, 'si', { cap: D + 'silicon', bottom: true }); m.cylY('chuck', [8, 8], 2, 26, 27.03, 'alu'); m.cylY('foot', [8, 8], 2, 3.97, 5, 'alu'); return m; }
  function rabbitTerminal(state) {
    const m = mk('electronics/ss');
    P(m, 'body', [2, 0, 3.4], [14, 12, 14], 'ss');
    P(m, 'head', [3, 12, 9], [13, 16, 14], 'ss');
    P(m, 'window', [5, 6, 3.2], [11, 10, 3.4], 'vdark');
    P(m, 'tube_out', [6, 16, 10], [10, 16.01, 13], 'alu', { hide: ['down'] });
    lamp(m, 'state', 4, 13, 9, { idle: 'l_off', sending: 'l_a', returned: 'l_g' }[state], state !== 'idle', 0.5);
    sign(m, 'sign', [10.6, 2, 3.2], [13, 4.4, 3.4]);
    return m;
  }
  function rabbitHatch() { const m = mk('electronics/ss'); P(m, 'hatch', [4, 12, 2.4], [12, 14, 9], 'ss'); P(m, 'grip', [7, 12.4, 2], [9, 13.4, 2.4], 'bk'); return m; }
  function tube(kind) {
    const m = mk('grid_lines/alu');
    if (kind === 'straight') { m.prism('tube', 'z', [8, 8], 2, -0.03, 16.03, 'alu', { split: '' }); return m; }
    m.prism('run_n', 'z', [8, 8], 2, -0.03, 8, 'alu', { split: '' });
    P(m, 'elbow', [5.5, 5.5, 5.5], [10.5, 10.5, 10.5], 'alu');
    m.prism('run_e', 'x', [8, 8], 2, 8, 16.03, 'alu', { split: '' });
    if (kind === 'tee') m.prism('run_s', 'z', [8, 8], 2, 8, 16.03, 'alu', { split: '' });
    return m;
  }
  function rtg(running) {
    const m = mk(D + 'rtg_fins');
    P(m, 'base', [2, 0, 1], [14, 2, 31], 'frame');
    m.cylY('heat', [8, 16], 5.6, 2, 14, 'rfin', { cap: 'material/steel' });
    for (const z of [5, 27]) { m.prism('stirling' + z, 'z', [8, 8], 3, z - 3, z + 3, 'ss', { split: '' }); P(m, 'fins' + z, [4, 3.4, z - 3], [12, 12.6, z + 3], 'rfin'); }
    P(m, 'alternator', [5, 4, 1], [11, 12, 3], 'ss');
    sign(m, 'tref', [6, 9, 10.2], [10, 13, 10.4]);
    lamp(m, 'state', 12.4, 13, 10.6, running ? 'l_g' : 'l_off', running, 0.5);
    return m;
  }
  function rtgShaft() { const m = mk('material/steel'); m.prism('shaft', 'z', [8, 8], 1, -0.04, 3.03, 'bright', { split: '', ends: ['north', 'south'] }); m.prism('coupling', 'z', [8, 8], 2, -0.04, 1, 'st', { split: '', ends: ['north', 'south'] }); return m; }
  function typeA() {
    const m = mk(D + 'yellow_case');
    P(m, 'case', [2, 0, 3], [14, 9, 13], 'ycase');
    for (const x of [3, 12]) P(m, 'latch' + x, [x, 6, 2.6], [x + 1, 8.6, 3], 'st');
    P(m, 'handle', [6, 4, 2.4], [10, 5, 3], 'bk');
    sign(m, 'tref', [9.6, 1, 2.8], [12.6, 4, 3]);
    P(m, 'label', [3.4, 1.4, 2.8], [8, 3, 3], 'wh');
    return m;
  }
  function typeALid() { const m = mk(D + 'yellow_case'); P(m, 'lid', [2, 9, 2.5], [14, 11, 13], 'ycase'); P(m, 'grip', [6, 11, 4], [10, 11.6, 5], 'bk'); return m; }
  function typeB() {
    const m = mk(D + 'lead');
    m.cylY('body', [8, 8], 6, 1, 13, 'lead');
    m.cylY('foot', [8, 8], 6.6, -0.07, 1, 'st', { bottom: true });
    m.cylY('lid', [8, 8], 6.6, 13, 15.07, 'st', { cap: 'material/steel' });
    for (const y of [4, 10]) m.ringY('rib' + y, [8, 8], 6.4, 0.6, y, y + 1, 'st');
    for (const a of [0, 180]) m.postY('lug' + a, [8, 8], 6.8, a, 1, 13, 15, 'st');
    sign(m, 'tref', [6, 6, 1.6], [10, 10, 1.8]);
    P(m, 'tag', [10.6, 6.4, 1.9], [13, 8.6, 2.1], 'ycase');
    return m;
  }

  const B = (model, java) => ({ model, cat: 'isotopes', java });
  const BUILDS = {
    reflector_irradiation_port: { model: () => reflectorPort(false), cat: 'core' }, reflector_irradiation_port_loaded: { model: () => reflectorPort(true), cat: 'core' },
    transient_rod_drive: { model: () => transientDrive('safe'), cat: 'core' }, transient_rod_drive_armed: { model: () => transientDrive('armed'), cat: 'core' },
    transient_rod_drive_fired: { model: () => transientDrive('fired'), cat: 'core' },
    'transient_rod_drive/rod': { model: transientRod, cat: 'core', java: 'transient_rod_drive/partial/rod' },
    tritium_lamp: { model: () => tritiumLamp('exit'), cat: 'decor' }, tritium_lamp_arrow: { model: () => tritiumLamp('arrow'), cat: 'decor' },
    tritium_lamp_blank: { model: () => tritiumLamp('blank'), cat: 'decor' }, tritium_lamp_dim: { model: () => tritiumLamp('dim'), cat: 'decor' },
    gamma_irradiator_cell_wall: B(() => cellWall(false)), gamma_irradiator_cell_wall_sign: B(() => cellWall(true)),
    gamma_irradiator_door: B(() => irrDoor('closed')), gamma_irradiator_door_open: B(() => irrDoor('open')),
    gamma_irradiator_door_interlocked: B(() => irrDoor('interlocked')), 'gamma_irradiator_door/door': B(irrDoorLeaf, 'gamma_irradiator_door/partial/door'),
    gamma_irradiator_pool: B(() => pool(false)), gamma_irradiator_pool_raised: B(() => pool(true)),
    'gamma_irradiator_pool/source_rack': B(sourceRack, 'gamma_irradiator_pool/partial/source_rack'),
    ntd_silicon_rig: B(ntdRig), 'ntd_silicon_rig/ingot': B(ingot, 'ntd_silicon_rig/partial/ingot'),
    pneumatic_rabbit_terminal: B(() => rabbitTerminal('idle')), pneumatic_rabbit_terminal_sending: B(() => rabbitTerminal('sending')),
    pneumatic_rabbit_terminal_returned: B(() => rabbitTerminal('returned')), 'pneumatic_rabbit_terminal/hatch': B(rabbitHatch, 'pneumatic_rabbit_terminal/partial/hatch'),
    pneumatic_tube: B(() => tube('straight')), pneumatic_tube_corner: B(() => tube('corner')), pneumatic_tube_tee: B(() => tube('tee')),
    rtg_stirling: B(() => rtg(true)), rtg_stirling_stalled: B(() => rtg(false)), 'rtg_stirling/shaft': B(rtgShaft, 'rtg_stirling/partial/shaft'),
    source_container_type_a: B(typeA), 'source_container_type_a/lid': B(typeALid, 'source_container_type_a/partial/lid'), source_container_type_b: B(typeB),
  };
  window.M12I = FUTK.machine(BUILDS, paint);
})();
