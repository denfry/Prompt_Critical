// VVER pressurised-water reactor of T2a, hand-authored for Blockbench (replaces core/reactor_vessel_wall and
// mechanics/control_rod_drive, which the installation used to borrow).
// Load inside Blockbench desktop, after the shared toolkit and the round shapes:
//   eval(require('fs').readFileSync('<repo>/art/blockbench/lib/pcart.js', 'utf8'))
//   eval(require('fs').readFileSync('<repo>/art/blockbench/lib/pcround.js', 'utf8'))
//   eval(require('fs').readFileSync('<repo>/art/blockbench/core/pwr_reactor.author.js', 'utf8'))
//   PCART.paintMaterials('<repo>'); PWR.paint('<repo>')
//   build + save each model in turn (PWR.build / PWR.save, ids in PWR.ids)
//
// Two 3x3x3 segments, each modelled about its middle block (-16..32 on every axis), front to the north.
// pwr_reactor (VESSEL): the vessel stands in the cylindrical dry shield (concrete, formwork marks) on a plinth.
// Above the shield's steel cornice with its hand rail come the main flange with the stud nuts round it and the
// start of the insulated head. The four loops leave the shield in pairs on the east and west sides. Each hot
// leg sits above its cold leg, as the VVER's two rows of main nozzles do. Ex-core neutron detector channels run
// up the front past the radiation sign and the plant number.
// pwr_reactor_head (HEAD): the rest of the elliptical head and the upper unit. An open frame of posts and rings
// stands round the forest of control rod drive housings (21 here, 61 on a VVER-1000), each housing with its
// electromagnet block. The cable deck with the rod position indicator tops sits above, with the lifting
// traverse over it. The drive cooling duct comes down the west side.
(function () {
  const path = require('path');
  const { rng, img, put, get, add, mix, rect, grain, rivet, writePng } = PCART;
  const T = {};
  const C = [8, 8];

  // 3x5 pixel stencil letters for plates.
  const FONT = {
    'В': ['110', '101', '110', '101', '110'], 'Э': ['110', '001', '011', '001', '110'], 'Р': ['110', '101', '110', '100', '100'],
    '-': ['000', '000', '111', '000', '000'], '1': ['010', '110', '010', '010', '111'], '0': ['111', '101', '101', '101', '111'],
    'А': ['010', '101', '111', '101', '101'], 'М': ['101', '111', '111', '101', '101'], 'Т': ['111', '010', '010', '010', '010'],
  };
  function stencil(im, x, y, text, col) {
    for (const ch of text) {
      const g = FONT[ch];
      if (g) g.forEach((row, j) => [...row].forEach((b, i) => { if (b === '1') put(im, x + i, y + j, col); }));
      x += 4;
    }
  }
  // Radiation sign (GOST 17925): black trefoil on yellow, black border.
  function trefoil(im, x0, y0, n) {
    const c = n / 2;
    for (let y = 0; y < n; y++) for (let x = 0; x < n; x++) {
      const dx = x + 0.5 - c, dy = y + 0.5 - c, d = Math.hypot(dx, dy) / c, a = Math.atan2(dy, dx) * 180 / Math.PI;
      let col = [214, 178, 40];
      const sector = (((a + 90) % 120) + 120) % 120 < 60;
      if (d < 0.16 || (d > 0.24 && d < 0.78 && sector)) col = [24, 22, 20];
      if (x === 0 || y === 0 || x === n - 1 || y === n - 1) col = [24, 22, 20];
      put(im, x0 + x, y0 + y, col);
    }
  }

  // Shield concrete cast in board formwork: 8 px board marks, tie holes on a 16 px grid, rust streaks under them.
  T['pwr_reactor/concrete'] = () => {
    const im = img(64, 64), r = grain(im, [146, 144, 136], 3101, 5, 7, 16);
    for (let y = 0; y < 64; y++) for (let x = 0; x < 64; x++) {
      if (y % 8 === 7) put(im, x, y, add(get(im, x, y), -10));
      if (y % 8 === 0) put(im, x, y, add(get(im, x, y), 5));
      if (x % 32 === 31) put(im, x, y, add(get(im, x, y), -14));
    }
    for (let y = 4; y < 64; y += 16) for (let x = 8; x < 64; x += 16) {
      put(im, x, y, [70, 68, 64]); put(im, x + 1, y, [96, 94, 88]);
      for (let k = 1; k < 4 + r() * 6; k++) if (r() < 0.7) put(im, x, y + k, mix(get(im, x, y + k), [120, 92, 70], 0.35));
    }
    return im;
  };
  T['pwr_reactor/concrete_dark'] = () => {
    const im = img(32, 32), r = grain(im, [112, 110, 104], 3102, 5, 6, 8);
    for (let x = 0; x < 32; x++) put(im, x, 0, [140, 138, 130]);
    return im;
  };
  // Metal-clad insulation of the head and the legs: aluminium sheets with lap joints and screws.
  T['pwr_reactor/clad'] = () => {
    const im = img(32, 32), r = grain(im, [176, 180, 182], 3111, 4, 6, 8);
    for (let y = 0; y < 32; y++) for (let x = 0; x < 32; x++) {
      if (y % 16 === 15) put(im, x, y, [128, 132, 136]);
      if (y % 16 === 0) put(im, x, y, [204, 208, 210]);
      if (x === 31) put(im, x, y, [138, 142, 146]);
    }
    for (let x = 3; x < 32; x += 8) { put(im, x, 2, [110, 112, 114]); put(im, x, 18, [110, 112, 114]); }
    return im;
  };
  // Pale grey-green enamel of the upper unit steelwork, chipped.
  T['pwr_reactor/paint'] = () => {
    const im = img(32, 32), r = grain(im, [134, 154, 140], 3121, 3, 5, 8);
    for (let k = 0; k < 8; k++) put(im, r() * 32, r() * 32, [92, 96, 94]);
    return im;
  };
  // Control rod drive housing: stainless tube with the electromagnet coil bands.
  T['pwr_reactor/drive'] = () => {
    const im = img(32, 32), r = rng(3131);
    for (let y = 0; y < 32; y++) for (let x = 0; x < 32; x++) {
      const band = y % 8 < 5;
      let c = band ? [62, 72, 88] : [150, 154, 158];
      if (band && y % 8 === 0) c = [88, 98, 114];
      if (!band && y % 8 === 7) c = [118, 122, 126];
      put(im, x, y, add(c, (r() - 0.5) * 8 + (x % 16 === 0 ? 14 : 0)));
    }
    return im;
  };
  // Safety yellow for the hand rail and the traverse.
  T['pwr_reactor/yellow'] = () => {
    const im = img(16, 16), r = grain(im, [198, 162, 44], 3141, 3, 5, 4);
    for (let k = 0; k < 4; k++) put(im, r() * 16, r() * 16, [96, 84, 50]);
    return im;
  };
  // Main flange from above: machined face, stud holes ring, the O-ring groove.
  T['pwr_reactor/flange'] = () => {
    const im = img(64, 64), r = rng(3151);
    for (let y = 0; y < 64; y++) for (let x = 0; x < 64; x++) {
      const d = Math.hypot(x + 0.5 - 32, y + 0.5 - 32) / 32, a = Math.atan2(y + 0.5 - 32, x + 0.5 - 32);
      let c = add([150, 154, 158], Math.sin(d * 90) * 5 + (r() - 0.5) * 6);
      if (Math.abs(d - 0.7) < 0.02) c = [92, 96, 100];
      if (d > 0.97) c = [104, 108, 112];
      const k = Math.round(a / (Math.PI / 16)), ad = Math.abs(a - k * Math.PI / 16) * 32 * 0.86;
      if (Math.abs(d - 0.86) < 0.06 && ad < 1.6) c = [58, 60, 64];
      put(im, x, y, c);
    }
    return im;
  };
  // Shield top: chequer floor plates in quadrants about the vessel opening.
  T['pwr_reactor/floor'] = () => {
    const im = img(64, 64), r = grain(im, [96, 98, 100], 3161, 4, 4, 8);
    for (let y = 0; y < 64; y++) for (let x = 0; x < 64; x++) {
      if (x === 32 || y === 32) put(im, x, y, [58, 60, 62]);
      else if ((x + 2 * y) % 8 === 0) put(im, x, y, [128, 130, 132]);
      const d = Math.hypot(x + 0.5 - 32, y + 0.5 - 32) / 32;
      if (d > 0.96) put(im, x, y, [150, 126, 40]);
    }
    return im;
  };
  // Cable deck: steel grating.
  T['pwr_reactor/grating'] = () => {
    const im = img(64, 64), r = rng(3171);
    for (let y = 0; y < 64; y++) for (let x = 0; x < 64; x++) {
      const bar = x % 4 === 0 || y % 8 === 0;
      put(im, x, y, bar ? add([138, 146, 140], (r() - 0.5) * 8) : [36, 38, 40]);
    }
    return im;
  };
  // Plates on the shield front: radiation sign and the plant number.
  T['pwr_reactor/plates'] = () => {
    const im = img(64, 32);
    trefoil(im, 0, 0, 24);
    rect(im, 26, 0, 38, 12, [226, 222, 208]);
    for (let x = 26; x < 64; x++) { put(im, x, 0, [40, 40, 40]); put(im, x, 11, [40, 40, 40]); }
    stencil(im, 28, 3, 'ВВЭР-1000', [30, 30, 30]);
    rect(im, 26, 14, 38, 10, [70, 74, 72]);
    stencil(im, 30, 16, 'РО-1', [226, 222, 208]);
    return im;
  };

  const MAT = {
    conc: { tex: 'pwr_reactor/concrete', P: 32 }, cdk: { tex: 'pwr_reactor/concrete_dark', P: 16 },
    clad: { tex: 'pwr_reactor/clad', P: 16 }, pnt: { tex: 'pwr_reactor/paint', P: 16 }, drv: { tex: 'pwr_reactor/drive', P: 16 },
    yel: { tex: 'pwr_reactor/yellow', P: 8 },
  };
  const ATLAS = { pl: { tex: 'pwr_reactor/plates', size: 64, rects: { sign: [0, 0, 24, 24], name: [26, 0, 38, 12], number: [26, 14, 38, 10] } } };
  const mk = (particle) => Object.assign(new PCART.Model(MAT, ATLAS), { particle: particle || 'pwr_reactor/concrete' });

  function paint(root) {
    for (const n of Object.keys(T)) writePng(T[n](), path.join(root, PCART.ASSETS, 'textures/block', n + '.png'));
    return Object.keys(T).length;
  }

  // Main loop pipe leaving the shield sideways: insulated leg, a bare flange at the block face.
  function leg(m, name, side, y, z, r) {
    const [a0, a1, f0, f1] = side > 0 ? [26, 30.6, 30.6, 32] : [-14.6, -10, -16, -14.6];
    m.prism(`${name}_pipe`, 'x', [y, z], r, a0, a1, 'clad', { split: '' });
    m.prism(`${name}_flange`, 'x', [y, z], r + 0.9, f0, f1, 'st', { split: '', ends: [side > 0 ? 'east' : 'west'] });
  }

  function vessel() {
    const m = mk();
    m.box('plinth', [-15.5, -16, -15.5], [31.5, -13, 31.5], 'cdk');
    m.cylY('shield', C, 21, -13, 19, 'conc');
    m.cylY('cornice', C, 22.6, 19, 21, 'pnt', { cap: 'pwr_reactor/floor', bottom: true });
    m.ringY('rail', C, 22.4, 0.6, 26, 26.8, 'yel');
    m.ringY('rail_mid', C, 22.4, 0.5, 23.4, 24, 'yel');
    for (let k = 0; k < 16; k++) if (k % 2 === 0) m.postY(`rail_post_${k}`, C, 22.1, k * 22.5, 0.6, 21, 26.8, 'yel', { split: '' });
    // main flange, stud nuts, the insulated head going up into the head segment
    m.cylY('flange', C, 14.4, 21, 24.6, 'st', { cap: 'pwr_reactor/flange' });
    for (let k = 0; k < 16; k++) {
      const a = (k + 0.5) * Math.PI / 8, x = 8 + 12.4 * Math.cos(a), z = 8 + 12.4 * Math.sin(a);
      m.box(`nut_${k}`, [x - 0.7, 24.6, z - 0.7], [x + 0.7, 26, z + 0.7], 'bright', { split: '' });
    }
    m.cylY('head', C, 11.6, 24.6, 32, 'clad', { seed: 3 });
    // four loops: hot legs above the cold legs, two each side
    for (const s of [1, -1]) {
      leg(m, `hot_${s}_n`, s, 15, 1.5, 2.4);
      leg(m, `cold_${s}_s`, s, 8, 14.5, 2.4);
      // colour bands by GOST 14202 at the flanges: hot water red-brown edge, cold water green
      const xb = s > 0 ? [29.4, 30.2] : [-14.2, -13.4];
      m.prism(`hot_band_${s}`, 'x', [15, 1.5], 2.55, xb[0], xb[1], 'red', { split: '' });
      m.prism(`cold_band_${s}`, 'x', [8, 14.5], 2.55, xb[0], xb[1], 'green', { split: '' });
    }
    // front: ex-core detector channels up the shield, the sign and the plant number
    for (const x of [-1, 1]) m.box(`ionch_${x}`, [x, -13, -12.4], [x + 1, 20, -11.4], 'st', { split: 'y' });
    m.box('ionch_box', [-2, 10, -13.4], [3, 14, -11.4], 'pnt', { split: '' });
    m.decal('sign', [5, 3, -13.5], [11, 9, -12.8], 'pnt', 'north', 'pl.sign');
    m.decal('name', [3.9, 11, -13.5], [12.1, 13.6, -12.8], 'pnt', 'north', 'pl.name');
    m.decal('number', [16, -15.2, -15.9], [24, -13.6, -15.4], 'cdk', 'north', 'pl.number');
    // plinth front: access hatch into the cavity under the vessel
    m.box('hatch', [3, -15.4, -15.9], [13, -13.2, -15.4], 'pnt', { split: '' });
    m.box('hatch_wheel', [7.4, -14.6, -16], [8.6, -13.8, -15.9], 'red', { split: '' });
    return m;
  }

  function head() {
    const m = mk('pwr_reactor/paint');
    // the rest of the head, stepping in like the ellipse it is
    m.cylY('dome_0', C, 11.6, -16, -12, 'clad', { seed: 3 });
    m.cylY('dome_1', C, 10, -12, -10, 'clad', { seed: 5, cap: 'pwr_reactor/flange' });
    // upper unit frame: support ring, eight posts, a middle band and the deck ring
    m.ringY('frame_ring_0', C, 14.2, 1.6, -12, -10, 'pnt');
    m.ringY('frame_ring_1', C, 14.2, 1, 4, 5.4, 'pnt');
    for (let k = 0; k < 8; k++) m.postY(`post_${k}`, C, 13.6, k * 45, 1.4, -10, 19, 'pnt');
    // drive housings with their electromagnet blocks and position indicator tops
    let n = 0;
    for (const dx of [-9, -4.5, 0, 4.5, 9]) for (const dz of [-9, -4.5, 0, 4.5, 9]) {
      if (Math.abs(dx) === 9 && Math.abs(dz) === 9) continue;
      const x = 8 + dx, z = 8 + dz, i = n++;
      m.box(`crd_${i}`, [x - 0.8, -10, z - 0.8], [x + 0.8, 24, z + 0.8], 'st', { split: 'y' });
      m.box(`crd_coil_${i}`, [x - 1.3, 4 + (i % 3), z - 1.3], [x + 1.3, 13 + (i % 3), z + 1.3], 'drv', { split: '' });
      m.box(`crd_top_${i}`, [x - 1.1, 24, z - 1.1], [x + 1.1, 26.4, z + 1.1], 'pnt', { split: '' });
      m.box(`crd_plug_${i}`, [x - 0.5, 26.4, z - 0.5], [x + 0.5, 27, z + 0.5], 'red', { split: '' });
    }
    // cable deck with its rail, cable trays round the edge
    m.cylY('deck', C, 14.6, 19, 20, 'pnt', { cap: 'pwr_reactor/grating', bottom: true });
    m.ringY('deck_rail', C, 14.6, 0.5, 23.4, 24, 'yel');
    for (let k = 0; k < 8; k++) m.postY(`deck_post_${k}`, C, 14.35, k * 45 + 22.5, 0.5, 20, 24, 'yel', { split: '' });
    m.box('tray_n', [1, 20, -4.6], [15, 21.2, -3.4], 'ci', { split: '' });
    m.box('tray_s', [1, 20, 19.4], [15, 21.2, 20.6], 'ci', { split: '' });
    // lifting traverse: three ties to a hub, the crane eye
    for (const a of [90, 225, 315]) m.postY(`tie_${a}`, C, 9, a, 1, 20, 29, 'yel', { split: '' });
    m.box('hub', [5, 28, 5], [11, 30, 11], 'yel', { split: '' });
    m.box('eye', [7, 30, 7.4], [9, 32, 8.6], 'st', { split: '' });
    // drive cooling duct down the west side, fan housing at its foot
    m.box('duct', [-14.4, -6, 4], [-10.4, 18, 12], 'pnt', { split: 'y' });
    m.box('duct_elbow', [-10.4, 14, 5], [-6, 18, 11], 'pnt', { split: '' });
    m.box('fan', [-15.4, -12, 3], [-9.4, -6, 13], 'pnt', { split: '' });
    m.box('fan_motor', [-14.4, -11, -1], [-10.4, -7, 3], 'green', { split: '' });
    return m;
  }

  const BUILDS = {
    pwr_reactor: { model: vessel },
    pwr_reactor_head: { model: head },
  };

  window.PWR = {
    paint, ids: Object.keys(BUILDS),
    build: (root, id) => PCART.realize(root, BUILDS[id].model(), id),
    save: (root, id) => PCART.save(root, 'core', id, BUILDS[id].java),
    preview(root) { return PCART.realize(root, vessel().merge(head(), 0, 48, 0, 'h_'), 'pwr_reactor_preview'); },
  };
})();
