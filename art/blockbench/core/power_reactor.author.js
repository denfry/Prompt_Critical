// AM-1 type graphite channel power reactor of T1, hand-authored for Blockbench (replaces the plain
// core/reactor_vessel_wall cube both segments used to borrow).
// Load inside Blockbench desktop, after the shared toolkit, the round shapes and the PWR (it shares the PWR's
// concrete, cladding, paint and lettering):
//   eval(require('fs').readFileSync('<repo>/art/blockbench/lib/pcart.js', 'utf8'))
//   eval(require('fs').readFileSync('<repo>/art/blockbench/lib/pcround.js', 'utf8'))
//   eval(require('fs').readFileSync('<repo>/art/blockbench/core/pwr_reactor.author.js', 'utf8'))
//   eval(require('fs').readFileSync('<repo>/art/blockbench/core/power_reactor.author.js', 'utf8'))
//   PCART.paintMaterials('<repo>'); PWR.paint('<repo>'); AM1.paint('<repo>')
//   build + save each model in turn (AM1.build / AM1.save, ids in AM1.ids)
//
// Two 3x3x3 segments about their middle blocks (-16..32), front to the north.
// power_reactor (CORE): the graphite stack is hidden in a square concrete block with steel corner angles. Along
// the north side runs the distributing header (cold water, green band) and along the south side the
// collecting header (hot water, red band). From each header the individual channel pipes rise up the wall to
// the top, the curtain of thin pipes every channel reactor has. Radiation sign and the plant plate are on the front.
// power_reactor_drives (DRIVES): the steel top plate and the round central plug. It carries the grid of
// channel heads, with the channel pipes coming over the edge to them. Four control rod servo drives stand tall
// among the heads. The refuelling bridge spans the top on two columns, with its trolley and the cask hanging
// on the ropes.
(function () {
  const path = require('path');
  const { rng, img, put, add, mix, rect, grain, writePng } = PCART;
  const T = {};
  const C = [8, 8];

  // Central plug from above: grey steel with the channel holes on a square lattice and their numbered rings.
  T['power_reactor/plug'] = () => {
    const im = img(64, 64), r = grain(im, [112, 116, 118], 3201, 4, 5, 8);
    for (let y = 0; y < 64; y++) for (let x = 0; x < 64; x++) {
      const d = Math.hypot(x + 0.5 - 32, y + 0.5 - 32) / 32;
      if (d > 0.95) put(im, x, y, [78, 80, 82]);
      else if (x % 6 === 2 && y % 6 === 2 && d < 0.9) { put(im, x, y, [30, 30, 32]); put(im, x + 1, y, [52, 52, 54]); put(im, x, y + 1, [52, 52, 54]); }
    }
    return im;
  };
  // Blue-grey enamel of the refuelling bridge.
  T['power_reactor/bridge'] = () => {
    const im = img(32, 32), r = grain(im, [84, 100, 118], 3211, 3, 5, 8);
    for (let k = 0; k < 10; k++) put(im, r() * 32, r() * 32, [60, 62, 64]);
    for (let x = 0; x < 32; x++) put(im, x, 31, [60, 72, 86]);
    return im;
  };
  // Lead-filled refuelling cask: heavy cast shell, lifting bands.
  T['power_reactor/cask'] = () => {
    const im = img(32, 32), r = grain(im, [70, 72, 74], 3221, 4, 6, 8);
    for (let x = 0; x < 32; x++) for (const y of [4, 5, 26, 27]) put(im, x, y, [196, 162, 44]);
    return im;
  };
  T['power_reactor/plates'] = () => {
    const im = img(64, 32), src = img(64, 32);
    // trefoil and the plant plate, lettered like the PWR's
    for (let y = 0; y < 24; y++) for (let x = 0; x < 24; x++) {
      const c = 12, dx = x + 0.5 - c, dy = y + 0.5 - c, d = Math.hypot(dx, dy) / c, a = Math.atan2(dy, dx) * 180 / Math.PI;
      let col = [214, 178, 40];
      if (d < 0.16 || (d > 0.24 && d < 0.78 && ((((a + 90) % 120) + 120) % 120) < 60)) col = [24, 22, 20];
      if (x === 0 || y === 0 || x === 23 || y === 23) col = [24, 22, 20];
      put(im, x, y, col);
    }
    rect(im, 26, 0, 38, 12, [226, 222, 208]);
    const glyph = { 'А': ['010', '101', '111', '101', '101'], 'М': ['101', '111', '111', '101', '101'], '-': ['000', '000', '111', '000', '000'], '1': ['010', '110', '010', '010', '111'] };
    let x0 = 36;
    for (const ch of 'АМ-1') { glyph[ch].forEach((row, j) => [...row].forEach((b, i) => { if (b === '1') put(im, x0 + i, 3 + j, [30, 30, 30]); })); x0 += 4; }
    return im;
  };

  const MAT = {
    conc: { tex: 'pwr_reactor/concrete', P: 32 }, cdk: { tex: 'pwr_reactor/concrete_dark', P: 16 },
    clad: { tex: 'pwr_reactor/clad', P: 16 }, pnt: { tex: 'pwr_reactor/paint', P: 16 }, yel: { tex: 'pwr_reactor/yellow', P: 8 },
    brg: { tex: 'power_reactor/bridge', P: 16 }, cask: { tex: 'power_reactor/cask', P: 16 },
  };
  const ATLAS = { pl: { tex: 'power_reactor/plates', size: 64, rects: { sign: [0, 0, 24, 24], name: [26, 0, 38, 12] } } };
  const mk = (particle) => Object.assign(new PCART.Model(MAT, ATLAS), { particle: particle || 'pwr_reactor/concrete' });

  function paint(root) {
    for (const n of Object.keys(T)) writePng(T[n](), path.join(root, PCART.ASSETS, 'textures/block', n + '.png'));
    return Object.keys(T).length;
  }

  const PIPES = [-8, -4, 0, 4, 8, 12, 16, 20, 24];

  function core() {
    const m = mk();
    m.box('plinth', [-15.5, -16, -15.5], [31.5, -13, 31.5], 'cdk');
    m.box('stack', [-13, -13, -13], [29, 22, 29], 'conc');
    for (const x of [-13.4, 28.4]) for (const z of [-13.4, 28.4]) m.box(`angle_${x}_${z}`, [x, -13, z], [x + 1, 22, z + 1], 'st', { split: 'y' });
    m.box('top_band', [-13.6, 22, -13.6], [29.6, 25, 29.6], 'pnt', { face: { up: 'st' } });
    m.box('top', [-12, 25, -12], [28, 32, 28], 'cdk');
    // headers along the north (distributing, cold) and south (collecting, hot) walls, out through the ends
    m.prism('header_cold', 'x', [17, -14.8], 1.9, -16, 29, 'clad', { split: '', ends: ['west'] });
    m.prism('header_hot', 'x', [17, 30.8], 1.9, -13, 32, 'clad', { split: '', ends: ['east'] });
    m.prism('band_cold', 'x', [17, -14.8], 2.05, -12, -11, 'green', { split: '' });
    m.prism('band_hot', 'x', [17, 30.8], 2.05, 27, 28, 'red', { split: '' });
    for (const x of [-6, 10, 26]) {
      m.box(`saddle_n_${x}`, [x, 13, -14.6], [x + 1.4, 15.2, -13], 'ci', { split: '' });
      m.box(`saddle_s_${x - 8}`, [x - 8, 13, 29], [x - 6.6, 15.2, 30.6], 'ci', { split: '' });
    }
    // individual channel pipes up the walls to the top
    for (const x of PIPES) {
      m.box(`pipe_n_${x}`, [x - 0.3, 18.6, -14.9], [x + 0.3, 32, -14.3], 'bright', { split: 'y' });
      m.box(`pipe_s_${x}`, [x - 0.3, 18.6, 30.3], [x + 0.3, 32, 30.9], 'bright', { split: 'y' });
    }
    m.decal('sign', [1, 0, -13.5], [7, 6, -13], 'conc', 'north', 'pl.sign');
    m.decal('name', [9, 2, -13.5], [18.6, 5, -13], 'conc', 'north', 'pl.name');
    m.box('hatch', [3, -15.4, -15.9], [13, -13.2, -15.4], 'pnt', { split: '' });
    return m;
  }

  function drives() {
    const m = mk('power_reactor/plug');
    m.box('top_plate', [-12, -16, -12], [28, -13, 28], 'st');
    m.cylY('plug', C, 12.4, -13, -12, 'st', { cap: 'power_reactor/plug' });
    // channel pipes coming over the edge of the top and in to the plate
    for (const x of PIPES) {
      m.box(`pipe_n_${x}`, [x - 0.3, -16, -14.9], [x + 0.3, -11.4, -14.3], 'bright', { split: '' });
      m.box(`pipe_n_run_${x}`, [x - 0.3, -12, -14.9], [x + 0.3, -11.4, -6 + Math.abs(x - 8) * 0.3], 'bright', { split: '' });
      m.box(`pipe_s_${x}`, [x - 0.3, -16, 30.3], [x + 0.3, -11.4, 30.9], 'bright', { split: '' });
      m.box(`pipe_s_run_${x}`, [x - 0.3, -12, 22 - Math.abs(x - 8) * 0.3], [x + 0.3, -11.4, 30.9], 'bright', { split: '' });
    }
    // channel heads on a lattice inside the plug, servo drives on five of them
    const rods = new Set(['-6,-6', '6,-6', '-6,6', '6,6']);
    for (let i = -3; i <= 3; i++) for (let j = -3; j <= 3; j++) {
      const dx = i * 3, dz = j * 3;
      if (Math.hypot(dx, dz) > 10.5) continue;
      const x = 8 + dx, z = 8 + dz;
      if (rods.has(`${dx},${dz}`)) {
        m.box(`servo_${i}_${j}`, [x - 1.1, -12, z - 1.1], [x + 1.1, 4, z + 1.1], 'yel', { split: 'y' });
        m.box(`servo_motor_${i}_${j}`, [x - 1.4, 4, z - 1.4], [x + 1.4, 7, z + 1.4], 'ci', { split: '' });
        m.box(`servo_cable_${i}_${j}`, [x - 0.3, 7, z - 0.3], [x + 0.3, 8.4, z + 0.3], 'soot', { split: '' });
      } else {
        m.box(`head_${i}_${j}`, [x - 0.6, -12, z - 0.6], [x + 0.6, -10.4, z + 0.6], 'bright', { split: '' });
        m.box(`cap_${i}_${j}`, [x - 0.8, -10.4, z - 0.8], [x + 0.8, -9.8, z + 0.8], (i + j) % 2 ? 'ci' : 'red', { split: '' });
      }
    }
    // refuelling bridge: two columns, the beam, the trolley, the cask on its ropes
    for (const [n, x0] of [['w', -15.6], ['e', 28.6]]) {
      m.box(`column_${n}`, [x0, -16, 5], [x0 + 3, 26, 11], 'brg', { split: 'y' });
      m.box(`column_foot_${n}`, [x0 - 0.2, -16, 3.6], [x0 + 3.2, -14.6, 12.4], 'ci', { split: '' });
    }
    m.box('beam', [-15.6, 26, 4], [31.6, 30, 12], 'brg', { split: 'x' });
    m.box('beam_rail', [-12.6, 25.4, 6.6], [28.6, 26, 9.4], 'st', { split: '' });
    m.box('trolley', [3, 21, 3], [13, 25.4, 13], 'yel', { split: '' });
    m.box('hoist_motor', [13, 21.6, 5], [16.6, 24.6, 11], 'ci', { split: '' });
    for (const x of [6.4, 9]) m.box(`rope_${x}`, [x, 13, 7.8], [x + 0.4, 21, 8.2], 'soot', { split: '' });
    m.box('hook_block', [5.4, 11.6, 6.6], [10.6, 13, 9.4], 'yel', { split: '' });
    m.cylY('cask', C, 4.2, -2, 11.6, 'cask', { cap: 'material/brass_disc' });
    m.cylY('cask_nose', C, 2.4, -5, -2, 'ci');
    return m;
  }

  const BUILDS = {
    power_reactor: { model: core },
    power_reactor_drives: { model: drives },
  };

  window.AM1 = {
    paint, ids: Object.keys(BUILDS),
    build: (root, id) => PCART.realize(root, BUILDS[id].model(), id),
    save: (root, id) => PCART.save(root, 'core', id, BUILDS[id].java),
    preview(root) { return PCART.realize(root, core().merge(drives(), 0, 48, 0, 'd_'), 'power_reactor_preview'); },
  };
})();
