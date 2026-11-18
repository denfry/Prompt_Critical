// Ventilation door and louvre regulator, hand-authored for Blockbench (replace the spec_metallurgy.py
// placeholders).
// Load inside Blockbench desktop, after the shared toolkit:
//   eval(require('fs').readFileSync('<repo>/art/blockbench/lib/pcart.js', 'utf8'))
//   eval(require('fs').readFileSync('<repo>/art/blockbench/mining/ventilation_door.author.js', 'utf8'))
//   PCART.paintMaterials('<repo>'); VDR.paint('<repo>')
//   await VDR.exportAll('<repo>')
//
// 1x2x1 blocks, model x 0..16, y 0..32, z 0..16; the doorway sits in the middle of a partition (z 6..10).
// The door is a steel-framed leaf of planks clad in conveyor belting on the pressure side, hung on strap
// hinges, sealed with rubber flaps; open, it stands square to the partition. The regulator has the same frame
// with six louvre blades. Its four window steps (VentilationDoorBlock.WINDOW_FRACTIONS: 10, 25, 50 and 100 % of
// the doorway) each get a model: for overlapping blades the free area is 1 - cos(theta) of the doorway, so the
// blades stand at the nearest 22.5 degrees to acos(1 - f): 22.5, 45, 67.5 and 90 (edge-on). A hand quadrant on
// the post shows the setting.
(function () {
  const path = require('path');
  const { rng, img, put, add, mix, rect, grain, rivet, writePng } = PCART;
  const TEX_REL = PCART.ASSETS + '/textures/block/ventilation_door';
  const T = {};

  // Door planks, vertical, tarred dark, joints and nail heads.
  T.planks = () => {
    const im = img(32, 32), r = rng(1401);
    for (let y = 0; y < 32; y++) for (let x = 0; x < 32; x++) {
      const board = Math.floor(x / 8), tone = [[92, 70, 50], [100, 76, 54], [86, 66, 48], [96, 72, 52]][board];
      put(im, x, y, add(tone, (x % 8 === 0 ? -30 : 0) + Math.sin(y * 0.7 + board * 3) * 4 + (r() - 0.5) * 10));
    }
    for (let b = 0; b < 4; b++) for (const y of [3, 28]) put(im, b * 8 + 4, y, [150, 146, 140]);
    return im;
  };
  // Conveyor belting: black rubber with the fabric ply showing at worn spots.
  T.belting = () => {
    const im = img(32, 32), r = grain(im, [34, 34, 36], 1411, 3, 4, 8);
    for (let k = 0; k < 6; k++) {
      const x = Math.floor(r() * 28), y = Math.floor(r() * 28);
      for (let j = 0; j < 4; j++) for (let i = 0; i < 5; i++) if (Math.hypot(i - 2, j - 1.5) < 2.2 && r() < 0.6) put(im, x + i, y + j, add([66, 60, 52], (r() - 0.5) * 10));
    }
    return im;
  };
  // Frame and blade steel, painted grey-green, rust pitting through the paint.
  T.steel = () => {
    const im = img(32, 32), r = grain(im, [84, 96, 88], 1421, 3, 5, 8);
    for (let k = 0; k < 18; k++) { const x = Math.floor(r() * 31), y = Math.floor(r() * 31); put(im, x, y, [104, 70, 48]); if (r() < 0.5) put(im, x + 1, y, [92, 64, 44]); }
    return im;
  };
  // Door sign: red board with a white band, the "keep the door shut" plate every mine door carries.
  T.sign = () => {
    const im = img(16, 16), r = rng(1431);
    for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) put(im, x, y, add([176, 36, 30], (r() - 0.5) * 10));
    rect(im, 1, 6, 14, 4, [226, 222, 212]);
    for (let x = 2; x < 14; x += 2) put(im, x, 7 + (x % 4 === 0 ? 1 : 0), [40, 36, 34]);
    for (let i = 0; i < 16; i++) { put(im, i, 0, [60, 58, 56]); put(im, i, 15, [60, 58, 56]); put(im, 0, i, [60, 58, 56]); put(im, 15, i, [60, 58, 56]); }
    return im;
  };

  const MAT = {
    pl: { tex: 'ventilation_door/planks', P: 16 }, belt: { tex: 'ventilation_door/belting', P: 16 },
    vs: { tex: 'ventilation_door/steel', P: 16 },
  };
  const mk = (particle) => Object.assign(new PCART.Model(MAT, {}), { particle: particle || 'ventilation_door/steel' });

  function paint(root) {
    for (const n of Object.keys(T)) writePng(T[n](), path.join(root, TEX_REL, n + '.png'));
    return Object.keys(T).length;
  }

  // Steel channel frame set in the partition: posts, lintel, threshold.
  function frame(m) {
    m.box('post_w', [0, 0, 6], [2, 29, 10], 'vs', { split: 'y' });
    m.box('post_e', [14, 0, 6], [16, 29, 10], 'vs', { split: 'y' });
    m.box('lintel', [0, 29, 6], [16, 32, 10], 'vs', { split: '' });
    m.box('threshold', [2, 0, 6.4], [14, 0.5, 9.6], 'vs', { split: '' });
  }

  // Door leaf between x0..x1 along `ax` laid flat in the doorway (closed) or swung to stand along z (open).
  function leaf(m, open) {
    // closed: leaf in the plane z 7..8.6, hinge on the west post at z 8.6; open: square to it, x 2..3.6.
    const L = (a0, a1, y0, y1, d0, d1, mat, name, o) => {
      if (!open) m.box(name, [a0, y0, d0], [a1, y1, d1], mat, o);
      else m.box(name, [2 + (8.6 - d1), y0, 8.6 + (a0 - 2)], [2 + (8.6 - d0), y1, 8.6 + (a1 - 2)], mat, o);
    };
    L(2.1, 13.9, 0.6, 28.8, 7.4, 8.6, 'pl', 'leaf', {});
    L(2.1, 13.9, 0.6, 28.8, 7.1, 7.4, 'belt', 'leaf_belting', {});
    // braces and strap hinges on the planks side
    for (const y of [4, 15, 25]) L(2.1, 13.9, y, y + 1.4, 8.6, 8.9, 'vs', `ledge_${y}`, { split: '' });
    for (const y of [5, 24]) L(2.1, 8, y - 0.3, y + 0.9, 8.9, 9.1, 'st', `hinge_strap_${y}`, { split: '' });
    // rubber seal flaps proud of the leaf on the belted side, round the edges
    L(2.1, 13.9, 0.1, 0.6, 7.1, 7.3, 'belt', 'flap_bottom', { split: '' });
    L(13.5, 14.4, 0.6, 28.8, 7, 7.2, 'belt', 'flap_side', { split: '' });
    // latch handle and the warning plate
    L(11.6, 12.4, 13, 16, 8.9, 9.6, 'st', 'handle', { split: '' });
    L(11.6, 12.4, 13, 16, 6.4, 7.1, 'st', 'handle_back', { split: '' });
    if (!open) m.plate('sign', [5, 18, 6.95], [11, 22, 7.1], 'vs', 'north', 'ventilation_door/sign', { hide: ['south'] });
    else m.plate('sign', [3.65, 18, 11.6], [3.8, 22, 17.6], 'vs', 'east', 'ventilation_door/sign', { hide: ['west'] });
    // hinge knuckles stay on the post
    for (const y of [5, 24]) m.box(`knuckle_${y}`, [1.6, y - 0.4, 8.4], [2.6, y + 1.2, 9.4], 'st', { split: '' });
  }

  function door() { const m = mk('ventilation_door/planks'); frame(m); leaf(m, false); return m; }
  function doorOpen() { const m = mk('ventilation_door/planks'); frame(m); leaf(m, true); return m; }

  // Louvre regulator: six blades on spindles across the doorway, tied by a link bar to the hand quadrant.
  function regulator(theta) {
    const m = mk();
    frame(m);
    const n = 6, y0 = 1, pitch = 27.6 / n, w = pitch + 0.4;
    for (let k = 0; k < n; k++) {
      const yc = y0 + pitch * (k + 0.5);
      if (theta === 90) m.box(`blade_${k}`, [2.1, yc - 0.2, 8 - w / 2], [13.9, yc + 0.2, 8 + w / 2], 'vs', { split: '' });
      else {
        const vertical = theta <= 45, a = vertical ? theta : theta - 90;
        const f = vertical ? [2.1, yc - w / 2, 7.8] : [2.1, yc - 0.2, 8 - w / 2];
        const t = vertical ? [13.9, yc + w / 2, 8.2] : [13.9, yc + 0.2, 8 + w / 2];
        m.box(`blade_${k}`, f, t, 'vs', { split: '', rot: { axis: 'x', angle: a, origin: [8, yc, 8] } });
      }
      m.box(`spindle_${k}`, [1.6, yc - 0.3, 7.7], [14.4, yc + 0.3, 8.3], 'bright', { split: '' });
      // crank on the east end; its pin moves with the blade angle, the link bar follows the pins
      const t = theta * Math.PI / 180, py = yc + 1.2 * Math.sin(t), pz = 8 - 1.2 * Math.cos(t);
      m.box(`crank_pin_${k}`, [14, py - 0.25, pz - 0.25], [14.6, py + 0.25, pz + 0.25], 'st', { split: '' });
    }
    const t = theta * Math.PI / 180, dz = -1.2 * Math.cos(t), dy = 1.2 * Math.sin(t);
    m.box('link_bar', [14.1, y0 + pitch / 2 + dy - 0.3, 8 + dz - 0.3], [14.5, y0 + pitch * (n - 0.5) + dy + 0.3, 8 + dz + 0.3], 'st', { split: '' });
    // hand quadrant on the east post: notched arc and the lever set to this step
    m.box('quadrant', [16, 12, 7], [16.3, 18, 11], 'st', { split: '' });
    const lever = { axis: 'x', angle: [0, 22.5, 45, -45][[22.5, 45, 67.5, 90].indexOf(theta)], origin: [16.4, 13, 9] };
    m.box('lever', [16.3, 13, 8.7], [16.7, 19.5, 9.3], 'red', { split: '', rot: lever.angle ? lever : undefined });
    return m;
  }

  const STEPS = [['ventilation_door_regulator', 22.5], ['ventilation_door_regulator_25', 45],
    ['ventilation_door_regulator_50', 67.5], ['ventilation_door_regulator_100', 90]];
  const BUILDS = { ventilation_door: { model: door }, ventilation_door_open: { model: doorOpen } };
  for (const [id, th] of STEPS) BUILDS[id] = { model: () => regulator(th) };

  window.VDR = {
    paint,
    build: (root, id) => PCART.realize(root, BUILDS[id].model(), id),
    exportAll: (root) => PCART.exportAll(root, 'mining', BUILDS),
    preview(root) {
      let m = door().merge(doorOpen(), 20, 0, 0, 'o_');
      STEPS.forEach(([id, th], i) => { m = m.merge(regulator(th), 40 + i * 20, 0, 0, `r${i}_`); });
      return PCART.realize(root, m, 'ventilation_door_preview');
    },
  };
})();
