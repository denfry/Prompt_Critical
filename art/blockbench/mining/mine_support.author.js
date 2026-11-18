// Drift supports, hand-authored for Blockbench (replace the spec_visual.py and spec_metallurgy.py
// placeholders): a timber door set, the same set crushed by roof pressure, and a yielding steel arch of
// trough section (SVP type) with mesh lagging.
// Load inside Blockbench desktop, after the shared toolkit:
//   eval(require('fs').readFileSync('<repo>/art/blockbench/lib/pcart.js', 'utf8'))
//   eval(require('fs').readFileSync('<repo>/art/blockbench/mining/mine_support.author.js', 'utf8'))
//   PCART.paintMaterials('<repo>'); MSP.paint('<repo>')
//   await MSP.exportAll('<repo>')
//
// 1x3x1 blocks, model x 0..16, y -16..32, z 0..16 (the anchor is the middle block; the floor is y -16, the
// roof y 32). The drift runs along z: sets stand one per block and the lagging, laid along z, closes the gap
// to the next set.
(function () {
  const path = require('path');
  const { rng, img, put, add, mix, rect, grain, vnoise, writePng } = PCART;
  const TEX_REL = PCART.ASSETS + '/textures/block/mine_support';
  const T = {};

  // Pine bark on round props: long plates split by vertical furrows, resin runs, mine dust.
  T.bark = () => {
    const im = img(32, 32), r = rng(1301), n = vnoise(r, 32, 32, 6);
    for (let y = 0; y < 32; y++) for (let x = 0; x < 32; x++) {
      const furrow = (x % 6 === 0 || (x + Math.floor(y / 9)) % 11 === 0) ? -34 : 0;
      put(im, x, y, add(mix([104, 74, 50], [70, 54, 40], n(x, y)), furrow + (r() - 0.5) * 12));
    }
    for (let k = 0; k < 4; k++) { const x = Math.floor(r() * 32), y0 = Math.floor(r() * 24); for (let j = 0; j < 6; j++) put(im, x, y0 + j, [176, 138, 70]); }
    return im;
  };
  // Sawn end grain of a prop or cap: rings about the centre, a radial check, bark rim.
  T.endgrain = () => {
    const im = img(16, 16), r = rng(1311);
    for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
      const d = Math.hypot(x + 0.5 - 8, y + 0.5 - 8);
      let c = d > 7.2 ? [86, 62, 42] : Math.floor(d * 1.3) % 2 ? [190, 156, 108] : [170, 134, 88];
      if (Math.abs(x - 8) < 0.6 && y < 8 && d < 7) c = [96, 72, 48];
      put(im, x, y, add(c, (r() - 0.5) * 8));
    }
    return im;
  };
  // Rough-sawn lagging plank and wedges, grey with age and coal dust.
  T.plank = () => {
    const im = img(32, 32), r = rng(1321);
    for (let y = 0; y < 32; y++) for (let x = 0; x < 32; x++) {
      const grainLine = Math.sin((x + Math.sin(y * 0.4) * 2) * 1.3) * 8;
      put(im, x, y, add([126, 104, 76], grainLine + (r() - 0.5) * 12 - (y % 16 === 0 ? 30 : 0)));
    }
    return im;
  };
  // Trough-section arch steel: dark paint gone to rust, the flange edges brighter.
  T.arch = () => {
    const im = img(32, 32), r = grain(im, [86, 60, 44], 1331, 5, 10, 6);
    for (let y = 0; y < 32; y++) for (let x = 0; x < 32; x++) {
      if (r() < 0.25) put(im, x, y, mix([86, 60, 44], [52, 50, 48], 0.6 + r() * 0.3));
    }
    return im;
  };
  // Welded wire mesh lagging, 4 px pitch, open cells transparent.
  T.mesh = () => {
    const im = img(32, 32), r = rng(1341);
    for (let y = 0; y < 32; y++) for (let x = 0; x < 32; x++) {
      if (x % 4 === 0 || y % 4 === 0) put(im, x, y, add([96, 90, 84], (r() - 0.5) * 20));
    }
    return im;
  };
  // Broken shale fallen from the roof.
  T.rock = () => {
    const im = img(32, 32), r = grain(im, [82, 80, 78], 1351, 6, 10, 6);
    for (let y = 0; y < 32; y += 3 + Math.floor(r() * 2)) for (let x = 0; x < 32; x++) put(im, x, y, add([60, 58, 56], (r() - 0.5) * 8));
    return im;
  };

  const MAT = {
    bark: { tex: 'mine_support/bark', P: 16 }, plank: { tex: 'mine_support/plank', P: 16 },
    arch: { tex: 'mine_support/arch', P: 16 }, mesh: { tex: 'mine_support/mesh', P: 16 },
    rock: { tex: 'mine_support/rock', P: 16 },
  };
  const END = 'mine_support/endgrain';
  const mk = (particle) => Object.assign(new PCART.Model(MAT, {}), { particle: particle || 'mine_support/bark' });

  function paint(root) {
    for (const n of Object.keys(T)) writePng(T[n](), path.join(root, TEX_REL, n + '.png'));
    return Object.keys(T).length;
  }

  const POST_R = 1.8, CAP_R = 1.9, CAP_Y = 29.6, PX = [2.4, 13.6];

  // Lagging boards over the cap and behind the posts, laid along the drift.
  function lagging(m, broken) {
    for (let i = 0; i < 4; i++) {
      if (broken && i === 2) continue;
      m.box(`roof_board_${i}`, [i * 4 + 0.2, 31.5, 0], [i * 4 + 3.8, 32, 16], 'plank', { split: '' });
    }
    for (const [s, x0, x1] of [['w', 0, 0.5], ['e', 15.5, 16]]) {
      for (let k = 0; k < 8; k++) {
        if (broken && s === 'e' && k === 6) continue;
        const y0 = -14 + k * 5.2;
        m.box(`wall_board_${s}_${k}`, [x0, y0, 0], [x1, y0 + 3.2, 16], 'plank', { split: '' });
      }
    }
  }

  function post(m, s, x, y0, y1) {
    m.prismY(`post_${s}`, [x, 8], POST_R, y0, y1, 'bark', { seed: x * 3, cap: END });
    m.box(`post_${s}_sill`, [x - 2.4, -16, 5], [x + 2.4, -15.4, 11], 'plank', { split: '' });
  }

  // Timber door set: two props with a cap notched over them, wedged tight to the roof.
  function timber() {
    const m = mk();
    for (const [s, x] of [['w', PX[0]], ['e', PX[1]]]) post(m, s, x, -15.4, CAP_Y - CAP_R + 0.4);
    m.prism('cap', 'x', [CAP_Y, 8], CAP_R, 0.3, 15.7, 'bark', { split: '' });
    m.disc('cap_end_w', 'x', [CAP_Y, 8], CAP_R, 0.2, 0.3, 'bark', END, 'west');
    m.disc('cap_end_e', 'x', [CAP_Y, 8], CAP_R, 15.7, 15.8, 'bark', END, 'east');
    for (const x of PX) m.box(`wedge_${x}`, [x - 1.6, CAP_Y + CAP_R - 0.2, 6.6], [x + 1.6, 31.5, 9.4], 'plank', { split: '' });
    lagging(m, false);
    return m;
  }

  // The same set after a roof weighting: the cap snapped in the middle and sagged, splinters at the break,
  // one board fallen and the rock that came through lying on the floor.
  function damaged() {
    const m = mk();
    for (const [s, x] of [['w', PX[0]], ['e', PX[1]]]) post(m, s, x, -15.4, CAP_Y - CAP_R + 0.4);
    m.box('cap_w', [0.3, CAP_Y - CAP_R, 6.1], [8.2, CAP_Y + CAP_R, 9.9], 'bark', { rot: { axis: 'z', angle: 22.5, origin: [2.4, CAP_Y, 8] }, face: { west: { tex: END, uv: [0, 0, 16, 16] } } });
    m.box('cap_e', [7.8, CAP_Y - CAP_R, 6.1], [15.7, CAP_Y + CAP_R, 9.9], 'bark', { rot: { axis: 'z', angle: -22.5, origin: [13.6, CAP_Y, 8] }, face: { east: { tex: END, uv: [0, 0, 16, 16] } } });
    for (const [k, x, dy, dz] of [[0, 7.2, -0.6, 6.8], [1, 8, -1.2, 7.8], [2, 8.6, -0.4, 8.6]]) {
      m.box(`splinter_${k}`, [x, CAP_Y - 3.4 + dy, dz], [x + 0.4, CAP_Y - 0.8 + dy, dz + 0.5], 'plank', { split: '' });
    }
    m.box('wedge_w', [0.8, CAP_Y + CAP_R - 0.2, 6.6], [4, 31.5, 9.4], 'plank', { split: '' });
    lagging(m, true);
    m.box('fallen_board', [3, -16, 2], [6.6, -15.5, 14], 'plank', { split: '', rot: { axis: 'y', angle: 22.5, origin: [5, -16, 8] } });
    const r = rng(1361);
    for (let k = 0; k < 7; k++) {
      const x = 4 + r() * 8, z = 1 + r() * 13, s = 0.8 + r() * 1.6, h = 0.6 + r() * 1.2;
      m.box(`rock_${k}`, [x, -16, z], [x + s, -16 + h, z + s * (0.7 + r() * 0.6)], 'rock', { split: '' });
    }
    return m;
  }

  // Yielding steel arch: two legs and a crown piece of trough section, overlapping at the joints and held
  // by two clamps each, so the arch slides under load instead of buckling. Crown approximated by tangent bars
  // at 22.5 degree steps; mesh lagging rides on the outside; tie rods to the next set along the drift.
  function arch() {
    const m = mk('mine_support/arch');
    const CX = 8, CY = 21, R = 7, W = 1.8, L = 2 * R * Math.tan(Math.PI / 16) + 0.3, Z0 = 7.1, Z1 = 8.9;
    m.box('leg_w', [CX - R - W / 2, -16, Z0], [CX - R + W / 2, CY + 0.6, Z1], 'arch', { split: '' });
    m.box('leg_e', [CX + R - W / 2, -16, Z0], [CX + R + W / 2, CY + 0.6, Z1], 'arch', { split: '' });
    const tangent = (name, th, rad, w, l, z0, z1, mat) => {
      const t = th * Math.PI / 180, px = CX - rad * Math.cos(t), py = CY + rad * Math.sin(t);
      const vertical = th <= 45 || th >= 135;
      const rot = th <= 45 ? -th : th >= 135 ? 180 - th : 90 - th;
      const f = vertical ? [px - w / 2, py - l / 2, z0] : [px - l / 2, py - w / 2, z0];
      const to = vertical ? [px + w / 2, py + l / 2, z1] : [px + l / 2, py + w / 2, z1];
      m.box(name, f, to, mat, { split: '', rot: rot ? { axis: 'z', angle: rot, origin: [px, py, 8] } : undefined });
    };
    for (let k = 1; k < 8; k++) tangent(`crown_${k}`, k * 22.5, R, W, L, Z0, Z1, 'arch');
    // overlaps and clamps at the yielding joints, just above the springing
    for (const [s, x] of [['w', CX - R], ['e', CX + R]]) {
      m.box(`overlap_${s}`, [x - W / 2 - 0.4, CY - 4, Z0 - 0.3], [x + W / 2 + 0.4, CY + 1.4, Z1 + 0.3], 'arch', { split: '' });
      for (const y of [CY - 3.4, CY - 0.6]) {
        m.box(`clamp_${s}_${y}`, [x - W / 2 - 0.8, y, Z0 - 0.6], [x + W / 2 + 0.8, y + 0.9, Z1 + 0.6], 'st', { split: '' });
        for (const z of [Z0 - 0.9, Z1 + 0.6]) m.box(`clamp_nut_${s}_${y}_${z}`, [x - 0.5, y + 0.1, z], [x + 0.5, y + 0.8, z + 0.3], 'bright', { split: '' });
      }
      m.box(`foot_${s}`, [x - 1.6, -16, Z0 - 0.8], [x + 1.6, -15.4, Z1 + 0.8], 'arch', { split: '' });
    }
    // mesh on the outside of the crown and behind the legs, the full length of the block along the drift
    for (let k = 1; k < 8; k++) tangent(`mesh_${k}`, k * 22.5, R + W / 2 + 0.15, 0.1, L + 0.2, 0, 16, 'mesh');
    for (const [s, x] of [['w', CX - R - W / 2 - 0.2], ['e', CX + R + W / 2 + 0.1]]) {
      m.box(`mesh_wall_${s}`, [x, -14, 0], [x + 0.1, CY, 16], 'mesh', { split: 'yz' });
    }
    // tie rods to the next arch along the drift
    for (const [n, x, y] of [['w', CX - R, 4], ['e', CX + R, 4], ['c', CX, CY + R - 0.4]]) {
      m.box(`tie_${n}`, [x - 0.2, y - 0.2, 0], [x + 0.2, y + 0.2, 16], 'st', { split: '' });
    }
    return m;
  }

  const BUILDS = {
    mine_support: { model: timber },
    mine_support_damaged: { model: damaged },
    mine_support_arch: { model: arch },
  };

  window.MSP = {
    paint,
    build: (root, id) => PCART.realize(root, BUILDS[id].model(), id),
    exportAll: (root) => PCART.exportAll(root, 'mining', BUILDS),
    preview(root) {
      return PCART.realize(root, timber().merge(damaged(), 0, 0, 20, 'd_').merge(arch(), 0, 0, 40, 'a_'), 'mine_support_preview');
    },
  };
})();
