// Liquid-ring vacuum pump for methane drainage (VVN type) and the drainage borehole head, hand-authored for
// Blockbench (replace the spec_visual.py and spec_metallurgy.py placeholders), plus the pump's panel board.
// Load inside Blockbench desktop, after the shared toolkit:
//   eval(require('fs').readFileSync('<repo>/art/blockbench/lib/pcart.js', 'utf8'))
//   eval(require('fs').readFileSync('<repo>/art/blockbench/mining/degasification_pump.author.js', 'utf8'))
//   PCART.paintMaterials('<repo>'); DGP.paint('<repo>'); DGP.paintGui('<repo>')
//   await DGP.exportAll('<repo>')
//
// Pump: 1x1x1, model x 0..16, y 0..16, z 0..16, front to the north, the Create shaft in from the south face
// (the rotor partial, which FoundryMachineRenderer spins about z through x 8, y 8). The casing is a cylinder
// on a base frame with end covers carrying the ports and bearing housings. Gas comes down from the top face
// through a flame arrester (required on every drainage suction line) past a vacuum gauge; the pump throws the
// gas-water mix into the separator on the east side, gas leaves its top through the east face, the ring water
// returns through the make-up line with a regulating cock.
// Borehole: 1x1x1, a concrete collar, casing head, master gate valve with handwheel, a sampling tee with a cock
// and a vacuum gauge, and the yellow gas pipe turning east to the drainage main.
(function () {
  const path = require('path');
  const { rng, img, put, add, mix, rect, grain, writePng } = PCART;
  const TEX_REL = PCART.ASSETS + '/textures/block/degasification_pump';
  const T = {};

  // Casing paint: dark green machine enamel, worn at the edges, water stains below the separator.
  T.casing = () => {
    const im = img(32, 32), r = grain(im, [58, 84, 64], 1601, 3, 5, 8);
    for (let y = 0; y < 32; y++) for (let x = 0; x < 32; x++) {
      const i = (y * 32 + x) * 4, c = [im.d[i], im.d[i + 1], im.d[i + 2]];
      put(im, x, y, r() < 0.02 ? [118, 118, 112] : mix(c, [40, 42, 40], Math.max(0, (y - 22) / 10) * 0.3));
    }
    return im;
  };
  // Gas pipework, yellow as gas lines are marked, dulled by dust.
  T.gas = () => {
    const im = img(32, 32), r = grain(im, [196, 164, 44], 1611, 3, 5, 8);
    for (let y = 0; y < 32; y++) for (let x = 0; x < 32; x++) if (r() < 0.12) put(im, x, y, add([150, 126, 40], (r() - 0.5) * 10));
    return im;
  };
  // Concrete of the borehole collar.
  T.concrete = () => {
    const im = img(32, 32), r = grain(im, [140, 138, 132], 1621, 7, 8, 6);
    for (let k = 0; k < 30; k++) put(im, Math.floor(r() * 32), Math.floor(r() * 32), [96, 94, 90]);
    return im;
  };
  // Separator sight glass: water to two thirds with a meniscus line, gas above.
  T.sight = () => {
    const im = img(16, 16);
    for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
      put(im, x, y, y < 5 ? [170, 186, 190] : y === 5 ? [230, 240, 240] : mix([70, 110, 130], [40, 70, 90], y / 16));
    }
    return im;
  };
  // Vacuum gauge, -0.1..0 MPa: black scale on white, red sector below -0.08, needle at a working vacuum.
  T.vacuum = () => {
    const im = img(32, 32), r = rng(1631), C = 16;
    for (let y = 0; y < 32; y++) for (let x = 0; x < 32; x++) {
      const d = Math.hypot(x + 0.5 - C, y + 0.5 - C);
      put(im, x, y, d > 14.6 ? [60, 62, 64] : d > 13.6 ? [30, 30, 30] : add([228, 222, 204], (r() - 0.5) * 5));
    }
    const ray = (f, r0, r1, col) => {
      const a = (225 - f * 270) * Math.PI / 180;
      for (let s = r0; s <= r1; s += 0.35) put(im, C - 0.5 + s * Math.cos(a), C - 0.5 - s * Math.sin(a), col);
    };
    for (let k = 0; k <= 10; k++) ray(k / 10, k % 2 ? 12 : 10.8, 12.9, [40, 38, 36]);
    for (let f = 0; f <= 0.2; f += 0.02) ray(f, 11.6, 12.9, [196, 34, 28]);
    ray(0.45, 0, 10, [30, 28, 26]);
    return im;
  };

  const MAT = {
    cs: { tex: 'degasification_pump/casing', P: 16 }, gas: { tex: 'degasification_pump/gas', P: 16 },
    conc: { tex: 'degasification_pump/concrete', P: 16 },
  };
  const mk = (particle) => Object.assign(new PCART.Model(MAT, {}), { particle: particle || 'degasification_pump/casing' });

  function paint(root) {
    for (const n of Object.keys(T)) writePng(T[n](), path.join(root, TEX_REL, n + '.png'));
    return Object.keys(T).length;
  }

  function flange(m, n, c, r, y0, y1) { m.prismY(n, c, r, y0, y1, 'st', { ends: ['up', 'down'] }); }

  function pump() {
    const m = mk();
    // base frame and casing feet
    m.box('frame_w', [0.5, 0, 0.5], [2, 1.6, 15.5], 'ci', { split: '' });
    m.box('frame_e', [14, 0, 0.5], [15.5, 1.6, 15.5], 'ci', { split: '' });
    for (const z of [0.5, 14]) m.box(`frame_x_${z}`, [2, 0, z], [14, 1.6, z + 1.5], 'ci', { split: '' });
    for (const z of [4, 10]) m.box(`foot_${z}`, [3, 1.6, z], [13, 3.6, z + 2], 'cs', { split: '' });
    // casing and end covers with bearing housings
    m.prism('casing', 'z', [8, 8], 5, 3, 12.6, 'cs', { split: '' });
    m.prism('cover_front', 'z', [8, 8], 5.6, 1.9, 3, 'cs', { split: '', ends: ['north'] });
    m.prism('cover_rear', 'z', [8, 8], 5.6, 12.6, 13.7, 'cs', { split: '', ends: ['south'] });
    m.prism('bearing_front', 'z', [8, 8], 2.2, 0.8, 1.9, 'ci', { split: '', ends: ['north'] });
    m.prism('bearing_rear', 'z', [8, 8], 2.4, 13.7, 14.4, 'ci', { split: '', ends: ['south'] });
    for (const [dx, dy] of [[4.4, 0], [-4.4, 0], [0, 4.4], [0, -4.4], [3.1, 3.1], [-3.1, 3.1], [3.1, -3.1], [-3.1, -3.1]]) {
      m.box(`cover_bolt_${dx}_${dy}`, [8 + dx - 0.3, 8 + dy - 0.3, 1.6], [8 + dx + 0.3, 8 + dy + 0.3, 1.9], 'st', { split: '' });
    }
    // suction: down from the top face through the flame arrester to the front cover port
    flange(m, 'suction_flange', [6, 4], 2, 15.4, 16);
    m.prismY('suction_pipe_a', [6, 4], 1.3, 14.2, 15.4, 'gas');
    m.prismY('arrester', [6, 4], 2.3, 11.6, 14.2, 'st', { ends: ['up', 'down'] });
    for (const y of [12, 13.8]) m.box(`arrester_band_${y}`, [3.6, y, 1.6], [8.4, y + 0.3, 6.4], 'red', { split: '' });
    m.prismY('suction_pipe_b', [6, 4], 1.3, 10.4, 11.6, 'gas');
    m.box('suction_elbow', [4.7, 9.4, 2.4], [7.3, 10.6, 5.6], 'gas', { split: '' });
    // vacuum gauge on a cock off the suction pipe, facing front
    m.box('gauge_cock', [8.2, 12.6, 3.4], [9.2, 13.4, 4.4], 'br', { split: '' });
    m.box('gauge_pipe', [8.5, 12.7, 1.6], [9.1, 13.3, 3.4], 'br', { split: '' });
    m.box('gauge_case', [8, 11.6, 1], [11, 14.6, 1.6], 'ci', { split: '' });
    m.plate('gauge_face', [8.2, 11.8, 0.9], [10.8, 14.4, 1], 'ci', 'north', 'degasification_pump/vacuum', { hide: ['south'] });
    // discharge to the separator, the separator with its sight glass, gas out east, ring water back
    m.box('discharge', [12.4, 9.6, 7], [13.4, 11, 9], 'cs', { split: '' });
    m.prismY('separator', [13.4, 11.6], 2.4, 1.6, 13, 'cs', { ends: ['down'] });
    m.prismY('separator_head', [13.4, 11.6], 2.6, 13, 13.8, 'cs', { ends: ['up', 'down'] });
    m.plate('sight', [15.75, 4, 10.6], [15.85, 10, 12.6], 'cs', 'east', 'degasification_pump/sight', { hide: ['west'] });
    m.box('sight_guard_t', [15.6, 10, 10.4], [16, 10.4, 12.8], 'st', { split: '' });
    m.box('sight_guard_b', [15.6, 3.6, 10.4], [16, 4, 12.8], 'st', { split: '' });
    m.box('gas_out', [13.4, 12.6, 10.9], [16, 13.8, 12.3], 'gas', { split: '' });
    m.box('gas_out_flange', [15.6, 12, 10.3], [16, 14.4, 12.9], 'st', { split: '' });
    m.box('makeup_down', [11.2, 2.6, 13.8], [11.8, 6, 14.4], 'br', { split: '' });
    m.box('makeup_run', [8.4, 2.6, 13.8], [11.2, 3.2, 14.4], 'br', { split: '' });
    m.box('makeup_cock', [9.2, 3.2, 13.6], [10.2, 4.2, 14.6], 'red', { split: '' });
    // coupling guard over the shaft end at the back
    m.box('guard_top', [4.4, 11.2, 14.4], [11.6, 11.7, 16], 'red', { split: '' });
    for (const x of [4.4, 11.1]) m.box(`guard_side_${x}`, [x, 3.6, 14.4], [x + 0.5, 11.2, 16], 'red', { split: '' });
    return m;
  }

  // Rotor partial, spun about z through (8, 8): shaft end through the rear bearing and the pin coupling half.
  function rotor() {
    const m = mk('material/bright_steel');
    m.prism('shaft', 'z', [8, 8], 0.9, 14.4, 16, 'bright', { split: '', ends: ['south'] });
    m.prism('coupling', 'z', [8, 8], 2.6, 14.8, 15.8, 'ci', { split: '', ends: ['north', 'south'] });
    for (const [dx, dy] of [[1.8, 0], [-1.8, 0], [0, 1.8], [0, -1.8]]) {
      m.box(`pin_${dx}_${dy}`, [8 + dx - 0.35, 8 + dy - 0.35, 15.8], [8 + dx + 0.35, 8 + dy + 0.35, 16], 'bright', { split: '' });
    }
    m.box('key', [7.7, 8.8, 14.4], [8.3, 9.3, 14.8], 'st', { split: '' });
    return m;
  }

  function borehole() {
    const m = mk('degasification_pump/concrete');
    m.box('collar', [2, 0, 2], [14, 1.6, 14], 'conc', { split: '' });
    m.prismY('casing_head', [8, 8], 2.6, 1.6, 3.6, 'st', { ends: ['up'] });
    flange(m, 'head_flange', [8, 8], 3.2, 3.6, 4.2);
    // master gate valve: body, bonnet, rising stem and handwheel
    m.box('valve_body', [5.6, 4.2, 5.6], [10.4, 7.4, 10.4], 'ci', { split: '' });
    flange(m, 'valve_flange', [8, 8], 3, 7.4, 8);
    m.box('bonnet', [4.4, 5.2, 7], [5.6, 6.6, 9], 'ci', { split: '' });
    m.box('stem', [2.4, 5.6, 7.7], [4.4, 6.2, 8.3], 'bright', { split: '' });
    m.box('handwheel_v', [2, 3.6, 7.6], [2.6, 8.2, 8.4], 'red', { split: '' });
    m.box('handwheel_h', [2, 5.5, 5.6], [2.6, 6.3, 10.4], 'red', { split: '' });
    // tee with sampling cock and gauge, then the gas pipe up and east
    m.prismY('riser', [8, 8], 1.6, 8, 12.6, 'gas');
    m.box('tee', [6.2, 9.2, 6.2], [9.8, 10.8, 9.8], 'gas', { split: '' });
    m.box('sample_cock', [8, 9.6, 9.8], [8.8, 10.4, 11.2], 'br', { split: '' });
    m.box('sample_tap', [8.1, 10.4, 10.8], [8.7, 11.2, 11.4], 'red', { split: '' });
    m.box('gauge_stem', [9.8, 9.6, 7.7], [11, 10.2, 8.3], 'br', { split: '' });
    m.box('gauge_case', [11, 8.6, 6.6], [11.6, 11.4, 9.4], 'ci', { split: '' });
    m.plate('gauge_face', [11.6, 8.8, 6.8], [11.7, 11.2, 9.2], 'ci', 'east', 'degasification_pump/vacuum', { hide: ['west'] });
    m.box('elbow', [6.4, 12.6, 6.4], [9.6, 15.4, 9.6], 'gas', { split: '' });
    m.box('to_main', [9.6, 13.2, 7], [16, 14.8, 9], 'gas', { split: '' });
    m.box('main_flange', [15.6, 12.6, 6.4], [16, 15.4, 9.6], 'st', { split: '' });
    // borehole number tag on the collar
    m.box('tag', [6, 1.6, 2.2], [10, 2.8, 2.4], 'br', { split: '' });
    return m;
  }

  // Panel board for MinePanels.DEGAS (176x100): drainage station board, two dial rings, a plate for the lamp.
  function guiPump() {
    const W = 176, H = 100, im = img(W, H), r = rng(1641);
    grain(im, [60, 78, 64], 1642, 3, 5, 12);
    for (let y = 0; y < H; y++) for (let x = 0; x < W; x++) {
      const d = Math.min(x, y, W - 1 - x, H - 1 - y);
      if (d < 4) put(im, x, y, add([170, 134, 70], (d === 0 ? -50 : d === 1 ? 30 : d === 3 ? -30 : 0) + (r() - 0.5) * 8));
    }
    for (const cx of [32, 88]) for (let a = 0; a < 360; a += 0.5) {
      const t = a * Math.PI / 180;
      put(im, cx + 26 * Math.cos(t), 48 + 26 * Math.sin(t), [34, 46, 38]);
      put(im, cx + 27 * Math.cos(t) + 0.5, 48 + 27 * Math.sin(t) + 0.5, [108, 128, 112]);
    }
    for (let y = 21; y < 78; y++) for (let x = 118; x < W - 6; x++) {
      const edge = x === 118 || y === 21 ? -22 : x === W - 7 || y === 77 ? 18 : 0;
      put(im, x, y, add([52, 68, 56], edge + (r() - 0.5) * 4));
    }
    // a yellow gas-line stripe across the lamp plate, the station's colour code
    rect(im, 122, 52, W - 132, 3, [196, 164, 44]);
    return im;
  }

  function paintGui(root) {
    writePng(guiPump(), path.join(root, PCART.ASSETS, 'textures/gui/panel/degasification_pump.png'));
    return 1;
  }

  const BUILDS = {
    degasification_pump: { model: pump },
    'degasification_pump/rotor': { model: rotor, java: 'degasification_pump/partial/rotor' },
    degasification_borehole: { model: borehole },
  };

  window.DGP = {
    paint, paintGui,
    build: (root, id) => PCART.realize(root, BUILDS[id].model(), id),
    exportAll: (root) => PCART.exportAll(root, 'mining', BUILDS),
    preview(root) {
      return PCART.realize(root, pump().merge(rotor(), 0, 0, 0, 'r_').merge(borehole(), 20, 0, 0, 'b_'), 'degasification_preview');
    },
  };
})();
