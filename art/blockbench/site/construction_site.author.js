// Construction site block (real-scale series, M5) and the design drawing item: a timber pallet on the ground, a
// drafting desk at the front with the drawing pinned on its sloped top, the site board with hazard stripes at the
// back on two posts, a red-and-white survey stake. 32 px per block. The script also paints the item icon of the
// design drawings (a blueprint sheet, rolled at the top). Load after pcart.js, then SITE.paint(repo);
// SITE.build(repo); /* wait */ SITE.save(repo).
(function () {
  const path = require('path');
  const { img, put, rect, grain, writePng, ASSETS } = PCART;
  const BLUE = [38, 78, 150], LINE = [214, 228, 246], DIM = [120, 156, 206];

  // Blueprint sheet: blue paper, a faint grid, a vessel in elevation with a nozzle and a dimension line, title block.
  function blueprint(n) {
    const im = img(n, n), k = n / 32;
    grain(im, BLUE, 11, 4, 3, 8);
    for (let i = 0; i < n; i += 4 * k) for (let j = 0; j < n; j++) { put(im, i, j, DIM); put(im, j, i, DIM); }
    const L = (x0, y0, x1, y1) => {
      const s = Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0)) * k;
      for (let t = 0; t <= s; t++) put(im, Math.round((x0 + (x1 - x0) * t / s) * k), Math.round((y0 + (y1 - y0) * t / s) * k), LINE);
    };
    // vessel: shell, heads, skirt, a nozzle; a dimension line on the left
    L(11, 7, 11, 22); L(19, 7, 19, 22); L(11, 7, 13, 5); L(13, 5, 17, 5); L(17, 5, 19, 7);
    L(11, 22, 13, 24); L(13, 24, 17, 24); L(17, 24, 19, 22); L(12, 24, 12, 27); L(18, 24, 18, 27); L(10, 27, 20, 27);
    L(19, 12, 23, 12); L(19, 14, 23, 14); L(23, 11, 23, 15);
    L(7, 5, 7, 27); L(6, 5, 8, 5); L(6, 27, 8, 27);
    // title block
    L(22, 24, 30, 24); L(22, 24, 22, 30); L(22, 27, 30, 27); for (let x = 23; x < 30; x += 2) L(x, 28, x + 1, 28);
    L(1, 1, 30, 1); L(1, 1, 1, 30); L(30, 1, 30, 30); L(1, 30, 30, 30);
    return im;
  }

  // Hazard stripes, yellow and black at 45 degrees.
  function hazard() {
    const im = img(32, 32);
    for (let y = 0; y < 32; y++) for (let x = 0; x < 32; x++) put(im, x, y, ((x + y) >> 3) % 2 ? [24, 24, 26] : [226, 182, 34]);
    return im;
  }

  // Site board: hazard frame round a white field with a crane pictogram and three lines of "text".
  function board() {
    const im = hazard();
    rect(im, 3, 3, 26, 26, [236, 234, 226]);
    const K = [30, 30, 34];
    rect(im, 7, 6, 2, 13, K); rect(im, 7, 6, 15, 2, K); rect(im, 19, 8, 1, 6, K); rect(im, 18, 14, 3, 2, K); rect(im, 5, 18, 6, 2, K);
    for (const [y, w] of [[22, 18], [24, 14], [26, 16]]) rect(im, 7, y, w, 1, [70, 70, 78]);
    return im;
  }

  // Item icon: a blueprint sheet tilted on transparent ground, its top rolled.
  function icon() {
    const src = blueprint(32), im = img(32, 32);
    for (let y = 0; y < 32; y++) for (let x = 0; x < 32; x++) {
      const u = Math.round(x + (y - 16) * 0.12 - 2), v = Math.round((y - 7) * 32 / 23);
      if (u < 3 || u > 28 || v < 0 || v > 31 || y < 7 || y > 29) continue;
      const i = (v * 32 + u) * 4;
      put(im, x, y, [src.d[i], src.d[i + 1], src.d[i + 2]]);
    }
    for (let x = 2; x < 30; x++) for (let y = 3; y < 8; y++) {           // the roll
      const t = (y - 3) / 4, c = Math.round(60 + 120 * Math.sin(Math.PI * t));
      put(im, x, y, [c * 0.4 | 0, c * 0.65 | 0, Math.min(255, c * 1.25) | 0]);
    }
    for (let y = 3; y < 8; y++) { put(im, 1, y, [20, 34, 70]); put(im, 30, y, [20, 34, 70]); }
    for (let y = 8; y < 30; y++) for (let x = 0; x < 32; x++) {           // outline
      const a = im.d[(y * 32 + x) * 4 + 3], l = x > 0 ? im.d[(y * 32 + x - 1) * 4 + 3] : 0, r = x < 31 ? im.d[(y * 32 + x + 1) * 4 + 3] : 0;
      if (a && (!l || !r)) put(im, x, y, [18, 32, 66]);
    }
    return im;
  }

  function paint(root) {
    const T = path.join(root, ASSETS, 'textures');
    writePng(blueprint(32), path.join(T, 'block/site/blueprint.png'));
    writePng(hazard(), path.join(T, 'block/site/hazard.png'));
    writePng(board(), path.join(T, 'block/site/board.png'));
    writePng(icon(), path.join(T, 'item/design_drawing.png'));
    return 4;
  }

  const MAT = { bp: { tex: 'site/blueprint', P: 16 }, hz: { tex: 'site/hazard', P: 16 }, bd: { tex: 'site/board', P: 16 } };

  function model() {
    const m = new PCART.Model(MAT);
    m.particle = 'material/lagging_wood';
    // pallet: two bearers and five deck boards
    for (const x of [1, 13]) m.box(`bearer_${x}`, [x, 0, 0], [x + 2, 1, 16], 'wood', { split: '' });
    for (const z of [0, 3.5, 7, 10.5, 13.5]) m.box(`deck_${z}`, [0, 1, z], [16, 2, z + 2.5], 'wood', { split: '' });
    // drafting desk at the front: four legs, a sloped top with the drawing, a ledge
    for (const [x, z] of [[2, 2], [13, 2], [2, 7], [13, 7]]) m.box(`leg_${x}_${z}`, [x, 2, z], [x + 1, z < 5 ? 8 : 10, z + 1], 'st', { split: '' });
    m.box('desk', [1.5, 8.5, 1], [14.5, 9.5, 9], 'wood', {
      split: '', rot: { axis: 'x', angle: -22.5, origin: [8, 9, 5] },
      face: { up: { tex: 'site/blueprint', uv: [0, 0, 16, 16] } },
    });
    m.box('ledge', [1.5, 7.2, 0.4], [14.5, 8.2, 1.2], 'st', { split: '' });
    // site board on two posts at the back, facing north
    for (const x of [2, 13]) m.box(`post_${x}`, [x, 2, 12], [x + 1, 15, 13], 'wood', { split: '' });
    m.box('board', [1, 8, 11.4], [15, 15, 12], 'hz', { split: '', face: { north: { tex: 'site/board', uv: [0, 0, 16, 16] } } });
    // survey stake, red and white
    for (let k = 0; k < 4; k++) m.box(`stake_${k}`, [14.6, 2 + k * 2.5, 9.6], [15.4, 4.5 + k * 2.5, 10.4], k % 2 ? 'bright' : 'red', { split: '' });
    return m;
  }

  window.SITE = {
    model, paint,
    build: (root) => { PCART.realize(root, model(), 'construction_site'); return Outliner.elements.length; },
    save: (root) => PCART.save(root, 'site', 'construction_site'),
  };
})();
