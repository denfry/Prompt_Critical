// Multistage sectional mine drainage pump (TsNS type), hand-authored for Blockbench (replaces the
// spec_visual.py placeholder).
// Load inside Blockbench desktop, after the shared toolkit:
//   eval(require('fs').readFileSync('<repo>/art/blockbench/lib/pcart.js', 'utf8'))
//   eval(require('fs').readFileSync('<repo>/art/blockbench/mining/mine_drainage_pump.author.js', 'utf8'))
//   PCART.paintMaterials('<repo>'); MDP.paint('<repo>'); MDP.paintGui('<repo>')
//   await MDP.exportAll('<repo>')
//
// 2x1x1 blocks, model x 0..32, y 0..16, z 0..16. The shaft runs along x at y 8, z 8; the Create shaft comes
// in from the west face through a pin coupling (in the shaft partial, which FoundryMachineRenderer spins about
// x). From the west: the drive-end bearing, the suction casing with its branch up, five stage sections drawn
// together by four tie bolts, the discharge casing with its branch up through a non-return valve and a gate
// valve to the rising main on the top face, and the balance-end bearing with its leak-off pipe. A priming
// funnel with a cock sits on the suction casing (a bucket of water primes a new pump), and a pressure gauge
// on the discharge.
(function () {
  const path = require('path');
  const { rng, img, put, add, mix, grain, writePng } = PCART;
  const TEX_REL = PCART.ASSETS + '/textures/block/mine_drainage_pump';
  const T = {};
  const AY = 8, AZ = 8;

  // Pump casing, cast iron painted blue-grey, mine-dirty toward the bottom.
  T.casing = () => {
    const im = img(32, 32), r = grain(im, [70, 86, 102], 1001, 4, 6, 8);
    for (let y = 0; y < 32; y++) for (let x = 0; x < 32; x++) {
      const i = (y * 32 + x) * 4, c = [im.d[i], im.d[i + 1], im.d[i + 2]];
      put(im, x, y, mix(c, [74, 62, 48], Math.max(0, (y - 20) / 12) * 0.45 + (r() < 0.03 ? 0.4 : 0)));
    }
    return im;
  };
  // Discharge pressure gauge, 0..2.5 MPa, needle at rest under the static head of a typical rising main.
  T.gauge = () => {
    const im = img(32, 32), r = rng(1011), C = 16;
    for (let y = 0; y < 32; y++) for (let x = 0; x < 32; x++) {
      const d = Math.hypot(x + 0.5 - C, y + 0.5 - C);
      put(im, x, y, d > 14.6 ? [60, 62, 64] : d > 13.6 ? [30, 30, 30] : add([228, 222, 204], (r() - 0.5) * 5));
    }
    const ray = (f, r0, r1, col) => {
      const a = (225 - f * 270) * Math.PI / 180;
      for (let s = r0; s <= r1; s += 0.35) put(im, C - 0.5 + s * Math.cos(a), C - 0.5 - s * Math.sin(a), col);
    };
    for (let k = 0; k <= 10; k++) ray(k / 10, k % 2 ? 12 : 10.8, 12.9, [40, 38, 36]);
    for (let f = 0.8; f <= 1; f += 0.02) ray(f, 11.6, 12.9, [196, 34, 28]);
    ray(0.6, 0, 10, [30, 28, 26]);
    return im;
  };

  const MAT = { pc: { tex: 'mine_drainage_pump/casing', P: 16 } };
  const mk = (particle) => Object.assign(new PCART.Model(MAT, {}), { particle: particle || 'mine_drainage_pump/casing' });

  function paint(root) {
    for (const n of Object.keys(T)) writePng(T[n](), path.join(root, TEX_REL, n + '.png'));
    return Object.keys(T).length;
  }

  // Pedestal bearing with a split housing on the shaft line.
  function bearing(m, n, x0, x1) {
    m.box(`${n}_foot`, [x0 - 0.5, 1.5, 4], [x1 + 0.5, 2.5, 12], 'pc', { split: '' });
    m.box(`${n}_body`, [x0, 2.5, 5], [x1, 8, 11], 'pc', { split: '' });
    m.box(`${n}_cap`, [x0 + 0.2, 8, 5.4], [x1 - 0.2, 11, 10.6], 'pc', { split: '' });
    m.box(`${n}_oiler`, [(x0 + x1) / 2 - 0.5, 11, 7.5], [(x0 + x1) / 2 + 0.5, 12.4, 8.5], 'br', { split: '' });
  }

  // Branch going up from a casing to the top face, with flanges; `cap` is the top flange height.
  function branch(m, n, x, r, y0, y1) {
    m.prismY(`${n}_pipe`, [x, AZ], r, y0, y1, 'pc');
    m.prismY(`${n}_flange`, [x, AZ], r + 1, y1 - 1, y1, 'pc', { ends: ['up', 'down'] });
  }

  function pump() {
    const m = mk();
    // fabricated bedplate with drip lip
    m.box('bed', [0, 0, 1.5], [32, 1.5, 14.5], 'ci', { split: 'x' });
    m.box('bed_lip_n', [0, 1.5, 1.5], [32, 2, 2], 'ci', { split: 'x' });
    m.box('bed_lip_s', [0, 1.5, 14], [32, 2, 14.5], 'ci', { split: 'x' });
    bearing(m, 'bearing_de', 3.5, 7);
    bearing(m, 'bearing_be', 28.5, 31.5);
    // suction casing, stage sections, discharge casing
    m.prism('suction_casing', 'x', [AY, AZ], 5.8, 7.5, 11, 'pc', { ends: ['west'] });
    for (let i = 0; i < 5; i++) {
      const x0 = 11 + i * 3;
      m.prism(`stage_${i}`, 'x', [AY, AZ], 5.2, x0, x0 + 2.6, 'pc', { split: '' });
      m.prism(`stage_${i}_rim`, 'x', [AY, AZ], 5.6, x0 + 2.6, x0 + 3, 'pc', { split: '', ends: ['east', 'west'] });
    }
    m.prism('discharge_casing', 'x', [AY, AZ], 5.8, 26, 28.5, 'pc', { ends: ['east'] });
    for (const [x0, x1] of [[7.5, 11], [26, 28.5]]) {
      m.box(`casing_foot_${x0}`, [x0 + 0.3, 1.5, 3], [x1 - 0.3, 3.5, 13], 'pc', { split: '' });
    }
    // four tie bolts with nuts
    for (const [y, z] of [[13, 3], [13, 12.4], [3, 3], [3, 12.4]]) {
      m.box(`tie_${y}_${z}`, [7.2, y, z], [28.8, y + 0.6, z + 0.6], 'bright', { split: 'x' });
      for (const x of [6.6, 28.8]) m.box(`tie_nut_${y}_${z}_${x}`, [x, y - 0.2, z - 0.2], [x + 0.6, y + 0.8, z + 0.8], 'st', { split: '' });
    }
    // suction branch up, closed with a blank: the sump suction comes in under the floor [the block takes
    // water from the mine network, not from a pipe]
    branch(m, 'suction', 9.2, 2.2, 13.8, 16);
    m.prismY('suction_neck', [9.2, AZ], 2.2, 13, 13.8, 'pc');
    // priming funnel and cock on the suction casing
    m.box('prime_pipe', [10.6, 13, 11.4], [11.2, 14.4, 12], 'br', { split: '' });
    m.box('prime_cock', [10.2, 14.4, 11], [11.6, 15, 12.4], 'br', { split: '' });
    m.box('prime_funnel', [9.9, 15, 10.7], [11.9, 16, 12.7], 'br', { split: '' });
    // discharge: non-return valve body, gate valve with handwheel and the flange to the rising main on top
    m.prismY('discharge_neck', [24, AZ], 2.6, 13, 14, 'pc');
    m.box('nrv_body', [21.4, 13.6, 5.6], [26.6, 15, 10.4], 'pc', { split: '' });
    m.box('nrv_cover', [22.4, 15, 6.4], [25.6, 15.6, 9.6], 'pc', { split: '' });
    m.prismY('discharge_flange', [24, AZ], 3.4, 15.6, 16, 'pc', { ends: ['up'] });
    m.box('gate_spindle', [23.7, 13, 2.4], [24.3, 13.6, 5.6], 'bright', { split: '' });
    m.box('gate_wheel_h', [21.8, 12.6, 1.6], [26.2, 13.4, 2.4], 'red', { split: '' });
    m.box('gate_wheel_v', [23.6, 11, 1.6], [24.4, 15, 2.4], 'red', { split: '' });
    // pressure gauge on a siphon off the discharge casing
    m.box('gauge_siphon', [28.6, 12, 11], [29.2, 14.6, 11.6], 'br', { split: '' });
    m.box('gauge_case', [27.4, 14.2, 11.6], [30.4, 17.2, 12.2], 'ci', { split: '' });
    m.plate('gauge_face', [27.6, 14.4, 12.2], [30.2, 17, 12.3], 'ci', 'south', 'mine_drainage_pump/gauge', { hide: ['north'] });
    // leak-off from the balance chamber, down to the drip lip
    m.box('leakoff', [29.6, 3, 12.6], [30.2, 6, 13.2], 'br', { split: '' });
    m.box('leakoff_down', [29.6, 2, 13.2], [30.2, 3, 13.8], 'br', { split: '' });
    // coupling guard over the drive end
    m.box('guard_top', [0, 11, 3.5], [3.5, 11.5, 12.5], 'red', { split: '' });
    for (const z of [3.5, 12]) m.box(`guard_side_${z}`, [0, 2, z], [3.5, 11, z + 0.5], 'red', { split: '' });
    return m;
  }

  // Shaft partial, spun about x through (y 8, z 8): the half of the pin coupling on the pump side, the shaft
  // through the bearing and the nut at the balance end.
  function shaft() {
    const m = mk('material/bright_steel');
    m.prism('shaft_de', 'x', [AY, AZ], 1.1, 0, 7.5, 'bright', { split: '', ends: ['west'] });
    m.prism('coupling', 'x', [AY, AZ], 3.2, 1, 3, 'ci', { split: '', ends: ['east', 'west'] });
    for (const [dy, dz] of [[2.2, 0], [-2.2, 0], [0, 2.2], [0, -2.2]]) {
      m.box(`pin_${dy}_${dz}`, [0.5, AY + dy - 0.4, AZ + dz - 0.4], [1, AY + dy + 0.4, AZ + dz + 0.4], 'bright', { split: '' });
    }
    m.prism('shaft_be', 'x', [AY, AZ], 1.1, 28.5, 32, 'bright', { split: '', ends: ['east'] });
    m.prism('nut_be', 'x', [AY, AZ], 1.6, 31.2, 31.9, 'st', { split: '', ends: ['east', 'west'] });
    return m;
  }

  // Local panel background for MinePanels.DRAINAGE (232x108): a pump-house board, three dial rings, a panel
  // for the lamps. Keep in step with the layout.
  function guiPump() {
    const W = 232, H = 108, im = img(W, H), r = rng(1021);
    grain(im, [64, 78, 92], 1022, 3, 5, 12);
    for (let y = 0; y < H; y++) for (let x = 0; x < W; x++) {
      const d = Math.min(x, y, W - 1 - x, H - 1 - y);
      if (d < 4) put(im, x, y, add([170, 134, 70], (d === 0 ? -50 : d === 1 ? 30 : d === 3 ? -30 : 0) + (r() - 0.5) * 8));
    }
    for (const cx of [32, 88, 144]) for (let a = 0; a < 360; a += 0.5) {
      const t = a * Math.PI / 180;
      put(im, cx + 26 * Math.cos(t), 48 + 26 * Math.sin(t), [34, 42, 50]);
      put(im, cx + 27 * Math.cos(t) + 0.5, 48 + 27 * Math.sin(t) + 0.5, [104, 118, 132]);
    }
    for (let y = 21; y < 90; y++) for (let x = 174; x < W - 6; x++) {
      const edge = x === 174 || y === 21 ? -22 : x === W - 7 || y === 89 ? 18 : 0;
      put(im, x, y, add([54, 66, 78], edge + (r() - 0.5) * 4));
    }
    return im;
  }

  function paintGui(root) {
    writePng(guiPump(), path.join(root, PCART.ASSETS, 'textures/gui/panel/mine_drainage_pump.png'));
    return 1;
  }

  const BUILDS = {
    mine_drainage_pump: { model: pump },
    'mine_drainage_pump/shaft': { model: shaft, java: 'mine_drainage_pump/partial/shaft' },
  };

  window.MDP = {
    paint,
    paintGui,
    build: (root, id) => PCART.realize(root, BUILDS[id].model(), id),
    exportAll: (root) => PCART.exportAll(root, 'mining', BUILDS),
    preview(root) {
      return PCART.realize(root, pump().merge(shaft(), 0, 0, 0, 'sh_'), 'mine_drainage_pump_preview');
    },
  };
})();
