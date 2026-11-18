// Explosion barriers, hand-authored for Blockbench (replace the spec_metallurgy.py placeholders): a stone
// dust barrier and a water trough barrier, both hung under the roof of a roadway.
// Load inside Blockbench desktop, after the shared toolkit:
//   eval(require('fs').readFileSync('<repo>/art/blockbench/lib/pcart.js', 'utf8'))
//   eval(require('fs').readFileSync('<repo>/art/blockbench/mining/explosion_barrier.author.js', 'utf8'))
//   PCART.paintMaterials('<repo>'); EXB.paint('<repo>')
//   await EXB.exportAll('<repo>')
//
// 1x1x1, model x 0..16, y 0..16 (the roof is y 16), z 0..16; the roadway runs along z. Both barriers work by
// being knocked down by the pressure wave that runs ahead of the flame: the stone dust barrier's boards lie
// loose on their bearers, unnailed, so the wave throws the inert dust into the roadway; the water troughs
// hang on open hooks and tip. Shelves and troughs run across the roadway on bearers from roof hangers.
(function () {
  const path = require('path');
  const { rng, img, put, add, mix, rect, grain, writePng } = PCART;
  const TEX_REL = PCART.ASSETS + '/textures/block/explosion_barrier';
  const T = {};

  // Inert limestone dust heaped on the boards: off-white, finely grained, settled coal dust on top.
  T.dust = () => {
    const im = img(32, 32), r = grain(im, [208, 204, 194], 1501, 6, 6, 6);
    for (let k = 0; k < 40; k++) put(im, Math.floor(r() * 32), Math.floor(r() * 32), [140, 136, 128]);
    return im;
  };
  // Loose shelf boards, sawn, pale where the dust lies on them.
  T.board = () => {
    const im = img(32, 32), r = rng(1511);
    for (let y = 0; y < 32; y++) for (let x = 0; x < 32; x++) {
      put(im, x, y, add(mix([136, 112, 80], [176, 170, 156], 0.25), Math.sin((y + Math.sin(x * 0.3) * 2) * 1.1) * 7 + (r() - 0.5) * 10 - (x % 16 === 0 ? 26 : 0)));
    }
    return im;
  };
  // PVC trough, blue-grey, a darker seam at the rim.
  T.trough = () => {
    const im = img(32, 32), r = grain(im, [96, 130, 150], 1521, 3, 4, 8);
    for (let x = 0; x < 32; x++) { put(im, x, 0, [60, 86, 104]); put(im, x, 16, [60, 86, 104]); }
    return im;
  };
  // Water surface in the trough, dark with dust film and a lighter ripple.
  T.water = () => {
    const im = img(32, 32), r = rng(1531);
    for (let y = 0; y < 32; y++) for (let x = 0; x < 32; x++) put(im, x, y, add([44, 70, 86], Math.sin(x * 0.6 + y * 0.3) * 6 + (r() - 0.5) * 6));
    return im;
  };

  const MAT = {
    dust: { tex: 'explosion_barrier/dust', P: 16 }, board: { tex: 'explosion_barrier/board', P: 16 },
    tr: { tex: 'explosion_barrier/trough', P: 16 }, wat: { tex: 'explosion_barrier/water', P: 16 },
  };
  const mk = (particle) => Object.assign(new PCART.Model(MAT, {}), { particle: particle || 'explosion_barrier/board' });

  function paint(root) {
    for (const n of Object.keys(T)) writePng(T[n](), path.join(root, TEX_REL, n + '.png'));
    return Object.keys(T).length;
  }

  // Roof hangers and the cross bearers a shelf or rail sits on.
  function hangers(m, y) {
    for (const [s, x] of [['w', 1], ['e', 14]]) {
      m.box(`hanger_${s}`, [x, y, 7.4], [x + 1, 16, 8.6], 'st', { split: '' });
      m.box(`hanger_plate_${s}`, [x - 0.8, 15.6, 6.6], [x + 1.8, 16, 9.4], 'st', { split: '' });
    }
  }

  // Stone dust barrier: two tiers of loose boards on bearers, each with a heap of dust.
  function stone() {
    const m = mk();
    hangers(m, 7.6);
    for (const [k, y] of [[0, 8], [1, 11.4]]) {
      m.box(`bearer_${k}_n`, [0.4, y - 0.6, 3.6], [15.6, y, 4.4], 'st', { split: '' });
      m.box(`bearer_${k}_s`, [0.4, y - 0.6, 11.6], [15.6, y, 12.4], 'st', { split: '' });
      // boards along the roadway, lying loose side by side with a gap
      for (let b = 0; b < 4; b++) m.box(`board_${k}_${b}`, [1.6 + b * 3.25, y, 2.4], [4.6 + b * 3.25, y + 0.5, 13.6], 'board', { split: '' });
      // dust heaped highest along the middle
      m.box(`dust_${k}_a`, [2, y + 0.5, 3], [14, y + 1.2, 13], 'dust', { split: '' });
      m.box(`dust_${k}_b`, [3.4, y + 1.2, 4], [12.6, y + 1.8, 12], 'dust', { split: '' });
      m.box(`dust_${k}_c`, [5.2, y + 1.8, 5.4], [10.8, y + 2.2, 10.6], 'dust', { split: '' });
    }
    return m;
  }

  // Water barrier: an I-section rail under the roof, three troughs on open hooks.
  function water() {
    const m = mk('explosion_barrier/trough');
    hangers(m, 13.6);
    m.box('rail_web', [0, 13.6, 7.7], [16, 14.8, 8.3], 'st', { split: 'x' });
    m.box('rail_flange_lo', [0, 13.2, 7], [16, 13.6, 9], 'st', { split: 'x' });
    m.box('rail_flange_hi', [0, 14.8, 7], [16, 15.2, 9], 'st', { split: 'x' });
    for (const [i, x] of [[0, 0.6], [1, 5.6], [2, 10.6]]) {
      // trough body narrower at the bottom; rim lips; water a little below the rim
      m.box(`trough_${i}_bottom`, [x + 0.8, 7.6, 3.6], [x + 4, 8.2, 12.4], 'tr', { split: '' });
      m.box(`trough_${i}_body`, [x + 0.4, 8.2, 3.2], [x + 4.4, 11.6, 12.8], 'tr', { split: '', hide: ['up'] });
      m.box(`trough_${i}_rim_n`, [x, 11.6, 2.8], [x + 4.8, 12, 3.6], 'tr', { split: '' });
      m.box(`trough_${i}_rim_s`, [x, 11.6, 12.4], [x + 4.8, 12, 13.2], 'tr', { split: '' });
      m.box(`trough_${i}_water`, [x + 0.5, 11.1, 3.6], [x + 4.3, 11.2, 12.4], 'wat', { split: '', hide: ['down', 'north', 'south', 'east', 'west'] });
      for (const z of [5, 11]) {
        m.box(`hook_${i}_${z}`, [x + 2.2, 12, z - 0.2], [x + 2.6, 13.4, z + 0.2], 'bright', { split: '' });
        m.box(`hook_${i}_${z}_bar`, [x + 2.2, 13.2, Math.min(z, 8)], [x + 2.6, 13.4, Math.max(z, 8)], 'bright', { split: '' });
      }
    }
    return m;
  }

  const BUILDS = { explosion_barrier: { model: stone }, explosion_barrier_water: { model: water } };

  window.EXB = {
    paint,
    build: (root, id) => PCART.realize(root, BUILDS[id].model(), id),
    exportAll: (root) => PCART.exportAll(root, 'mining', BUILDS),
    preview(root) { return PCART.realize(root, stone().merge(water(), 20, 0, 0, 'w_'), 'explosion_barrier_preview'); },
  };
})();
