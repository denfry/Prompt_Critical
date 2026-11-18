// Cupola furnace, hand-authored for Blockbench (replaces the spec_visual.py placeholder), plus its panel board.
// Load inside Blockbench desktop, after the shared toolkit:
//   eval(require('fs').readFileSync('<repo>/art/blockbench/lib/pcart.js', 'utf8'))
//   eval(require('fs').readFileSync('<repo>/art/blockbench/foundry/cupola_furnace.author.js', 'utf8'))
//   PCART.paintMaterials('<repo>'); CUP.paint('<repo>'); CUP.paintGui('<repo>')
//   build + save each model in turn (CUP.build / CUP.save)
//
// 1x3x1 blocks, model x 0..16, y -16..32 (the controller is the middle block), z 0..16, front to the north.
// A riveted steel shaft lined with firebrick stands on four cast columns over the drop-bottom doors, which are
// held shut by a prop. Around the lower shaft runs the wind box; the blower on the west end (driven by the
// Create shaft through the block) feeds it through the blast pipe, and the tuyeres have peep sights that glow
// when the furnace is lit. In front: the taphole with its clay plug and the refractory-lined spout (MachineFx
// flames and drips use x 8, y -4, z 0.6 and x 8, y -6, z -2.5); the slag hole at the back. Up the shaft: two
// bands and the charging door, which glows when lit; on top the spark arrester under a raised cap (sparks and
// smoke at x 8, y 32, z 8).
(function () {
  const path = require('path');
  const { rng, img, put, add, mix, rect, grain, rivet, writePng } = PCART;
  const TEX_REL = PCART.ASSETS + '/textures/block/cupola_furnace';
  const T = {};

  // Shell plate: dull black-red heat-tinted steel, vertical seams with rivets, soot toward the top of a tile.
  T.shell = () => {
    const im = img(32, 32), r = grain(im, [70, 58, 52], 1901, 4, 7, 8);
    for (let y = 0; y < 32; y++) for (let x = 0; x < 32; x++) {
      const i = (y * 32 + x) * 4;
      put(im, x, y, mix([im.d[i], im.d[i + 1], im.d[i + 2]], [30, 28, 28], Math.max(0, (14 - y) / 14) * 0.35));
    }
    for (const x of [0, 16]) for (let y = 0; y < 32; y++) put(im, x, y, [44, 38, 36]);
    for (let y = 2; y < 32; y += 4) { rivet(im, 2, y, [70, 58, 52]); rivet(im, 18, y, [70, 58, 52]); }
    return im;
  };
  // Firebrick lining at the spout and taphole: buff brick, slag glaze.
  T.refractory = () => {
    const im = img(32, 32), r = rng(1911);
    for (let y = 0; y < 32; y++) for (let x = 0; x < 32; x++) {
      const joint = y % 8 === 0 || (x + (Math.floor(y / 8) % 2) * 8) % 16 === 0;
      put(im, x, y, joint ? [120, 104, 86] : add(mix([196, 168, 124], [96, 70, 56], r() < 0.1 ? 0.6 : 0), (r() - 0.5) * 14));
    }
    return im;
  };
  // Molten iron at the taphole and the charging door: yellow-white core, orange skin.
  T.melt = () => {
    const im = img(16, 16), r = rng(1921);
    for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
      const d = Math.hypot(x + 0.5 - 8, y + 0.5 - 8) / 8;
      put(im, x, y, add(mix([255, 240, 170], [236, 110, 30], Math.min(1, d)), (r() - 0.5) * 20));
    }
    return im;
  };
  // Clay plug and cold charging door.
  T.clay = () => { const im = img(16, 16); grain(im, [118, 96, 78], 1931, 6, 8, 4); return im; };
  // Peep sight glass, cold (dark blue) and hot (the tuyere zone seen through it).
  T.sight = () => { const im = img(16, 16); grain(im, [30, 40, 56], 1941, 3, 4, 4); return im; };
  T.sight_hot = () => { const im = img(16, 16); grain(im, [255, 196, 90], 1951, 8, 12, 4); return im; };

  const MAT = {
    sh: { tex: 'cupola_furnace/shell', P: 16 }, rf: { tex: 'cupola_furnace/refractory', P: 16 },
    melt: { tex: 'cupola_furnace/melt', P: 16 }, clay: { tex: 'cupola_furnace/clay', P: 16 },
    sight: { tex: 'cupola_furnace/sight', P: 16 }, hot: { tex: 'cupola_furnace/sight_hot', P: 16 },
  };
  const mk = (particle) => Object.assign(new PCART.Model(MAT, {}), { particle: particle || 'cupola_furnace/shell' });

  function paint(root) {
    for (const n of Object.keys(T)) writePng(T[n](), path.join(root, TEX_REL, n + '.png'));
    return Object.keys(T).length;
  }

  function cupola(lit) {
    const m = mk();
    const glow = (n) => (lit ? 'glow_' + n : n);
    // columns, bottom plate, drop doors and prop
    for (const [x, z] of [[1, 1], [13, 1], [1, 13], [13, 13]]) {
      m.prismY(`column_${x}_${z}`, [x + 1, z + 1], 1.1, -16, -8, 'ci', { ends: [] });
      m.box(`column_foot_${x}_${z}`, [x - 0.2, -16, z - 0.2], [x + 2.2, -15.4, z + 2.2], 'ci', { split: '' });
    }
    m.box('bottom_plate', [0.4, -8, 0.4], [15.6, -7, 15.6], 'ci', { split: '' });
    m.box('door_w', [2.4, -9, 2.4], [7.9, -8, 13.6], 'ci', { split: '' });
    m.box('door_e', [8.1, -9, 2.4], [13.6, -8, 13.6], 'ci', { split: '' });
    m.box('door_hinge_w', [2, -9.4, 3], [2.8, -8.6, 13], 'st', { split: '' });
    m.box('door_hinge_e', [13.2, -9.4, 3], [14, -8.6, 13], 'st', { split: '' });
    m.prismY('prop', [8, 8], 0.8, -16, -9, 'st');
    m.box('prop_foot', [6.6, -16, 6.6], [9.4, -15.4, 9.4], 'st', { split: '' });
    // shaft, wind box, bands
    m.prismY('shaft', [8, 8], 6.5, -7, 29, 'sh', { seed: 3 });
    m.prismY('wind_box', [8, 8], 7.7, -4.4, 1.6, 'ci', { ends: ['up', 'down'] });
    for (const y of [11, 23]) m.prismY(`band_${y}`, [8, 8], 6.9, y, y + 1.2, 'ci', { ends: ['up', 'down'] });
    // tuyere peep sights round the wind box
    for (const [n, f, t, d] of [['n', [7, -2.6, 0], [9, -0.8, 0.4], 'north'], ['s', [7, -2.6, 15.6], [9, -0.8, 16], 'south'],
      ['e', [15.6, -2.6, 7], [16, -0.8, 9], 'east']]) {
      m.box(`${glow('sight')}_${n}`, f, t, lit ? 'hot' : 'sight', { split: '' });
    }
    // blower on the west end: volute casing, blast pipe down into the wind box; shaft boss on the east
    m.prism('blower', 'x', [8, 8], 4.4, 0, 2.8, 'ci', { split: '', ends: ['west', 'east'] });
    m.box('blower_outlet', [0.6, 1.6, 6.4], [2.4, 4.2, 9.6], 'ci', { split: '' });
    m.box('blast_pipe', [0.6, -2.4, 6.6], [2.4, 1.6, 9.4], 'st', { split: '' });
    m.box('blast_elbow', [0.6, -3.6, 6.6], [2.8, -2.4, 9.4], 'st', { split: '' });
    m.prism('bearing_e', 'x', [8, 8], 2.4, 14.4, 16, 'ci', { split: '', ends: ['east'] });
    // taphole with clay plug (glowing metal when lit), refractory-lined spout to the north
    m.box('taphole_frame', [5.8, -6.4, 0.6], [10.2, -2.2, 1.6], 'ci', { split: '' });
    m.box(glow('taphole'), [6.6, -5.6, 0.4], [9.4, -3, 0.9], lit ? 'melt' : 'clay', { split: '' });
    m.box('spout', [6, -7, -3.4], [10, -6, 1.6], 'rf', { split: '' });
    m.box('spout_lip_w', [6, -6, -3.4], [6.8, -4.8, 1.6], 'rf', { split: '' });
    m.box('spout_lip_e', [9.2, -6, -3.4], [10, -4.8, 1.6], 'rf', { split: '' });
    m.box('spout_bracket', [7, -8, -2.6], [9, -7, -1.4], 'ci', { split: '' });
    if (lit) m.box('glow_spout_metal', [6.8, -6, -3.2], [9.2, -5.7, 1.4], 'melt', { split: '' });
    // slag hole at the back
    m.box('slag_frame', [6.6, -4.2, 14.6], [9.4, -1.8, 15.6], 'ci', { split: '' });
    m.box('slag_hole', [7.2, -3.6, 15.4], [8.8, -2.4, 15.8], 'clay', { split: '' });
    // charging door: cast frame, the door panel (glowing open charge when lit), hinges
    m.box('charge_frame', [4.2, 16, 0.8], [11.8, 25, 1.8], 'ci', { split: '' });
    m.box(glow('charge_door'), [5.2, 17, 0.4], [10.8, 24, 1], lit ? 'melt' : 'ci', { split: '' });
    for (const y of [17.6, 22.6]) m.box(`charge_hinge_${y}`, [3.6, y, 0.6], [4.6, y + 1, 1.4], 'st', { split: '' });
    // spark arrester: reduced stack, raised cap on four posts
    m.prismY('arrester', [8, 8], 4.6, 29, 30.8, 'sh', { ends: ['up', 'down'] });
    for (const [dx, dz] of [[3.4, 3.4], [-3.4, 3.4], [3.4, -3.4], [-3.4, -3.4]]) {
      m.box(`cap_post_${dx}_${dz}`, [8 + dx - 0.3, 30.8, 8 + dz - 0.3], [8 + dx + 0.3, 31.4, 8 + dz + 0.3], 'st', { split: '' });
    }
    m.prismY('cap', [8, 8], 6, 31.4, 31.9, 'soot', { ends: ['up', 'down'] });
    return m;
  }

  // Panel board for FoundryPanels.CUPOLA (200x150): rings behind the three dials, a strip for the lamps and a
  // plate for the bars, the read-out and the two charge slots.
  function guiCupola() {
    const W = 200, H = 150, im = img(W, H), r = rng(1961);
    grain(im, [66, 58, 54], 1962, 3, 5, 12);
    for (let y = 0; y < H; y++) for (let x = 0; x < W; x++) {
      const d = Math.min(x, y, W - 1 - x, H - 1 - y);
      if (d < 4) put(im, x, y, add([170, 134, 70], (d === 0 ? -50 : d === 1 ? 30 : d === 3 ? -30 : 0) + (r() - 0.5) * 8));
    }
    const ring = (cx, cy, R) => { for (let a = 0; a < 360; a += 0.4) { const t = a * Math.PI / 180; put(im, cx + R * Math.cos(t), cy + R * Math.sin(t), [36, 30, 28]); put(im, cx + (R + 1) * Math.cos(t) + 0.5, cy + (R + 1) * Math.sin(t) + 0.5, [130, 112, 100]); } };
    ring(40, 56, 34); ring(104, 48, 26); ring(164, 48, 26);
    const plate = (x0, y0, x1, y1, base) => {
      for (let y = y0; y < y1; y++) for (let x = x0; x < x1; x++) {
        const edge = x === x0 || y === y0 ? -22 : x === x1 - 1 || y === y1 - 1 ? 18 : 0;
        put(im, x, y, add(base, edge + (r() - 0.5) * 4));
      }
    };
    plate(76, 82, W - 6, 102, [54, 48, 44]);
    plate(4 + 2, 106, W - 6, 146, [50, 44, 40]);
    return im;
  }

  function paintGui(root) {
    writePng(guiCupola(), path.join(root, PCART.ASSETS, 'textures/gui/panel/cupola_furnace.png'));
    return 1;
  }

  const BUILDS = { cupola_furnace: { model: () => cupola(false) }, cupola_furnace_lit: { model: () => cupola(true) } };

  window.CUP = {
    paint, paintGui,
    build: (root, id) => PCART.realize(root, BUILDS[id].model(), id),
    save: (root, id) => PCART.save(root, 'foundry', id, BUILDS[id].java),
    preview(root) { return PCART.realize(root, cupola(false).merge(cupola(true), 20, 0, 0, 'l_'), 'cupola_preview'); },
  };
})();
