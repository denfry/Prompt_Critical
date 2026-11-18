// Rig and field pieces of I2, hand-authored for Blockbench (replace the spec_oilgas.py and spec_ops.py
// placeholders): the mud tank in three levels, the driller's console (off and on), the seismic recorder (off and
// on) and the flare tip (cold and lit).
// Load inside Blockbench desktop, after the shared toolkit:
//   eval(require('fs').readFileSync('<repo>/art/blockbench/lib/pcart.js', 'utf8'))
//   eval(require('fs').readFileSync('<repo>/art/blockbench/drilling/rig_floor.author.js', 'utf8'))
//   PCART.paintMaterials('<repo>'); RIG.paint('<repo>')
//   build + save each model in turn (RIG.build / RIG.save, ids in RIG.ids)
//
// Front to the north.
// mud_tank 1x1x1: an open-top steel tank with outside stiffeners in rig red, an expanded-metal grating on top
//   (cutout: the mud shows through), the agitator gearbox and motor on the grating, a mud gun, the suction
//   at the foot. Mud surface y = 1 + 1.8 * level (empty 0, normal 4, full 7).
// drillers_console 1x2x1 (y 0..32): the pressurised cabinet of the driller's station on a pedestal, the
//   instrument face with the weight indicator (Martin-Decker dial), the pump stroke counters and the torque
//   gauge, a row of lamps (lit in _on), the drawworks brake lever and the throttles on the desk, a sun hood.
// seismic_recorder 1x1x1: a field recorder in a hard case with the lid open, the screen and the paper
//   monitor, the keypad, the power lamp (lit in _on), the cable reel of the spread on the side.
// flare_tip 1x1x1: the tip barrel in heat-tinted stainless with the flame retention ring, the steam ring
//   with its injection nozzles, three pilots with their flame-front ignition lines; in _lit the pilot flames glow.
(function () {
  const path = require('path');
  const { rng, img, put, add, mix, rect, grain, writePng } = PCART;
  const T = {};

  // Rig red (the usual drilling contractor colour), scuffed and mud-splashed low down.
  T['rig_floor/rig_red'] = () => {
    const im = img(32, 32), r = grain(im, [150, 46, 36], 3901, 3, 6, 8);
    for (let k = 0; k < 30; k++) put(im, r() * 32, 22 + r() * 10, [98, 84, 70]);
    for (let k = 0; k < 10; k++) put(im, r() * 32, r() * 32, [70, 64, 60]);
    return im;
  };
  // Expanded-metal grating: diamond holes (cutout) in galvanised strands.
  T['rig_floor/grating'] = () => {
    const im = img(16, 16), r = rng(3911);
    for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
      const u = (x + 2 * (y % 4 < 2 ? 0 : 1)) % 4, v = y % 4;
      if (!(u === 0 || v === 0)) continue;
      put(im, x, y, add([150, 154, 156], (r() - 0.5) * 18));
    }
    return im;
  };
  // Drilling mud surface: water-based bentonite, grey-brown, with bubbles and sheen.
  T['rig_floor/mud'] = () => {
    const im = img(16, 16), r = grain(im, [110, 98, 84], 3921, 4, 8, 4);
    for (let k = 0; k < 6; k++) { const x = r() * 15, y = r() * 15; put(im, x, y, [150, 140, 126]); put(im, x + 1, y, [80, 72, 62]); }
    return im;
  };
  // Driller's instrument face (32 px over 16 units): weight indicator dial left, two stroke counters, torque gauge.
  T['rig_floor/console_face'] = () => {
    const im = img(32, 32), r = grain(im, [60, 64, 66], 3931, 2, 3, 8);
    const dial = (cx, cy, R, face, needleA) => {
      for (let y = -R - 1; y <= R + 1; y++) for (let x = -R - 1; x <= R + 1; x++) {
        const d = Math.hypot(x, y);
        if (d <= R) put(im, cx + x, cy + y, d > R - 1 ? [176, 144, 80] : face);
      }
      for (let k = 0; k < 12; k++) { const a = k * Math.PI / 6; put(im, cx + (R - 2) * Math.cos(a), cy + (R - 2) * Math.sin(a), [30, 30, 30]); }
      for (let s = 0; s < R - 2; s += 0.5) put(im, cx + s * Math.cos(needleA), cy + s * Math.sin(needleA), [190, 30, 26]);
    };
    dial(9, 10, 7, [232, 228, 210], -0.6);
    dial(25, 8, 4, [232, 228, 210], 2.2);
    for (const x of [19, 26]) { rect(im, x, 16, 6, 4, [20, 20, 20]); for (let k = 0; k < 4; k++) rect(im, x + 0.5 + k * 1.4, 17, 1, 2, [220, 200, 120]); }
    rect(im, 3, 22, 26, 3, [226, 222, 200]);
    for (let x = 4; x < 28; x += 3) put(im, x, 23, [60, 60, 60]);
    return im;
  };
  // Recorder screen with the shot record (wiggle traces) and the hard case in olive.
  T['rig_floor/recorder_screen'] = () => {
    const im = img(16, 16), r = rng(3941);
    for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) put(im, x, y, x === 0 || y === 0 || x === 15 || y === 15 ? [30, 30, 30] : [214, 216, 204]);
    for (let tr = 2; tr < 14; tr += 2) for (let y = 2; y < 14; y++) {
      const first = 3 + tr * 0.6, a = y > first ? Math.sin(y * 1.7 + tr) * Math.exp(-(y - first) / 4) * 1.5 : 0;
      put(im, tr + a, y, [30, 30, 40]);
    }
    return im;
  };
  T['rig_floor/olive'] = () => { const im = img(32, 32), r = grain(im, [92, 96, 62], 3951, 3, 5, 8); return im; };
  // Flare tip: 310 stainless after years of flame — straw and blue heat tints toward the top of a tile, soot.
  T['rig_floor/flare_steel'] = () => {
    const im = img(32, 32), r = rng(3961), n = PCART.vnoise(r, 32, 32, 8);
    for (let y = 0; y < 32; y++) for (let x = 0; x < 32; x++) {
      const t = 1 - y / 31;
      let c = t < 0.4 ? mix([150, 146, 140], [176, 150, 96], t / 0.4) : t < 0.75 ? mix([176, 150, 96], [96, 90, 140], (t - 0.4) / 0.35) : mix([96, 90, 140], [40, 38, 40], (t - 0.75) / 0.25);
      if (n(x, y) > 0.66) c = mix(c, [36, 34, 32], 0.6);
      put(im, x, y, add(c, (r() - 0.5) * 10));
    }
    return im;
  };
  T['rig_floor/flare_top'] = () => {
    const im = img(16, 16), r = rng(3971);
    for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
      const d = Math.hypot(x + 0.5 - 8, y + 0.5 - 8);
      put(im, x, y, add(d > 6.4 ? [60, 56, 60] : d > 5 ? [36, 34, 34] : [14, 12, 12], (r() - 0.5) * 8));
    }
    return im;
  };
  T['rig_floor/pilot_flame'] = () => {
    const im = img(16, 16), r = rng(3981);
    for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) put(im, x, y, add(mix([255, 236, 120], [240, 110, 30], y / 15), (r() - 0.5) * 20));
    return im;
  };
  const MAT = {
    rr: { tex: 'rig_floor/rig_red', P: 16 }, grat: { tex: 'rig_floor/grating', P: 16 }, mud: { tex: 'rig_floor/mud', P: 16 },
    olv: { tex: 'rig_floor/olive', P: 16 }, fs: { tex: 'rig_floor/flare_steel', P: 16 }, pf: { tex: 'rig_floor/pilot_flame', P: 16 },
  };
  const mk = (particle) => Object.assign(new PCART.Model(MAT, {}), { particle });

  function paint(root) {
    for (const n of Object.keys(T)) writePng(T[n](), path.join(root, PCART.ASSETS, 'textures/block', n + '.png'));
    return Object.keys(T).length;
  }

  function mudTank(level) {
    const m = mk('rig_floor/rig_red');
    m.box('floor', [0, 0, 0], [16, 1, 16], 'rr');
    m.box('wall_n', [0, 1, 0.4], [16, 14, 0.9], 'rr', { split: '' });
    m.box('wall_s', [0, 1, 15.1], [16, 14, 15.6], 'rr', { split: '' });
    m.box('wall_w', [0, 1, 0.9], [0.6, 14, 15.1], 'rr', { split: '' });
    m.box('wall_e', [15.4, 1, 0.9], [16, 14, 15.1], 'rr', { split: '' });
    // outside stiffeners and the top angle
    for (const x of [4, 11.4]) {
      m.box(`stiff_n_${x}`, [x, 1, 0], [x + 0.6, 14, 0.4], 'rr', { split: '' });
      m.box(`stiff_s_${x}`, [x, 1, 15.6], [x + 0.6, 14, 16], 'rr', { split: '' });
    }
    if (level > 0) m.box('mud', [0.6, 1, 0.9], [15.4, 1 + level * 1.8, 15.1], 'mud', { split: '', hide: ['north', 'south', 'east', 'west', 'down'] });
    m.box('grating', [0, 14, 0], [16, 14.4, 16], 'grat', { split: '' });
    // agitator: gearbox and motor on the grating over the middle
    m.box('gearbox', [6, 14.4, 6], [10, 16, 10], 'green', { split: '' });
    m.box('motor', [10, 14.6, 6.8], [14, 16, 9.2], 'green', { split: '' });
    m.box('agitator_shaft', [7.6, 3, 7.6], [8.4, 14, 8.4], 'st', { split: '' });
    // mud gun on its swivel at the east end, the suction at the foot of the north wall
    m.box('mud_gun', [13, 14.4, 12], [14, 16, 13], 'st', { split: '' });
    m.box('suction', [3, 1.4, 0], [5.4, 3.8, 0.4], 'st', { split: '' });
    m.box('level_board', [8.6, 9, 0.1], [11, 13.6, 0.4], 'yel_cnd', { split: '' });
    return m;
  }
  MAT.yel_cnd = { tex: 'candu_reactor/yellow', P: 16 };

  function console(on) {
    const m = mk('rig_floor/rig_red');
    m.box('pedestal', [4, 0, 6], [12, 8, 13], 'ci');
    m.box('desk', [0, 8, 2], [16, 13, 15], 'rr');
    m.box('desk_top', [0, 13, 1], [16, 13.6, 13], 'ci', { split: '' });
    m.plate('cabinet', [0, 13.6, 9], [16, 29.6, 15], 'rr', 'north', 'rig_floor/console_face');
    m.box('hood', [0, 29.6, 6], [16, 32, 15], 'rr');
    m.box('hood_lip', [0, 31.2, 4], [16, 32, 6], 'rr', { split: '' });
    // the brake lever on the west and the two throttles on the desk
    m.box('brake_lever_base', [0.6, 13.6, 4], [2.4, 15, 6], 'ci', { split: '' });
    m.box('brake_lever', [1.1, 15, 4.6], [1.9, 22, 5.4], 'bright', { split: '', rot: { axis: 'x', angle: -22.5, origin: [1.5, 15, 5] } });
    m.box('brake_knob', [0.8, 21.4, 2.2], [2.2, 22.8, 3.6], 'soot', { split: '' });
    for (const x of [10, 12.4]) m.box(`throttle_${x}`, [x, 13.6, 5], [x + 1, 16, 6], 'soot', { split: '' });
    // lamps over the face: green power, yellow alarm, red trip, white horn
    ['green', 'yel_cnd', 'red', 'bright'].forEach((mat, i) => {
      const x = 3 + i * 3.3;
      m.box((on ? 'glow_' : '') + `lamp_${i}`, [x, 27.4, 8.6], [x + 1.2, 28.6, 9], on ? mat : 'soot', { split: '' });
    });
    return m;
  }

  function recorder(on) {
    const m = mk('rig_floor/olive');
    m.box('case', [1, 0, 2], [15, 7, 15], 'olv');
    m.box('case_rim', [0.8, 6.4, 1.8], [15.2, 7, 15.2], 'soot', { split: '' });
    m.box('keypad', [3, 7, 4], [13, 7.3, 9], 'soot', { split: '' });
    for (let i = 0; i < 4; i++) for (let j = 0; j < 2; j++) m.box(`key_${i}_${j}`, [3.6 + i * 2.4, 7.3, 4.6 + j * 2.2], [5.2 + i * 2.4, 7.6, 6.2 + j * 2.2], 'bright', { split: '' });
    m.plate('lid', [1, 7, 13], [15, 19, 14.4], 'olv', 'north', 'rig_floor/recorder_screen', { rot: { axis: 'x', angle: -22.5, origin: [8, 7, 14] } });
    m.box('paper_slot', [10, 7, 10], [14, 7.4, 12.4], 'bright', { split: '' });
    m.box((on ? 'glow_' : '') + 'lamp_power', [13.2, 7, 3], [14.2, 7.6, 4], on ? 'green' : 'soot', { split: '' });
    // cable reel of the spread on the east side
    m.prism('reel', 'x', [6, 8], 3.8, 15, 16, 'ci', { ends: ['east'] });
    m.box('cable', [15.2, 2.4, 4.6], [16, 9.6, 11.4], 'soot', { split: '' });
    return m;
  }

  function flareTip(lit) {
    const m = mk('rig_floor/flare_steel');
    m.prismY('flange', [8, 8], 4.6, 0, 1, 'ci');
    m.prismY('barrel', [8, 8], 3.6, 1, 12, 'fs', { tex: 'rig_floor/flare_steel' });
    m.prismY('retention_ring', [8, 8], 4.1, 12, 13, 'fs', { cap: 'rig_floor/flare_top' });
    m.prismY('steam_ring', [8, 8], 5.2, 9.6, 10.6, 'st');
    for (const [x, z] of [[8, 2.4], [8, 13.6], [2.4, 8], [13.6, 8]]) m.box(`steam_nozzle_${x}_${z}`, [x - 0.4, 10.6, z - 0.4], [x + 0.4, 12.6, z + 0.4], 'st', { split: '' });
    m.box('steam_riser', [12.6, 0, 12.6], [13.6, 10, 13.6], 'st', { split: '' });
    for (const [k, x, z] of [[0, 2.4, 3.4], [1, 13.6, 3.4], [2, 2.4, 12.6]]) {
      m.box(`pilot_${k}`, [x - 0.5, 1, z - 0.5], [x + 0.5, 13, z + 0.5], 'st', { split: '' });
      m.box(`pilot_head_${k}`, [x - 0.8, 13, z - 0.8], [x + 0.8, 14, z + 0.8], 'ci', { split: '' });
      m.box((lit ? 'glow_' : '') + `pilot_flame_${k}`, [x - 0.6, 14, z - 0.6], [x + 0.6, 15.6, z + 0.6], lit ? 'pf' : 'soot', { split: '' });
    }
    return m;
  }

  const BUILDS = {
    mud_tank: { model: () => mudTank(4) },
    mud_tank_empty: { model: () => mudTank(0) },
    mud_tank_full: { model: () => mudTank(7) },
    drillers_console: { model: () => console(false) },
    drillers_console_on: { model: () => console(true) },
    seismic_recorder: { model: () => recorder(false) },
    seismic_recorder_on: { model: () => recorder(true) },
    flare_tip: { model: () => flareTip(false) },
    flare_tip_lit: { model: () => flareTip(true) },
  };
  const CAT = { mud: 'drilling', dri: 'drilling', sei: 'oil_exploration', fla: 'flare' };

  window.RIG = {
    paint, ids: Object.keys(BUILDS),
    build: (root, id) => PCART.realize(root, BUILDS[id].model(), id),
    save: (root, id) => PCART.save(root, CAT[id.slice(0, 3)], id, BUILDS[id].java),
    preview(root) {
      let m = mudTank(4), x = 0;
      for (const id of ['mud_tank_full', 'drillers_console_on', 'seismic_recorder_on', 'flare_tip_lit']) m = m.merge(BUILDS[id].model(), (x += 20), 0, 0, id + '_');
      return PCART.realize(root, m, 'rig_floor_preview');
    },
  };
})();
