// Main mine fan, hand-authored for Blockbench (replaces the spec_metallurgy.py placeholder).
// Load inside Blockbench desktop, after the shared toolkit:
//   eval(require('fs').readFileSync('<repo>/art/blockbench/lib/pcart.js', 'utf8'))
//   eval(require('fs').readFileSync('<repo>/art/blockbench/mining/main_mine_fan.author.js', 'utf8'))
//   PCART.paintMaterials('<repo>'); MFN.paint('<repo>'); MFN.paintGui('<repo>')
//   await MFN.exportAll('<repo>')
//
// 3x3x3 blocks, model -16..32 on every axis, the inlet to the north. Mine air comes up the fan drift into the
// bellmouth, through the guard and the impeller (a partial turned by MineClient about z through x 8, y 8),
// and turns up into the evase diffuser on top, which slows it and gives back pressure. Behind the casing sits
// the reduction gearbox; the Create shaft comes into it at the back, z 32, on the fan axis. A starter box with
// the run lamp stands at the east side. The fan is a small axial unit, about 1.6 m across the impeller, which
// suits the simulated curve (h0 1200 Pa, Q0 12 m3/s at 300 rpm).
(function () {
  const path = require('path');
  const { rng, img, put, add, mix, grain, writePng } = PCART;
  const TEX_REL = PCART.ASSETS + '/textures/block/main_mine_fan';
  const T = {};
  const C = [8, 8];              // fan axis (x, y)
  const R = 14;                  // casing radius

  // Fan casing sheet, painted mid grey with a green cast, rust at the seams.
  T.casing = () => {
    const im = img(32, 32), r = grain(im, [96, 104, 96], 901, 4, 7, 8);
    for (let y = 0; y < 32; y++) for (let x = 0; x < 32; x++) {
      if (y % 16 === 0) put(im, x, y, [70, 62, 52]);
      else if (r() < 0.02) put(im, x, y, [110, 72, 50]);
    }
    return im;
  };
  // Guard over the inlet, seen face on: concentric rings and four spokes of round bar, clear between.
  T.guard = () => {
    const im = img(32, 32), r = rng(911);
    for (let y = 0; y < 32; y++) for (let x = 0; x < 32; x++) {
      const dx = x + 0.5 - 16, dy = y + 0.5 - 16, d = Math.hypot(dx, dy);
      const ring = d > 15 || Math.abs(d % 3.5 - 1.75) > 1.3, spoke = Math.abs(dx) < 0.7 || Math.abs(dy) < 0.7;
      if (!ring && !spoke || d > 16) { put(im, x, y, [0, 0, 0, 0]); continue; }
      put(im, x, y, add([60, 62, 64], (r() - 0.5) * 12 + (d > 15 ? -10 : 8)));
    }
    return im;
  };
  // Impeller blade: cast aluminium, darker at the root, a leading-edge highlight.
  T.blade = () => {
    const im = img(16, 16), r = rng(921);
    for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
      put(im, x, y, add([150, 154, 156], (x < 2 ? 28 : 0) - y * 1.5 + (r() - 0.5) * 8));
    }
    return im;
  };
  // Nose cone of the hub, seen face on.
  T.nose = () => {
    const im = img(16, 16), r = rng(931);
    for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
      const d = Math.hypot(x + 0.5 - 8, y + 0.5 - 8) / 8;
      put(im, x, y, add([170, 60, 40], (1 - d) * 40 - (d > 0.9 ? 30 : 0) + (r() - 0.5) * 8));
    }
    return im;
  };

  // Run lamp lens: green glass, lit or dark.
  const lens = (on) => () => {
    const im = img(16, 16), r = rng(on ? 951 : 952);
    for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
      const d = Math.hypot(x + 0.5 - 6, y + 0.5 - 6) / 11;
      put(im, x, y, on ? mix([220, 255, 200], [60, 200, 80], Math.min(1, d)) : add([30, 70, 40], -d * 20 + (r() - 0.5) * 6));
    }
    return im;
  };
  T.lamp_on = lens(true);
  T.lamp_off = lens(false);

  const MAT = {
    casing: { tex: 'main_mine_fan/casing', P: 16 }, blade: { tex: 'main_mine_fan/blade', P: 16 },
    lampon: { tex: 'main_mine_fan/lamp_on', P: 16 }, lampoff: { tex: 'main_mine_fan/lamp_off', P: 16 },
  };
  const mk = (particle) => Object.assign(new PCART.Model(MAT, {}), { particle: particle || 'main_mine_fan/casing' });

  function paint(root) {
    for (const n of Object.keys(T)) writePng(T[n](), path.join(root, TEX_REL, n + '.png'));
    return Object.keys(T).length;
  }

  function fan(running) {
    const m = mk();
    // concrete-capped stone plinth with two saddles under the casing
    m.box('plinth', [-16, -16, -16], [32, -14, 32], 'ashlar', { hide: ['down'] });
    for (const z of [-8, 12]) m.box(`saddle_${z}`, [-4, -14, z], [20, -4, z + 3], 'ci', { split: '' });
    // casing between bolted flanges
    m.prism('casing', 'z', C, R, -12, 18, 'casing');
    for (const z of [-12, 2, 17]) m.ring(`flange_${z}`, 'z', C, R + 1, 1.6, z, z + 1, 'ci');
    // bellmouth: a flared lip in two steps, and the guard across the inlet
    m.ring('bell_a', 'z', C, R + 1.2, 1.4, -14, -12, 'casing');
    m.ring('bell_b', 'z', C, R + 2, 1.6, -16, -14, 'casing');
    m.disc('guard', 'z', C, R, -14.6, -14.4, 'ci', 'main_mine_fan/guard', 'north');
    // bend and evase diffuser over the back half of the casing, widening upward
    m.box('bend', [-6, 8, 4], [22, 18, 17], 'casing', { hide: ['down'] });
    m.box('evase_a', [-8, 18, 2], [24, 24, 19], 'casing', { hide: ['down'] });
    m.box('evase_b', [-10, 24, 0], [26, 30, 21], 'casing', { hide: ['down', 'up'] });
    m.box('evase_lip', [-11, 30, -1], [27, 31, 22], 'ci', { hide: ['up'] });
    m.box('evase_mouth', [-10, 30.9, 0], [26, 30.95, 21], 'soot', { hide: ['down'] });
    for (const x of [-9.6, 25]) m.box(`evase_stay_${x}`, [x, 18, 9.5], [x + 0.6, 30, 10.5], 'st', { split: '' });
    // back cover, gearbox on its pedestal and the coupling to the Create shaft
    m.disc('back_cover', 'z', C, R, 18, 19, 'casing', 'main_mine_fan/nose', 'south');
    m.box('gear_pedestal', [1, -14, 20], [15, 0, 30], 'ci');
    m.box('gearbox', [1, 0, 20], [15, 15, 30], 'green');
    for (let y = 2; y < 14; y += 2.5) m.box(`gear_fin_${y}`, [0.4, y, 21], [15.6, y + 0.6, 29], 'green', { split: '' });
    m.box('gear_cover', [2, 15, 21], [14, 16, 29], 'ci', { split: '' });
    m.box('gear_breather', [7.4, 16, 24], [8.6, 17.4, 25.2], 'br', { split: '' });
    m.box('gear_dipstick', [12, 16, 27], [12.4, 18, 27.4], 'bright', { split: '' });
    m.prism('coupling', 'z', C, 2.6, 30, 31, 'ci', { split: '', ends: ['north', 'south'] });
    m.prism('stub', 'z', C, 1.2, 31, 32, 'bright', { split: '', ends: ['south'] });
    m.prism('fan_shaft', 'z', C, 1.4, 19, 20, 'bright', { split: '' });
    // starter with the run lamp at the east side
    m.box('starter', [24, -14, -12], [31, 2, -4], 'green');
    m.box('starter_door', [23.6, -12, -11], [24, 0, -5], 'green', { split: '' });
    m.box('starter_handle', [23, -7, -6], [23.6, -4, -5.4], 'bright', { split: '' });
    m.box('starter_switch', [27, -2, -12.6], [28.2, 1, -12], 'red', { split: '' });
    m.box(running ? 'glow_lamp_run' : 'lamp_run', [26.6, 2, -9], [28.4, 3.6, -7.2], running ? 'lampon' : 'lampoff', { split: '' });
    m.box('lamp_guard', [26.4, 3.6, -9.2], [28.6, 4, -7], 'st', { split: '' });
    m.box('cable_conduit', [26.5, -14, -4], [28.5, -12, 20], 'st', { split: 'z' });
    return m;
  }

  // Impeller, turned by MineClient about z through (8, 8): nose cone, hub and eight blades.
  function impeller() {
    const m = mk('main_mine_fan/blade');
    m.disc('nose', 'z', C, 4, -11, -9, 'ci', 'main_mine_fan/nose', 'north');
    m.prism('hub', 'z', C, 4, -9, -2, 'ci', { split: '' });
    const L = 9, W = 3.2, z0 = -7.6, z1 = -5.6;
    // up, down, east and west blades are axis-aligned; the diagonals are the vertical ones turned 45 degrees
    m.box('blade_up', [C[0] - W / 2, C[1] + 4, z0], [C[0] + W / 2, C[1] + 4 + L, z1], 'blade', { split: '' });
    m.box('blade_down', [C[0] - W / 2, C[1] - 4 - L, z0], [C[0] + W / 2, C[1] - 4, z1], 'blade', { split: '' });
    m.box('blade_e', [C[0] + 4, C[1] - W / 2, z0], [C[0] + 4 + L, C[1] + W / 2, z1], 'blade', { split: '' });
    m.box('blade_w', [C[0] - 4 - L, C[1] - W / 2, z0], [C[0] - 4, C[1] + W / 2, z1], 'blade', { split: '' });
    for (const a of [45, -45]) {
      const rot = { axis: 'z', angle: a, origin: [C[0], C[1], z0] };
      m.box(`blade_d_up_${a}`, [C[0] - W / 2, C[1] + 4, z0], [C[0] + W / 2, C[1] + 4 + L, z1], 'blade', { split: '', rot });
      m.box(`blade_d_down_${a}`, [C[0] - W / 2, C[1] - 4 - L, z0], [C[0] + W / 2, C[1] - 4, z1], 'blade', { split: '', rot });
    }
    return m;
  }

  // Local panel background for MinePanels.FAN (208x108): a fan-house board, painted steel with a brass rim,
  // recesses behind the two dials and the water gauge. Keep in step with the layout.
  function guiFan() {
    const W = 208, H = 108, im = img(W, H), r = rng(941);
    grain(im, [70, 78, 72], 942, 3, 5, 12);
    for (let y = 0; y < H; y++) for (let x = 0; x < W; x++) {
      const d = Math.min(x, y, W - 1 - x, H - 1 - y);
      if (d < 4) put(im, x, y, add([170, 134, 70], (d === 0 ? -50 : d === 1 ? 30 : d === 3 ? -30 : 0) + (r() - 0.5) * 8));
    }
    for (const [cx, cy] of [[32, 48], [88, 48]]) for (let a = 0; a < 360; a += 0.5) {
      const t = a * Math.PI / 180;
      put(im, cx + 26 * Math.cos(t), cy + 26 * Math.sin(t), [36, 40, 36]);
      put(im, cx + 27 * Math.cos(t) + 0.5, cy + 27 * Math.sin(t) + 0.5, [110, 118, 110]);
    }
    // niche behind the water gauge (MinePanels.FAN utube at 122, 24, 24x56, label below)
    for (let y = 21; y < 90; y++) for (let x = 119; x < 149; x++) {
      const edge = x === 119 || y === 21 ? -24 : x === 148 || y === 89 ? 20 : 0;
      put(im, x, y, add([58, 64, 60], edge + (r() - 0.5) * 4));
    }
    return im;
  }

  function paintGui(root) {
    writePng(guiFan(), path.join(root, PCART.ASSETS, 'textures/gui/panel/main_mine_fan.png'));
    return 1;
  }

  const BUILDS = {
    main_mine_fan: { model: () => fan(false) },
    main_mine_fan_running: { model: () => fan(true) },
    'main_mine_fan/impeller': { model: impeller, java: 'main_mine_fan/partial/impeller' },
  };

  window.MFN = {
    paint,
    paintGui,
    build: (root, id) => PCART.realize(root, BUILDS[id].model(), id),
    exportAll: (root) => PCART.exportAll(root, 'mining', BUILDS),
    preview(root) {
      return PCART.realize(root, fan(true).merge(impeller(), 0, 0, 0, 'imp_'), 'main_mine_fan_preview');
    },
  };
})();
