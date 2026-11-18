// Two-leg shield powered roof support (one section of a longwall face), hand-authored for Blockbench
// (replaces the spec_visual.py placeholder).
// Load inside Blockbench desktop, after the shared toolkit:
//   eval(require('fs').readFileSync('<repo>/art/blockbench/lib/pcart.js', 'utf8'))
//   eval(require('fs').readFileSync('<repo>/art/blockbench/mining/powered_roof_support.author.js', 'utf8'))
//   PCART.paintMaterials('<repo>'); PRS.paint('<repo>')
//   await PRS.exportAll('<repo>')
//
// 1x3x2 blocks, model x 0..16, y -16..32, z -4..32 (controller in the middle block). North looks at the face:
// the canopy tip reaches out over the travelling way toward the shearer, the caving shield closes the back
// against the goaf. Two base pontoons stand on the floor, joined at the back where the lemniscate links
// guide the shield; between them the relay bar and advancing ram run forward to the face conveyor. Two
// hydraulic legs (the legs partial, which SupportRenderer lowers by LEG_TRAVEL_PX when the section is
// released) press the canopy to the roof. The control valve bank with its levers and leg pressure gauge hangs
// between the legs facing the travelling way.
(function () {
  const path = require('path');
  const { rng, img, put, add, mix, rect, grain, writePng } = PCART;
  const TEX_REL = PCART.ASSETS + '/textures/block/powered_roof_support';
  const T = {};
  const LX = [3.75, 12.25], LZ = 9;   // leg centres

  // Shield paint, yellow under coal and roof dust; the top faces of the tile darker with settled dust.
  T.paint = () => {
    const im = img(32, 32), r = grain(im, [206, 168, 40], 1201, 3, 6, 8);
    const streak = []; for (let x = 0; x < 32; x++) streak.push(r() < 0.3 ? 0.1 + r() * 0.14 : r() * 0.05);
    for (let y = 0; y < 32; y++) for (let x = 0; x < 32; x++) {
      const i = (y * 32 + x) * 4, c = [im.d[i], im.d[i + 1], im.d[i + 2]];
      let out = mix(c, [52, 48, 40], 0.16 + streak[x] + Math.max(0, (y - 20) / 12) * 0.2);
      if (r() < 0.015) out = [118, 110, 96];
      put(im, x, y, out);
    }
    return im;
  };
  // Hydraulic hose, black rubber with a yellow lay line.
  T.hose = () => { const im = img(32, 32); grain(im, [30, 30, 32], 1211, 3, 4, 8); for (let y = 0; y < 32; y += 8) rect(im, 0, y, 32, 1, [170, 140, 40]); return im; };
  // Leg pressure gauge, 0..60 MPa; red sector from the yield setting, needle at a set load.
  T.gauge = () => {
    const im = img(32, 32), r = rng(1221), C = 16;
    for (let y = 0; y < 32; y++) for (let x = 0; x < 32; x++) {
      const d = Math.hypot(x + 0.5 - C, y + 0.5 - C);
      put(im, x, y, d > 14.6 ? [60, 62, 64] : d > 13.6 ? [30, 30, 30] : add([228, 222, 204], (r() - 0.5) * 5));
    }
    const ray = (f, r0, r1, col) => {
      const a = (225 - f * 270) * Math.PI / 180;
      for (let s = r0; s <= r1; s += 0.35) put(im, C - 0.5 + s * Math.cos(a), C - 0.5 - s * Math.sin(a), col);
    };
    for (let k = 0; k <= 12; k++) ray(k / 12, k % 2 ? 12 : 10.8, 12.9, [40, 38, 36]);
    for (let f = 0.72; f <= 1; f += 0.02) ray(f, 11.6, 12.9, [196, 34, 28]);
    ray(0.55, 0, 10, [30, 28, 26]);
    return im;
  };

  const MAT = {
    yp: { tex: 'powered_roof_support/paint', P: 16 }, hs: { tex: 'powered_roof_support/hose', P: 16 },
    hz: { tex: 'longwall_shearer/hazard', P: 16 }, dk: { tex: 'longwall_shearer/dark', P: 16 },
  };
  const mk = (particle) => Object.assign(new PCART.Model(MAT, {}), { particle: particle || 'powered_roof_support/paint' });

  function paint(root) {
    for (const n of Object.keys(T)) writePng(T[n](), path.join(root, TEX_REL, n + '.png'));
    return Object.keys(T).length;
  }

  function section() {
    const m = mk();
    // base: two pontoons with their toes chamfered toward the face, the rear bridge and leg sockets
    for (const [n, a, b] of [['w', 1, 6.5], ['e', 9.5, 15]]) {
      m.box(`pontoon_${n}`, [a, -16, 1.5], [b, -11, 30], 'yp');
      m.box(`pontoon_${n}_toe`, [a, -16, -0.2], [b, -13, 3], 'hz', { rot: { axis: 'x', angle: -22.5, origin: [8, -16, 1.5] } });
      m.box(`pontoon_${n}_rib`, [a + 0.6, -11, 4], [b - 0.6, -10.4, 28], 'yp', { split: 'z' });
    }
    m.box('base_bridge', [6.5, -15.6, 20], [9.5, -11.6, 30], 'yp', { split: '' });
    for (const x of LX) {
      m.prismY(`leg_socket_${x}`, [x, LZ], 2.9, -11, -9.6, 'dk', { ends: ['up'] });
    }
    // relay bar and advancing ram between the pontoons, forward to the conveyor connector
    m.box('relay_bar', [6.9, -15.8, -3.6], [9.1, -13.4, 18], 'dk', { split: 'z' });
    m.box('relay_head', [6.2, -16, -4], [9.8, -12.6, -2.4], 'dk', { split: '' });
    m.prism('push_ram', 'z', [8, -12.6], 1.1, 0, 17, 'yp', { split: '', ends: ['north'] });
    m.prism('push_rod', 'z', [8, -12.6], 0.6, -2.4, 0, 'bright', { split: '' });
    // canopy: roof bar with side shields, a tapered tip over the travelling way and the hinge to the shield
    m.box('canopy', [0, 28, -1.5], [16, 31.4, 24.4], 'yp');
    m.box('canopy_tip', [0.6, 28.6, -4], [15.4, 31, -1.5], 'yp', { split: '' });
    m.box('canopy_tip_band', [0.6, 28.4, -4.1], [15.4, 28.6, -1.5], 'hz', { split: '' });
    for (const [n, a, b] of [['w', 0, 0.6], ['e', 15.4, 16]]) {
      m.box(`side_shield_${n}`, [a, 25.4, 0], [b, 28, 22], 'yp', { split: 'z' });
    }
    m.box('canopy_web_w', [2, 26.6, 0], [3, 28, 21.4], 'yp', { split: 'z' });
    m.box('canopy_web_e', [13, 26.6, 0], [14, 28, 21.4], 'yp', { split: 'z' });
    for (const x of LX) m.prismY(`leg_cap_${x}`, [x, LZ], 2.4, 26, 28, 'dk', { ends: ['down'] });
    m.prism('hinge', 'x', [27.4, 23], 1.6, 1, 15, 'dk', { split: '', ends: ['east', 'west'] });
    // nozzle bar for dust suppression under the canopy tip
    m.box('spray_bar', [2, 27.6, -1.2], [14, 28.2, -0.6], 'bright', { split: '' });
    for (let x = 3; x <= 13; x += 2.5) m.box(`spray_${x}`, [x - 0.25, 27.1, -1.15], [x + 0.25, 27.6, -0.65], 'br', { split: '' });
    // caving shield, sloping down and back from the hinge, with stiffening ribs on its goaf face
    const tilt = { axis: 'x', angle: -22.5, origin: [8, 27.4, 23] };
    m.box('shield', [0.6, 6, 21.6], [15.4, 27.4, 24.4], 'yp', { rot: tilt, split: '' });
    for (const x of [2.4, 7.4, 12.4]) m.box(`shield_rib_${x}`, [x, 7, 24.4], [x + 1.2, 26, 25.4], 'yp', { rot: tilt, split: '' });
    // lemniscate links from the shield foot down to the base bridge: two long outer links and a short inner pair
    const link = { axis: 'x', angle: 22.5, origin: [8, -1, 27] };
    m.box('link_w', [2, -10, 25.6], [4.6, 8, 28.4], 'dk', { rot: link, split: '' });
    m.box('link_e', [11.4, -10, 25.6], [14, 8, 28.4], 'dk', { rot: link, split: '' });
    m.box('link_inner', [6, -11, 26.6], [10, -3, 29.4], 'dk', { rot: link, split: '' });
    for (const [y, z] of [[7.4, 30.6], [-9.4, 23.4]]) {
      m.prism(`pin_${y}`, 'x', [y, z], 0.9, 1.6, 14.4, 'bright', { split: '', ends: ['east', 'west'] });
    }
    // control valve bank between the legs facing the travelling way: four levers and the leg pressure gauge
    m.box('valve_bank', [5, -6, 4.2], [11, -2, 7.2], 'dk', { split: '' });
    const levers = [['red', 6], ['bright', 7.4], ['bright', 8.8], ['green', 10.2]];
    for (const [mat, x] of levers) {
      m.box(`lever_${x}`, [x - 0.3, -5, 3.2], [x + 0.3, -2.6, 4.2], mat, { split: '' });
      m.box(`lever_knob_${x}`, [x - 0.45, -2.6, 3.05], [x + 0.45, -1.8, 3.95], 'dk', { split: '' });
    }
    m.box('gauge_stem', [7.7, -2, 4.7], [8.3, -0.8, 5.1], 'br', { split: '' });
    m.box('gauge_case', [6.6, -0.8, 4.6], [9.4, 2, 5.2], 'ci', { split: '' });
    m.plate('gauge_face', [6.8, -0.6, 4.5], [9.2, 1.8, 4.6], 'ci', 'north', 'powered_roof_support/gauge', { hide: ['south'] });
    // hoses from the valve bank to both legs and down to the pressure and return mains along the face
    for (const x of LX) {
      const a = Math.min(x, 8), b = Math.max(x, 8);
      m.box(`hose_leg_${x}`, [a, -4.2, 6.2], [b, -3.6, 6.8], 'hs', { split: '' });
    }
    m.box('hose_main_p', [0, -15.2, 14], [16, -14.4, 14.8], 'hs', { split: 'x' });
    m.box('hose_main_r', [0, -15.2, 15.2], [16, -14.4, 16], 'hs', { split: 'x' });
    m.box('hose_drop', [9.6, -14.4, 6.4], [10.2, -6, 7], 'hs', { split: '' });
    return m;
  }

  // Legs partial, lowered by the renderer when the section is released: cylinder, collar and chromed rod.
  function legs() {
    const m = mk();
    for (const x of LX) {
      m.prismY(`cyl_${x}`, [x, LZ], 2.2, -10.6, 9, 'yp', { seed: x });
      m.prismY(`collar_${x}`, [x, LZ], 2.6, 9, 10.4, 'dk', { ends: ['up', 'down'] });
      m.prismY(`rod_${x}`, [x, LZ], 1.4, 10.4, 26, 'bright');
      m.box(`port_${x}`, [x - 0.5, -6, LZ - 2.8], [x + 0.5, -5, LZ - 2.1], 'br', { split: '' });
    }
    return m;
  }

  const BUILDS = {
    powered_roof_support: { model: section },
    'powered_roof_support/legs': { model: legs, java: 'powered_roof_support/partial/legs' },
  };

  window.PRS = {
    paint,
    build: (root, id) => PCART.realize(root, BUILDS[id].model(), id),
    exportAll: (root) => PCART.exportAll(root, 'mining', BUILDS),
    preview(root) {
      return PCART.realize(root, section().merge(legs(), 0, 0, 0, 'lg_'), 'powered_roof_support_preview');
    },
  };
})();
