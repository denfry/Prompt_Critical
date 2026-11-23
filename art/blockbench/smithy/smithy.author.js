// Hand stage of GDD 18 (§18.6.1–18.6.4, §18.7): bloomery, forge hearth, smithing anvil, their panel boards and the
// item icons of the workpiece, the smith's hammer and the shaft. Hand-authored for Blockbench / pcart_headless.
//   node art/tools/pcart_headless.js <repo> smithy/smithy.author.js SMY --paint
// All blocks 1x1x1, model x 0..16, y 0..16, z 0..16, front to the north.
//  - bloomery: a squat clay-daubed brick shaft on a stone footing, charging mouth on top, tap arch low in front,
//    a tuyere from leather bellows on the west side. Lit: glow in the mouth and behind the tap arch.
//  - forge_hearth: a stone hearth table with a firepot of coals, a brick hood and chimney at the back, bellows on the
//    east side with a blast pipe into the firepot. Lit: the coals glow.
//  - smithing_anvil: a London-pattern anvil of wrought iron with a steel face, horn to the west, on an oak stump.
(function () {
  const path = require('path');
  const { rng, img, put, add, mix, grain, writePng } = PCART;
  const TEX_REL = PCART.ASSETS + '/textures/block/smithy';
  const T = {};

  // Clay daub over brick: ochre, cracked, scorched dark towards the top.
  T.daub = () => {
    const im = img(32, 32), r = rng(5101);
    for (let y = 0; y < 32; y++) for (let x = 0; x < 32; x++) {
      const crack = (x * 7 + y * 3) % 23 === 0 || (x + y * 5) % 29 === 0;
      const base = mix([168, 126, 84], [70, 52, 40], Math.max(0, (8 - y) / 8) * 0.7);
      put(im, x, y, crack ? add(base, -40) : add(base, (r() - 0.5) * 22));
    }
    return im;
  };
  // Glowing charcoal: orange cores, black crust.
  T.coals = () => {
    const im = img(16, 16), r = rng(5111);
    for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
      const v = r();
      put(im, x, y, v > 0.55 ? mix([255, 200, 80], [230, 90, 20], r()) : v > 0.25 ? [120, 40, 16] : [30, 24, 22]);
    }
    return im;
  };
  // Dead coals for the unlit hearth.
  T.coals_dead = () => { const im = img(16, 16), r = rng(5112); for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) put(im, x, y, add([38, 34, 32], (r() - 0.5) * 24)); return im; };
  // Bellows leather: brown, folds every 4 px.
  T.leather = () => {
    const im = img(16, 16), r = rng(5121);
    for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) put(im, x, y, add(y % 4 === 0 ? [74, 46, 28] : [116, 74, 44], (r() - 0.5) * 12));
    return im;
  };
  // Anvil body: dark wrought iron; face: ground bright steel.
  T.anvil = () => { const im = img(16, 16); grain(im, [58, 58, 62], 5131, 3, 4, 6); return im; };
  T.anvil_face = () => { const im = img(16, 16), r = rng(5141); for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) put(im, x, y, add([176, 178, 184], (r() - 0.5) * 16 + (x === y ? 18 : 0))); return im; };
  // Oak stump: end grain rings on top, bark on the sides.
  T.stump_top = () => {
    const im = img(16, 16), r = rng(5151);
    for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
      const d = Math.hypot(x + 0.5 - 8, y + 0.5 - 8);
      put(im, x, y, d > 7 ? [70, 50, 34] : add(Math.floor(d) % 2 ? [168, 132, 86] : [146, 110, 70], (r() - 0.5) * 10));
    }
    return im;
  };
  T.bark = () => { const im = img(16, 16), r = rng(5161); for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) put(im, x, y, add(x % 3 === 0 ? [58, 42, 30] : [88, 66, 46], (r() - 0.5) * 14)); return im; };

  const MAT = {
    daub: { tex: 'smithy/daub', P: 16 }, coals: { tex: 'smithy/coals', P: 16 }, dead: { tex: 'smithy/coals_dead', P: 16 },
    lea: { tex: 'smithy/leather', P: 16 }, anv: { tex: 'smithy/anvil', P: 16 }, face: { tex: 'smithy/anvil_face', P: 16 },
    stump: { tex: 'smithy/stump_top', P: 16 }, bark: { tex: 'smithy/bark', P: 16 },
  };
  const mk = (particle) => Object.assign(new PCART.Model(MAT, {}), { particle: particle || 'smithy/daub' });

  function paint(root) {
    for (const n of Object.keys(T)) writePng(T[n](), path.join(root, TEX_REL, n + '.png'));
    return Object.keys(T).length;
  }

  // Leather bellows with a wooden head and board, nozzle pointing along +x (rotated by the caller's position).
  function bellows(m, x0, y0, z0, lit) {
    m.box('bellows_board_top', [x0, y0 + 3.4, z0], [x0 + 5, y0 + 3.9, z0 + 4], 'wood', { split: '' });
    m.box('bellows_board_bot', [x0, y0, z0], [x0 + 5, y0 + 0.5, z0 + 4], 'wood', { split: '' });
    m.box('bellows_leather', [x0 + 0.4, y0 + 0.5, z0 + 0.3], [x0 + 4.6, y0 + 3.4, z0 + 3.7], 'lea', { split: '' });
    m.box('bellows_handle', [x0 - 2.4, y0 + 3.5, z0 + 1.6], [x0 + 0.2, y0 + 3.9, z0 + 2.4], 'wood', { split: '' });
  }

  function bloomery(lit) {
    const m = mk();
    m.box('footing', [1, 0, 1], [15, 1.4, 15], 'ashlar', { split: '' });
    m.prismY('shaft', [8, 8], 6.4, 1.4, 14.6, 'daub', { seed: 3 });
    m.prismY('collar', [8, 8], 6.7, 13.8, 14.8, 'brick', { ends: ['up', 'down'] });
    m.prismY('mouth', [8, 8], 3, 14.8, 15, lit ? 'coals' : 'dead', { ends: ['up'] });
    if (lit) m.box('glow_mouth', [5.4, 14.85, 5.4], [10.6, 14.9, 10.6], 'coals', { split: '', hide: ['down', 'north', 'south', 'east', 'west'] });
    // tap arch in front, plugged with clay; glows when lit
    m.box('tap_frame', [5.6, 1.4, 1.2], [10.4, 5.6, 1.8], 'brick', { split: '' });
    m.box(lit ? 'glow_tap' : 'tap_plug', [6.4, 1.6, 1.0], [9.6, 4.8, 1.4], lit ? 'coals' : 'daub', { split: '' });
    // tuyere from the bellows on the west side
    m.box('tuyere', [0.2, 3.2, 7.2], [2.2, 4.6, 8.8], 'daub', { split: '' });
    bellows(m, 0.2, 1.4, 11, lit);
    m.box('blast_pipe', [1.6, 3.4, 8.6], [2.4, 4.2, 11], 'ci', { split: '' });
    return m;
  }

  function forge(lit) {
    const m = mk('smithy/coals_dead');
    m.box('table', [0.5, 0, 0.5], [15.5, 9, 15.5], 'ashlar', { split: '' });
    m.box('rim_n', [0.5, 9, 0.5], [15.5, 10, 2], 'brick', { split: '' });
    m.box('rim_w', [0.5, 9, 2], [2, 10, 12], 'brick', { split: '' });
    m.box('rim_e', [14, 9, 2], [15.5, 10, 12], 'brick', { split: '' });
    m.box(lit ? 'glow_coals' : 'coals', [2, 8.8, 2], [14, 9.6, 12], lit ? 'coals' : 'dead', { split: '', hide: ['down', 'north', 'south', 'east', 'west'] });
    // brick hood and chimney at the back
    m.box('hood', [2, 10, 11], [14, 13.5, 15.5], 'brick', { split: '' });
    m.box('chimney', [5, 13.5, 12], [11, 16, 15.5], 'brick', { split: '' });
    m.box('hood_lip', [2.5, 12.6, 10.4], [13.5, 13.4, 11], 'soot', { split: '' });
    // blast pipe from the bellows on the east into the firepot
    bellows(m, 10.6, 10, 2.6, lit);
    m.box('blast_pipe', [12, 9.4, 4.2], [12.8, 10.6, 5], 'ci', { split: '' });
    // tongs and a quenching ladle lying on the rim
    m.box('tongs', [1, 10, 4], [1.6, 10.3, 11], 'st', { split: '' });
    return m;
  }

  function anvil() {
    const m = mk('smithy/anvil');
    m.prismY('stump', [8, 8], 5.4, 0, 6, 'bark', { ends: [] });
    m.prismY('stump_top', [8, 8], 5.4, 5.9, 6, 'stump', { ends: ['up'] });
    m.box('foot', [4, 6, 5], [13, 7.6, 11], 'anv', { split: '' });
    m.box('waist', [6, 7.6, 6.2], [11, 10.4, 9.8], 'anv', { split: '' });
    m.box('body', [3, 10.4, 5.2], [14.6, 12.6, 10.8], 'anv', { split: '' });
    m.box('face', [5, 12.6, 5.4], [14.4, 13, 10.6], 'face', { split: '' });
    m.box('horn', [0.6, 11, 6.4], [3, 12.4, 9.6], 'anv', { split: '' });
    m.box('horn_tip', [0, 11.4, 7.2], [0.6, 12, 8.8], 'anv', { split: '' });
    m.box('hardy_hole', [12.4, 12.95, 7.4], [13.4, 13.05, 8.4], 'soot', { split: '', hide: ['down', 'north', 'south', 'east', 'west'] });
    return m;
  }

  // Panel boards 200x150 in the style of the foundry boards: brass frame, dark plates under the bars and buttons.
  function board(seed, plates) {
    const W = 200, H = 150, im = img(W, H), r = rng(seed);
    grain(im, [62, 54, 50], seed + 1, 3, 5, 12);
    for (let y = 0; y < H; y++) for (let x = 0; x < W; x++) {
      const d = Math.min(x, y, W - 1 - x, H - 1 - y);
      if (d < 4) put(im, x, y, add([170, 134, 70], (d === 0 ? -50 : d === 1 ? 30 : d === 3 ? -30 : 0) + (r() - 0.5) * 8));
    }
    for (const [x0, y0, x1, y1] of plates) for (let y = y0; y < y1; y++) for (let x = x0; x < x1; x++) {
      const edge = x === x0 || y === y0 ? -22 : x === x1 - 1 || y === y1 - 1 ? 18 : 0;
      put(im, x, y, add([50, 44, 40], edge + (r() - 0.5) * 4));
    }
    return im;
  }

  function paintGui(root) {
    const out = (n, im) => writePng(im, path.join(root, PCART.ASSETS, 'textures/gui/panel/' + n + '.png'));
    out('bloomery', board(5201, [[80, 18, 194, 104], [6, 106, 194, 146]]));
    out('forge_hearth', board(5211, [[80, 18, 194, 84], [6, 88, 194, 120], [6, 122, 194, 146]]));
    out('smithing_anvil', board(5221, [[6, 6, 194, 92], [6, 94, 194, 134], [6, 134, 194, 146]]));
    return 3;
  }

  // Item icons 16x16 and their item/generated models.
  function icon(draw) { const im = img(16, 16); draw(im); return im; }
  const ICONS = {
    workpiece: icon((im) => { for (let y = 6; y < 10; y++) for (let x = 2; x < 14; x++) put(im, x, y, y === 6 ? [120, 116, 112] : y === 9 ? [40, 38, 38] : [78, 74, 72]); }),
    smith_hammer: icon((im) => {
      for (let i = 0; i < 9; i++) put(im, 4 + i, 12 - i, [134, 96, 58]);
      for (let y = 2; y < 6; y++) for (let x = 9; x < 15; x++) put(im, x, y, y === 2 ? [170, 170, 176] : [96, 96, 102]);
    }),
    shaft: icon((im) => {
      for (let i = 1; i < 15; i++) { put(im, i, 15 - i, [150, 150, 158]); put(im, i, 14 - i, [104, 104, 112]); }
      for (let i = 4; i < 7; i++) put(im, i, 15 - i - 1, [70, 70, 78]);
    }),
  };

  function paintItems(root) {
    for (const [n, im] of Object.entries(ICONS)) {
      writePng(im, path.join(root, PCART.ASSETS, 'textures/item/' + n + '.png'));
      require('fs').writeFileSync(path.join(root, PCART.ASSETS, 'models/item/' + n + '.json'),
        JSON.stringify({ parent: 'minecraft:item/generated', textures: { layer0: 'promptcritical:item/' + n } }, null, 2) + '\n');
    }
    return Object.keys(ICONS).length;
  }

  const BUILDS = {
    bloomery: { model: () => bloomery(false) }, bloomery_lit: { model: () => bloomery(true) },
    forge_hearth: { model: () => forge(false) }, forge_hearth_lit: { model: () => forge(true) },
    smithing_anvil: { model: anvil },
  };

  window.SMY = {
    paint(root) { return paint(root) + paintGui(root) + paintItems(root); }, paintGui, paintItems, BUILDS, ids: Object.keys(BUILDS),
    build: (root, id) => PCART.realize(root, BUILDS[id].model(), id),
    save: (root, id) => PCART.save(root, 'smithy', id, BUILDS[id].java),
    // синхронно через PCART.realize/save на момент вызова: headless-раннер подменяет их своими
    exportAll: (root, ids) => {
      const out = {};
      for (const id of ids || Object.keys(BUILDS)) { PCART.realize(root, BUILDS[id].model(), id); out[id] = PCART.save(root, 'smithy', id, BUILDS[id].java); }
      return out;
    },
  };
})();
