// Suspension PVC polymerisation reactor, hand-authored for Blockbench (replaces the borrowed
// polymers/high_pressure_autoclave and its agitator partial).
// Load inside Blockbench desktop, after the shared toolkit, the round shapes and the PWR (it shares the PWR's
// yellow and cladding):
//   eval(require('fs').readFileSync('<repo>/art/blockbench/lib/pcart.js', 'utf8'))
//   eval(require('fs').readFileSync('<repo>/art/blockbench/lib/pcround.js', 'utf8'))
//   eval(require('fs').readFileSync('<repo>/art/blockbench/core/pwr_reactor.author.js', 'utf8'))
//   eval(require('fs').readFileSync('<repo>/art/blockbench/polymers/pvc_reactor.author.js', 'utf8'))
//   PCART.paintMaterials('<repo>'); PWR.paint('<repo>'); PVC.paint('<repo>')
//   build + save each model in turn (PVC.build / PVC.save, ids in PVC.ids)
//
// 1x3x1 about the middle block (x, z 0..16, y -16..32), front to the north. A jacketed stainless autoclave of
// the kind used for suspension PVC. It stands on four legs over the bottom discharge valve, which takes the
// slurry out to the north. The cooling-water jacket is fed low and drained high (green, GOST 14202), with
// the jacket's weld bands round the shell. On the top head are the manway, the vinyl chloride charge nozzle
// (yellow, gas, red ring for toxic) and the rupture disc with its vent stack. The agitator drive stands on a
// lantern: the gearbox with the motor alongside, and in the open lantern the shaft and the flanged coupling.
// The coupling is the agitator partial (pvc_reactor/agitator), which the renderer turns about y through
// x 8, z 8 while the reactor runs.
(function () {
  const path = require('path');
  const { rng, img, put, get, add, mix, rect, grain, writePng } = PCART;
  const T = {};
  const C = [8, 8];

  // Jacket: brushed stainless, weld bands every half block, a faint blue heat tint low down.
  T['pvc_reactor/jacket'] = () => {
    const im = img(32, 32), r = rng(3401);
    const streak = []; for (let x = 0; x < 32; x++) streak.push((r() - 0.5) * 12);
    for (let y = 0; y < 32; y++) for (let x = 0; x < 32; x++) {
      let c = add([168, 172, 176], streak[x] + (r() - 0.5) * 6);
      if (y % 16 === 0) c = [120, 116, 110];
      if (y % 16 === 1) c = [146, 138, 128];
      put(im, x, y, c);
    }
    return im;
  };
  // Top head from above: dished end, the weld round the knuckle.
  T['pvc_reactor/head'] = () => {
    const im = img(32, 32), r = rng(3411);
    for (let y = 0; y < 32; y++) for (let x = 0; x < 32; x++) {
      const d = Math.hypot(x + 0.5 - 16, y + 0.5 - 16) / 16;
      let c = add([178, 182, 186], -d * 26 + (r() - 0.5) * 6);
      if (Math.abs(d - 0.8) < 0.05) c = [124, 118, 112];
      put(im, x, y, c);
    }
    return im;
  };
  // Drive: grey-blue enamel of the gearbox and motor.
  T['pvc_reactor/drive'] = () => {
    const im = img(32, 32), r = grain(im, [74, 92, 112], 3421, 3, 5, 8);
    for (let y = 0; y < 32; y += 4) for (let x = 0; x < 32; x++) put(im, x, y, add(get(im, x, y), -12));
    return im;
  };

  const MAT = {
    jk: { tex: 'pvc_reactor/jacket', P: 16 }, drv: { tex: 'pvc_reactor/drive', P: 16 }, yel: { tex: 'pwr_reactor/yellow', P: 8 },
    clad: { tex: 'pwr_reactor/clad', P: 16 },
  };
  const mk = (particle) => Object.assign(new PCART.Model(MAT, {}), { particle: particle || 'pvc_reactor/jacket' });

  function paint(root) {
    for (const n of Object.keys(T)) writePng(T[n](), path.join(root, PCART.ASSETS, 'textures/block', n + '.png'));
    return Object.keys(T).length;
  }

  function reactor() {
    const m = mk();
    // legs and their pads, bottom head and the discharge valve out to the north
    for (const x of [1.4, 13.4]) for (const z of [1.4, 13.4]) {
      m.box(`leg_${x}_${z}`, [x, -16, z], [x + 1.2, -4, z + 1.2], 'ci', { split: '' });
      m.box(`pad_${x}_${z}`, [x - 0.4, -16, z - 0.4], [x + 1.6, -15.4, z + 1.6], 'ci', { split: '' });
    }
    // leg brackets welded to the shell, a ring girder joining them
    for (const [x, z] of [[2, 2], [14, 2], [2, 14], [14, 14]]) {
      const ix = x < 8 ? x + 0.6 : x - 2.4, iz = z < 8 ? z + 0.6 : z - 2.4;
      m.box(`bracket_${x}_${z}`, [Math.min(x - 0.6, ix), -5.4, Math.min(z - 0.6, iz)], [Math.max(x + 0.6, ix + 1.8), -4, Math.max(z + 0.6, iz + 1.8)], 'ci', { split: '' });
    }
    m.ringY('girder', [8, 8], 6.9, 0.6, -5.4, -4, 'ci');
    m.cylY('bottom_0', C, 5.4, -9, -6, 'jk', { bottom: true });
    m.cylY('bottom_1', C, 3.6, -10.4, -9, 'jk', { bottom: true });
    m.box('valve_body', [6.4, -13.6, 6.4], [9.6, -10.4, 9.6], 'st', { split: '' });
    m.box('valve_actuator', [6.8, -13, 9.6], [9.2, -11, 13], 'yel', { split: '' });
    m.prism('slurry_out', 'z', [8, -12.6], 1.1, 0, 6.4, 'st', { split: '', ends: ['north'] });
    m.box('slurry_flange', [6.2, -14.4, 0], [9.8, -10.8, 0.6], 'st', { split: '' });
    // shell (the jacket) with lugs, cooling water in low and out high
    m.cylY('shell', C, 6.6, -6, 22, 'jk', { bottom: true });
    m.prism('cw_in', 'x', [-4, 4], 0.9, 13.6, 16, 'green', { split: '', ends: ['east'] });
    m.prism('cw_out', 'x', [19, 12], 0.9, 13.6, 16, 'green', { split: '', ends: ['east'] });
    // instrument pocket and a pressure gauge on the shell front
    m.box('gauge_stem', [11, 16, 1.2], [11.8, 16.8, 2.2], 'st', { split: '' });
    m.plate('gauge', [9.9, 15.4, 0.6], [12.9, 18.4, 1.2], 'br', 'north', 'material/gauge_dial');
    // top head and its nozzles
    m.cylY('head_0', C, 6, 22, 23.4, 'jk', { cap: 'pvc_reactor/head' });
    m.cylY('head_1', C, 4.4, 23.4, 24.4, 'jk', { cap: 'pvc_reactor/head' });
    m.box('manway', [1.6, 23, 5], [4.6, 25.4, 11], 'st', { split: '' });
    m.box('manway_cover', [1.2, 25.4, 4.6], [5, 26, 11.4], 'st', { split: '' });
    m.box('vcm_nozzle', [11, 23, 2.6], [12.6, 26, 4.2], 'yel', { split: '' });
    m.box('vcm_ring', [10.9, 24.4, 2.5], [12.7, 25, 4.3], 'red', { split: '' });
    m.box('vcm_line', [11, 26, 0], [12.6, 27.6, 4.2], 'yel', { split: '' });
    m.box('rupture_disc', [11, 23, 11], [13.4, 25, 13.4], 'red', { split: '' });
    m.box('vent_stack', [11.6, 25, 11.6], [12.8, 32, 12.8], 'st', { split: 'y' });
    // agitator drive on its lantern: seal housing, four lantern posts, gearbox, motor alongside
    m.cylY('seal', C, 2, 24.4, 25.4, 'st');
    for (const [x, z] of [[5.2, 5.2], [10, 5.2], [5.2, 10], [10, 10]]) m.box(`lantern_${x}_${z}`, [x, 25.4, z], [x + 0.8, 28.6, z + 0.8], 'drv', { split: '' });
    m.box('gearbox', [4.4, 28.6, 4.4], [11.6, 31.4, 11.6], 'drv', { split: '' });
    m.box('motor', [11.6, 28.8, 5.4], [15.8, 31.2, 10.6], 'drv', { split: '' });
    m.box('motor_fan_cover', [15.8, 29.2, 5.8], [16, 30.8, 10.2], 'st', { split: '' });
    m.box('terminal_box', [12.6, 31.2, 6.4], [14.6, 32, 9.6], 'drv', { split: '' });
    return m;
  }

  // Agitator partial: the shaft in the lantern and the flanged coupling with its bolts (turned about y, x 8, z 8).
  function agitator() {
    const m = mk('material/bright_steel');
    m.cylY('shaft', C, 0.7, 25.4, 28.6, 'bright');
    m.cylY('coupling_lo', C, 1.9, 26.4, 27, 'st');
    m.cylY('coupling_hi', C, 1.9, 27, 27.6, 'st');
    for (let k = 0; k < 4; k++) {
      const a = k * Math.PI / 2 + Math.PI / 4, x = 8 + 1.4 * Math.cos(a), z = 8 + 1.4 * Math.sin(a);
      m.box(`bolt_${k}`, [x - 0.25, 26.1, z - 0.25], [x + 0.25, 27.9, z + 0.25], 'ci', { split: '' });
    }
    m.box('key_mark', [8.6, 25.6, 7.7], [9.4, 26.4, 8.3], 'red', { split: '' });
    return m;
  }

  const BUILDS = {
    pvc_reactor: { model: reactor },
    'pvc_reactor/agitator': { model: agitator, java: 'pvc_reactor/partial/agitator' },
  };

  window.PVC = {
    paint, ids: Object.keys(BUILDS),
    build: (root, id) => PCART.realize(root, BUILDS[id].model(), id),
    save: (root, id) => PCART.save(root, 'polymers', id, BUILDS[id].java),
    preview(root) { return PCART.realize(root, reactor().merge(agitator(), 0, 0, 0, 'a_'), 'pvc_reactor_preview'); },
  };
})();
