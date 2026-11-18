// Coke-fired crucible furnace with forced draught, hand-authored for Blockbench (replaces the spec_visual.py
// placeholder), plus its panel board.
// Load inside Blockbench desktop, after the shared toolkit:
//   eval(require('fs').readFileSync('<repo>/art/blockbench/lib/pcart.js', 'utf8'))
//   eval(require('fs').readFileSync('<repo>/art/blockbench/foundry/crucible_furnace.author.js', 'utf8'))
//   PCART.paintMaterials('<repo>'); CRF.paint('<repo>'); CRF.paintGui('<repo>')
//   build + save each model in turn (CRF.build / CRF.save)
//
// 1x1x1, model x 0..16, y 0..16, z 0..16, front to the north. A round firebrick-lined furnace in a steel
// casing with two bands, set a little into the floor. The lid swings aside on a post: a firebrick-lined
// cast ring with the flue hole in the middle (MachineFx flame and smoke at x 8, y 13.6..14, z 8), a lever
// handle. The blast comes from the back through a tuyere pipe with a damper; the ash-pit door is in front
// low down. Crucible tongs hang on the lid post. The crucible (partial, static) stands on its stand inside,
// the melt showing through the flue hole.
(function () {
  const path = require('path');
  const { rng, img, put, add, mix, rect, grain, rivet, writePng } = PCART;
  const TEX_REL = PCART.ASSETS + '/textures/block/crucible_furnace';
  const T = {};

  // Casing: black stove-painted steel, heat-browned near the top of a tile.
  T.casing = () => {
    const im = img(32, 32), r = grain(im, [46, 44, 44], 2001, 3, 5, 8);
    for (let y = 0; y < 32; y++) for (let x = 0; x < 32; x++) {
      const i = (y * 32 + x) * 4;
      put(im, x, y, mix([im.d[i], im.d[i + 1], im.d[i + 2]], [96, 66, 44], Math.max(0, (10 - y) / 10) * 0.4));
    }
    return im;
  };
  // Graphite-clay crucible: dark grey, glazed and flaked.
  T.crucible = () => { const im = img(32, 32), r = grain(im, [66, 66, 70], 2011, 6, 8, 6); for (let k = 0; k < 20; k++) put(im, Math.floor(r() * 32), Math.floor(r() * 32), [120, 110, 100]); return im; };
  // Melt surface, bright with a dross skin at the rim.
  T.melt = () => {
    const im = img(16, 16), r = rng(2021);
    for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
      const d = Math.hypot(x + 0.5 - 8, y + 0.5 - 8) / 8;
      put(im, x, y, add(mix([255, 236, 160], [214, 96, 30], Math.min(1, d * 1.1)), (r() - 0.5) * 18));
    }
    return im;
  };
  // Firebrick lining at the lid, the flue hole and the rim.
  T.firebrick = () => {
    const im = img(32, 32), r = rng(2031);
    for (let y = 0; y < 32; y++) for (let x = 0; x < 32; x++) {
      const joint = y % 6 === 0 || (x + (Math.floor(y / 6) % 2) * 6) % 12 === 0;
      put(im, x, y, joint ? [124, 106, 88] : add([200, 170, 126], (r() - 0.5) * 16));
    }
    return im;
  };

  const MAT = {
    cs: { tex: 'crucible_furnace/casing', P: 16 }, cr: { tex: 'crucible_furnace/crucible', P: 16 },
    melt: { tex: 'crucible_furnace/melt', P: 16 }, fb: { tex: 'crucible_furnace/firebrick', P: 16 },
  };
  const mk = (particle) => Object.assign(new PCART.Model(MAT, {}), { particle: particle || 'crucible_furnace/casing' });

  function paint(root) {
    for (const n of Object.keys(T)) writePng(T[n](), path.join(root, TEX_REL, n + '.png'));
    return Object.keys(T).length;
  }

  function furnace(lit) {
    const m = mk();
    m.prismY('body', [8, 8], 7, 0, 11.6, 'cs', { seed: 5 });
    m.prismY('rim', [8, 8], 7.2, 11.6, 12, 'fb', { ends: ['up'] });
    for (const y of [1.6, 8.6]) m.prismY(`band_${y}`, [8, 8], 7.3, y, y + 1, 'ci', { ends: ['up', 'down'] });
    // lid: firebrick-lined cast ring round the flue hole, lever handle, hinge arm to the post
    for (const [n, f, t] of [['n', [2.4, 12, 2.4], [13.6, 13.4, 5.8]], ['s', [2.4, 12, 10.2], [13.6, 13.4, 13.6]],
      ['w', [2.4, 12, 5.8], [5.8, 13.4, 10.2]], ['e', [10.2, 12, 5.8], [13.6, 13.4, 10.2]]]) {
      m.box(`lid_${n}`, f, t, 'ci', { split: '', face: { down: 'fb' } });
    }
    m.box('flue_lining_n', [5.8, 12, 5.6], [10.2, 13.4, 5.8], 'fb', { split: '' });
    m.box('flue_lining_s', [5.8, 12, 10.2], [10.2, 13.4, 10.4], 'fb', { split: '' });
    if (lit) m.box('glow_flue', [5.9, 12.2, 5.9], [10.1, 12.3, 10.1], 'melt', { split: '', hide: ['down', 'north', 'south', 'east', 'west'] });
    m.box('lid_lever', [7.4, 13.4, 0.4], [8.6, 14.2, 3.2], 'st', { split: '' });
    m.box('lid_lever_grip', [7.2, 13.2, 0], [8.8, 14.4, 0.8], 'wood', { split: '' });
    m.box('hinge_arm', [12.4, 13.4, 11.6], [15, 14, 12.6], 'st', { split: '' });
    m.prismY('lid_post', [15, 12.6], 0.6, 0, 15, 'st');
    // tongs hanging on the post
    m.box('tongs_a', [15.6, 6, 13.2], [15.9, 14.6, 13.5], 'st', { split: '' });
    m.box('tongs_b', [15.6, 6, 11.7], [15.9, 14.6, 12], 'st', { split: '' });
    m.box('tongs_jaw', [15.6, 5.4, 11.4], [15.9, 6.2, 13.8], 'st', { split: '' });
    // blast at the back: tuyere pipe with damper; ash-pit door in front
    m.box('tuyere', [6.8, 2.4, 14.4], [9.2, 4.8, 16], 'ci', { split: '' });
    m.box('blast_pipe', [7.2, 4.8, 14.8], [8.8, 10, 16], 'st', { split: '' });
    m.box('damper', [6.6, 7, 14.6], [9.4, 7.6, 16], 'red', { split: '' });
    m.box('ash_door_frame', [5.4, 0.4, 0.6], [10.6, 4, 1.2], 'ci', { split: '' });
    m.box('ash_door', [5.9, 0.8, 0.4], [10.1, 3.6, 0.7], lit ? 'soot' : 'ci', { split: '' });
    m.box('ash_door_latch', [9, 2, 0.1], [9.8, 2.6, 0.4], 'bright', { split: '' });
    return m;
  }

  // Crucible partial: on a firebrick stand, the melt surface at the top.
  function crucible() {
    const m = mk('crucible_furnace/crucible');
    m.prismY('stand', [8, 8], 3, 0.4, 2.4, 'fb', { ends: [] });
    m.prismY('pot', [8, 8], 4, 2.4, 11.4, 'cr', { ends: [] });
    m.prismY('pot_rim', [8, 8], 4.3, 11, 11.6, 'cr', { ends: ['up'] });
    m.box('glow_melt', [5, 11.4, 5], [11, 11.5, 11], 'melt', { split: '', hide: ['down', 'north', 'south', 'east', 'west'] });
    m.box('lip', [7, 11.3, 3.4], [9, 11.9, 4.2], 'cr', { split: '' });
    return m;
  }

  // Panel board for FoundryPanels.CRUCIBLE (200x150): ring behind the melt dial, plates for the lamps, the bars,
  // the read-out and the slots.
  function guiCrucible() {
    const W = 200, H = 150, im = img(W, H), r = rng(2041);
    grain(im, [66, 58, 54], 2042, 3, 5, 12);
    for (let y = 0; y < H; y++) for (let x = 0; x < W; x++) {
      const d = Math.min(x, y, W - 1 - x, H - 1 - y);
      if (d < 4) put(im, x, y, add([170, 134, 70], (d === 0 ? -50 : d === 1 ? 30 : d === 3 ? -30 : 0) + (r() - 0.5) * 8));
    }
    for (let a = 0; a < 360; a += 0.4) { const t = a * Math.PI / 180; put(im, 40 + 34 * Math.cos(t), 56 + 34 * Math.sin(t), [36, 30, 28]); put(im, 40 + 35 * Math.cos(t) + 0.5, 56 + 35 * Math.sin(t) + 0.5, [130, 112, 100]); }
    const plate = (x0, y0, x1, y1, base) => {
      for (let y = y0; y < y1; y++) for (let x = x0; x < x1; x++) {
        const edge = x === x0 || y === y0 ? -22 : x === x1 - 1 || y === y1 - 1 ? 18 : 0;
        put(im, x, y, add(base, edge + (r() - 0.5) * 4));
      }
    };
    plate(80, 22, W - 6, 58, [54, 48, 44]);
    plate(80, 64, W - 6, 112, [54, 48, 44]);
    plate(6, 118, W - 6, 146, [50, 44, 40]);
    return im;
  }

  function paintGui(root) {
    writePng(guiCrucible(), path.join(root, PCART.ASSETS, 'textures/gui/panel/crucible_furnace.png'));
    return 1;
  }

  const BUILDS = {
    crucible_furnace: { model: () => furnace(false) },
    crucible_furnace_lit: { model: () => furnace(true) },
    'crucible_furnace/crucible': { model: crucible, java: 'crucible_furnace/partial/crucible' },
  };

  window.CRF = {
    paint, paintGui,
    build: (root, id) => PCART.realize(root, BUILDS[id].model(), id),
    save: (root, id) => PCART.save(root, 'foundry', id, BUILDS[id].java),
    preview(root) { return PCART.realize(root, furnace(true).merge(crucible(), 0, 0, 0, 'c_').merge(furnace(false), 20, 0, 0, 'u_'), 'crucible_preview'); },
  };
})();
