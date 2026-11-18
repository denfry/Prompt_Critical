// Chlorine liquefier of the chlor-alkali plant, hand-authored for Blockbench (replaces the borrowed
// automation/air_dryer, an instrument-air dryer that stood in for it).
// Load inside Blockbench desktop, after the shared toolkit, the round shapes and the PWR (shared yellow):
//   eval(require('fs').readFileSync('<repo>/art/blockbench/lib/pcart.js', 'utf8'))
//   eval(require('fs').readFileSync('<repo>/art/blockbench/lib/pcround.js', 'utf8'))
//   eval(require('fs').readFileSync('<repo>/art/blockbench/core/pwr_reactor.author.js', 'utf8'))
//   eval(require('fs').readFileSync('<repo>/art/blockbench/chlor_alkali/chlorine_liquefier.author.js', 'utf8'))
//   PCART.paintMaterials('<repo>'); PWR.paint('<repo>'); CLQ.paint('<repo>')
//   build + save each model in turn (CLQ.build / CLQ.save, ids in CLQ.ids)
//
// 1x2x1 (x, z 0..16, y 0..32), front to the north. Dry chlorine is liquefied by cold, not by much pressure.
// The upper half is a horizontal shell-and-tube condenser on a steel frame, with chlorine in the shell and
// brine (or freon) in the tubes through the channel heads at both ends. Liquid chlorine drains from the
// bottom of the shell into the receiver below. The receiver is a horizontal tank on saddles with its level
// gauge and the liquid chlorine take-off. Tail gas (the uncondensed hydrogen and air with some chlorine) goes
// off at the top to the absorber. Pipes follow GOST 14202: chlorine yellow with a red ring for toxic, brine
// brown. While it runs (_running) the condenser shell, the drain leg and the receiver are white with frost
// at about -30 C.
(function () {
  const path = require('path');
  const { rng, img, put, get, add, mix, rect, grain, writePng } = PCART;
  const T = {};

  // Condenser and receiver shell: green-grey enamel, girth seams.
  T['chlorine_liquefier/shell'] = () => {
    const im = img(32, 32), r = grain(im, [96, 118, 104], 3501, 3, 5, 8);
    for (let y = 0; y < 32; y++) for (let x = 0; x < 32; x++) if (x % 16 === 0) put(im, x, y, add(get(im, x, y), -24));
    for (let k = 0; k < 8; k++) put(im, r() * 32, r() * 32, [140, 110, 70]);
    return im;
  };
  // Hoar frost over the cold shell: white crystals, the paint showing grey-green between.
  T['chlorine_liquefier/frost'] = () => {
    const im = img(32, 32), r = rng(3511);
    for (let y = 0; y < 32; y++) for (let x = 0; x < 32; x++) {
      const t = r();
      put(im, x, y, t < 0.12 ? [150, 168, 160] : add([226, 234, 240], (r() - 0.5) * 22 + (t > 0.92 ? 20 : 0)));
    }
    return im;
  };
  // Tube sheet seen in the open channel head would be hidden; the head cover is a bolted dished cover.
  T['chlorine_liquefier/cover'] = () => {
    const im = img(32, 32), r = rng(3521);
    for (let y = 0; y < 32; y++) for (let x = 0; x < 32; x++) {
      const d = Math.hypot(x + 0.5 - 16, y + 0.5 - 16) / 16;
      let c = add([96, 118, 104], -d * 20 + (r() - 0.5) * 6);
      if (d > 0.84 && d < 0.96 && Math.round(Math.atan2(y - 16, x - 16) * 12 / Math.PI) % 2 === 0) c = [60, 62, 60];
      put(im, x, y, c);
    }
    return im;
  };
  // Brine pipe: brown (GOST 14202, other liquids).
  T['chlorine_liquefier/brine'] = () => { const im = img(16, 16); grain(im, [112, 76, 50], 3531, 3, 4, 4); return im; };

  const MAT = {
    sh: { tex: 'chlorine_liquefier/shell', P: 16 }, fr: { tex: 'chlorine_liquefier/frost', P: 16 },
    brn: { tex: 'chlorine_liquefier/brine', P: 16 }, yel: { tex: 'pwr_reactor/yellow', P: 8 },
  };
  const mk = () => Object.assign(new PCART.Model(MAT, {}), { particle: 'chlorine_liquefier/shell' });

  function paint(root) {
    for (const n of Object.keys(T)) writePng(T[n](), path.join(root, PCART.ASSETS, 'textures/block', n + '.png'));
    return Object.keys(T).length;
  }

  function liquefier(running) {
    const m = mk(), cold = running ? 'fr' : 'sh';
    const cover = (n, x0, x1, end) => {
      m.prism(n, 'x', [22, 8], 5.4, x0, x1, 'sh', { split: '', ends: ['east', 'west'] });
      m.disc(`${n}_cover`, 'x', [22, 8], 5.4, end === 'east' ? x1 - 0.4 : x0, end === 'east' ? x1 : x0 + 0.4, 'sh', 'chlorine_liquefier/cover', end);
    };
    // frame: four posts, cross beams under the condenser
    for (const x of [1, 14]) for (const z of [1, 14]) m.box(`post_${x}_${z}`, [x, 0, z], [x + 1, 17, z + 1], 'ci', { split: 'y' });
    for (const x of [1, 14]) m.box(`beam_${x}`, [x, 16, 1], [x + 1, 17, 15], 'ci', { split: '' });
    // condenser: shell along x, channel heads at both ends
    m.prism('condenser', 'x', [22, 8], 4.8, 2.4, 13.6, cold, { split: '' });
    cover('head_w', 0.4, 2.4, 'west');
    cover('head_e', 13.6, 15.6, 'east');
    for (const x of [3, 13]) m.box(`saddle_${x}`, [x, 17, 4.6], [x + 1, 18.6, 11.4], 'ci', { split: '' });
    // brine in at the west head bottom, out at the east head top
    m.box('brine_in', [0.8, 13, 7.2], [2, 17.2, 8.8], 'brn', { split: '' });
    m.box('brine_in_run', [0, 13, 7.2], [2, 14.2, 8.8], 'brn', { split: '' });
    m.box('brine_out', [14, 27, 7.2], [15.2, 32, 8.8], 'brn', { split: '' });
    // chlorine gas in on top from the north, tail gas off at the top of the far end
    m.box('cl2_in', [5, 26.6, 0], [6.6, 28.2, 9], 'yel', { split: '' });
    m.box('cl2_in_drop', [5, 26.6, 7.4], [6.6, 28.2, 9], 'yel', { split: '' });
    m.box('cl2_ring', [4.9, 26.5, 1.6], [6.7, 28.3, 2.2], 'red', { split: '' });
    m.box('tail_gas', [9.6, 26.6, 9.4], [10.8, 32, 10.6], 'yel', { split: 'y' });
    m.box('tail_valve', [9, 29, 9], [11.4, 30.2, 11], 'red', { split: '' });
    // drain leg down to the receiver
    m.box('drain', [7.4, 7, 7.4], [8.6, 17.4, 8.6], cold, { split: '' });
    // receiver on saddles with level gauge and take-off
    m.prism('receiver', 'x', [5, 8], 4, 1.6, 14.4, cold, { split: '', ends: ['east', 'west'] });
    for (const x of [3, 12]) m.box(`rsaddle_${x}`, [x, 0, 4], [x + 1, 2.4, 12], 'ci', { split: '' });
    m.box('gauge_glass', [10.4, 2, 3.2], [11, 8, 3.8], running ? 'fr' : 'st', { split: '' });
    m.box('gauge_cock_top', [10.2, 8, 3.4], [11.2, 8.6, 4.4], 'red', { split: '' });
    m.box('takeoff', [2, 0, 1], [3.2, 4, 2.2], 'yel', { split: '' });
    m.box('takeoff_run', [2, 1, 0], [3.2, 2.2, 4.4], 'yel', { split: '' });
    m.box('takeoff_ring', [1.9, 0.9, 0.6], [3.3, 2.3, 1.1], 'red', { split: '' });
    m.box('safety_valve', [12, 9, 7], [13.4, 11, 9], 'red', { split: '' });
    return m;
  }

  const BUILDS = {
    chlorine_liquefier: { model: () => liquefier(false) },
    chlorine_liquefier_running: { model: () => liquefier(true) },
  };

  window.CLQ = {
    paint, ids: Object.keys(BUILDS),
    build: (root, id) => PCART.realize(root, BUILDS[id].model(), id),
    save: (root, id) => PCART.save(root, 'chlor_alkali', id, BUILDS[id].java),
    preview(root) { return PCART.realize(root, liquefier(false).merge(liquefier(true), 20, 0, 0, 'r_'), 'chlorine_liquefier_preview'); },
  };
})();
